package com.nibras.terminal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * App Manager / APK Extractor / APKTool M style helpers served to the terminal through the API bridge:
 *   apps list [-s|-a] [filter]       installed apps (user apps by default)
 *   apps info <package>              version, SDKs, installer, paths, signer SHA-256, permissions, components
 *   apps extract <package> <dir>     copy base.apk (+ splits as one .apks) of an installed app
 *   apps manifest <package>          requested permissions + exported components (security review of your apps)
 *   install-apks <file...>           install split APKs / .apks / .xapk / .apkm (unencrypted) in one session
 */
object AppTools {
    private const val FLAGS = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS

    @Suppress("DEPRECATION")
    private fun info(context: Context, pkg: String, flags: Int = 0): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else context.packageManager.getPackageInfo(pkg, flags)

    @Suppress("DEPRECATION")
    private fun signer(context: Context, pkg: String): String = runCatching {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val p = info(context, pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            p.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else info(context, pkg, PackageManager.GET_SIGNATURES).signatures
        sigs.orEmpty().joinToString("\n  ") { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(":") { "%02X".format(it) } }
    }.getOrDefault("?")

    fun apps(context: Context, args: List<String>, toHost: (String) -> File?): String {
        val pm = context.packageManager
        return when (args.firstOrNull() ?: "list") {
            "list", "ls" -> {
                val all = "-a" in args; val sys = "-s" in args
                val filter = args.drop(1).firstOrNull { !it.startsWith("-") }?.lowercase()
                @Suppress("DEPRECATION")
                val list = pm.getInstalledPackages(0).filter { p ->
                    val isSys = (p.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                    all || (sys == isSys)
                }.map { p -> Triple(p.packageName, p.applicationInfo?.loadLabel(pm)?.toString() ?: p.packageName, p.versionName ?: "") }
                    .filter { filter == null || it.first.lowercase().contains(filter) || it.second.lowercase().contains(filter) }
                    .sortedBy { it.second.lowercase() }
                "OK\n" + list.joinToString("\n") { "%-45s %-28s %s".format(it.first, it.second.take(28), it.third) } + "\n(${list.size})"
            }
            "info" -> {
                val pkg = args.getOrNull(1) ?: return "ERR\nusage: apps info <package>"
                val p = runCatching { info(context, pkg, FLAGS) }.getOrElse { return "ERR\nnot installed: $pkg" }
                val a = p.applicationInfo!!
                val code = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
                val installer = runCatching { if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg) }.getOrNull()
                val perms = p.requestedPermissions.orEmpty().mapIndexed { i, n ->
                    val granted = (p.requestedPermissionsFlags?.getOrNull(i) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                    (if (granted) "  ✓ " else "  · ") + n
                }
                fun comps(title: String, xs: Array<out android.content.pm.ComponentInfo>?) =
                    "$title (${xs?.size ?: 0}):\n" + xs.orEmpty().joinToString("\n") { "  " + (if (it.exported) "⇢ " else "  ") + it.name }
                buildString {
                    append("OK\n${a.loadLabel(pm)}  ($pkg)\n")
                    append("version: ${p.versionName} ($code)\nminSdk: ${if (Build.VERSION.SDK_INT >= 24) a.minSdkVersion else "?"}  targetSdk: ${a.targetSdkVersion}\n")
                    append("installer: ${installer ?: "-"}\nsystem: ${a.flags and ApplicationInfo.FLAG_SYSTEM != 0}  debuggable: ${a.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0}\n")
                    append("apk: ${a.sourceDir}\n"); a.splitSourceDirs?.forEach { append("split: $it\n") }
                    append("data: ${a.dataDir}\nnative libs: ${a.nativeLibraryDir}\n")
                    append("first install: ${java.util.Date(p.firstInstallTime)}\nupdated: ${java.util.Date(p.lastUpdateTime)}\n")
                    append("signer SHA-256:\n  ${signer(context, pkg)}\n")
                    append("permissions (${perms.size}, ✓ granted):\n${perms.joinToString("\n")}\n")
                    append(comps("activities", p.activities) + "\n" + comps("services", p.services) + "\n" + comps("receivers", p.receivers) + "\n" + comps("providers", p.providers))
                    append("\n(⇢ = exported)")
                }
            }
            "manifest", "exported" -> {
                val pkg = args.getOrNull(1) ?: return "ERR\nusage: apps exported <package>"
                val p = runCatching { info(context, pkg, FLAGS) }.getOrElse { return "ERR\nnot installed: $pkg" }
                val ex = listOfNotNull(p.activities, p.services, p.receivers, p.providers).flatMap { it.toList() }.filter { it.exported }
                "OK\nexported components of $pkg (${ex.size}):\n" + ex.joinToString("\n") { "  ${it.javaClass.simpleName.removeSuffix("Info")}: ${it.name}" + ((it as? android.content.pm.ActivityInfo)?.permission?.let { p -> "  [needs $p]" } ?: "") }
            }
            "extract", "backup" -> {
                val pkg = args.getOrNull(1) ?: return "ERR\nusage: apps extract <package> [dir]"
                val dir = toHost(args.getOrNull(2) ?: ".") ?: return "ERR\nbad directory"
                val p = runCatching { info(context, pkg) }.getOrElse { return "ERR\nnot installed: $pkg" }
                val a = p.applicationInfo!!; dir.mkdirs()
                val name = "$pkg-${p.versionName ?: "x"}".replace(Regex("[^A-Za-z0-9._-]"), "_")
                val splits = a.splitSourceDirs.orEmpty()
                if (splits.isEmpty()) {
                    val out = File(dir, "$name.apk"); File(a.sourceDir).copyTo(out, true); "OK\n${out.name} (${out.length() / 1024} KB)"
                } else {
                    val out = File(dir, "$name.apks")
                    ZipOutputStream(out.outputStream().buffered()).use { z ->
                        (listOf(a.sourceDir) + splits).forEach { src ->
                            z.putNextEntry(ZipEntry(File(src).name)); File(src).inputStream().use { it.copyTo(z) }; z.closeEntry()
                        }
                    }
                    "OK\n${out.name} (${out.length() / 1024} KB, base + ${splits.size} splits) — install with: nibras-apk install-split ${out.name}  ·  one APK: nibras-apk merge ${out.name}"
                }
            }
            else -> "ERR\nusage: apps list [-s|-a] [filter] | info <pkg> | exported <pkg> | extract <pkg> [dir]"
        }
    }

    /** Installs one app from several APK files or a bundle archive (.apks/.xapk/.apkm) with a PackageInstaller session. */
    fun installSplits(context: Context, files: List<File>): String {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "ERR\nAllow \"Install unknown apps\" for Nibras Terminal (screen opened), then run again"
        }
        val tmp = File(context.cacheDir, "splits").apply { deleteRecursively(); mkdirs() }
        val apks = mutableListOf<File>()
        for (f in files) {
            if (!f.isFile) return "ERR\nno such file: ${f.name}"
            if (f.name.endsWith(".apk", true)) { apks += f; continue }
            try {
                ZipFile(f).use { z ->
                    z.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.forEach { e ->
                        val out = File(tmp, e.name.substringAfterLast('/')); z.getInputStream(e).use { i -> out.outputStream().use { i.copyTo(it) } }; apks += out
                    }
                }
            } catch (t: Throwable) { return "ERR\n${f.name}: not a readable bundle (encrypted .apkm files cannot be opened): ${t.message}" }
        }
        if (apks.isEmpty()) return "ERR\nno APK inside"
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            apks.forEach { a -> a.inputStream().use { i -> s.openWrite(a.name, 0, a.length()).use { o -> i.copyTo(o); s.fsync(o) } } }
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
            s.commit(pi.intentSender)
        }
        return "OK\ninstalling ${apks.size} APK file(s): ${apks.joinToString(" ") { it.name }} — confirm on screen"
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> android.widget.Toast.makeText(context, "✓ " + (intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: "installed"), android.widget.Toast.LENGTH_LONG).show()
            else -> android.widget.Toast.makeText(context, "✗ " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "install failed"), android.widget.Toast.LENGTH_LONG).show()
        }
        File(context.cacheDir, "splits").deleteRecursively()
    }
}

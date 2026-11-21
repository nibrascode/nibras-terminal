package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.WebView
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Prefs
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.ui.HostPaths
import com.termux.terminal.TerminalSession
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.thread

/** The one release certificate. An update whose signature is different is refused. */
object ReleaseCert {
    const val SHA256 = "5E93EB4DF15A6496D892D2BCD088D50F2F69A7F1F8A1115AE693D19CE48593C9"
}

object GuestMode {
    fun on(context: Context) = Prefs(context).sp.getBoolean("guest", false)
    fun set(context: Context, on: Boolean) = Prefs(context).sp.edit().putBoolean("guest", on).apply()
}

object AllowFile {
    fun file(context: Context) = File(Rootfs.homeOf(context), ".nibras/allow")

    /** Null when the user has not created a list. An empty file allows nothing automatic. */
    fun lines(context: Context): List<String>? {
        val f = file(context)
        if (!f.isFile) return null
        return f.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() && PlusRules.bare(it) !in Sensitive.names }
    }

    fun automatic(context: Context, command: String) =
        PlusRules.allowAutomatic(command, lines(context), Sensitive.names)

    /** Boot scripts: a private phone command inside the file is never started. A list, when present, names the scripts. */
    fun bootOk(context: Context, script: File): Boolean {
        val text = runCatching { script.readText().take(20_000) }.getOrDefault("")
        if (Sensitive.names.any { n -> text.contains("nibras-$n") || text.contains("termux-$n") }) return false
        val list = lines(context) ?: return true
        return list.any { it == script.name || it == "boot/${script.name}" }
    }
}

object ApkInspect {
    fun diff(context: Context, old: File, new: File): String {
        val a = read(context, old) ?: return "not an APK: ${old.name}"
        val b = read(context, new) ?: return "not an APK: ${new.name}"
        val added = b.permissions - a.permissions
        val removed = a.permissions - b.permissions
        val sameCert = a.cert != null && a.cert == b.cert
        return buildString {
            appendLine("${a.pkg} ${a.version}  →  ${b.pkg} ${b.version}")
            appendLine("signature: " + when {
                a.cert == null || b.cert == null -> "could not be read"
                sameCert -> "same"
                else -> "CHANGED"
            })
            appendLine("old ${a.cert ?: "?"}")
            appendLine("new ${b.cert ?: "?"}")
            appendLine("permissions added: ${added.ifEmpty { setOf("none") }.joinToString(" ")}")
            appendLine("permissions removed: ${removed.ifEmpty { setOf("none") }.joinToString(" ")}")
            if (!sameCert && a.pkg == b.pkg) appendLine("This is not an update. Android will ask you to uninstall the old app first.")
        }.trimEnd()
    }

    /** Empty when the package is new or the certificate matches. Otherwise the warning the user must accept. */
    fun replaceWarning(context: Context, apk: File): String? {
        val info = read(context, apk) ?: return null
        val installed = installed(context, info.pkg) ?: return null
        if (installed.cert == null || info.cert == null || installed.cert == info.cert) return null
        return "The installed ${info.pkg} was signed by a different key.\nInstalled ${installed.cert}\nThis file ${info.cert}\nAndroid will not update it in place."
    }

    private data class Meta(val pkg: String, val version: String, val permissions: Set<String>, val cert: String?)

    private fun read(context: Context, file: File): Meta? {
        if (!file.isFile) return null
        val flags = (PackageManager.GET_PERMISSIONS.toLong()) or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES.toLong() else PackageManager.GET_SIGNATURES.toLong())
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.path, flags.toInt())
        } ?: return null
        return meta(info)
    }

    private fun installed(context: Context, pkg: String): Meta? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, flags)
        }
        meta(info)
    }.getOrNull()

    private fun meta(info: PackageInfo): Meta {
        val perms = info.requestedPermissions?.toSet().orEmpty()
        val certs = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
        val cert = certs?.firstOrNull()?.toByteArray()?.let { sha256(it) }
        val ver = info.versionName ?: "?"
        return Meta(info.packageName ?: "?", ver, perms, cert)
    }

    fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
    fun sha256(file: File) = sha256(file.readBytes())
}

object Updater {
    private const val REPO = "https://api.github.com/repos/nibrascode/nibras-terminal/releases/latest"
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()

    fun run(context: Context): String = runCatching { check(context) }.getOrElse { "Update failed: ${it.message ?: it.javaClass.simpleName}" }

    private fun check(context: Context): String {
        val body = http.newCall(Request.Builder().url(REPO).header("User-Agent", "Nibras-Terminal").header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        val json = JSONObject(body)
        val ver = json.optString("tag_name").removePrefix("v")
        val current = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        if (!newer(ver, current)) return "You have $current. Nothing newer."
        val assets = json.getJSONArray("assets")
        var apkUrl = ""
        var sumUrl = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            when (a.optString("name")) {
                "nibras-terminal.apk" -> apkUrl = a.optString("browser_download_url")
                "SHA256SUMS" -> sumUrl = a.optString("browser_download_url")
            }
        }
        if (!apkUrl.startsWith("https://")) error("no APK on the release")
        val dest = File(context.cacheDir, "shared").apply { mkdirs() }
        val apk = File(dest, "nibras-terminal-$ver.apk")
        http.newCall(Request.Builder().url(apkUrl).header("User-Agent", "Nibras-Terminal").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.byteStream()?.use { input -> apk.outputStream().use { input.copyTo(it) } } ?: error("empty")
        }
        if (sumUrl.startsWith("https://")) {
            val sums = http.newCall(Request.Builder().url(sumUrl).header("User-Agent", "Nibras-Terminal").build()).execute().use { it.body?.string().orEmpty() }
            val want = sums.lineSequence().firstOrNull { it.contains("nibras-terminal.apk") }?.trim()?.substringBefore(' ')?.lowercase()
            val got = ApkInspect.sha256(apk).lowercase()
            if (want.isNullOrBlank() || want != got) {
                apk.delete()
                error("checksum does not match the published file")
            }
        }
        val info = readCert(context, apk)
        if (info != ReleaseCert.SHA256) {
            apk.delete()
            error("signature is not the Nibras release key")
        }
        val ask = PromptActivity.ask(context, "dialog", listOf("confirm", "-t", "Nibras", "-i", "Install $ver over $current? The signature matches the release key."), timeoutSec = 30)
        if (JSONObject(ask).optString("text") != "yes") return "Not installed."
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Allow \"Install unknown apps\", then run nibras-update again."
        }
        val uri = SharedFiles.expose(context, apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Installer opened for $ver."
    }

    private fun readCert(context: Context, apk: File): String? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apk.path, flags)
        } ?: return null
        val certs = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
        return certs?.firstOrNull()?.toByteArray()?.let { ApkInspect.sha256(it) }
    }

    private fun newer(remote: String, local: String): Boolean {
        val a = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val b = local.split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}

/** Wraps a file with a key that lives in Android Keystore. The wrapped bytes are not the key. */
object SecretBox {
    private const val ALIAS = "nibras-file-wrap"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return gen.generateKey()
    }

    private fun dir(context: Context) = File(context.filesDir, "secrets").apply { mkdirs() }
    fun has(context: Context, name: String) = File(dir(context), "$name.bin").isFile

    fun write(context: Context, name: String, plain: ByteArray) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        File(dir(context), "$name.bin").writeBytes(c.iv + c.doFinal(plain))
    }

    fun read(context: Context, name: String): ByteArray? {
        val f = File(dir(context), "$name.bin")
        if (!f.isFile) return null
        val blob = f.readBytes()
        if (blob.size < 13) return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return c.doFinal(blob.copyOfRange(12, blob.size))
    }

    fun delete(context: Context, name: String) { File(dir(context), "$name.bin").delete() }

    fun text(context: Context, name: String) = read(context, name)?.toString(Charsets.UTF_8)
}

object Secrets {
    fun run(context: Context, env: String, pwd: String, args: List<String>): String {
        if (GuestMode.on(context)) return "ERR\nguest mode"
        val cmd = args.firstOrNull() ?: return "ERR\nsecret lock-ssh|unlock-ssh|import-release|release|forget-git"
        fun host(p: String) = HostPaths.toHost(context, env, if (p.startsWith("/")) p else "$pwd/$p")
        return when (cmd) {
            "lock-ssh" -> {
                val f = host(args.getOrNull(1) ?: return "ERR\npath") ?: return "ERR\npath"
                if (!f.isFile) return "ERR\nno such file"
                if (!yes(context, "Move ${f.name} into the phone keystore and delete the file?")) return "ERR\nrefused"
                SecretBox.write(context, "ssh-key", f.readBytes())
                SecretBox.write(context, "ssh-name", f.name.toByteArray())
                if (f.length() in 1..1_048_576) f.writeBytes(ByteArray(f.length().toInt()))
                f.delete()
                "OK\nlocked ${f.name}. Unlock writes it back only after you say yes."
            }
            "unlock-ssh" -> {
                val bytes = SecretBox.read(context, "ssh-key") ?: return "ERR\nnothing locked"
                val name = SecretBox.text(context, "ssh-name") ?: "id_ed25519"
                if (!yes(context, "Write $name back into ~/.ssh for this session?")) return "ERR\nrefused"
                val ssh = (if (env == Rootfs.ANDROID) File(Rootfs.homeOf(context, env), ".ssh") else File(Rootfs.dir(context, env), "root/.ssh")).apply { mkdirs() }
                val out = File(ssh, name.replace("/", "").replace("..", ""))
                out.writeBytes(bytes)
                Rootfs.chmod(out, "600".toInt(8))
                Rootfs.chmod(ssh, "700".toInt(8))
                "OK\n" + HostPaths.toGuest(context, env, out) + "\nLock it again with: nibras-secret lock-ssh " + HostPaths.toGuest(context, env, out)
            }
            "import-release" -> {
                val f = host(args.getOrNull(1) ?: return "ERR\npath") ?: return "ERR\npath"
                if (!f.isFile) return "ERR\nno such file"
                val pass = askSecret(context, "Release key password") ?: return "ERR\ncancelled"
                if (!yes(context, "Store this release key in the phone keystore? The file stays where it is. The password is not saved in the shell.")) return "ERR\nrefused"
                SecretBox.write(context, "release-p12", f.readBytes())
                SecretBox.write(context, "release-pass", pass.toByteArray())
                "OK\nstored. Sign with: nibras-apk sign --release app.apk"
            }
            "release" -> {
                val p12 = SecretBox.read(context, "release-p12") ?: return "ERR\nno release key. nibras-secret import-release <file.p12>"
                val pass = SecretBox.read(context, "release-pass") ?: return "ERR\nno password stored"
                if (!yes(context, "Use the stored release key to sign? The password is written to a file only the app can read, then deleted.")) return "ERR\nrefused"
                val dir = File(Rootfs.dir(context, env), "tmp").apply { mkdirs() }
                val ks = File(dir, "nibras-release-${System.nanoTime()}.p12")
                val pw = File(dir, ks.name + ".pass")
                ks.writeBytes(p12)
                pw.writeBytes(pass)
                Rootfs.chmod(ks, "600".toInt(8))
                Rootfs.chmod(pw, "600".toInt(8))
                "OK\n" + HostPaths.toGuest(context, env, ks) + "\n" + HostPaths.toGuest(context, env, pw)
            }
            "forget-git" -> {
                SecretBox.delete(context, "git-user")
                SecretBox.delete(context, "git-pass")
                "OK\nforgotten"
            }
            "env" -> {
                val f = host(".env") ?: return "ERR\npath"
                if (!f.isFile) return "ERR\nno .env"
                if (!yes(context, "Move .env into the phone keystore and delete the file?")) return "ERR\nrefused"
                SecretBox.write(context, "env-file", f.readBytes())
                if (f.length() in 1..1_048_576) f.writeBytes(ByteArray(f.length().toInt()))
                f.delete()
                "OK\nlocked. dotenv unlock writes it back."
            }
            "env-unlock" -> {
                val bytes = SecretBox.read(context, "env-file") ?: return "ERR\nnothing locked"
                if (!yes(context, "Write .env back into this folder?")) return "ERR\nrefused"
                val dir = host(if (pwd.startsWith("/")) pwd else "/") ?: return "ERR\npath"
                val out = File(dir, ".env")
                out.writeBytes(bytes)
                Rootfs.chmod(out, "600".toInt(8))
                "OK\n" + HostPaths.toGuest(context, env, out)
            }
            else -> "ERR\nsecret lock-ssh|unlock-ssh|import-release|release|forget-git"
        }
    }

    fun gitCred(context: Context, prompt: String): String {
        if (GuestMode.on(context)) return "ERR\nguest mode"
        val kind = if (prompt.lowercase().contains("user")) "git-user" else "git-pass"
        SecretBox.text(context, kind)?.let { return "OK\n$it" }
        val title = if (kind == "git-user") "Git username" else "Git password"
        val typed = askSecret(context, title) ?: return "ERR\ncancelled"
        SecretBox.write(context, kind, typed.toByteArray())
        return "OK\n$typed"
    }

    private fun yes(context: Context, message: String): Boolean {
        val raw = PromptActivity.ask(context, "dialog", listOf("confirm", "-t", "Nibras", "-i", message), timeoutSec = 30)
        return JSONObject(raw).optString("text") == "yes"
    }

    private fun askSecret(context: Context, title: String): String? {
        val raw = PromptActivity.ask(context, "dialog", listOf("password", "-t", title, "-p"), timeoutSec = 120)
        val o = JSONObject(raw)
        if (o.optInt("code") != -1) return null
        return o.optString("text").ifBlank { null }
    }
}

object ProjectKeys {
    fun read(context: Context, env: String, cwd: String): List<List<String>>? {
        if (cwd.isBlank()) return null
        val dir = HostPaths.toHost(context, env, cwd) ?: return null
        val f = File(dir, ".nibras/keys")
        if (!f.isFile) return null
        val rows = f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(Regex("\\s+")) }
        return rows.takeIf { it.isNotEmpty() }
    }
}

object SessionLog {
    private var lastAt = 0L

    fun capture(context: Context, session: TerminalSession) {
        val path = File(context.filesDir, "record.path")
        if (!path.isFile) return
        val now = System.currentTimeMillis()
        if (now - lastAt < 400) return
        lastAt = now
        val text = session.emulator?.screen?.transcriptTextWithoutJoinedLines ?: return
        val dest = File(path.readText().trim())
        runCatching {
            dest.parentFile?.mkdirs()
            dest.writeText(text.takeLast(2_000_000))
        }
    }
}

object Mirror {
    fun path(context: Context) = File(context.filesDir, "mirror.txt").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    fun hit(context: Context, filename: String): File? {
        val root = path(context) ?: return null
        val f = File(root, filename)
        return f.takeIf { it.isFile }
    }

    fun use(context: Context, folder: String): String {
        File(context.filesDir, "mirror.txt").writeText(folder)
        return "OK\n$folder"
    }

    fun save(context: Context, folder: String): String {
        val from = File(context.filesDir, "downloads")
        if (!from.isDirectory) return "ERR\nnothing downloaded yet"
        val dest = File(folder)
        var n = 0
        from.walkTopDown().filter { it.isFile }.forEach { src ->
            val rel = src.relativeTo(from).path
            val out = File(dest, rel)
            out.parentFile?.mkdirs()
            src.copyTo(out, overwrite = true)
            n++
        }
        File(context.filesDir, "mirror.txt").writeText(dest.absolutePath)
        return "OK\n$n files → ${dest.absolutePath}"
    }
}

object WhatsNew {
    fun maybe(activity: Activity) {
        if (!Lang.chosen(activity)) return
        val ver = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull().orEmpty()
        if (ver.isEmpty()) return
        val prefs = Prefs(activity)
        if (prefs.sp.getString("seenVersion", "") == ver) return
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.whats_new_title))
            .setMessage(text(Lang.current(activity)))
            .setPositiveButton(android.R.string.ok) { _, _ -> activity.seen() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> activity.seen() }
            .setOnCancelListener { activity.seen() }
            .show()
    }

    private fun Activity.seen() {
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        Prefs(this).sp.edit().putString("seenVersion", ver).apply()
    }

    private fun text(lang: String): String = when (lang) {
        "az" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest apk-nin XML-ini çap edir. İcazə siyahısı perms-dir. db və crash başqa tətbiqə yalnız sənin debug quruluşunda, nibras-adb pair olanda baxır. Saytın ilk ekranı dəyişməyib."
        "tr" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest APK'nın XML'ini basar. İzin listesi perms. db ve crash başka uygulamaya yalnız senin debug derlemen ve nibras-adb pair varken bakar. Sitenin ilk ekranı değişmedi."
        "ru" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest печатает XML. Список разрешений — perms. db и crash смотрят чужое приложение только если это твоя debug-сборка и включён nibras-adb pair. Первый экран сайта не менялся."
        "ar" -> "1.0.6: nibras-apk test وlint وanalyze وassets وperms وdb وcrash. manifest يطبع XML. قائمة الأذونات هي perms. db وcrash يقرآن تطبيقاً آخر فقط إذا كان بناء تصحيح خاصتك ومع nibras-adb pair. الشاشة الأولى للموقع لم تتغير."
        "fa" -> "1.0.6: nibras-apk test، lint، analyze، assets، perms، db، crash. manifest همان XML است. فهرست مجوز perms است. db و crash فقط ساخت دیباگ خودت را با nibras-adb pair می‌خوانند. صفحه اول سایت عوض نشده."
        "ur" -> "1.0.6: nibras-apk test، lint، analyze، assets، perms، db، crash۔ manifest XML چھاپتا ہے۔ اجازتوں کی فہرست perms ہے۔ db اور crash دوسری ایپ صرف تب جب وہ آپ کی ڈیبگ بلڈ ہو اور nibras-adb pair ہو۔ سائٹ کی پہلی اسکرین نہیں بدلی۔"
        "fr" -> "1.0.6 : nibras-apk test, lint, analyze, assets, perms, db, crash. manifest affiche encore le XML. Les permissions se règlent avec perms. db et crash ne lisent une autre appli que si c'est votre build de debug et que nibras-adb pair est actif. Le premier écran du site est inchangé."
        "de" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest zeigt weiter das XML. Rechte ändert perms. db und crash lesen eine andere App nur, wenn es dein Debug-Build ist und nibras-adb pair aktiv ist. Der erste Bildschirm der Website ist unverändert."
        "es" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest sigue imprimiendo el XML. Los permisos van con perms. db y crash leen otra app solo si es tu build de depuración y hay nibras-adb pair. La primera pantalla del sitio no cambia."
        "pt" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest continua a imprimir o XML. As permissões ficam em perms. db e crash leem outra app só se for a tua build de debug e houver nibras-adb pair. O primeiro ecrã do site não muda."
        "it" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest stampa ancora l'XML. I permessi stanno in perms. db e crash leggono un'altra app solo se è la tua build di debug e c'è nibras-adb pair. La prima schermata del sito non cambia."
        "id" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest tetap mencetak XML. Izin diubah dengan perms. db dan crash membaca aplikasi lain hanya jika itu build debug milikmu dan nibras-adb pair aktif. Layar pertama situs tidak berubah."
        "ms" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest masih mencetak XML. Kebenaran ditukar dengan perms. db dan crash membaca apl lain hanya jika itu binaan nyahpepijat anda dan nibras-adb pair hidup. Skrin pertama tapak tidak berubah."
        "zh" -> "1.0.6：nibras-apk test、lint、analyze、assets、perms、db、crash。manifest 仍打印 XML。权限列表是 perms。db 和 crash 只在对方是你的调试包并且已 nibras-adb pair 时读取。网站首页未改。"
        "ja" -> "1.0.6：nibras-apk test、lint、analyze、assets、perms、db、crash。manifest はこれまで通り XML を出します。権限は perms。db と crash は、相手が自分のデバッグ版で nibras-adb pair 済みのときだけ読みます。サイトの最初の画面は変えていません。"
        "ko" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest 는 그대로 XML을 인쇄합니다. 권한은 perms. db 와 crash 는 그 앱이 내 디버그 빌드이고 nibras-adb pair 가 있을 때만 읽습니다. 사이트 첫 화면은 그대로입니다."
        "hi" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash. manifest अब भी XML छापता है। अनुमति सूची perms है। db और crash दूसरी ऐप को तभी पढ़ते हैं जब वह आपकी डीबग बिल्ड हो और nibras-adb pair हो। साइट की पहली स्क्रीन नहीं बदली।"
        "bn" -> "1.0.6: nibras-apk test, lint, analyze, assets, perms, db, crash। manifest এখনও XML ছাপে। অনুমতির তালিকা perms। db ও crash অন্য অ্যাপ তখনই পড়ে যখন সেটা আপনার ডিবাগ বিল্ড এবং nibras-adb pair আছে। সাইটের প্রথম পর্দা বদলায়নি।"
        else -> "1.0.6 adds nibras-apk test, lint, analyze, assets, perms, db and crash. manifest still prints the APK XML. perms edits uses-permission. db and crash read another app only when it is your debug build and nibras-adb pair is on. The website's first screen is unchanged."
    }
}

object LocalPage {
    fun allowed(url: String, lan: Boolean): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val host = uri.host ?: return false
        if (host == "127.0.0.1" || host == "localhost" || host == "::1") return true
        if (!lan) return false
        if (host.startsWith("192.168.") || host.startsWith("10.")) return true
        if (host.startsWith("172.")) {
            val n = host.split('.').getOrNull(1)?.toIntOrNull() ?: return false
            return n in 16..31
        }
        return false
    }
}

class LocalPageActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        val lan = intent.getBooleanExtra("lan", false)
        if (!LocalPage.allowed(url, lan)) { finish(); return }
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        @Suppress("DEPRECATION")
        web.settings.allowUniversalAccessFromFileURLs = false
        setContentView(web)
        web.loadUrl(url)
    }
}

/** A command the user started, with a notification, so the screen can turn off. */
class JobRunService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cmd = intent?.getStringExtra("cmd").orEmpty()
        val logName = intent?.getStringExtra("logName").orEmpty().ifBlank { "job.log" }
        val env = intent?.getStringExtra("env")
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel("jobs", "Jobs", NotificationManager.IMPORTANCE_LOW))
        val note = Notification.Builder(this, "jobs")
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(cmd.take(80))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(8811, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else @Suppress("DEPRECATION") startForeground(8811, note)
        if (cmd.isBlank() || logName.contains('/') || logName.contains("..")) {
            if (running.get() == 0) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        running.incrementAndGet()
        thread(name = "nibras-job") {
            try {
                val id = env?.takeIf { it.isNotBlank() } ?: Rootfs.pick(this, null)
                val dir = File(Rootfs.homeOf(this, id), "downloads/jobs").apply { mkdirs() }
                val guest = HostPaths.toGuest(this, id, File(dir, logName))
                Headless.run(this, id, "(${cmd}) > ${Rootfs.quote(guest)} 2>&1", timeoutSec = 86_400)
                if (!Quiet.on(this)) nm?.notify(8812, Notification.Builder(this, "jobs")
                    .setSmallIcon(R.drawable.ic_stat_terminal)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(guest)
                    .setAutoCancel(true)
                    .build())
            } finally {
                if (running.decrementAndGet() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private val running = java.util.concurrent.atomic.AtomicInteger(0)
    }
}

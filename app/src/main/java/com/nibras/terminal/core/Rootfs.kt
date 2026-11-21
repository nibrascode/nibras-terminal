package com.nibras.terminal.core

import android.content.Context
import android.os.Build
import android.os.Environment
import com.nibras.terminal.Component
import com.nibras.terminal.R
import com.nibras.terminal.data.CatalogStore
import com.nibras.terminal.data.deleteTree
import com.nibras.terminal.data.extractArchive
import com.nibras.terminal.data.sha256
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

data class Env(val id: String, val name: String)

/** Linux environments (PRoot root filesystems) and how a terminal session enters them. */
object Rootfs {
    const val ANDROID = "android"
    private const val STAMP = ".nibras/rootfs-v2"

    val all = listOf(
        Env("rootfs-alpine", "Alpine"),
        Env("rootfs-debian", "Debian"),
        Env("rootfs-ubuntu", "Ubuntu"),
    )

    fun name(id: String) = all.firstOrNull { it.id == id }?.name ?: if (id == ANDROID) "Android" else id

    fun dir(context: Context, id: String) = File(context.filesDir, "unpacked/$id")

    fun isReady(context: Context, id: String) = File(dir(context, id), STAMP).isFile

    fun ready(context: Context) = all.filter { isReady(context, it.id) }

    fun nativeDir(context: Context) = File(context.applicationInfo.nativeLibraryDir)

    fun prootAvailable(context: Context) = File(nativeDir(context), "libproot.so").isFile

    /** Picks the default environment, falling back to any ready one, then to the Android shell. */
    fun pick(context: Context, wanted: String?): String {
        val prefs = Prefs(context)
        if (!prootAvailable(context)) return ANDROID
        val order = listOfNotNull(wanted, prefs.defaultEnv, "rootfs-alpine") + all.map { it.id }
        return order.firstOrNull { it == ANDROID || isReady(context, it) } ?: ANDROID
    }

    /** First start: unpack the Alpine minirootfs bundled for this ABI. */
    fun ensureBundledAlpine(context: Context, progress: (String) -> Unit) {
        val id = "rootfs-alpine"
        if (isReady(context, id)) return
        val component = CatalogStore(context).bundled()
            .firstOrNull { it.id == id && DeviceAbi.matches(it.abi) }
            ?: error("Alpine ${DeviceAbi.label} not bundled")
        val tmp = File(context.cacheDir, component.filename)
        context.assets.open("payloads/${component.filename}.bin").use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (sha256(tmp) != component.sha256) {
            tmp.delete()
            error("Alpine SHA-256 mismatch")
        }
        installArchive(context, id, tmp, component, progress)
        tmp.delete()
    }

    /** Extracts a verified rootfs archive and prepares it for PRoot. */
    fun installArchive(context: Context, id: String, archive: File, component: Component?, progress: (String) -> Unit) {
        val target = dir(context, id)
        val staging = File(context.filesDir, "unpacked/.$id.staging")
        progress(context.getString(R.string.boot_extracting, name(id)))
        extractArchive(archive, staging) { n -> progress(context.getString(R.string.boot_extracting_n, name(id), n)) }
        deleteTree(target)
        target.parentFile?.mkdirs()
        if (!staging.renameTo(target)) error("rename failed")
        prepare(context, id)
        File(target, STAMP).apply { parentFile?.mkdirs() }.writeText(
            "${component?.filename ?: archive.name}\n${DeviceAbi.current}\n",
        )
    }

    fun remove(context: Context, id: String) {
        deleteTree(dir(context, id))
    }

    /** Files Nibras owns inside every rootfs. Safe to run again on each update. */
    fun prepare(context: Context, id: String) {
        val root = dir(context, id)
        fun f(path: String) = File(root, path.removePrefix("/"))
        listOf("root", "root/.nibras", "root/storage", "root/downloads", "tmp", "etc/nibras", "etc/profile.d",
            "usr/local/bin", "usr/local/share/nibras", ".nibras/proc", "run", "var/tmp").forEach { f(it).mkdirs() }
        chmod(f("tmp"), "1777".toInt(8))
        chmod(f("var/tmp"), "1777".toInt(8))
        chmod(f("root"), "700".toInt(8))

        writeReplacing(f("etc/resolv.conf"), "nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        val hosts = f("etc/hosts")
        if (!hosts.isFile || hosts.length() == 0L) {
            writeReplacing(hosts, "127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n")
        }
        addAndroidGroups(f("etc/group"))

        if (f("usr/bin/apt-get").exists()) {
            writeReplacing(
                f("etc/apt/apt.conf.d/99nibras"),
                "APT::Sandbox::User \"root\";\nAcquire::Retries \"3\";\nDpkg::Use-Pty \"0\";\n",
            )
        }

        writeReplacing(f("etc/profile.d/nibras.sh"), guest(context, "profile.sh"))
        exe(f("usr/local/bin/nibras-login"), guest(context, "nibras-login"))
        exe(f("usr/local/bin/pkg"), guest(context, "pkg"))
        exe(f("usr/local/bin/nibras-api"), guest(context, "nibras-api"))
        exe(f("usr/local/bin/nibras-desktop"), guest(context, "nibras-desktop"))
        exe(f("usr/local/bin/nibras-sshd"), guest(context, "nibras-sshd"))
        exe(f("usr/local/bin/nibras-shell"), guest(context, "nibras-shell"))
        exe(f("usr/local/bin/nibras-help"), "#!/bin/sh\ncat /etc/nibras/help\n")
        for (cmd in API_COMMANDS) {
            for (prefix in listOf("termux-", "nibras-")) {
                val link = f("usr/local/bin/$prefix$cmd")
                runCatching {
                    Files.deleteIfExists(link.toPath())
                    Files.createSymbolicLink(link.toPath(), Paths.get("nibras-api"))
                }
            }
        }
        // Storage shortcuts like Termux's ~/storage (work after "nibras-setup-storage").
        mapOf("shared" to "/sdcard", "downloads" to "/sdcard/Download", "dcim" to "/sdcard/DCIM",
            "pictures" to "/sdcard/Pictures", "music" to "/sdcard/Music", "movies" to "/sdcard/Movies",
            "documents" to "/sdcard/Documents").forEach { (name, target) ->
            val link = f("root/storage/$name").toPath()
            if (!Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                runCatching { Files.createSymbolicLink(link, Paths.get(target)) }
            }
        }
        listOf(".shortcuts", ".shortcuts/tasks", ".nibras/boot", "bin").forEach { f("root/$it").mkdirs() }

        // Android hides some /proc files from apps; tools like top need them.
        writeReplacing(f(".nibras/proc/loadavg"), "0.12 0.07 0.02 2/165 765\n")
        writeReplacing(f(".nibras/proc/stat"), guest(context, "proc-stat"))
        writeReplacing(f(".nibras/proc/uptime"), "124.08 932.80\n")
        writeReplacing(f(".nibras/proc/version"), "Linux version ${System.getProperty("os.version")} (nibras@android) #1 SMP PREEMPT\n")
        writeReplacing(f(".nibras/proc/vmstat"), guest(context, "proc-vmstat"))

        // Bundled helper scripts and the command booklet.
        runCatching {
            context.assets.list("payloads")?.filter { it.endsWith(".sh") || it.endsWith(".txt") || it.endsWith(".zip") }?.forEach { name ->
                val out = f("usr/local/share/nibras/$name")
                context.assets.open("payloads/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
                if (name.endsWith(".sh")) chmod(out, "755".toInt(8))
            }
        }
    }

    /** Per-session files: localized motd/help and the phone bridge address. */
    fun refreshSessionFiles(context: Context, id: String, apiPort: Int, apiToken: String) {
        val root = dir(context, id)
        val etc = File(root, "etc/nibras").apply { mkdirs() }
        writeReplacing(File(etc, "motd"), context.getString(R.string.motd, name(id), DeviceAbi.label) + "\n")
        writeReplacing(File(etc, "help"), context.getString(R.string.shell_help) + "\n")
        val api = File(etc, "api")
        writeReplacing(api, "NIBRAS_API_PORT=$apiPort\nNIBRAS_API_TOKEN=$apiToken\n")
        chmod(api, "600".toInt(8))
    }

    data class Launch(val exe: String, val args: Array<String>, val env: Array<String>, val cwd: String)

    /** Builds the PTY command for a session in [id]; [command] runs a script instead of a login shell. */
    fun launch(context: Context, id: String, command: List<String>? = null): Launch {
        val home = File(context.filesDir, "home").apply { mkdirs() }
        val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        val lang = "LANG=C.UTF-8"
        val term = "TERM=xterm-256color"
        if (id == ANDROID || !isReady(context, id)) {
            val args = if (command != null) arrayOf("sh", "-c", command.joinToString(" ") { quote(it) }) else arrayOf("-sh")
            return Launch(
                "/system/bin/sh", args,
                arrayOf("HOME=${home.absolutePath}", "TMPDIR=${tmp.absolutePath}", term, lang, "COLORTERM=truecolor",
                    "PATH=/system/bin:/system/xbin", "ANDROID_ROOT=/system", "ANDROID_DATA=/data"),
                home.absolutePath,
            )
        }
        val native = nativeDir(context)
        val root = dir(context, id)
        val proot = File(native, "libproot.so").absolutePath
        val args = mutableListOf(
            proot, "--kill-on-exit", "--link2symlink", "--sysvipc", "-L", "-0",
            "-r", root.absolutePath, "-w", "/root",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "/dev/urandom:/dev/random",
            "-b", "/proc/self/fd:/dev/fd",
            "-b", "/proc/self/fd/0:/dev/stdin",
            "-b", "/proc/self/fd/1:/dev/stdout",
            "-b", "/proc/self/fd/2:/dev/stderr",
            "-b", "${File(root, "tmp").absolutePath}:/dev/shm",
        )
        for (name in listOf("loadavg", "stat", "uptime", "version", "vmstat")) {
            if (!File("/proc/$name").canRead()) {
                args += listOf("-b", "${File(root, ".nibras/proc/$name").absolutePath}:/proc/$name")
            }
        }
        for (sys in listOf("/system", "/apex", "/vendor", "/linkerconfig/ld.config.txt")) {
            if (File(sys).exists()) args += listOf("-b", sys)
        }
        if (hasSharedStorage(context)) {
            val shared = Environment.getExternalStorageDirectory().absolutePath
            args += listOf("-b", "$shared:/sdcard", "-b", "/storage")
        }
        context.getExternalFilesDir(null)?.let { ext ->
            ext.mkdirs()
            args += listOf("-b", "${ext.absolutePath}:/root/storage/nibras")
        }
        runtimeHome(context, "runtime-python", "bin/python3")?.let { args += listOf("-b", "${it.absolutePath}:/opt/python") }
        runtimeHome(context, "runtime-node", "bin/node")?.let { args += listOf("-b", "${it.absolutePath}:/opt/node") }

        args += listOf(
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "LOGNAME=root", term, "COLORTERM=truecolor", lang,
            "PATH=/root/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/python/bin:/opt/node/bin",
            "TMPDIR=/tmp", "NIBRAS_ENV=${name(id).lowercase()}", "NIBRAS_ABI=${DeviceAbi.current}",
            "ANDROID_ROOT=/system", "ANDROID_DATA=/data",
            "/usr/local/bin/nibras-login",
        )
        if (command != null) args += command

        val env = mutableListOf(
            "PROOT_TMP_DIR=${tmp.absolutePath}",
            "PROOT_LOADER=${File(native, "libproot-loader.so").absolutePath}",
            "LD_LIBRARY_PATH=${native.absolutePath}",
            "HOME=${home.absolutePath}", term, lang, "PATH=/system/bin:/system/xbin",
            "ANDROID_ROOT=/system", "ANDROID_DATA=/data",
        )
        val loader32 = File(native, "libproot-loader32.so")
        if (loader32.isFile) env += "PROOT_LOADER_32=${loader32.absolutePath}"
        return Launch(proot, args.toTypedArray(), env.toTypedArray(), root.absolutePath)
    }

    fun hasSharedStorage(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Directory inside an unpacked runtime archive that contains [marker] (e.g. bin/python3). */
    fun runtimeHome(context: Context, id: String, marker: String): File? {
        val base = File(context.filesDir, "unpacked/$id")
        if (!base.isDirectory) return null
        if (File(base, marker).exists()) return base
        return base.listFiles()?.firstOrNull { File(it, marker).exists() }
    }

    /** Host path of the default environment's /root (for ~/.shortcuts, ~/.nibras, share). */
    fun homeOf(context: Context, id: String = pick(context, null)): File =
        if (id == ANDROID) File(context.filesDir, "home").apply { mkdirs() } else File(dir(context, id), "root")

    private fun addAndroidGroups(group: File) {
        if (!group.isFile) return
        val text = group.readText()
        if (text.contains("aid_inet")) return
        val appId = android.os.Process.myUid() % 100000
        val extra = buildString {
            append("aid_inet:x:3003:root\n")
            append("aid_net_raw:x:3004:root\n")
            append("aid_everybody:x:9997:root\n")
            append("aid_u0_a${appId - 10000}:x:$appId:root\n")
            append("aid_u0_a${appId - 10000}_cache:x:${appId + 10000}:root\n")
            append("aid_all_a${appId - 10000}:x:${appId + 40000}:root\n")
        }
        writeReplacing(group, text.trimEnd('\n') + "\n" + extra)
    }

    private fun writeReplacing(file: File, text: String) {
        file.parentFile?.mkdirs()
        val path = file.toPath()
        if (Files.isSymbolicLink(path)) Files.delete(path)
        file.writeText(text)
    }

    private fun exe(file: File, text: String) {
        writeReplacing(file, text)
        chmod(file, "755".toInt(8))
    }

    fun chmod(file: File, mode: Int) {
        runCatching { android.system.Os.chmod(file.absolutePath, mode) }
    }

    private fun guest(context: Context, name: String): String =
        context.assets.open("guest/$name").bufferedReader().use { it.readText() }

    /** Phone commands served by [com.nibras.terminal.api.ApiBridge]; each gets termux-* and nibras-* names. */
    val API_COMMANDS = listOf(
        "battery-status", "clipboard-get", "clipboard-set", "toast", "vibrate", "tts-speak",
        "notification", "notification-list", "notification-remove", "wake-lock", "wake-unlock",
        "share", "wifi-connectioninfo", "torch", "brightness", "volume", "camera-info",
        "microphone-record", "location", "sensor", "fingerprint", "download", "setup-storage",
        "open-url", "open", "info", "media-player", "contact-list", "call-log", "sms-list", "sms-send",
        "reload-settings", "new-session",
    )

    fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}

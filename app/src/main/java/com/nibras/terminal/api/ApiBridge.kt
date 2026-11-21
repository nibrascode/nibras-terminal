package com.nibras.terminal.api

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.nibras.terminal.PhoneBridge
import com.nibras.terminal.R
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.data.resolveInside
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Termux:API-style bridge. Shell commands inside PRoot (termux-* / nibras-*) connect to
 * 127.0.0.1:[port] and authenticate with [token], which is only readable inside the app's
 * private rootfs (/etc/nibras/api, mode 600).
 */
class ApiBridge(private val context: Context, private val host: Host) {
    interface Host {
        fun reloadSettings()
        fun newSession(env: String?, command: List<String>?)
        fun setWakeLock(on: Boolean): Boolean
        /** Shows a runtime permission prompt if an activity is visible. */
        fun requestPermission(permission: String)
        fun requestStorage()
    }

    val token: String = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    var port: Int = 0
        private set
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (server != null) return
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        server = socket
        port = socket.localPort
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                pool.execute { handle(client) }
            }
        }, "nibras-api").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 60_000
            val line = s.getInputStream().bufferedReader().readLine() ?: return
            val reply = runCatching { dispatch(line) }.getOrElse { "ERR\n${it.message ?: it.javaClass.simpleName}" }
            s.getOutputStream().apply {
                write((reply.trimEnd('\n') + "\n").toByteArray())
                flush()
            }
        }
    }

    private fun dispatch(line: String): String {
        val given = line.substringBefore(' ')
        if (!MessageDigest.isEqual(given.toByteArray(), token.toByteArray())) return "ERR\nbad token"
        val decoded = String(Base64.decode(line.substringAfter(' ', ""), Base64.DEFAULT))
        val parts = decoded.split('\n', limit = 3)
        val name = parts.getOrNull(0)?.trim().orEmpty()
        val env = parts.getOrNull(1)?.trim().orEmpty()
        val raw = parts.getOrNull(2).orEmpty()
        val args = raw.trim()
        return when (name) {
            "reload-settings" -> { main.post { host.reloadSettings() }; "OK\nsettings reloaded" }
            "new-session" -> {
                main.post { host.newSession(null, if (args.isBlank()) null else listOf("/bin/sh", "-c", args)) }
                "OK"
            }
            "wake-lock" -> { host.setWakeLock(true); "OK\nwake lock acquired" }
            "wake-unlock" -> { host.setWakeLock(false); "OK\nwake lock released" }
            "setup-storage" -> {
                if (Rootfs.hasSharedStorage(context)) "OK\n/sdcard → ~/storage/shared"
                else { main.post { host.requestStorage() }; "OK\n" + context.getString(R.string.storage_hint) }
            }
            "open" -> open(env, args)
            "edit" -> ui { com.nibras.terminal.EditorActivity.open(context, envId(env), args.ifBlank { "/root/untitled.txt" }) }
            "files" -> ui { com.nibras.terminal.FilesActivity.open(context, envId(env), args.ifBlank { null }) }
            "desktop-view" -> ui { com.nibras.terminal.DesktopActivity.open(context, env.ifBlank { null }) }
            "hosts" -> ui { context.startActivity(Intent(context, com.nibras.terminal.HostsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "dialog" -> com.nibras.terminal.PromptActivity.ask(context, "dialog", words(raw)).let { "OK\n$it" }
            "storage-get" -> {
                val out = words(raw).firstOrNull() ?: return "ERR\nusage: nibras-storage-get <output file>"
                val target = com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), out) ?: return "ERR\nbad path: $out"
                com.nibras.terminal.PromptActivity.ask(context, "storage-get", emptyList(), target).let { "OK\n$it" }
            }
            "camera-photo" -> {
                val out = words(raw).lastOrNull { !it.startsWith("-") && it.toIntOrNull() == null } ?: return "ERR\nusage: nibras-camera-photo <file.jpg>"
                val target = com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), out) ?: return "ERR\nbad path: $out"
                "OK\n" + com.nibras.terminal.PromptActivity.ask(context, "camera-photo", emptyList(), target)
            }
            "speech-to-text" -> org.json.JSONObject(com.nibras.terminal.PromptActivity.ask(context, "speech-to-text", emptyList())).optString("text").let { "OK\n$it" }
            "telephony-call" -> ui { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + args.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "media-scan" -> {
                val files = words(raw).mapNotNull { com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), it)?.absolutePath }
                android.media.MediaScannerConnection.scanFile(context, files.toTypedArray(), null, null); "OK\n${files.size} file(s)"
            }
            "infrared-transmit" -> "OK\n" + com.nibras.terminal.ExtraPhone.infrared(context, args)
            "telephony-cellinfo" -> {
                val perm = android.Manifest.permission.ACCESS_FINE_LOCATION
                if (context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) { main.post { host.requestPermission(perm) }; "ERR\n" + context.getString(R.string.permission_needed, "ACCESS_FINE_LOCATION") }
                else "OK\n" + com.nibras.terminal.ExtraPhone.cellInfo(context)
            }
            "job-scheduler" -> "OK\n" + com.nibras.terminal.ScriptJobService.handle(context, words(raw), envId(env))
            "audio-info" -> "OK\n" + com.nibras.terminal.ExtraPhone.audioInfo(context)
            "telephony-deviceinfo" -> "OK\n" + com.nibras.terminal.ExtraPhone.telephony(context)
            "wifi-scaninfo" -> {
                val perm = android.Manifest.permission.ACCESS_FINE_LOCATION
                if (context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) { main.post { host.requestPermission(perm) }; "ERR\n" + context.getString(R.string.permission_needed, "ACCESS_FINE_LOCATION") }
                else "OK\n" + com.nibras.terminal.ExtraPhone.wifiScan(context)
            }
            "wallpaper" -> {
                val lock = args.startsWith("-l ")
                val path = args.removePrefix("-l ").trim()
                "OK\n" + com.nibras.terminal.ExtraPhone.wallpaper(context, com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), path), lock)
            }
            "usb" -> {
                val w = words(raw)
                val dev = w.lastOrNull { it.startsWith("/dev/") || Regex("[0-9a-fA-F]{4}:[0-9a-fA-F]{4}").matches(it) }
                when {
                    w.isEmpty() || "-l" in w -> "OK\n" + com.nibras.terminal.UsbBridge.list(context)
                    "-r" in w && "-e" !in w -> com.nibras.terminal.UsbBridge.request(context, dev)
                    "--fd" in w || "-e" in w -> com.nibras.terminal.UsbBridge.fd(context, dev)
                    else -> "ERR\nnibras-usb -l | -r [device] | -e <command> [device] | serial [device] [baud]"
                }
            }
            "usb-serial" -> {
                val w = words(raw)
                val baud = w.firstOrNull { it.toIntOrNull() != null }?.toInt() ?: 115200
                com.nibras.terminal.UsbBridge.serial(context, w.firstOrNull { it.toIntOrNull() == null }, baud)
            }
            "env-install" -> {
                val id = args.trim().let { if (it.startsWith("rootfs-")) it else "rootfs-$it" }
                if (Rootfs.all.none { it.id == id }) return "ERR\nunknown environment: $args"
                if (Rootfs.isReady(context, id)) return "OK\n${Rootfs.name(id)} is installed: nibras-shell ${Rootfs.name(id).lowercase()}"
                ui { context.startActivity(Intent(context, com.nibras.terminal.PackagesActivity::class.java).putExtra("install", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                "OK\n${Rootfs.name(id)}: download started in the Environments screen"
            }
            "keystore" -> com.nibras.terminal.KeystoreBridge.run(words(raw))
            "nfc" -> {
                val r = org.json.JSONObject(com.nibras.terminal.PromptActivity.ask(context, "nfc", words(raw)))
                (if (r.optInt("code") == -1) "OK\n" else "ERR\n") + r.optString("text")
            }
            "prayer-alarm" -> com.nibras.terminal.PrayerAlarms.command(context, words(raw))
            "media-builder" -> ui { context.startActivity(Intent(context, com.nibras.terminal.MediaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("tab", args.ifBlank { "video" })) }
            else -> phone(name, args)
        }
    }

    private fun envId(env: String) = com.nibras.terminal.ui.HostPaths.envId(context, env.ifBlank { null })

    /** Arguments the guest sent separated by \u001f (keeps quoting); falls back to spaces. */
    private fun words(args: String): List<String> =
        if (args.contains('\u001f')) args.split('\u001f').dropLastWhile { it.isEmpty() } else args.trim().split(' ').filter { it.isNotEmpty() }

    private fun ui(block: () -> Unit): String {
        onMainThread(block)
        return "OK"
    }

    private fun phone(name: String, args: String): String {
        val line = "nibras-$name" + if (args.isNotEmpty()) " $args" else ""
        val permission = PhoneBridge.permission(line)
        if (permission != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            main.post { host.requestPermission(permission) }
            return "ERR\n" + context.getString(R.string.permission_needed, permission.substringAfterLast('.'))
        }
        val onMain = name in setOf("toast", "tts-speak", "media-player", "share", "open-url", "clipboard-get", "clipboard-set", "notification")
        val result = if (onMain) onMainThread { PhoneBridge.run(context, line) } else PhoneBridge.run(context, line)
        return if (result == null) "ERR\nunknown command: $name" else "OK\n$result"
    }

    private fun open(env: String, guestPath: String): String {
        if (guestPath.isBlank()) return "ERR\nusage: termux-open <file|url>"
        if (guestPath.startsWith("http://") || guestPath.startsWith("https://")) {
            onMainThread {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guestPath)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return "OK"
        }
        val id = Rootfs.all.firstOrNull { it.name.equals(env, ignoreCase = true) }?.id ?: Rootfs.pick(context, null)
        val file = hostPath(id, guestPath) ?: return "ERR\nfile not found: $guestPath"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        onMainThread { context.startActivity(Intent.createChooser(view, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return "OK\n${file.name}"
    }

    private fun hostPath(id: String, guestPath: String): File? {
        val path = if (guestPath.startsWith("/")) guestPath else "/root/$guestPath"
        val file = if (path == "/sdcard" || path.startsWith("/sdcard/")) {
            File(Environment.getExternalStorageDirectory(), path.removePrefix("/sdcard"))
        } else {
            val root = Rootfs.dir(context, id).toPath().toAbsolutePath().normalize()
            runCatching { resolveInside(root, path, followLast = true).toFile() }.getOrNull()
        }
        return file?.takeIf { it.isFile }
    }

    private fun <T> onMainThread(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var value: T? = null
        var error: Throwable? = null
        val gate = CountDownLatch(1)
        main.post {
            try { value = block() } catch (t: Throwable) { error = t } finally { gate.countDown() }
        }
        gate.await(20, TimeUnit.SECONDS)
        error?.let { throw it }
        return value
    }
}

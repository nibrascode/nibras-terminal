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
import com.nibras.terminal.PhoneBridge
import com.nibras.terminal.SharedFiles
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
        fun applyLanguage() {}
        fun newSession(env: String?, command: List<String>?)
        fun renameSession(key: String, title: String) {}
        fun setSplit(on: Boolean) {}
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
            val out = java.io.BufferedOutputStream(s.getOutputStream())
            val reply = runCatching { dispatch(line, out) }.getOrElse { "ERR\n${it.message ?: it.javaClass.simpleName}" }
            runCatching {
                if (reply != null) out.write((reply.trimEnd('\n') + "\n").toByteArray())
                out.flush()
            }
        }
    }

    /** Returns the reply, or null when a streaming command already wrote it to [out]. */
    private fun dispatch(line: String, out: java.io.OutputStream): String? {
        val given = line.substringBefore(' ')
        if (!MessageDigest.isEqual(given.toByteArray(), token.toByteArray())) return "ERR\nbad token"
        val decoded = String(Base64.decode(line.substringAfter(' ', ""), Base64.DEFAULT))
        val parts = decoded.split('\n', limit = 3)
        val name = parts.getOrNull(0)?.trim().orEmpty()
        // Header line 2: "<env>\u001e<cwd>"; body: "<args joined by \u001f>\u001d<base64 stdin>"
        val envField = parts.getOrNull(1).orEmpty()
        val env = envField.substringBefore('\u001e').trim()
        val pwd = envField.substringAfter('\u001e', "/root").trim().ifBlank { "/root" }
        val body = parts.getOrNull(2).orEmpty()
        val raw = body.substringBefore('\u001d')
        val stdin = body.substringAfter('\u001d', "").takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.DEFAULT) }
        val args = if (raw.contains('\u001f')) words(raw).joinToString(" ") else raw.trim()
        if (name in com.nibras.terminal.TermuxApi.NAMES) {
            com.nibras.terminal.TermuxApi.run(com.nibras.terminal.TermuxApi.Call(context, words(raw), stdin, envId(env), pwd,
                { p -> main.post { host.requestPermission(p) } }, out), name)
            return null
        }
        return when (name) {
            "lang" -> {
                val code = args.trim().substringBefore(' ').lowercase()
                when {
                    code.isEmpty() || code == "list" -> "OK\n" + com.nibras.terminal.core.Lang.names.entries.joinToString("\n") { (k, v) ->
                        (if (k == com.nibras.terminal.core.Lang.current(context)) "* " else "  ") + "$k  $v" }
                    code == "auto" || code == "system" || code in com.nibras.terminal.core.Lang.names -> {
                        com.nibras.terminal.core.Lang.set(context, if (code == "auto" || code == "system") "" else code)
                        main.post { host.reloadSettings(); host.applyLanguage() }
                        "OK\n" + com.nibras.terminal.core.Lang.current(context)
                    }
                    else -> "ERR\nunknown language: $code (lang list)"
                }
            }
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
            "edit" -> ui { com.nibras.terminal.EditorActivity.open(context, envId(env), args.ifBlank { "/root/untitled.txt" }) }
            "res-edit" -> ui { com.nibras.terminal.ResEditorActivity.open(context, envId(env), args.ifBlank { pwd }.let { if (it.startsWith("/")) it else "$pwd/$it" }) }
            "files" -> ui { com.nibras.terminal.FilesActivity.open(context, envId(env), args.ifBlank { null }) }
            "desktop-view" -> ui { com.nibras.terminal.DesktopActivity.open(context, env.ifBlank { null }) }
            "hosts" -> ui { context.startActivity(Intent(context, com.nibras.terminal.HostsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "speech-to-text" -> org.json.JSONObject(com.nibras.terminal.PromptActivity.ask(context, "speech-to-text", emptyList())).optString("text").let { "OK\n$it" }
            "telephony-cellinfo" -> {
                val perm = android.Manifest.permission.ACCESS_FINE_LOCATION
                if (context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) { main.post { host.requestPermission(perm) }; "ERR\n" + context.getString(R.string.permission_needed, "ACCESS_FINE_LOCATION") }
                else "OK\n" + com.nibras.terminal.ExtraPhone.cellInfo(context)
            }
            "job-scheduler" -> "OK\n" + com.nibras.terminal.ScriptJobService.handle(context, words(raw), envId(env), pwd)
            "audio-info" -> "OK\n" + com.nibras.terminal.ExtraPhone.audioInfo(context)
            "telephony-deviceinfo" -> "OK\n" + com.nibras.terminal.ExtraPhone.telephony(context)
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
            "keystore" -> com.nibras.terminal.KeystoreBridge.run(context, words(raw))
            "am" -> "OK\n" + AmCommand.run(context, words(raw))
            "env-list" -> "OK\n" + Rootfs.all.joinToString("\n") { e ->
                val ready = Rootfs.isReady(context, e.id)
                "%-8s %s%s".format(e.id.removePrefix("rootfs-"), if (ready) "installed" else "available", if (Rootfs.pick(context, null) == e.id) " (default)" else "")
            }
            "env-open" -> {
                val w = words(raw)
                val id = w.firstOrNull()?.let { if (it.startsWith("rootfs-")) it else "rootfs-$it" } ?: return "ERR\nusage: proot-distro login <name>"
                if (Rootfs.all.none { it.id == id }) return "ERR\nunknown environment: ${w.first()}"
                if (!Rootfs.isReady(context, id)) return "ERR\n${Rootfs.name(id)} is not installed: proot-distro install ${id.removePrefix("rootfs-")}"
                val cmd = w.drop(1).joinToString(" ").trim()
                main.post { host.newSession(id, if (cmd.isEmpty()) null else listOf("/bin/sh", "-c", cmd)) }
                "OK\n${Rootfs.name(id)}: opened in a new session"
            }
            "install-apk" -> {
                val f = com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), args.let { if (it.startsWith("/")) it else "$pwd/$it" })
                if (f == null || !f.isFile) return "ERR\nno such APK: $args"
                val warn = com.nibras.terminal.ApkInspect.replaceWarning(context, f)
                if (warn != null) {
                    val answer = org.json.JSONObject(com.nibras.terminal.PromptActivity.ask(context, "dialog", listOf("confirm", "-t", "Nibras", "-i", warn + "\nOpen the installer anyway?"), timeoutSec = 30))
                    if (answer.optString("text") != "yes") return "ERR\nrefused"
                }
                if (!context.packageManager.canRequestPackageInstalls()) {
                    ui { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    return "ERR\nAllow \"Install unknown apps\" for Nibras Terminal (screen opened), then run again"
                }
                val uri = SharedFiles.expose(context, f)
                ui { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
                "OK\nAndroid installer opened for ${f.name}"
            }
            "apps" -> com.nibras.terminal.AppTools.apps(context, words(raw)) { p -> com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), if (p.startsWith("/")) p else "$pwd/$p") }
            "install-apks" -> {
                val fs = words(raw).map { p -> com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), if (p.startsWith("/")) p else "$pwd/$p") ?: return "ERR\nbad path: $p" }
                if (fs.isEmpty()) "ERR\nusage: nibras-apk install-split <base.apk split*.apk | app.apks | app.xapk | app.apkm>" else com.nibras.terminal.AppTools.installSplits(context, fs)
            }
            "environments" -> ui { context.startActivity(Intent(context, com.nibras.terminal.PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            "nfc" -> {
                val r = org.json.JSONObject(com.nibras.terminal.PromptActivity.ask(context, "nfc", words(raw)))
                (if (r.optInt("code") == -1) "OK\n" else "ERR\n") + r.optString("text")
            }
            "prayer-alarm" -> com.nibras.terminal.PrayerAlarms.command(context, words(raw))
            "apk-diff" -> {
                val w = words(raw)
                if (w.size < 2) return "ERR\nusage: nibras-apk diff old.apk new.apk"
                fun h(p: String) = com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), if (p.startsWith("/")) p else "$pwd/$p")
                val a = h(w[0]) ?: return "ERR\nno file ${w[0]}"
                val b = h(w[1]) ?: return "ERR\nno file ${w[1]}"
                "OK\n" + com.nibras.terminal.ApkInspect.diff(context, a, b)
            }
            "app-update" -> "OK\n" + com.nibras.terminal.Updater.run(context)
            "allow-check" -> if (com.nibras.terminal.AllowFile.automatic(context, args.trim())) "OK\nyes" else "ERR\nnot allowed"
            "guest-mode" -> when (args.trim()) {
                "on" -> { com.nibras.terminal.GuestMode.set(context, true); "OK\non" }
                "off" -> {
                    val answer = org.json.JSONObject(com.nibras.terminal.PromptActivity.ask(context, "dialog", listOf("confirm", "-t", "Nibras", "-i", "Leave guest mode?"), timeoutSec = 30))
                    if (answer.optString("text") != "yes") "ERR\nrefused" else { com.nibras.terminal.GuestMode.set(context, false); "OK\noff" }
                }
                else -> "OK\n" + if (com.nibras.terminal.GuestMode.on(context)) "on" else "off"
            }
            "secret-box" -> com.nibras.terminal.Secrets.run(context, envId(env), pwd, words(raw))
            "record-log" -> {
                val w = words(raw)
                when (w.firstOrNull()) {
                    "on" -> {
                        val p = w.getOrNull(1) ?: return "ERR\npath"
                        val f = com.nibras.terminal.ui.HostPaths.toHost(context, envId(env), if (p.startsWith("/")) p else "$pwd/$p") ?: return "ERR\npath"
                        f.parentFile?.mkdirs()
                        java.io.File(context.filesDir, "record.path").writeText(f.absolutePath)
                        "OK\n" + f.absolutePath
                    }
                    "off" -> { java.io.File(context.filesDir, "record.path").delete(); "OK\noff" }
                    else -> "ERR\nrecord-log on <file> | off"
                }
            }
            "run-job" -> {
                val cmd = args.trim()
                if (cmd.isEmpty()) return "ERR\nnibras-job <command>"
                if (com.nibras.terminal.GuestMode.on(context)) return "ERR\nguest mode"
                if (!com.nibras.terminal.AllowFile.automatic(context, cmd)) return "ERR\nnot allowed"
                val id = com.nibras.terminal.core.Rootfs.pick(context, null)
                val name = "job-${System.currentTimeMillis()}.log"
                val dir = java.io.File(com.nibras.terminal.core.Rootfs.homeOf(context, id), "downloads/jobs").apply { mkdirs() }
                val guest = com.nibras.terminal.ui.HostPaths.toGuest(context, id, java.io.File(dir, name))
                val started = runCatching {
                    context.startForegroundService(android.content.Intent(context, com.nibras.terminal.JobRunService::class.java)
                        .putExtra("cmd", cmd).putExtra("logName", name).putExtra("env", id))
                }
                if (started.isFailure) "ERR\n${started.exceptionOrNull()?.message}" else "OK\n$guest"
            }
            "peer-join" -> {
                val url = args.trim().lineSequence().firstOrNull().orEmpty()
                if (!com.nibras.terminal.LocalPage.allowed(url, lan = true)) return "ERR\nonly a private address on this Wi-Fi"
                val answer = org.json.JSONObject(com.nibras.terminal.PromptActivity.ask(context, "dialog", listOf("confirm", "-t", "Nibras", "-i", "Open the other phone's terminal?\n$url"), timeoutSec = 30))
                if (answer.optString("text") != "yes") return "ERR\nrefused"
                context.startActivity(android.content.Intent(context, com.nibras.terminal.LocalPageActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("url", url).putExtra("lan", true))
                "OK"
            }
            "ports-open" -> {
                val port = args.trim().toIntOrNull() ?: return "ERR\nport"
                if (port !in 1..65535) return "ERR\nport"
                val url = "http://127.0.0.1:$port/"
                context.startActivity(android.content.Intent(context, com.nibras.terminal.LocalPageActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("url", url))
                "OK\n$url"
            }
            "mirror-path" -> {
                val w = words(raw)
                when (w.firstOrNull()) {
                    "use" -> com.nibras.terminal.Mirror.use(context, w.getOrNull(1) ?: return "ERR\nfolder")
                    "save" -> com.nibras.terminal.Mirror.save(context, w.getOrNull(1) ?: "/sdcard/nibras-mirror")
                    else -> "ERR\nuse|save"
                }
            }
            "phrase-parse" -> {
                val steps = com.nibras.terminal.PlusRules.parsePhrase(args) ?: return "ERR\nnot a known sentence"
                "OK\n" + steps.joinToString("\n")
            }
            "work" -> com.nibras.terminal.Work.run(context, host, envId(env), pwd, words(raw))
            "git-cred" -> com.nibras.terminal.Secrets.gitCred(context, args)
            "qr-run" -> {
                val text = args.trim()
                if (text.isEmpty() || text.length > 200 || '\n' in text) return "ERR\nrefused"
                val steps = com.nibras.terminal.PlusRules.parsePhrase(text)
                val listed = com.nibras.terminal.AllowFile.lines(context)?.let { com.nibras.terminal.PlusRules.allowAutomatic(text, it, com.nibras.terminal.Sensitive.names) } == true
                if (steps == null && !listed) return "ERR\nonly a known sentence, or a command on your allow list"
                val shown = steps?.joinToString("\n") ?: text
                val answer = org.json.JSONObject(com.nibras.terminal.PromptActivity.ask(context, "dialog", listOf("confirm", "-t", "Nibras", "-i", "Run this?\n$shown"), timeoutSec = 30))
                if (answer.optString("text") != "yes") return "ERR\nrefused"
                val cmd = if (steps != null) steps.joinToString(" && ") { step ->
                    val bits = step.split(' ')
                    when (bits[0]) {
                        "install" -> "nibras-kit install " + com.nibras.terminal.core.Rootfs.quote(bits.getOrElse(1) { "" })
                        "run" -> "nibras-kit run " + com.nibras.terminal.core.Rootfs.quote(bits.getOrElse(1) { "" })
                        "theme" -> "nibras-look theme " + com.nibras.terminal.core.Rootfs.quote(bits.getOrElse(1) { "" })
                        else -> "nibras-help"
                    }
                } else text
                main.post { host.newSession(null, listOf("/bin/sh", "-c", cmd)) }
                "OK"
            }
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

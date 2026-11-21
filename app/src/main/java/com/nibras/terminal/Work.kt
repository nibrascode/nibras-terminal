package com.nibras.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.StatFs
import com.nibras.terminal.api.ApiBridge
import com.nibras.terminal.core.Prefs
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.ui.HostPaths
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.math.abs

/** Phone side of the short commands (use, here, alert, …). The shell script decides what is safe to run. */
object Work {
    fun run(context: Context, host: ApiBridge.Host, env: String, pwd: String, args: List<String>): String {
        val cmd = args.firstOrNull() ?: return "ERR\nwork <command>"
        val rest = args.drop(1)
        return when (cmd) {
            "use" -> use(context, host, rest.firstOrNull())
            "here" -> {
                val f = HostPaths.toHost(context, env, pwd) ?: return "ERR\npath"
                "OK\n" + f.absolutePath
            }
            "title" -> {
                val key = rest.firstOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,16}")) } ?: return "ERR\ntitle"
                val text = rest.drop(1).joinToString(" ").trim().take(40)
                if (text.isEmpty() || text.any { it == '\n' || it == '\r' }) return "ERR\ntitle"
                host.renameSession(key, text)
                "OK\n$text"
            }
            "split" -> {
                host.setSplit(rest.firstOrNull() != "off")
                "OK"
            }
            "confirm" -> if (yes(context, rest.joinToString(" ").ifBlank { "?" })) "OK\nyes" else "ERR\nrefused"
            "ask" -> ask(context, rest.joinToString(" ").ifBlank { "Nibras" })
            "alert" -> alert(context, rest.firstOrNull() ?: "?", rest.drop(1).joinToString(" "))
            "listed" -> {
                val list = AllowFile.lines(context) ?: return "ERR\nno allow list"
                val line = rest.joinToString(" ").trim()
                if (PlusRules.allowAutomatic(line, list, Sensitive.names)) "OK\nyes" else "ERR\nnot allowed"
            }
            "quiet" -> quiet(context)
            "health" -> "OK\n" + health(context)
            "crash" -> crash(context)
            "pick" -> pick(context, env, pwd)
            "sayit" -> sayit(context, host)
            "scan" -> scan(context, host)
            "send" -> send(context, rest.joinToString(" "))
            "disk" -> "OK\n" + disk(context)
            "voicenote" -> voiceCopy(context, env)
            "servers" -> servers(context)
            else -> "ERR\nunknown"
        }
    }

    private fun use(context: Context, host: ApiBridge.Host, name: String?): String {
        val raw = name?.trim()?.lowercase().orEmpty()
        if (raw.isEmpty()) {
            val id = Rootfs.pick(context, null)
            return "OK\n" + Rootfs.name(id) + "\n" + Rootfs.all.joinToString(" ") { it.id.removePrefix("rootfs-") } + " android"
        }
        val id = if (raw == "android") Rootfs.ANDROID else {
            val full = if (raw.startsWith("rootfs-")) raw else "rootfs-$raw"
            if (Rootfs.all.none { it.id == full }) return "ERR\nunknown: $raw"
            if (!Rootfs.isReady(context, full)) return "ERR\nnot installed: $raw"
            full
        }
        Prefs(context).defaultEnv = id
        host.newSession(id, null)
        return "OK\n" + Rootfs.name(id)
    }

    private fun ask(context: Context, title: String): String {
        val raw = PromptActivity.ask(context, "dialog", listOf("text", "-t", title.take(60)), timeoutSec = 120)
        val o = JSONObject(raw)
        if (o.optInt("code") != -1) return "ERR\ncancelled"
        val text = o.optString("text")
        if (text.isBlank() || text.any { it == '\n' || it == '\r' }) return "ERR\nrefused"
        return "OK\n$text"
    }

    private fun alert(context: Context, code: String, what: String): String {
        if (Quiet.on(context)) return "OK\nquiet"
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel("jobs", "Jobs", NotificationManager.IMPORTANCE_DEFAULT))
        nm?.notify(8813, Notification.Builder(context, "jobs")
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText((if (code == "0") "done " else "exit $code ") + what.take(80))
            .setAutoCancel(true)
            .build())
        return "OK"
    }

    private fun quiet(context: Context) = if (Quiet.on(context)) "OK\nquiet" else "OK\noff"

    private fun health(context: Context): String {
        val free = runCatching { StatFs(context.filesDir.absolutePath).availableBytes / (1024 * 1024) }.getOrDefault(-1)
        val proot = Rootfs.prootAvailable(context)
        val env = Rootfs.name(Rootfs.pick(context, null))
        val sign = runCatching {
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            val info = if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, flags)
            val certs = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
            val sha = certs?.firstOrNull()?.toByteArray()?.let { ApkInspect.sha256(it) }
            if (sha == ReleaseCert.SHA256) "release key" else "other key"
        }.getOrDefault("?")
        return "bridge up\nproot ${if (proot) "yes" else "no"}\nenv $env\ndisk $free MB free\nsignature $sign"
    }

    private fun crash(context: Context): String {
        val f = NibrasApp.crashFile(context)
        if (!f.isFile || f.length() == 0L) return "OK\nnone"
        val text = f.readText().take(8000)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "crash").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "OK\n" + text.lineSequence().take(8).joinToString("\n")
    }

    private fun pick(context: Context, env: String, pwd: String): String {
        val dir = HostPaths.toHost(context, env, pwd) ?: return "ERR\npath"
        dir.mkdirs()
        val dest = File(dir, "picked-" + System.currentTimeMillis())
        val r = JSONObject(PromptActivity.ask(context, "storage-get", emptyList(), dest))
        if (r.optInt("code") != -1) {
            dest.delete()
            return "ERR\ncancelled"
        }
        return "OK\n" + HostPaths.toGuest(context, env, dest)
    }

    private fun sayit(context: Context, host: ApiBridge.Host): String {
        if (GuestMode.on(context)) return "ERR\nguest mode"
        val heard = JSONObject(PromptActivity.ask(context, "speech-to-text", emptyList(), timeoutSec = 30)).optString("text").trim()
        if (heard.isEmpty()) return "ERR\nempty"
        val steps = PlusRules.parsePhrase(heard) ?: return "ERR\nnot a known sentence"
        if (!yes(context, "Run this?\n" + steps.joinToString("\n"))) return "ERR\nrefused"
        host.newSession(null, listOf("/bin/sh", "-c", phraseLine(steps)))
        return "OK\n" + steps.joinToString("\n")
    }

    private fun scan(context: Context, host: ApiBridge.Host): String {
        val r = JSONObject(PromptActivity.ask(context, "qr", emptyList(), timeoutSec = 60))
        if (r.optInt("code") != -1) return "ERR\n" + r.optString("text").ifBlank { "no scanner" }
        val text = r.optString("text").trim()
        if (text.isEmpty() || text.length > 200 || '\n' in text) return "ERR\nrefused"
        val steps = PlusRules.parsePhrase(text)
        val listed = AllowFile.lines(context)?.let { PlusRules.allowAutomatic(text, it, Sensitive.names) } == true
        if (steps == null && !listed) return "ERR\nonly a known sentence, or a command on your allow list"
        val shown = steps?.joinToString("\n") ?: text
        if (!yes(context, "Run this?\n$shown")) return "ERR\nrefused"
        val cmd = if (steps != null) phraseLine(steps) else text
        host.newSession(null, listOf("/bin/sh", "-c", cmd))
        return "OK"
    }

    private fun phraseLine(steps: List<String>) = steps.joinToString(" && ") { step ->
        val bits = step.split(' ')
        when (bits[0]) {
            "install" -> "nibras-kit install " + Rootfs.quote(bits.getOrElse(1) { "" })
            "run" -> "nibras-kit run " + Rootfs.quote(bits.getOrElse(1) { "" })
            "theme" -> "nibras-look theme " + Rootfs.quote(bits.getOrElse(1) { "" })
            else -> "nibras-help"
        }
    }

    private fun send(context: Context, text: String): String {
        if (text.isEmpty() || text.length > 32_768) return "ERR\nup to 32 KB"
        if (!yes(context, "Show this text to one phone on this Wi-Fi?\n" + text.take(180))) return "ERR\nrefused"
        val ip = wifiIp(context) ?: return "ERR\nnot on Wi-Fi"
        if (!privateIp(ip)) return "ERR\nnot a private address"
        val server = ServerSocket(0, 1, InetAddress.getByName(ip))
        server.soTimeout = 60_000
        thread(name = "nibras-send") {
            runCatching {
                val sock = server.accept()
                sock.soTimeout = 10_000
                val remote = sock.inetAddress?.hostAddress.orEmpty()
                if (!privateIp(remote)) {
                    sock.close()
                } else {
                    val head = "HTTP/1.0 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\n"
                    sock.getOutputStream().use { it.write(head.toByteArray()); it.write(text.toByteArray()) }
                    sock.close()
                }
            }
            runCatching { server.close() }
        }
        return "OK\nhttp://$ip:${server.localPort}/\nThe other phone: peer join <that link>"
    }

    private fun disk(context: Context): String {
        fun bytes(dir: File) = if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val mb = 1024 * 1024
        val free = runCatching { StatFs(context.filesDir.absolutePath).availableBytes / mb }.getOrDefault(-1)
        return "free $free MB\ndownloads ${bytes(File(context.filesDir, "downloads")) / mb} MB\nsecrets ${bytes(File(context.filesDir, "secrets")) / mb} MB\nunpacked ${bytes(File(context.filesDir, "unpacked")) / mb} MB"
    }

    private fun voiceCopy(context: Context, env: String): String {
        if (GuestMode.on(context)) return "ERR\nguest mode"
        val src = File(context.filesDir, "nibras-mic.m4a")
        if (!src.isFile || src.length() == 0L) return "ERR\nno recording"
        val notes = File(Rootfs.homeOf(context, env), "notes").apply { mkdirs() }
        val dest = File(notes, "voice-${System.currentTimeMillis()}.m4a")
        src.copyTo(dest, overwrite = true)
        return "OK\n" + HostPaths.toGuest(context, env, dest)
    }

    private fun servers(context: Context): String {
        val home = Rootfs.homeOf(context)
        val f = File(home, ".config/nibras/servers")
        if (!f.isFile) return "OK\nnone"
        val lines = f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) return "OK\nnone"
        val sp = Prefs(context).sp
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel("servers", "Servers", NotificationManager.IMPORTANCE_DEFAULT))
        val out = StringBuilder()
        for (line in lines.take(12)) {
            val name = line.substringBefore(' ').take(40)
            val target = line.substringAfter(' ', "").trim()
            val ok = probe(target)
            val key = "srv:" + name
            val was = sp.getString(key, "up")
            if (!ok && was != "down" && !Quiet.on(context)) {
                nm?.notify(7700 + (name.hashCode() and 0xff), Notification.Builder(context, "servers")
                    .setSmallIcon(R.drawable.ic_stat_terminal)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(name)
                    .setAutoCancel(true)
                    .build())
            }
            sp.edit().putString(key, if (ok) "up" else "down").apply()
            out.append(if (ok) "up  " else "down ").append(name).append('\n')
        }
        return "OK\n" + out.toString().trimEnd()
    }

    private fun probe(target: String): Boolean = runCatching {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            val c = java.net.URL(target).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 6000; c.instanceFollowRedirects = true
            val code = c.responseCode; c.disconnect(); code in 200..399
        } else {
            val host = target.substringBeforeLast(':').ifBlank { target }
            val port = target.substringAfterLast(':', "").toIntOrNull() ?: 443
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 6000) }; true
        }
    }.getOrDefault(false)

    private fun yes(context: Context, message: String): Boolean {
        val raw = PromptActivity.ask(context, "dialog", listOf("confirm", "-t", "Nibras", "-i", message.take(500)), timeoutSec = 30)
        return JSONObject(raw).optString("text") == "yes"
    }

    @Suppress("DEPRECATION")
    private fun wifiIp(context: Context): String? {
        val info = context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo ?: return null
        val i = info.ipAddress
        if (i == 0) return null
        return "${i and 0xff}.${i shr 8 and 0xff}.${i shr 16 and 0xff}.${i shr 24 and 0xff}"
    }

    private fun privateIp(host: String): Boolean {
        if (host.startsWith("10.") || host.startsWith("192.168.") || host == "127.0.0.1") return true
        if (host.startsWith("172.")) {
            val n = host.split('.').getOrNull(1)?.toIntOrNull() ?: return false
            return n in 16..31
        }
        return false
    }
}

object Quiet {
    fun on(context: Context): Boolean {
        val text = runCatching { File(Rootfs.homeOf(context), ".nibras/quiet").takeIf { it.isFile }?.readText()?.trim() }.getOrNull().orEmpty()
        if (text.isEmpty() || text == "off") return false
        if (text == "prayer") {
            val today = runCatching { PrayerAlarms.today(context) }.getOrNull() ?: return false
            val now = System.currentTimeMillis()
            return today.second.values.any { abs(it - now) < 15 * 60 * 1000 }
        }
        val parts = text.split(Regex("\\s+"))
        val a = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val b = parts.getOrNull(1)?.toIntOrNull() ?: return false
        if (a !in 0..23 || b !in 0..23 || a == b) return false
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (a < b) hour in a until b else hour >= a || hour < b
    }
}

package com.nibras.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.ui.HostPaths
import com.termux.terminal.NibrasPty
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * `remote`: a small web terminal for a computer on the same Wi-Fi.
 * - LAN only: connections from non-private addresses are refused.
 * - One-time link token: the first visit trades it for a session cookie; the link then stops working.
 * - Real shell on a PTY over a WebSocket (xterm.js is bundled in the app, nothing is loaded from the internet).
 * - File list, download and upload inside the Linux home.
 * - Stops by itself after [IDLE_MS] without a connected browser, or with `remote off` / the notification button.
 */
object RemoteServer {
    private const val IDLE_MS = 30 * 60_000L
    private const val NOTE_ID = 7351
    private const val CHANNEL = "remote"
    private val rnd = SecureRandom()

    @Volatile private var server: ServerSocket? = null
    @Volatile private var token: String? = null
    @Volatile private var envId: String = Rootfs.ANDROID
    @Volatile private var lastActive = 0L
    private val sessions = ConcurrentHashMap.newKeySet<String>()
    private val live = AtomicInteger(0)
    private lateinit var app: Context

    val running get() = server?.isClosed == false

    private fun hex(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun isLan(a: InetAddress): Boolean = a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress ||
        (a is Inet6Address && (a.address[0].toInt() and 0xfe) == 0xfc) ||
        (a is Inet4Address && a.address[0].toInt() and 0xff == 100 && (a.address[1].toInt() and 0xc0) == 64) // CGNAT / hotspot

    /** API: remote on|off|status. Returns text for the terminal. */
    @Synchronized
    fun command(context: Context, env: String, args: List<String>): String {
        app = context.applicationContext
        return when (args.firstOrNull() ?: "status") {
            "on", "start" -> {
                val port = args.getOrNull(1)?.toIntOrNull() ?: 8765
                if (!running) start(env, port)
                token = hex(16); sessions.clear(); lastActive = System.currentTimeMillis()
                val ip = lanAddress() ?: "127.0.0.1"
                val url = "http://$ip:${server!!.localPort}/?t=$token"
                notifyOn(url); url
            }
            "off", "stop" -> { stop(); "off" }
            else -> if (running) "on · port ${server?.localPort} · browsers: ${live.get()}" else "off"
        }
    }

    private fun start(env: String, wanted: Int) {
        envId = env
        val s = ServerSocket()
        s.reuseAddress = true
        var port = wanted
        while (true) {
            try { s.bind(InetSocketAddress(port)); break } catch (e: java.io.IOException) { if (++port > wanted + 30) throw e }
        }
        server = s
        Thread({ acceptLoop(s) }, "remote-accept").start()
        Thread({
            while (!s.isClosed) {
                Thread.sleep(30_000)
                if (live.get() == 0 && System.currentTimeMillis() - lastActive > IDLE_MS) stop()
            }
        }, "remote-idle").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null; token = null; sessions.clear()
        if (::app.isInitialized) app.getSystemService(NotificationManager::class.java)?.cancel(NOTE_ID)
    }

    private fun notifyOn(url: String) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.remote_title), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getBroadcast(app, 7351, Intent(app, RemoteStopReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(app, 7352, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_terminal).setContentTitle(app.getString(R.string.remote_title))
            .setContentText(url.substringBefore("/?")).setStyle(android.app.Notification.BigTextStyle().bigText(app.getString(R.string.remote_note, url)))
            .setOngoing(true).setContentIntent(open)
            .addAction(android.app.Notification.Action.Builder(null, app.getString(R.string.remote_stop), stop).build())
            .build()
        runCatching { nm.notify(NOTE_ID, n) }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = runCatching { s.accept() }.getOrNull() ?: break
            if (!isLan(c.inetAddress)) { runCatching { c.close() }; continue }
            Thread({ runCatching { handle(c) }; runCatching { c.close() } }, "remote-client").start()
        }
    }

    // ------------------------------------------------------------------ HTTP

    private class Req(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val input: InputStream)

    private fun readLine(i: InputStream): String? {
        val b = StringBuilder()
        while (true) {
            val c = i.read(); if (c < 0) return if (b.isEmpty()) null else b.toString()
            if (c == '\n'.code) return b.toString().trimEnd('\r')
            b.append(c.toChar()); if (b.length > 8192) return null
        }
    }

    private fun parse(i: InputStream): Req? {
        val first = readLine(i)?.split(' ') ?: return null
        if (first.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) { val l = readLine(i) ?: return null; if (l.isEmpty()) break; val k = l.substringBefore(':').trim().lowercase(); headers[k] = l.substringAfter(':').trim() }
        val target = first[1]
        val q = target.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        return Req(first[0], target.substringBefore('?'), q, headers, i)
    }

    private fun send(o: OutputStream, code: Int, type: String, body: ByteArray, extra: String = "") {
        val status = mapOf(200 to "OK", 302 to "Found", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 500 to "Error")[code] ?: "OK"
        o.write(("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\n" +
            "X-Frame-Options: DENY\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n$extra\r\n").toByteArray())
        o.write(body); o.flush()
    }

    private fun cookie(r: Req) = r.headers["cookie"].orEmpty().split(';').map { it.trim() }.firstOrNull { it.startsWith("nt=") }?.removePrefix("nt=")

    private fun handle(c: Socket) {
        c.soTimeout = 0
        val i = BufferedInputStream(c.getInputStream()); val o = c.getOutputStream()
        val r = parse(i) ?: return
        lastActive = System.currentTimeMillis()
        // One-time link: trade the token for a session cookie, then drop the token from the address bar.
        val t = r.query["t"]
        if (t != null) {
            val ok = token != null && MessageDigest.isEqual(t.toByteArray(), token!!.toByteArray())
            if (!ok) { send(o, 403, "text/html; charset=utf-8", page(app.getString(R.string.remote_used))); return }
            token = null
            val sid = hex(24); sessions += sid
            send(o, 302, "text/plain", ByteArray(0), "Location: /\r\nSet-Cookie: nt=$sid; HttpOnly; SameSite=Strict; Path=/\r\n"); return
        }
        val sid = cookie(r)
        if (sid == null || sid !in sessions) { send(o, 403, "text/html; charset=utf-8", page(app.getString(R.string.remote_used))); return }
        // Same-origin only for anything that acts (blocks other web pages driving the terminal).
        val origin = r.headers["origin"]
        if (origin != null && origin.substringAfter("://") != r.headers["host"]) { send(o, 403, "text/plain", "bad origin".toByteArray()); return }
        when {
            r.path == "/ws" && r.headers["upgrade"].equals("websocket", true) -> webSocket(r, c, o)
            r.path == "/" -> asset(o, "remote/index.html", "text/html; charset=utf-8")
            r.path == "/xterm.js" -> asset(o, "remote/xterm.js", "text/javascript")
            r.path == "/xterm.css" -> asset(o, "remote/xterm.css", "text/css")
            r.path == "/addon-fit.js" -> asset(o, "remote/addon-fit.js", "text/javascript")
            r.path == "/api/ls" -> list(o, r.query["path"] ?: home())
            r.path == "/api/dl" -> download(o, r.query["path"] ?: "")
            r.path == "/api/up" && (r.method == "PUT" || r.method == "POST") -> upload(o, r)
            else -> send(o, 404, "text/plain", "not found".toByteArray())
        }
    }

    private fun page(msg: String) = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>Nibras Terminal</title><body style='background:#101418;color:#ddd;font:16px sans-serif;padding:2em'><h2 style='color:#D6B36A'>Nibras Terminal</h2><p>$msg</p>".toByteArray()

    private fun asset(o: OutputStream, name: String, type: String) =
        send(o, 200, type, app.assets.open(name).use { it.readBytes() })

    private fun home() = if (envId == Rootfs.ANDROID) Rootfs.homeOf(app, envId).absolutePath else "/root"
    private fun host(guest: String): File? = HostPaths.toHost(app, envId, guest)

    private fun list(o: OutputStream, path: String) {
        val d = host(path)?.takeIf { it.isDirectory } ?: return send(o, 404, "application/json", "{}".toByteArray())
        val a = JSONArray()
        d.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach {
            a.put(JSONObject().put("name", it.name).put("dir", it.isDirectory).put("size", it.length()).put("time", it.lastModified()))
        }
        send(o, 200, "application/json", JSONObject().put("path", path).put("items", a).toString().toByteArray())
    }

    private fun download(o: OutputStream, path: String) {
        val f = host(path)?.takeIf { it.isFile } ?: return send(o, 404, "text/plain", "not found".toByteArray())
        val name = f.name.replace("\"", "")
        o.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${f.length()}\r\nCache-Control: no-store\r\n" +
            "Content-Disposition: attachment; filename=\"$name\"\r\nConnection: close\r\n\r\n").toByteArray())
        FileInputStream(f).use { it.copyTo(o) }; o.flush()
    }

    private fun upload(o: OutputStream, r: Req) {
        val dir = host(r.query["path"] ?: home())?.takeIf { it.isDirectory } ?: return send(o, 404, "text/plain", "no folder".toByteArray())
        val name = File(r.query["name"] ?: "upload.bin").name.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: return send(o, 400, "text/plain", "bad name".toByteArray())
        val len = r.headers["content-length"]?.toLongOrNull() ?: return send(o, 400, "text/plain", "length needed".toByteArray())
        val target = File(dir, name)
        FileOutputStream(target).use { out ->
            val buf = ByteArray(65536); var left = len
            while (left > 0) { val n = r.input.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n < 0) break; out.write(buf, 0, n); left -= n; lastActive = System.currentTimeMillis() }
        }
        send(o, 200, "application/json", JSONObject().put("ok", true).put("name", name).toString().toByteArray())
    }

    // ------------------------------------------------------------------ WebSocket + PTY

    private fun webSocket(r: Req, c: Socket, o: OutputStream) {
        val key = r.headers["sec-websocket-key"] ?: return send(o, 400, "text/plain", ByteArray(0))
        val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
        o.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray()); o.flush()
        val launch = Rootfs.launch(app, envId, null, listOf("NIBRAS_REMOTE=1"))
        val pid = IntArray(1)
        val fd = NibrasPty.start(launch.exe, launch.cwd, launch.args, launch.env, pid, 30, 100)
        val pfd = ParcelFileDescriptor.adoptFd(fd)
        val ptyIn = FileInputStream(pfd.fileDescriptor); val ptyOut = FileOutputStream(pfd.fileDescriptor)
        live.incrementAndGet()
        val lock = Any()
        fun frame(op: Int, data: ByteArray, off: Int = 0, len: Int = data.size) = synchronized(lock) {
            o.write(0x80 or op)
            when { len < 126 -> o.write(len); len < 65536 -> { o.write(126); o.write(len shr 8); o.write(len and 0xff) }
                else -> { o.write(127); for (s in 56 downTo 0 step 8) o.write(((len.toLong() shr s) and 0xff).toInt()) } }
            o.write(data, off, len); o.flush()
        }
        val reader = Thread({
            val buf = ByteArray(16384)
            try { while (true) { val n = ptyIn.read(buf); if (n <= 0) break; frame(2, buf, 0, n); lastActive = System.currentTimeMillis() } } catch (_: Throwable) {}
            runCatching { frame(8, ByteArray(0)) }; runCatching { c.close() }
        }, "remote-pty")
        reader.start()
        val i = r.input
        try {
            while (true) {
                val b0 = i.read(); if (b0 < 0) break
                val b1 = i.read(); if (b1 < 0) break
                var len = (b1 and 0x7f).toLong()
                if (len == 126L) len = ((i.read() shl 8) or i.read()).toLong()
                else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or i.read().toLong() } }
                if (len > 8 shl 20) break
                val mask = if (b1 and 0x80 != 0) ByteArray(4) { i.read().toByte() } else null
                val data = ByteArray(len.toInt()); var got = 0
                while (got < data.size) { val n = i.read(data, got, data.size - got); if (n < 0) break; got += n }
                if (mask != null) for (k in data.indices) data[k] = (data[k].toInt() xor mask[k % 4].toInt()).toByte()
                lastActive = System.currentTimeMillis()
                when (b0 and 0x0f) {
                    8 -> break
                    9 -> frame(10, data)
                    1, 2, 0 -> if (data.isNotEmpty() && data[0] == 0.toByte()) {
                        // Control message: \0{"rows":N,"cols":M}
                        runCatching { val j = JSONObject(String(data, 1, data.size - 1)); NibrasPty.resize(fd, j.getInt("rows"), j.getInt("cols")) }
                    } else ptyOut.write(data)
                }
            }
        } catch (_: Throwable) {
        } finally {
            live.decrementAndGet(); lastActive = System.currentTimeMillis()
            runCatching { android.os.Process.sendSignal(pid[0], 1) } // SIGHUP: like closing a terminal window
            runCatching { pfd.close() }
            Thread { runCatching { NibrasPty.waitFor(pid[0]) } }.start()
        }
    }
}

class RemoteStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { RemoteServer.stop() }
}

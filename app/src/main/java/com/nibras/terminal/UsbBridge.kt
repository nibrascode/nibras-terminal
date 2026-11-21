package com.nibras.terminal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.LocalServerSocket
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * USB for the Linux side (termux-usb equivalent) plus a small built-in USB-serial driver
 * (CDC-ACM, CP210x, FTDI, CH340/CH341, the chips used by Arduino, ESP32, routers and most adapters).
 *
 *  - list:        JSON of attached devices
 *  - permission:  system "allow access" prompt
 *  - fd:          opens the device and hands its file descriptor to the guest over an abstract
 *                 unix socket (only our own uid may connect), like Termux does for libusb
 *  - serial:      opens a serial adapter and bridges it to 127.0.0.1:<port>
 */
object UsbBridge {
    private const val ACTION = "com.nibras.terminal.USB_PERMISSION"
    private val open = mutableMapOf<String, UsbDeviceConnection>()

    private fun manager(c: Context) = c.getSystemService(UsbManager::class.java)

    private fun find(c: Context, name: String?): UsbDevice? {
        val all = manager(c).deviceList.values
        if (name.isNullOrBlank()) return all.singleOrNull()
        return all.firstOrNull { it.deviceName == name || "%04x:%04x".format(it.vendorId, it.productId) == name.lowercase() }
    }

    fun list(c: Context): String = JSONArray().apply {
        manager(c).deviceList.values.forEach { d ->
            put(JSONObject().put("device", d.deviceName).put("vendor_id", "%04x".format(d.vendorId)).put("product_id", "%04x".format(d.productId))
                .put("manufacturer", runCatching { d.manufacturerName }.getOrNull()).put("product", runCatching { d.productName }.getOrNull())
                .put("class", d.deviceClass).put("interfaces", d.interfaceCount).put("serial_driver", driverName(d))
                .put("permission", manager(c).hasPermission(d)))
        }
    }.toString(2)

    /** Asks for permission if needed; blocks up to 60 s for the user's answer. */
    fun permission(c: Context, d: UsbDevice): Boolean {
        val m = manager(c)
        if (m.hasPermission(d)) return true
        val gate = CountDownLatch(1)
        var granted = false
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) { granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false); gate.countDown() }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") c.registerReceiver(r, filter)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        m.requestPermission(d, PendingIntent.getBroadcast(c, 7, Intent(ACTION).setPackage(c.packageName), flags))
        gate.await(60, TimeUnit.SECONDS)
        runCatching { c.unregisterReceiver(r) }
        return granted || m.hasPermission(d)
    }

    fun request(c: Context, name: String?): String {
        val d = find(c, name) ?: return "ERR\n" + noDevice(c)
        return if (permission(c, d)) "OK\ngranted ${d.deviceName}" else "ERR\npermission denied"
    }

    private fun noDevice(c: Context) = if (manager(c).deviceList.isEmpty()) "no USB device attached (OTG)" else "several devices: give one from nibras-usb -l"

    private fun connect(c: Context, name: String?): Pair<UsbDevice, UsbDeviceConnection> {
        val d = find(c, name) ?: error(noDevice(c))
        if (!permission(c, d)) error("permission denied")
        open.remove(d.deviceName)?.close()
        val conn = manager(c).openDevice(d) ?: error("cannot open ${d.deviceName}")
        open[d.deviceName] = conn
        return d to conn
    }

    /** Opens the device and serves its fd once over an abstract socket. Returns the socket name. */
    fun fd(c: Context, name: String?): String {
        val (d, conn) = runCatching { connect(c, name) }.getOrElse { return "ERR\n${it.message}" }
        val sock = "nibras-usb-" + java.util.UUID.randomUUID().toString().take(12)
        val server = LocalServerSocket(sock)
        thread(isDaemon = true, name = "usb-fd") {
            server.use { srv ->
                repeat(3) {
                    val client = runCatching { srv.accept() }.getOrNull() ?: return@thread
                    client.use { cl ->
                        if (cl.peerCredentials.uid != Process.myUid()) return@use // never hand the device to another app
                        val pfd = ParcelFileDescriptor.fromFd(conn.fileDescriptor)
                        cl.setFileDescriptorsForSend(arrayOf(pfd.fileDescriptor))
                        cl.outputStream.write(1); cl.outputStream.flush()
                        Thread.sleep(300); pfd.close()
                        return@thread
                    }
                }
            }
        }
        return "OK\n$sock ${d.deviceName}"
    }

    // ------------------------------------------------------------------ serial
    private fun driverName(d: UsbDevice): String? = when (d.vendorId) {
        0x10C4 -> "cp210x"; 0x0403 -> "ftdi"; 0x1A86 -> "ch34x"
        else -> if ((0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }) "cdc-acm" else null
    }

    private class Port(val conn: UsbDeviceConnection, val iface: UsbInterface, val inEp: UsbEndpoint, val outEp: UsbEndpoint, val driver: String, val ctrlIndex: Int)

    private fun bulk(i: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
        var inEp: UsbEndpoint? = null; var outEp: UsbEndpoint? = null
        for (k in 0 until i.endpointCount) { val e = i.getEndpoint(k); if (e.type == UsbConstants.USB_ENDPOINT_XFER_BULK) { if (e.direction == UsbConstants.USB_DIR_IN) inEp = e else outEp = e } }
        return if (inEp != null && outEp != null) inEp to outEp else null
    }

    private fun openSerial(d: UsbDevice, conn: UsbDeviceConnection, baud: Int): Port {
        val driver = driverName(d) ?: error("not a known serial adapter (cdc-acm, cp210x, ftdi, ch34x)")
        val data = (0 until d.interfaceCount).map { d.getInterface(it) }.firstOrNull { (driver != "cdc-acm" || it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) && bulk(it) != null }
            ?: error("no bulk endpoints")
        val comm = (0 until d.interfaceCount).map { d.getInterface(it) }.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_COMM }
        comm?.let { conn.claimInterface(it, true) }
        if (!conn.claimInterface(data, true)) error("cannot claim interface")
        val (i, o) = bulk(data)!!
        val p = Port(conn, data, i, o, driver, comm?.id ?: data.id)
        configure(p, baud)
        return p
    }

    private fun out(p: Port, type: Int, req: Int, value: Int, index: Int, buf: ByteArray? = null) =
        p.conn.controlTransfer(type, req, value, index, buf, buf?.size ?: 0, 1000)

    private fun configure(p: Port, baud: Int) {
        val le = byteArrayOf((baud and 0xff).toByte(), (baud shr 8 and 0xff).toByte(), (baud shr 16 and 0xff).toByte(), (baud shr 24 and 0xff).toByte())
        when (p.driver) {
            "cdc-acm" -> {
                out(p, 0x21, 0x20, 0, p.ctrlIndex, le + byteArrayOf(0, 0, 8)) // SET_LINE_CODING 8N1
                out(p, 0x21, 0x22, 0x03, p.ctrlIndex)                         // DTR | RTS
            }
            "cp210x" -> {
                out(p, 0x41, 0x00, 0x0001, p.iface.id)       // IFC_ENABLE
                out(p, 0x41, 0x1E, 0, p.iface.id, le)        // SET_BAUDRATE
                out(p, 0x41, 0x03, 0x0800, p.iface.id)       // 8N1
                out(p, 0x41, 0x07, 0x0303, p.iface.id)       // DTR | RTS
            }
            "ftdi" -> {
                val divfrac = intArrayOf(0, 3, 2, 4, 1, 5, 6, 7)
                val d3 = (8L * 48_000_000 + baud) / (2L * baud)
                var div = (d3 shr 3).toInt() or (divfrac[(d3 and 7).toInt()] shl 14)
                if (div == 1) div = 0 else if (div == 0x4001) div = 1
                val port = p.iface.id + 1
                out(p, 0x40, 0, 0, port)                                  // reset
                out(p, 0x40, 3, div and 0xFFFF, ((div shr 8) and 0xFF00) or port) // baud
                out(p, 0x40, 4, 8, port)                                  // 8N1
                out(p, 0x40, 1, 0x0303, port)                             // DTR | RTS
            }
            "ch34x" -> {
                val table = mapOf(2400 to (0xd901 to 0x0038), 4800 to (0x6402 to 0x001f), 9600 to (0xb202 to 0x0013), 19200 to (0xd902 to 0x000d),
                    38400 to (0x6403 to 0x000a), 57600 to (0x9803 to 0x0010), 115200 to (0xcc03 to 0x0008), 230400 to (0xe603 to 0x0008),
                    460800 to (0xf303 to 0x0008), 921600 to (0xf387 to 0x0000))
                val (a, b) = table[baud] ?: table.getValue(115200)
                p.conn.controlTransfer(0xC0, 0x5F, 0, 0, ByteArray(2), 2, 1000)
                out(p, 0x40, 0xA1, 0, 0)
                out(p, 0x40, 0x9A, 0x1312, a); out(p, 0x40, 0x9A, 0x0f2c, b)
                out(p, 0x40, 0x9A, 0x2518, 0x00c3)                        // 8N1
                out(p, 0x40, 0xA1, 0x501f, 0xd90a)
                out(p, 0x40, 0x9A, 0x1312, a); out(p, 0x40, 0x9A, 0x0f2c, b)
                out(p, 0x40, 0xA4, (0x20 or 0x40).inv() and 0xFF, 0)       // DTR | RTS on
            }
        }
    }

    /** Opens a serial adapter and bridges it to a local TCP port. Returns the port. */
    fun serial(c: Context, name: String?, baud: Int): String {
        val (d, conn) = runCatching { connect(c, name) }.getOrElse { return "ERR\n${it.message}" }
        val port = runCatching { openSerial(d, conn, baud) }.getOrElse { conn.close(); open.remove(d.deviceName); return "ERR\n${it.message}" }
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true, name = "usb-serial") {
            server.use { srv ->
                srv.soTimeout = 120_000
                val s = runCatching { srv.accept() }.getOrNull() ?: run { conn.close(); return@thread }
                s.tcpNoDelay = true
                val input = s.getInputStream(); val output = s.getOutputStream()
                var alive = true
                thread(isDaemon = true) { // phone → device
                    val buf = ByteArray(1024)
                    while (alive) {
                        val n = runCatching { input.read(buf) }.getOrDefault(-1)
                        if (n < 0) { alive = false; break }
                        var off = 0
                        while (off < n) { val w = conn.bulkTransfer(port.outEp, buf.copyOfRange(off, n), n - off, 2000); if (w <= 0) break; off += w }
                    }
                }
                val buf = ByteArray(maxOf(64, port.inEp.maxPacketSize) * 4)
                while (alive) { // device → phone
                    val n = conn.bulkTransfer(port.inEp, buf, buf.size, 200)
                    if (n > 0) {
                        val bytes = if (port.driver == "ftdi") stripFtdi(buf, n, port.inEp.maxPacketSize) else buf.copyOf(n)
                        if (bytes.isNotEmpty()) runCatching { output.write(bytes); output.flush() }.onFailure { alive = false }
                    }
                }
                runCatching { s.close() }
                runCatching { conn.releaseInterface(port.iface); conn.close() }
                open.remove(d.deviceName)
            }
        }
        return "OK\n${server.localPort} ${port.driver} ${d.deviceName} $baud"
    }

    /** FTDI chips put two status bytes in front of every packet. */
    private fun stripFtdi(buf: ByteArray, n: Int, packet: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < n) { val end = minOf(i + packet, n); if (end - i > 2) out.write(buf, i + 2, end - i - 2); i += packet }
        return out.toByteArray()
    }
}

package com.nibras.terminal

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.view.Gravity
import android.webkit.MimeTypeMap
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nibras.terminal.ui.HostPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Termux:API compatible phone commands: same names, same flags, same JSON output, so existing
 * Termux scripts (termux-battery-status | jq .percentage …) work unchanged under nibras-* names.
 */
object TermuxApi {
    class Call(
        val context: Context,
        val args: List<String>,
        val stdin: ByteArray?,
        val envId: String,
        val pwd: String,
        val requestPermission: (String) -> Unit,
        val out: OutputStream,
    )

    /** Thrown to end a command with an error message (exit code 1 in the shell). */
    class Fail(message: String) : Exception(message)

    val NAMES = setOf(
        "battery-status", "brightness", "call-log", "camera-info", "camera-photo", "clipboard-get", "clipboard-set",
        "contact-list", "download", "fingerprint", "infrared-frequencies", "infrared-transmit", "location",
        "media-player", "media-scan", "microphone-record", "notification", "notification-remove", "notification-list",
        "notification-channel", "sensor", "share", "sms-list", "sms-inbox", "sms-send", "telephony-call", "toast",
        "torch", "tts-engines", "tts-speak", "vibrate", "volume", "wifi-connectioninfo", "wifi-enable", "wifi-scaninfo",
        "wallpaper", "open", "open-url", "info", "api-start", "api-stop", "storage-get", "dialog", "storage-put", "crypt", "remote-server", "widget-refresh", "app-data",
        "saf-managedir", "saf-dirs", "saf-ls", "saf-stat", "saf-read", "saf-write", "saf-mkdir", "saf-create", "saf-rm",
    )

    private val main = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------ plumbing

    /** Minimal getopt: [withValue] lists options taking a value (short "-t" or long "--title"). */
    class Opts(args: List<String>, withValue: Set<String>) {
        val values = mutableMapOf<String, String>()
        val flags = mutableSetOf<String>()
        val rest = mutableListOf<String>()
        init {
            var i = 0
            var noMore = false
            while (i < args.size) {
                val a = args[i]
                when {
                    noMore || a == "-" || !a.startsWith("-") -> rest += a
                    a == "--" -> noMore = true
                    a.startsWith("--") && a.contains('=') -> values[a.substringBefore('=')] = a.substringAfter('=')
                    a in withValue -> { values[a] = args.getOrElse(i + 1) { "" }; i++ }
                    !a.startsWith("--") && a.length > 2 && "-${a[1]}" in withValue -> values["-${a[1]}"] = a.substring(2)
                    !a.startsWith("--") && a.length > 2 -> a.drop(1).forEach { flags += "-$it" }
                    else -> flags += a
                }
                i++
            }
        }
        operator fun get(vararg names: String): String? = names.firstNotNullOfOrNull { values[it] }
        fun has(vararg names: String) = names.any { it in flags || it in values }
    }

    private fun Call.perm(p: String) {
        if (context.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(p)
            throw Fail("permission needed: ${p.substringAfterLast('.')} — allow it in the dialog (or Android Settings → Apps → Nibras Terminal → Permissions), then run the command again")
        }
    }

    private fun Call.text(): String = stdin?.let { String(it) } ?: ""
    fun Call.host(path: String): File {
        val abs = if (path.startsWith("/")) path else "${pwd.trimEnd('/')}/$path"
        return HostPaths.toHost(context, envId, abs) ?: throw Fail("bad path: $path")
    }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var v: Result<T>? = null
        val gate = CountDownLatch(1)
        main.post { v = runCatching(block); gate.countDown() }
        gate.await(20, TimeUnit.SECONDS)
        return (v ?: throw Fail("timeout")).getOrThrow()
    }

    private fun Call.emit(s: String) { out.write(s.toByteArray()); if (!s.endsWith("\n")) out.write('\n'.code); out.flush() }

    /** Runs [name]; writes "OK\n…" / "ERR\n…" / "B64\n…" to [Call.out]. */
    fun run(c: Call, name: String) {
        try {
            val result = dispatch(c, name)
            if (result != null) { c.out.write("OK\n".toByteArray()); if (result.isNotEmpty()) c.emit(result) else c.out.flush() }
        } catch (f: Fail) {
            c.out.write("ERR\n${f.message}\n".toByteArray())
        } catch (t: Throwable) {
            c.out.write("ERR\n${t.message ?: t.javaClass.simpleName}\n".toByteArray())
        }
        runCatching { c.out.flush() }
    }

    /** Returns text for a normal reply, or null when the command already streamed its own reply. */
    private fun dispatch(c: Call, name: String): String? {
        if (!Sensitive.allow(c.context, name)) throw Fail("refused")
        return when (name) {
        "battery-status" -> battery(c)
        "brightness" -> brightness(c)
        "call-log" -> callLog(c)
        "camera-info" -> cameraInfo(c)
        "camera-photo" -> cameraPhoto(c)
        "clipboard-get" -> onMain { c.context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c.context)?.toString() ?: "" }
        "clipboard-set" -> {
            val t = if (c.args.isNotEmpty()) c.args.joinToString(" ") else c.text()
            onMain { c.context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Nibras", t)) }; ""
        }
        "contact-list" -> contacts(c)
        "download" -> download(c)
        "fingerprint" -> fingerprint(c)
        "infrared-frequencies" -> irFrequencies(c)
        "infrared-transmit" -> irTransmit(c)
        "location" -> location(c)
        "media-player" -> mediaPlayer(c)
        "media-scan" -> mediaScan(c)
        "microphone-record" -> microphone(c)
        "notification" -> notification(c)
        "notification-remove" -> { val id = c.args.firstOrNull() ?: throw Fail("usage: nibras-notification-remove <id>"); notificationManager(c).cancel(id, NOTE_ID); notificationManager(c).cancel(id.toIntOrNull() ?: id.hashCode()); "" }
        "notification-list" -> notificationList(c)
        "notification-channel" -> notificationChannel(c)
        "sensor" -> { sensor(c); null }
        "share" -> share(c)
        "sms-list" -> smsList(c, c.args)
        "sms-inbox" -> smsList(c, listOf("-t", "inbox") + c.args)
        "sms-send" -> smsSend(c)
        "telephony-call" -> call(c)
        "toast" -> toast(c)
        "torch" -> torch(c)
        "tts-engines" -> ttsEngines(c)
        "tts-speak" -> ttsSpeak(c)
        "vibrate" -> vibrate(c)
        "volume" -> volume(c)
        "wifi-connectioninfo" -> wifiInfo(c)
        "wifi-enable" -> wifiEnable(c)
        "wifi-scaninfo" -> { c.perm(Manifest.permission.ACCESS_FINE_LOCATION); wifiScan(c) }
        "wallpaper" -> wallpaper(c)
        "open" -> open(c)
        "open-url" -> { val u = c.args.firstOrNull() ?: throw Fail("usage: nibras-open-url <url>"); onMain { c.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; "" }
        "info" -> info(c)
        "api-start", "api-stop" -> "Nibras API is built into the app; nothing to start or stop."
        "storage-get" -> storageGet(c)
        "storage-put" -> storagePut(c)
        "crypt" -> crypt(c)
        "app-data" -> appData(c)
        "remote-server" -> RemoteServer.command(c.context, c.envId, c.args)
        "widget-refresh" -> { NibrasWidgets.refreshAll(c.context); "" }
        "dialog" -> dialog(c)
        "saf-managedir", "saf-dirs", "saf-ls", "saf-stat", "saf-read", "saf-write", "saf-mkdir", "saf-create", "saf-rm" -> saf(c, name)
        else -> throw Fail("unknown command: $name")
        }
    }

    // ------------------------------------------------------------------ device state

    private fun battery(c: Call): String {
        val i = c.context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: throw Fail("no battery info")
        val bm = c.context.getSystemService(BatteryManager::class.java)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val o = JSONObject()
        o.put("present", i.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true))
        o.put("technology", i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "")
        o.put("health", when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"; BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"; BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE"
            else -> "UNKNOWN" })
        o.put("plugged", when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            0 -> "UNPLUGGED"; BatteryManager.BATTERY_PLUGGED_AC -> "PLUGGED_AC"; BatteryManager.BATTERY_PLUGGED_USB -> "PLUGGED_USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "PLUGGED_WIRELESS"; else -> "PLUGGED_" + i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) })
        o.put("status", when (i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"; BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"; BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"; else -> "UNKNOWN" })
        o.put("temperature", i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)
        o.put("voltage", i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0))
        o.put("current", bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0)
        o.put("current_average", bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0)
        o.put("percentage", if (level >= 0 && scale > 0) level * 100 / scale else bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1)
        o.put("level", level)
        o.put("scale", scale)
        o.put("charge_counter", bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0)
        o.put("energy", bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) ?: 0L)
        if (Build.VERSION.SDK_INT >= 34) o.put("cycle", i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1))
        return o.toString(2)
    }

    private fun brightness(c: Call): String {
        val v = c.args.firstOrNull() ?: throw Fail("usage: nibras-brightness <0-255|auto>")
        if (!Settings.System.canWrite(c.context)) {
            onMain { c.context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            throw Fail("allow \"Modify system settings\" for Nibras Terminal (screen opened), then run again")
        }
        val cr = c.context.contentResolver
        if (v == "auto") { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC); return "" }
        val n = v.toIntOrNull()?.coerceIn(0, 255) ?: throw Fail("brightness must be 0-255 or auto")
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, n)
        return ""
    }

    private fun volume(c: Call): String {
        val am = c.context.getSystemService(AudioManager::class.java)
        val streams = linkedMapOf("call" to AudioManager.STREAM_VOICE_CALL, "system" to AudioManager.STREAM_SYSTEM, "ring" to AudioManager.STREAM_RING,
            "music" to AudioManager.STREAM_MUSIC, "alarm" to AudioManager.STREAM_ALARM, "notification" to AudioManager.STREAM_NOTIFICATION)
        if (c.args.size >= 2) {
            val s = streams[c.args[0]] ?: throw Fail("stream must be one of: ${streams.keys.joinToString()}")
            val v = c.args[1].toIntOrNull() ?: throw Fail("volume must be a number")
            runCatching { am.setStreamVolume(s, v.coerceIn(0, am.getStreamMaxVolume(s)), 0) }.onFailure {
                throw Fail("Android refused (Do Not Disturb access is needed for ring/notification volume): ${it.message}") }
            return ""
        }
        val a = JSONArray()
        streams.forEach { (n, s) -> a.put(JSONObject().put("stream", n).put("volume", am.getStreamVolume(s)).put("max_volume", am.getStreamMaxVolume(s))) }
        return a.toString(2)
    }

    private fun torch(c: Call): String {
        val on = c.args.firstOrNull() ?: throw Fail("usage: nibras-torch on|off")
        val cm = c.context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: throw Fail("no flashlight")
        cm.setTorchMode(id, on == "on")
        return ""
    }

    private fun vibrate(c: Call): String {
        val o = Opts(c.args, setOf("-d"))
        val ms = o["-d"]?.toLongOrNull() ?: 1000L
        val am = c.context.getSystemService(AudioManager::class.java)
        if (!o.has("-f") && am.ringerMode == AudioManager.RINGER_MODE_SILENT) return ""
        c.context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms.coerceIn(1, 30_000), VibrationEffect.DEFAULT_AMPLITUDE))
        return ""
    }

    private fun toast(c: Call): String {
        val o = Opts(c.args, setOf("-b", "-c", "-g"))
        val text = if (o.rest.isNotEmpty()) o.rest.joinToString(" ") else c.text().trimEnd('\n')
        onMain {
            @Suppress("DEPRECATION")
            val t = Toast.makeText(c.context.applicationContext, text, if (o.has("-s")) Toast.LENGTH_SHORT else Toast.LENGTH_LONG)
            val custom = o["-b"] != null || o["-c"] != null
            if (custom && Build.VERSION.SDK_INT < 30) {
                val tv = TextView(c.context).apply {
                    this.text = text; setPadding(32, 20, 32, 20)
                    setBackgroundColor(runCatching { Color.parseColor(o["-b"] ?: "gray") }.getOrDefault(Color.GRAY))
                    setTextColor(runCatching { Color.parseColor(o["-c"] ?: "white") }.getOrDefault(Color.WHITE))
                }
                @Suppress("DEPRECATION") run { t.view = tv }
            }
            when (o["-g"]) { "top" -> t.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 120); "bottom" -> t.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 120); "middle" -> t.setGravity(Gravity.CENTER, 0, 0) }
            t.show()
        }
        return ""
    }

    // ------------------------------------------------------------------ people

    private fun contacts(c: Call): String {
        c.perm(Manifest.permission.READ_CONTACTS)
        val a = JSONArray()
        c.context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cur ->
            while (cur.moveToNext()) a.put(JSONObject().put("name", cur.getString(0) ?: "").put("number", cur.getString(1) ?: ""))
        }
        return a.toString(2)
    }

    private val stamp get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun callLog(c: Call): String {
        c.perm(Manifest.permission.READ_CALL_LOG)
        val o = Opts(c.args, setOf("-l", "-o"))
        val limit = o["-l"]?.toIntOrNull() ?: 10
        val offset = o["-o"]?.toIntOrNull() ?: 0
        val a = JSONArray()
        c.context.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID),
            null, null, CallLog.Calls.DATE + " DESC")?.use { cur ->
            if (offset > 0 && !cur.move(offset)) return a.toString(2)
            while (cur.moveToNext() && a.length() < limit) {
                val d = cur.getLong(4)
                a.put(JSONObject().put("name", cur.getString(0) ?: "UNKNOWN_CALLER").put("phone_number", cur.getString(1) ?: "")
                    .put("type", when (cur.getInt(2)) { CallLog.Calls.INCOMING_TYPE -> "INCOMING"; CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"; CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"; CallLog.Calls.REJECTED_TYPE -> "REJECTED"; CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"; else -> "UNKNOWN" })
                    .put("date", stamp.format(Date(cur.getLong(3)))).put("duration", "%02d:%02d".format(d / 60, d % 60)).put("sim_id", cur.getString(5) ?: ""))
            }
        }
        return a.toString(2)
    }

    private fun smsList(c: Call, args: List<String>): String {
        c.perm(Manifest.permission.READ_SMS)
        val o = Opts(args, setOf("-l", "-o", "-t", "-f"))
        val limit = o["-l"]?.toIntOrNull() ?: 10
        val offset = o["-o"]?.toIntOrNull() ?: 0
        val type = o["-t"] ?: "inbox"
        val uri = Uri.parse(if (type == "all") "content://sms" else "content://sms/$type")
        val from = o["-f"]
        val conv = o.has("-c")
        val seen = mutableSetOf<Long>()
        val a = JSONArray()
        var skipped = 0
        c.context.contentResolver.query(uri, arrayOf("_id", "thread_id", "type", "read", "address", "date", "body"),
            if (from != null) "address LIKE ?" else null, if (from != null) arrayOf("%$from%") else null, "date DESC")?.use { cur ->
            while (cur.moveToNext() && a.length() < limit) {
                if (conv && !seen.add(cur.getLong(1))) continue
                if (skipped++ < offset) continue
                val number = cur.getString(4) ?: ""
                val obj = JSONObject().put("threadid", cur.getLong(1))
                    .put("type", when (cur.getInt(2)) { 1 -> "inbox"; 2 -> "sent"; 3 -> "draft"; 4 -> "outbox"; 5 -> "failed"; 6 -> "queued"; else -> "unknown" })
                    .put("read", cur.getInt(3) == 1).put("number", number)
                    .put("received", stamp.format(Date(cur.getLong(5)))).put("body", cur.getString(6) ?: "").put("_id", cur.getLong(0))
                if (!o.has("-n")) contactName(c, number)?.let { obj.put("sender", it) }
                a.put(obj)
            }
        }
        return a.toString(2)
    }

    private fun contactName(c: Call, number: String): String? = runCatching {
        if (number.isBlank() || c.context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        c.context.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    private fun smsSend(c: Call): String {
        c.perm(Manifest.permission.SEND_SMS)
        val o = Opts(c.args, setOf("-n", "-s"))
        val numbers = o["-n"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: throw Fail("usage: nibras-sms-send -n number[,number2] [-s slot] text")
        val text = if (o.rest.isNotEmpty()) o.rest.joinToString(" ") else c.text().trimEnd('\n')
        if (text.isEmpty()) throw Fail("empty message")
        val sms = o["-s"]?.toIntOrNull()?.let { slot ->
            c.perm(Manifest.permission.READ_PHONE_STATE)
            @SuppressLint("MissingPermission")
            val sub = c.context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList?.firstOrNull { it.simSlotIndex == slot } ?: throw Fail("no SIM in slot $slot")
            @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(sub.subscriptionId)
        } ?: if (Build.VERSION.SDK_INT >= 31) c.context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        for (n in numbers) {
            val parts = sms.divideMessage(text)
            if (parts.size > 1) sms.sendMultipartTextMessage(n, null, parts, null, null) else sms.sendTextMessage(n, null, text, null, null)
        }
        return ""
    }

    private fun call(c: Call): String {
        val n = c.args.firstOrNull() ?: throw Fail("usage: nibras-telephony-call <number>")
        val granted = c.context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (!granted) c.requestPermission(Manifest.permission.CALL_PHONE)
        onMain { c.context.startActivity(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return ""
    }

    // ------------------------------------------------------------------ camera, media

    private fun cameraInfo(c: Call): String {
        val cm = c.context.getSystemService(CameraManager::class.java)
        val a = JSONArray()
        for (id in cm.cameraIdList) {
            val ch = cm.getCameraCharacteristics(id)
            val o = JSONObject().put("id", id)
            o.put("facing", when (ch.get(CameraCharacteristics.LENS_FACING)) { CameraCharacteristics.LENS_FACING_FRONT -> "front"; CameraCharacteristics.LENS_FACING_BACK -> "back"; else -> "external" })
            val sizes = JSONArray()
            ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.forEach { sizes.put(JSONObject().put("width", it.width).put("height", it.height)) }
            o.put("jpeg_output_sizes", sizes)
            o.put("focal_lengths", JSONArray(ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.map { it.toDouble() } ?: emptyList<Double>()))
            o.put("auto_exposure_modes", JSONArray(ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.map { when (it) { 0 -> "CONTROL_AE_MODE_OFF"; 1 -> "CONTROL_AE_MODE_ON"; 2 -> "CONTROL_AE_MODE_ON_AUTO_FLASH"; 3 -> "CONTROL_AE_MODE_ON_ALWAYS_FLASH"; 4 -> "CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE"; else -> "MODE_$it" } } ?: emptyList<String>()))
            ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { o.put("physical_size", JSONObject().put("width", it.width.toDouble()).put("height", it.height.toDouble())) }
            o.put("capabilities", JSONArray(ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.map { when (it) { 0 -> "backward_compatible"; 1 -> "manual_sensor"; 2 -> "manual_post_processing"; 3 -> "raw"; 5 -> "read_sensor_settings"; 6 -> "burst_capture"; 8 -> "depth_output"; 11 -> "logical_multi_camera"; 12 -> "monochrome"; else -> "capability_$it" } } ?: emptyList<String>()))
            a.put(o)
        }
        return a.toString(2)
    }

    private fun cameraPhoto(c: Call): String {
        c.perm(Manifest.permission.CAMERA)
        val o = Opts(c.args, setOf("-c"))
        val target = c.host(o.rest.lastOrNull() ?: throw Fail("usage: nibras-camera-photo [-c camera-id] <output-file.jpg>"))
        target.parentFile?.mkdirs()
        val silent = runCatching { CameraShot.take(c.context, o["-c"] ?: "0", target) }
        if (silent.isSuccess) return ""
        // Fallback: the system camera app (some phones block background camera access).
        val r = JSONObject(PromptActivity.ask(c.context, "camera-photo", emptyList(), target))
        if (r.optInt("code") != -1) throw Fail("camera: ${silent.exceptionOrNull()?.message ?: r.optString("text")}")
        return ""
    }

    private fun storageGet(c: Call): String {
        val target = c.host(c.args.firstOrNull() ?: throw Fail("usage: nibras-storage-get <output-file>"))
        val r = JSONObject(PromptActivity.ask(c.context, "storage-get", emptyList(), target))
        if (r.optInt("code") != -1) throw Fail(r.optString("text").ifBlank { "cancelled" })
        return ""
    }

    /** nibras-storage-put <file> [suggested-name]: "Save as" picker (Drive, Downloads, USB…), copies the file there. */
    private fun storagePut(c: Call): String {
        val src = c.host(c.args.firstOrNull() ?: throw Fail("usage: nibras-storage-put <file> [name]"))
        if (!src.isFile) throw Fail("no such file: ${c.args[0]}")
        val r = JSONObject(PromptActivity.ask(c.context, "storage-put", listOf(c.args.getOrNull(1) ?: src.name), src))
        if (r.optInt("code") != -1) throw Fail(r.optString("text").ifBlank { "cancelled" })
        return r.optString("text")
    }

    /** nibras-crypt enc|dec <in> <out>, passphrase on stdin (AES-256-GCM, see BackupCrypt). */
    private fun crypt(c: Call): String {
        val mode = c.args.getOrNull(0); val a = c.args.getOrNull(1); val b = c.args.getOrNull(2)
        if (mode !in setOf("enc", "dec") || a == null || b == null) throw Fail("usage: nibras-crypt enc|dec <in> <out>  (passphrase on stdin)")
        val pass = (c.stdin?.let { String(it) } ?: "").trimEnd('\n', '\r').toCharArray()
        if (pass.isEmpty()) throw Fail("passphrase needed on stdin")
        try {
            if (mode == "enc") BackupCrypt.encrypt(c.host(a), c.host(b), pass) else BackupCrypt.decrypt(c.host(a), c.host(b), pass)
        } catch (e: IllegalArgumentException) { throw Fail(e.message ?: "failed") } finally { pass.fill(' ') }
        return ""
    }

    /** nibras-app-data export|import <file.json>: saved hosts and app settings, for `backup drive`. */
    private fun appData(c: Call): String {
        val mode = c.args.getOrNull(0); val f = c.host(c.args.getOrNull(1) ?: throw Fail("usage: nibras-app-data export|import <file.json>"))
        val prefsNames = listOf("nibras", "editor", "prayer")
        val hosts = File(c.context.filesDir, "hosts.txt")
        when (mode) {
            "export" -> {
                val o = JSONObject().put("format", "nibras-app-data-1").put("hosts", if (hosts.isFile) hosts.readText() else "")
                val prefs = JSONObject()
                for (n in prefsNames) {
                    val p = JSONObject()
                    for ((k, v) in c.context.getSharedPreferences(n, Context.MODE_PRIVATE).all) when (v) {
                        is Boolean -> p.put(k, JSONObject().put("b", v)); is Int -> p.put(k, JSONObject().put("i", v))
                        is Long -> p.put(k, JSONObject().put("l", v)); is Float -> p.put(k, JSONObject().put("f", v.toDouble()))
                        is String -> if (!k.startsWith("times:")) p.put(k, JSONObject().put("s", v))
                    }
                    prefs.put(n, p)
                }
                f.writeText(o.put("prefs", prefs).toString())
            }
            "import" -> {
                val o = JSONObject(f.readText())
                if (o.optString("format") != "nibras-app-data-1") throw Fail("not a Nibras app-data file")
                o.optString("hosts").takeIf { it.isNotBlank() }?.let { hosts.writeText(it) }
                val prefs = o.optJSONObject("prefs") ?: JSONObject()
                for (n in prefsNames) {
                    val p = prefs.optJSONObject(n) ?: continue
                    val e = c.context.getSharedPreferences(n, Context.MODE_PRIVATE).edit()
                    for (k in p.keys()) { val v = p.getJSONObject(k)
                        when { v.has("b") -> e.putBoolean(k, v.getBoolean("b")); v.has("i") -> e.putInt(k, v.getInt("i"))
                            v.has("l") -> e.putLong(k, v.getLong("l")); v.has("f") -> e.putFloat(k, v.getDouble("f").toFloat()); v.has("s") -> e.putString(k, v.getString("s")) } }
                    e.apply()
                }
            }
            else -> throw Fail("usage: nibras-app-data export|import <file.json>")
        }
        return ""
    }

    private fun dialog(c: Call): String {
        if (c.args.firstOrNull() in setOf("-l", "--list")) return "confirm checkbox counter date radio sheet spinner speech text time"
        return PromptActivity.ask(c.context, "dialog", c.args)
    }

    private fun mediaScan(c: Call): String {
        val o = Opts(c.args, emptySet())
        val files = mutableListOf<String>()
        for (p in o.rest) {
            val f = c.host(p)
            if (f.isDirectory && o.has("-r")) f.walkTopDown().filter { it.isFile }.forEach { files += it.absolutePath } else files += f.absolutePath
        }
        val done = CountDownLatch(files.size)
        android.media.MediaScannerConnection.scanFile(c.context, files.toTypedArray(), null) { path, _ -> if (o.has("-v")) runCatching { c.out.write("scanned $path\n".toByteArray()) }; done.countDown() }
        done.await(60, TimeUnit.SECONDS)
        return "Finished scanning ${files.size} file(s)"
    }

    @Volatile private var player: MediaPlayer? = null
    @Volatile private var playing: String? = null

    private fun mediaPlayer(c: Call): String {
        fun pos(ms: Int) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)
        return when (c.args.firstOrNull()) {
            "play" -> {
                val src = c.args.getOrNull(1)
                if (src == null) { val p = player ?: throw Fail("No track to resume"); p.start(); return "Resumed playback" }
                val uri = if (src.contains("://")) Uri.parse(src) else Uri.fromFile(c.host(src).also { if (!it.isFile) throw Fail("No such file: $src") })
                player?.release()
                val mp = MediaPlayer()
                mp.setDataSource(c.context, uri); mp.prepare(); mp.start()
                mp.setOnCompletionListener { playing = null }
                player = mp; playing = src.substringAfterLast('/')
                "Now Playing: $playing"
            }
            "pause" -> { player?.takeIf { it.isPlaying }?.pause() ?: return "No track currently playing"; "Paused playback" }
            "resume" -> { player?.start() ?: return "No track to resume"; "Resumed playback" }
            "stop" -> { player?.release() ?: return "No track to stop"; player = null; playing = null; "Stopped playback" }
            "info" -> player?.let { "Status: ${if (it.isPlaying) "Playing" else "Paused"}\nTrack: $playing\nCurrent Position: ${pos(it.currentPosition)} / ${pos(it.duration)}" } ?: "No track currently!"
            else -> throw Fail("usage: nibras-media-player info | play <file|url> | pause | resume | stop")
        }
    }

    @Volatile private var recorder: MediaRecorder? = null
    @Volatile private var recordFile: String? = null

    private fun microphone(c: Call): String {
        val o = Opts(c.args, setOf("-f", "-l", "-e", "-b", "-r", "-c"))
        if (o.has("-i")) return JSONObject().put("isRecording", recorder != null).apply { recordFile?.let { put("outputFile", it) } }.toString(2)
        if (o.has("-q")) {
            val r = recorder ?: return "No recording to stop"
            runCatching { r.stop() }; r.release(); recorder = null
            return "Recording finished: $recordFile"
        }
        if (!o.has("-d") && o["-f"] == null) throw Fail("usage: nibras-microphone-record -d | -f <file> [-l seconds] [-e aac|amr_wb|amr_nb|opus] [-b kbps] [-r Hz] [-c channels] | -i | -q")
        c.perm(Manifest.permission.RECORD_AUDIO)
        if (recorder != null) throw Fail("Recording already in progress: $recordFile")
        val enc = o["-e"] ?: "aac"
        val ext = when (enc) { "amr_wb", "amr_nb" -> "amr"; "opus" -> "ogg"; else -> "m4a" }
        val guest = o["-f"] ?: ((if (File("/sdcard").let { com.nibras.terminal.core.Rootfs.hasSharedStorage(c.context) }) "/sdcard/" else "/root/") +
            "NibrasRecording_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".$ext")
        val file = c.host(guest).also { it.parentFile?.mkdirs() }
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(c.context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        when (enc) {
            "amr_wb" -> { r.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB); r.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB) }
            "amr_nb" -> { r.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB); r.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB) }
            "opus" -> if (Build.VERSION.SDK_INT >= 29) { r.setOutputFormat(MediaRecorder.OutputFormat.OGG); r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS) } else throw Fail("opus needs Android 10+")
            else -> { r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC) }
        }
        o["-b"]?.toIntOrNull()?.let { r.setAudioEncodingBitRate(it * 1000) }
        o["-r"]?.toIntOrNull()?.let { r.setAudioSamplingRate(it) }
        o["-c"]?.toIntOrNull()?.let { r.setAudioChannels(it) }
        val limit = o["-l"]?.toIntOrNull() ?: (15 * 60)
        if (limit > 0) r.setMaxDuration(limit * 1000)
        r.setOutputFile(file.absolutePath)
        r.setOnInfoListener { mr, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) { runCatching { mr.stop() }; mr.release(); if (recorder === mr) recorder = null }
        }
        r.prepare(); r.start()
        recorder = r; recordFile = guest
        return "Recording started: $guest${if (limit > 0) " (max ${limit}s; stop with nibras-microphone-record -q)" else ""}"
    }

    private fun tts(c: Context, engine: String?): TextToSpeech {
        val gate = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val t = onMain { if (engine != null) TextToSpeech(c.applicationContext, { status = it; gate.countDown() }, engine) else TextToSpeech(c.applicationContext) { status = it; gate.countDown() } }
        gate.await(15, TimeUnit.SECONDS)
        if (status != TextToSpeech.SUCCESS) { t.shutdown(); throw Fail("text-to-speech engine did not start") }
        return t
    }

    private fun ttsEngines(c: Call): String {
        val t = tts(c.context, null)
        val def = t.defaultEngine
        val a = JSONArray()
        t.engines.forEach { a.put(JSONObject().put("name", it.name).put("label", it.label).put("default", it.name == def)) }
        t.shutdown()
        return a.toString(2)
    }

    private fun ttsSpeak(c: Call): String {
        val o = Opts(c.args, setOf("-e", "-l", "-n", "-v", "-p", "-r", "-s"))
        val text = if (o.rest.isNotEmpty()) o.rest.joinToString(" ") else c.text()
        if (text.isBlank()) return ""
        val t = tts(c.context, o["-e"])
        try {
            o["-l"]?.let { t.language = Locale(it, o["-n"] ?: "", o["-v"] ?: "") } ?: run { t.language = Locale.getDefault() }
            o["-p"]?.toFloatOrNull()?.let { t.setPitch(it) }
            o["-r"]?.toFloatOrNull()?.let { t.setSpeechRate(it) }
            val params = Bundle()
            o["-s"]?.let { s ->
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, when (s.uppercase()) { "ALARM" -> AudioManager.STREAM_ALARM; "NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION
                    "RING" -> AudioManager.STREAM_RING; "SYSTEM" -> AudioManager.STREAM_SYSTEM; "VOICE_CALL" -> AudioManager.STREAM_VOICE_CALL; else -> AudioManager.STREAM_MUSIC })
            }
            // Speak line by line (long input) and block until done, like Termux.
            val chunks = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }.flatMap { it.chunked(TextToSpeech.getMaxSpeechInputLength() - 1) }
            val done = CountDownLatch(chunks.size)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = done.countDown()
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = done.countDown()
            })
            chunks.forEachIndexed { i, s -> t.speak(s, TextToSpeech.QUEUE_ADD, params, "nibras-$i") }
            done.await(10, TimeUnit.MINUTES)
        } finally { t.shutdown() }
        return ""
    }

    private fun wallpaper(c: Call): String {
        val o = Opts(c.args, setOf("-f", "-u"))
        val bmp = when {
            o["-u"] != null -> java.net.URL(o["-u"]).openStream().use { BitmapFactory.decodeStream(it) }
            else -> BitmapFactory.decodeFile(c.host(o["-f"] ?: o.rest.firstOrNull() ?: throw Fail("usage: nibras-wallpaper -f <file> | -u <url> [-l]")).absolutePath)
        } ?: throw Fail("not an image")
        val wm = android.app.WallpaperManager.getInstance(c.context)
        wm.setBitmap(bmp, null, true, if (o.has("-l")) android.app.WallpaperManager.FLAG_LOCK else android.app.WallpaperManager.FLAG_SYSTEM)
        return "Wallpaper set successfully!"
    }

    // ------------------------------------------------------------------ network, location, sensors

    @Suppress("DEPRECATION")
    private fun wifiInfo(c: Call): String {
        val wm = c.context.applicationContext.getSystemService(WifiManager::class.java)
        val i = wm.connectionInfo
        val ip = i.ipAddress
        val o = JSONObject()
        o.put("bssid", i.bssid ?: JSONObject.NULL).put("frequency_mhz", i.frequency)
            .put("ip", "%d.%d.%d.%d".format(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff))
            .put("link_speed_mbps", i.linkSpeed).put("mac_address", i.macAddress ?: "").put("network_id", i.networkId)
            .put("rssi", i.rssi).put("ssid", i.ssid?.removeSurrounding("\"") ?: "").put("ssid_hidden", i.hiddenSSID)
            .put("supplicant_state", i.supplicantState?.name ?: "")
        if (c.context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) c.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        return o.toString(2)
    }

    @Suppress("DEPRECATION")
    private fun wifiScan(c: Call): String {
        val wm = c.context.applicationContext.getSystemService(WifiManager::class.java)
        val a = JSONArray()
        wm.scanResults.sortedByDescending { it.level }.forEach { r ->
            val o = JSONObject().put("bssid", r.BSSID).put("frequency_mhz", r.frequency).put("rssi", r.level).put("ssid", r.SSID)
                .put("timestamp", r.timestamp).put("channel_bandwidth_mhz", when (r.channelWidth) { 0 -> "20"; 1 -> "40"; 2 -> "80"; 3 -> "160"; 4 -> "80+80"; 5 -> "320"; else -> "?" })
                .put("capabilities", r.capabilities)
            if (r.centerFreq0 > 0) o.put("center_frequency_mhz", r.centerFreq0)
            a.put(o)
        }
        return a.toString(2)
    }

    @Suppress("DEPRECATION")
    private fun wifiEnable(c: Call): String {
        val on = when (c.args.firstOrNull()) { "true" -> true; "false" -> false; else -> throw Fail("usage: nibras-wifi-enable true|false") }
        if (Build.VERSION.SDK_INT < 29) { c.context.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled = on; return "" }
        onMain { c.context.startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return "Android 10+ lets only the user switch Wi-Fi: the Wi-Fi panel is open (or use nibras-adb / root: svc wifi ${if (on) "enable" else "disable"})"
    }

    private fun locJson(l: Location) = JSONObject().put("latitude", l.latitude).put("longitude", l.longitude).put("altitude", l.altitude)
        .put("accuracy", l.accuracy.toDouble()).put("vertical_accuracy", if (Build.VERSION.SDK_INT >= 26 && l.hasVerticalAccuracy()) l.verticalAccuracyMeters.toDouble() else 0.0)
        .put("bearing", l.bearing.toDouble()).put("speed", l.speed.toDouble())
        .put("elapsedMs", (android.os.SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000).put("provider", l.provider ?: "")

    @SuppressLint("MissingPermission")
    private fun location(c: Call): String? {
        c.perm(Manifest.permission.ACCESS_FINE_LOCATION)
        val o = Opts(c.args, setOf("-p", "-r"))
        val provider = when (o["-p"] ?: "gps") { "network" -> LocationManager.NETWORK_PROVIDER; "passive" -> LocationManager.PASSIVE_PROVIDER; else -> LocationManager.GPS_PROVIDER }
        val lm = c.context.getSystemService(LocationManager::class.java)
        if (!lm.isProviderEnabled(provider) && provider != LocationManager.PASSIVE_PROVIDER) throw Fail("location provider \"$provider\" is off — turn on Location in Android")
        val request = o["-r"] ?: "once"
        if (request == "last") return lm.getLastKnownLocation(provider)?.let { locJson(it).toString(2) } ?: throw Fail("no last known location")
        val ht = HandlerThread("nibras-loc").apply { start() }
        val updates = request == "updates"
        if (updates) c.out.write("OK\n".toByteArray())
        val got = CountDownLatch(1)
        var last: Location? = null
        var broken = false
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                last = l
                if (updates) runCatching { c.out.write((locJson(l).toString(2) + "\n").toByteArray()); c.out.flush() }.onFailure { broken = true; got.countDown() } else got.countDown()
            }
            @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
            override fun onProviderEnabled(p: String) = Unit
            override fun onProviderDisabled(p: String) = Unit
        }
        try {
            lm.requestLocationUpdates(provider, if (updates) 1000L else 0L, 0f, listener, ht.looper)
            if (updates) { while (!broken) { got.await(5, TimeUnit.SECONDS); runCatching { c.out.write(ByteArray(0)); c.out.flush() }.onFailure { broken = true } }; return null }
            got.await(60, TimeUnit.SECONDS)
        } finally { lm.removeUpdates(listener); ht.quitSafely() }
        return (last ?: lm.getLastKnownLocation(provider))?.let { locJson(it).toString(2) } ?: throw Fail("no location fix within 60 s (go outdoors or use -p network)")
    }

    private fun sensor(c: Call) {
        val sm = c.context.getSystemService(SensorManager::class.java)
        val o = Opts(c.args, setOf("-s", "-d", "-n"))
        val all = sm.getSensorList(Sensor.TYPE_ALL)
        if (o.has("-l") || c.args.isEmpty()) {
            c.out.write("OK\n".toByteArray()); c.emit(JSONObject().put("sensors", JSONArray(all.map { it.name })).toString(2)); return
        }
        if (o.has("-c")) { c.out.write("OK\n".toByteArray()); c.out.flush(); return }
        val wanted = if (o.has("-a")) all else o["-s"]!!.split(',').map { it.trim().lowercase() }.flatMap { w -> all.filter { it.name.lowercase().contains(w) } }.distinct()
        if (wanted.isEmpty()) throw Fail("no matching sensor; list them with nibras-sensor -l")
        val delay = o["-d"]?.toLongOrNull() ?: 1000L
        val limit = o["-n"]?.toIntOrNull() ?: Int.MAX_VALUE
        val latest = java.util.concurrent.ConcurrentHashMap<String, FloatArray>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { latest[e.sensor.name] = e.values.clone() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val ht = HandlerThread("nibras-sensor").apply { start() }
        wanted.forEach { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI, Handler(ht.looper)) }
        c.out.write("OK\n".toByteArray())
        try {
            var n = 0
            Thread.sleep(minOf(delay, 300L))
            while (n < limit) {
                val obj = JSONObject()
                wanted.forEach { s -> latest[s.name]?.let { obj.put(s.name, JSONObject().put("values", JSONArray(it.map { v -> v.toDouble() }))) } }
                c.out.write((obj.toString(2) + "\n").toByteArray()); c.out.flush()
                n++
                if (n < limit) Thread.sleep(delay)
            }
        } catch (_: java.io.IOException) {
        } finally { sm.unregisterListener(listener); ht.quitSafely() }
    }

    private fun irFrequencies(c: Call): String {
        val ir = c.context.getSystemService(android.hardware.ConsumerIrManager::class.java)?.takeIf { it.hasIrEmitter() } ?: throw Fail("No infrared emitter on this phone")
        val a = JSONArray(); ir.carrierFrequencies?.forEach { a.put(JSONObject().put("min", it.minFrequency).put("max", it.maxFrequency)) }
        return a.toString(2)
    }

    private fun irTransmit(c: Call): String {
        val o = Opts(c.args, setOf("-f"))
        val ir = c.context.getSystemService(android.hardware.ConsumerIrManager::class.java)?.takeIf { it.hasIrEmitter() } ?: throw Fail("No infrared emitter on this phone")
        val freq = o["-f"]?.toIntOrNull() ?: throw Fail("usage: nibras-infrared-transmit -f <carrier Hz> <pattern,on,off,…µs>")
        val pattern = o.rest.joinToString(",").split(',').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
        if (pattern.isEmpty()) throw Fail("pattern missing")
        ir.transmit(freq, pattern)
        return ""
    }

    // ------------------------------------------------------------------ share / open / download

    private fun uriFor(c: Call, f: File): Uri = FileProvider.getUriForFile(c.context, "${c.context.packageName}.files", f)
    private fun mime(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"

    private fun share(c: Call): String {
        val o = Opts(c.args, setOf("-a", "-c", "-t"))
        val action = o["-a"] ?: "view"
        val title = o["-t"] ?: "Share"
        val i: Intent
        if (o.rest.isNotEmpty()) {
            val f = c.host(o.rest[0]).also { if (!it.isFile) throw Fail("No such file: ${o.rest[0]}") }
            val uri = uriFor(c, f); val type = o["-c"] ?: mime(f)
            i = when (action) {
                "send" -> Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri)
                "edit" -> Intent(Intent.ACTION_EDIT).setDataAndType(uri, type)
                else -> Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
            }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            i.clipData = ClipData.newRawUri(f.name, uri)
        } else {
            i = Intent(Intent.ACTION_SEND).setType(o["-c"] ?: "text/plain").putExtra(Intent.EXTRA_TEXT, c.text())
        }
        val launch = if (o.has("-d")) i else Intent.createChooser(i, title)
        onMain { c.context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return ""
    }

    private fun open(c: Call): String {
        val o = Opts(c.args, setOf("--content-type"))
        val target = o.rest.firstOrNull() ?: throw Fail("usage: nibras-open [--send|--view] [--chooser] [--content-type type] <file|url>")
        val i = if (target.contains("://") && !target.startsWith("file://")) {
            Intent(Intent.ACTION_VIEW, Uri.parse(target))
        } else {
            val f = c.host(target.removePrefix("file://"))
            if (!f.exists()) throw Fail("No such file: $target")
            if (f.isDirectory) return run { onMain { FilesActivity.open(c.context, c.envId, HostPaths.toGuest(c.context, c.envId, f)) }; "" }
            val uri = uriFor(c, f); val type = o["--content-type"] ?: mime(f)
            (if (o.has("--send")) Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri) else Intent(Intent.ACTION_VIEW).setDataAndType(uri, type))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri(f.name, uri) }
        }
        val launch = if (o.has("--chooser") || o.has("--send")) Intent.createChooser(i, null) else i
        onMain {
            runCatching { c.context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { c.context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        return ""
    }

    private fun download(c: Call): String {
        val o = Opts(c.args, setOf("-d", "-t", "-p"))
        val url = o.rest.firstOrNull() ?: throw Fail("usage: nibras-download [-t title] [-d description] [-p path] <url>")
        val uri = Uri.parse(url)
        if (uri.scheme !in setOf("http", "https")) throw Fail("only http(s) URLs")
        val name = uri.lastPathSegment?.ifBlank { null } ?: "download"
        val r = DownloadManager.Request(uri).setTitle(o["-t"] ?: name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        o["-d"]?.let { r.setDescription(it) }
        val p = o["-p"]
        val ext = Environment.getExternalStorageDirectory().absolutePath
        val dest = p?.let { c.host(it) }
        if (dest != null && dest.absolutePath.startsWith(ext)) r.setDestinationUri(Uri.fromFile(if (dest.isDirectory) File(dest, name) else dest))
        else r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        val id = c.context.getSystemService(DownloadManager::class.java).enqueue(r)
        return if (dest != null && !dest.absolutePath.startsWith(ext)) "Downloading to Download/$name (Android's download manager can only write to shared storage); id $id" else ""
    }

    private fun info(c: Call): String {
        val pi = c.context.packageManager.getPackageInfo(c.context.packageName, 0)
        return buildString {
            appendLine("Nibras Terminal ${pi.versionName} (${pi.longVersionCode})")
            appendLine("Package: ${c.context.packageName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString(",")} · app: ${com.nibras.terminal.core.DeviceAbi.current}")
            appendLine("Kernel: ${System.getProperty("os.version")}")
            appendLine("Shared storage: ${if (com.nibras.terminal.core.Rootfs.hasSharedStorage(c.context)) "granted" else "not granted (nibras-setup-storage)"}")
            appendLine("Environments: ${com.nibras.terminal.core.Rootfs.all.filter { com.nibras.terminal.core.Rootfs.isReady(c.context, it.id) }.joinToString { it.name }}")
            appendLine("External apps (RUN_COMMAND): ${if (Automation.allowed(c.context)) "allowed" else "off"}")
            append("Notification access: ${if (NotesListener.instance != null) "on" else "off"}")
        }
    }

    // ------------------------------------------------------------------ notifications

    const val NOTE_ID = 7300
    private fun notificationManager(c: Call) = c.context.getSystemService(NotificationManager::class.java)

    private fun channel(c: Call, priority: String?, sound: Boolean): String {
        val nm = notificationManager(c)
        val (id, imp) = when {
            priority == "high" || priority == "max" -> "nibras-api-high" to NotificationManager.IMPORTANCE_HIGH
            sound -> "nibras-api-sound" to NotificationManager.IMPORTANCE_DEFAULT
            priority == "min" -> "nibras-api-min" to NotificationManager.IMPORTANCE_MIN
            else -> "nibras-api" to NotificationManager.IMPORTANCE_LOW
        }
        if (nm.getNotificationChannel(id) == null) nm.createNotificationChannel(NotificationChannel(id, "Script notifications ($id)", imp))
        return id
    }

    private fun actionIntent(c: Call, command: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(c.context, code, Intent(c.context, NotificationActionReceiver::class.java)
            .putExtra("command", command).putExtra("env", c.envId).putExtra("cwd", c.pwd),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(c: Call): String {
        if (Build.VERSION.SDK_INT >= 33) c.perm(Manifest.permission.POST_NOTIFICATIONS)
        val o = Opts(c.args, setOf("-c", "--content", "-t", "--title", "-i", "--id", "--group", "--image-path", "--led-color", "--led-on", "--led-off",
            "--priority", "--vibrate", "--action", "--on-delete", "--button1", "--button1-action", "--button2", "--button2-action", "--button3", "--button3-action",
            "--icon", "--type", "--channel", "--media-next", "--media-pause", "--media-play", "--media-previous", "--when"))
        val content = o["-c", "--content"] ?: c.text().trimEnd('\n').takeIf { it.isNotEmpty() } ?: ""
        val title = o["-t", "--title"] ?: ""
        val idText = o["-i", "--id"] ?: "nibras-${System.currentTimeMillis()}"
        val ch = o["--channel"]?.takeIf { notificationManager(c).getNotificationChannel(it) != null } ?: channel(c, o["--priority"], o.has("--sound"))
        val b = Notification.Builder(c.context, ch).setSmallIcon(R.drawable.ic_stat_terminal).setContentTitle(title).setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content)).setShowWhen(true)
        o["--image-path"]?.let { p -> BitmapFactory.decodeFile(c.host(p).absolutePath)?.let { b.setStyle(Notification.BigPictureStyle().bigPicture(it)).setLargeIcon(it) } }
        if (o.has("--ongoing")) b.setOngoing(true)
        if (o.has("--alert-once")) b.setOnlyAlertOnce(true)
        o["--group"]?.let { b.setGroup(it) }
        o["--led-color"]?.let { runCatching { b.setColor(Color.parseColor("#" + it.removePrefix("#"))) } }
        val code = idText.hashCode()
        o["--action"]?.let { b.setContentIntent(actionIntent(c, it, code)) }
            ?: b.setContentIntent(PendingIntent.getActivity(c.context, 0, Intent(c.context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        o["--on-delete"]?.let { b.setDeleteIntent(actionIntent(c, it, code + 1)) }
        for (n in 1..3) o["--button$n"]?.let { label ->
            b.addAction(Notification.Action.Builder(null, label, actionIntent(c, o["--button$n-action"] ?: "true", code + 1 + n)).build())
        }
        if (o["--type"] == "media") {
            listOf("--media-previous" to "⏮", "--media-pause" to "⏸", "--media-play" to "▶", "--media-next" to "⏭").forEachIndexed { k, (opt, label) ->
                o[opt]?.let { b.addAction(Notification.Action.Builder(null, label, actionIntent(c, it, code + 10 + k)).build()) }
            }
            b.setStyle(Notification.MediaStyle())
        }
        notificationManager(c).notify(idText, NOTE_ID, b.build())
        o["--vibrate"]?.let { v -> val p = v.split(',').mapNotNull { it.trim().toLongOrNull() }; if (p.isNotEmpty()) c.context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform((listOf(0L) + p).toLongArray(), -1)) }
        return ""
    }

    private fun notificationChannel(c: Call): String {
        val nm = notificationManager(c)
        if (c.args.firstOrNull() == "-d") { nm.deleteNotificationChannel(c.args.getOrNull(1) ?: throw Fail("usage: nibras-notification-channel -d <channel-id>")); return "" }
        val id = c.args.getOrNull(0) ?: throw Fail("usage: nibras-notification-channel <channel-id> <channel-name> | -d <channel-id>")
        nm.createNotificationChannel(NotificationChannel(id, c.args.getOrNull(1) ?: id, NotificationManager.IMPORTANCE_DEFAULT))
        return ""
    }

    private fun notificationList(c: Call): String {
        val a = JSONArray()
        val listener = NotesListener.instance
        val list = runCatching { listener?.activeNotifications?.toList() }.getOrNull() ?: notificationManager(c).activeNotifications.toList()
        for (s in list) {
            val e = s.notification.extras
            a.put(JSONObject().put("id", s.id).put("tag", s.tag ?: "").put("key", s.key).put("group", s.notification.group ?: "")
                .put("packageName", s.packageName).put("title", e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "")
                .put("content", (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: "")
                .put("when", stamp.format(Date(s.postTime))))
        }
        if (listener == null && "--own" !in c.args) {
            onMain { runCatching { c.context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        return a.toString(2)
    }

    // ------------------------------------------------------------------ fingerprint

    private fun fingerprint(c: Call): String {
        val o = Opts(c.args, setOf("-t", "-d", "-s", "-c"))
        val r = JSONObject(PromptActivity.ask(c.context, "fingerprint", listOf(o["-t"] ?: "Nibras Terminal", o["-d"] ?: "", o["-s"] ?: "", o["-c"] ?: "Cancel")))
        return JSONObject().put("errors", r.optJSONArray("errors") ?: JSONArray()).put("failed_attempts", r.optInt("failed_attempts"))
            .put("auth_result", r.optString("auth_result", "AUTH_RESULT_UNKNOWN")).toString(2)
    }

    // ------------------------------------------------------------------ Storage Access Framework

    private fun safRoots(c: Context) = c.contentResolver.persistedUriPermissions.map { it.uri }

    private fun docJson(c: Context, uri: Uri): JSONObject? {
        c.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cur ->
            if (!cur.moveToFirst()) return null
            val type = cur.getString(1) ?: ""
            val flags = cur.getInt(4)
            val o = JSONObject().put("name", cur.getString(0) ?: "").put("type", type).put("uri", uri.toString())
                .put("editable", flags and (DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0)
            if (type != DocumentsContract.Document.MIME_TYPE_DIR) o.put("length", cur.getLong(2))
            o.put("last_modified", cur.getLong(3))
            return o
        }
        return null
    }

    /** A tree URI from saf-managedir, or a document URI under it. */
    private fun docUri(s: String): Uri {
        val u = Uri.parse(s)
        return if (DocumentsContract.isTreeUri(u) && runCatching { DocumentsContract.getDocumentId(u) }.isFailure)
            DocumentsContract.buildDocumentUriUsingTree(u, DocumentsContract.getTreeDocumentId(u)) else u
    }

    private fun saf(c: Call, name: String): String? {
        val cr = c.context.contentResolver
        fun arg(i: Int, usage: String) = c.args.getOrNull(i) ?: throw Fail("usage: nibras-$name $usage")
        return when (name) {
            "saf-managedir" -> {
                val r = JSONObject(PromptActivity.ask(c.context, "saf-tree", emptyList()))
                if (r.optInt("code") != -1) throw Fail("no folder chosen")
                r.optString("text")
            }
            "saf-dirs" -> JSONArray(safRoots(c.context).mapNotNull { docJson(c.context, docUri(it.toString())) }).toString(2)
            "saf-ls" -> {
                val d = docUri(arg(0, "<folder-uri>"))
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(d, DocumentsContract.getDocumentId(d))
                val a = JSONArray()
                cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cur ->
                    while (cur.moveToNext()) docJson(c.context, DocumentsContract.buildDocumentUriUsingTree(d, cur.getString(0)))?.let { a.put(it) }
                }
                a.toString(2)
            }
            "saf-stat" -> docJson(c.context, docUri(arg(0, "<uri>")))?.toString(2) ?: throw Fail("not found")
            "saf-read" -> {
                val bytes = cr.openInputStream(docUri(arg(0, "<uri>")))?.use { it.readBytes() } ?: throw Fail("cannot read")
                c.out.write("B64\n".toByteArray()); c.out.write(android.util.Base64.encode(bytes, android.util.Base64.NO_WRAP)); c.out.write('\n'.code); null
            }
            "saf-write" -> { cr.openOutputStream(docUri(arg(0, "<uri>  < data")), "wt")?.use { it.write(c.stdin ?: ByteArray(0)) } ?: throw Fail("cannot write"); "" }
            "saf-mkdir" -> DocumentsContract.createDocument(cr, docUri(arg(0, "<parent-uri> <name>")), DocumentsContract.Document.MIME_TYPE_DIR, arg(1, "<parent-uri> <name>"))?.toString() ?: throw Fail("failed")
            "saf-create" -> {
                val o = Opts(c.args, setOf("-t"))
                val parent = o.rest.getOrNull(0); val n = o.rest.getOrNull(1)
                if (parent == null || n == null) throw Fail("usage: nibras-saf-create [-t mime] <parent-uri> <name>")
                DocumentsContract.createDocument(cr, docUri(parent), o["-t"] ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substringAfterLast('.', "")) ?: "application/octet-stream", n)?.toString() ?: throw Fail("failed")
            }
            "saf-rm" -> { if (!DocumentsContract.deleteDocument(cr, docUri(arg(0, "<uri>")))) throw Fail("failed"); "" }
            else -> throw Fail(name)
        }
    }
}

/** Runs the shell command attached to a nibras-notification --action / --button / --on-delete. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("command") ?: return
        val pending = goAsync()
        thread(name = "nibras-note-action") {
            try { Headless.run(context, intent.getStringExtra("env"), command, intent.getStringExtra("cwd"), timeoutSec = 120) } finally { pending.finish() }
        }
    }
}

/** Optional notification access (like Termux:API) so nibras-notification-list can show every app's notifications. */
class NotesListener : NotificationListenerService() {
    companion object { @Volatile var instance: NotesListener? = null }
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { instance = null }
    override fun onDestroy() { instance = null; super.onDestroy() }
}

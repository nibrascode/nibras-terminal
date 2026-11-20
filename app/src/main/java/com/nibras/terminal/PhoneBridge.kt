package com.nibras.terminal

import android.Manifest
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Toast
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object PhoneBridge {
    private var wake: PowerManager.WakeLock? = null
    private var player: MediaPlayer? = null
    private var speech: TextToSpeech? = null
    private val notes = linkedMapOf<String, Int>()

    fun permission(line: String): String? {
        val cmd = line.substringBefore(" ").removePrefix("termux-").let { if (line.startsWith("termux-")) "nibras-$it" else line.substringBefore(" ") }
        val name = when (cmd) {
            "nibras-torch", "nibras-camera-info" -> Manifest.permission.CAMERA
            "nibras-microphone-record" -> Manifest.permission.RECORD_AUDIO
            "nibras-location" -> Manifest.permission.ACCESS_FINE_LOCATION
            "nibras-contact-list" -> Manifest.permission.READ_CONTACTS
            "nibras-call-log" -> Manifest.permission.READ_CALL_LOG
            "nibras-sms-list" -> Manifest.permission.READ_SMS
            "nibras-sms-send" -> Manifest.permission.SEND_SMS
            "nibras-notification", "nibras-notification-list", "nibras-notification-remove" ->
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
            else -> null
        }
        return name
    }

    fun run(context: Context, raw: String): String? {
        val line = if (raw.startsWith("termux-")) "nibras-${raw.removePrefix("termux-")}" else raw
        val cmd = line.substringBefore(" ")
        val rest = line.substringAfter(" ", "").trim()
        if (!cmd.startsWith("nibras-")) return null
        return try {
            when (cmd) {
                "nibras-battery-status" -> battery(context)
                "nibras-clipboard-get" -> clipGet(context)
                "nibras-clipboard-set" -> clipSet(context, rest)
                "nibras-toast" -> toast(context, rest)
                "nibras-vibrate" -> vibrate(context, rest)
                "nibras-tts-speak" -> speak(context, rest)
                "nibras-notification" -> notify(context, rest)
                "nibras-notification-list" -> notes.keys.joinToString("\n").ifBlank { "bildiriş yoxdur" }
                "nibras-notification-remove" -> removeNote(context, rest)
                "nibras-wake-lock" -> lock(context, true)
                "nibras-wake-unlock" -> lock(context, false)
                "nibras-share" -> share(context, rest)
                "nibras-wifi-connectioninfo" -> wifi(context)
                "nibras-torch" -> torch(context, rest != "off")
                "nibras-brightness" -> brightness(context, rest)
                "nibras-volume" -> volume(context, rest)
                "nibras-camera-info" -> cameras(context)
                "nibras-microphone-record" -> mic(context, rest)
                "nibras-location" -> location(context)
                "nibras-sensor" -> sensor(context)
                "nibras-fingerprint" -> fingerprint(context)
                "nibras-download" -> download(context, rest)
                "nibras-setup-storage" -> storage(context)
                "nibras-open-url" -> open(context, rest)
                "nibras-info" -> info(context)
                "nibras-media-player" -> play(context, rest)
                "nibras-contact-list" -> contacts(context)
                "nibras-call-log" -> calls(context)
                "nibras-sms-list" -> smsList(context)
                "nibras-sms-send" -> smsSend(context, rest)
                else -> null
            }
        } catch (error: Exception) {
            error.message ?: "olmadı"
        }
    }

    private fun battery(context: Context): String {
        val sticky = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temp = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (scale > 0 && level >= 0) level * 100 / scale else -1
        return "$percent% · ${if (charging) "doldurulur" else "batareya"} · $temp°C"
    }

    private fun clipGet(context: Context): String {
        val clip = context.getSystemService(ClipboardManager::class.java)
        return clip?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().ifBlank { "lövhə boşdur" }
    }

    private fun clipSet(context: Context, text: String): String {
        if (text.isBlank()) return "mətn yoxdur"
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("nibras", text))
        return "lövhəyə yazıldı"
    }

    private fun toast(context: Context, text: String): String {
        if (text.isBlank()) return "mətn yoxdur"
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        return text
    }

    private fun vibrate(context: Context, raw: String): String {
        val ms = raw.toLongOrNull()?.coerceIn(1, 2000) ?: 200
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return "titrəmə yoxdur"
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        return "$ms ms"
    }

    private fun speak(context: Context, text: String): String {
        if (text.isBlank()) return "mətn yoxdur"
        val engine = speech ?: TextToSpeech(context.applicationContext) { }.also { speech = it }
        engine.language = Locale.getDefault()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nibras")
        return text
    }

    private fun notify(context: Context, rest: String): String {
        val title = rest.substringBefore(" ").ifBlank { return "başlıq yoxdur" }
        val body = rest.substringAfter(" ", "")
        val manager = context.getSystemService(NotificationManager::class.java) ?: return "bildiriş yoxdur"
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel("nibras") == null) {
            manager.createNotificationChannel(NotificationChannel("nibras", "Nibras", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val id = notes.size + 1
        notes[title] = id
        val note = Notification.Builder(context, "nibras")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .build()
        manager.notify(id, note)
        return title
    }

    private fun removeNote(context: Context, title: String): String {
        val id = notes.remove(title) ?: return "bildiriş yoxdur"
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
        return title
    }

    private fun lock(context: Context, on: Boolean): String {
        if (!on) {
            wake?.release()
            wake = null
            return "ekran buraxıldı"
        }
        val power = context.getSystemService(PowerManager::class.java) ?: return "oyaqlıq yoxdur"
        wake?.release()
        wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nibras:wake").apply { acquire(10 * 60 * 1000L) }
        return "telefon oyaqdır"
    }

    private fun share(context: Context, text: String): String {
        if (text.isBlank()) return "mətn yoxdur"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, "Nibras").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "paylaşıldı"
    }

    private fun wifi(context: Context): String {
        val link = context.getSystemService(ConnectivityManager::class.java)?.getNetworkCapabilities(
            context.getSystemService(ConnectivityManager::class.java)?.activeNetwork,
        )
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val rssi = wifi?.connectionInfo?.rssi
        val kind = when {
            link?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            link?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "mobil"
            else -> "şəbəkə yoxdur"
        }
        return "$kind · ${rssi ?: "?"} dBm"
    }

    private fun torch(context: Context, on: Boolean): String {
        val cameras = context.getSystemService(CameraManager::class.java) ?: return "fənər yoxdur"
        val id = cameras.cameraIdList.firstOrNull() ?: return "fənər yoxdur"
        cameras.setTorchMode(id, on)
        return if (on) "fənər yanır" else "fənər söndü"
    }

    private fun brightness(context: Context, raw: String): String {
        val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        if (raw.isBlank()) return current.toString()
        val level = raw.toIntOrNull() ?: return "0-255"
        if (level !in 0..255) return "0-255"
        if (!Settings.System.canWrite(context)) return "yazmaq üçün ayar icazəsi yoxdur · indi $current"
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
        return level.toString()
    }

    private fun volume(context: Context, raw: String): String {
        val audio = context.getSystemService(AudioManager::class.java) ?: return "səs yoxdur"
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (raw.isBlank()) return "${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}"
        val level = raw.toIntOrNull() ?: return "0-100"
        if (level !in 0..100) return "0-100"
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level * max / 100, 0)
        return level.toString()
    }

    private fun cameras(context: Context): String {
        val cameras = context.getSystemService(CameraManager::class.java) ?: return "kamera yoxdur"
        if (cameras.cameraIdList.isEmpty()) return "kamera yoxdur"
        return cameras.cameraIdList.joinToString("\n") { id -> "kamera $id" }
    }

    private fun mic(context: Context, raw: String): String {
        val seconds = raw.toIntOrNull()?.coerceIn(1, 15) ?: 3
        val file = File(context.filesDir, "nibras-mic.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        recorder.start()
        Thread.sleep(seconds * 1000L)
        recorder.stop()
        recorder.release()
        return "$seconds san · ${file.absolutePath}"
    }

    private fun location(context: Context): String {
        val manager = context.getSystemService(LocationManager::class.java) ?: return "yer yoxdur"
        val spot = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: return "yer yoxdur"
        return "${spot.latitude}, ${spot.longitude}"
    }

    private fun sensor(context: Context): String {
        val manager = context.getSystemService(SensorManager::class.java) ?: return "sensor yoxdur"
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return "sensor yoxdur"
        val gate = CountDownLatch(1)
        var text = "sensor yoxdur"
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                text = "x ${v.getOrNull(0)} · y ${v.getOrNull(1)} · z ${v.getOrNull(2)}"
                gate.countDown()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        gate.await(2, TimeUnit.SECONDS)
        manager.unregisterListener(listener)
        return text
    }

    private fun fingerprint(context: Context): String {
        val has = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT)
        return if (has) "barmaq izi var" else "barmaq izi yoxdur"
    }

    private fun download(context: Context, raw: String): String {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return "ünvan səhvdir"
        if (uri.scheme != "https") return "yalnız https"
        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank() || host == "localhost" || host.endsWith(".local") || host == "169.254.169.254") return "bu ünvan bağlıdır"
        val name = uri.path?.substringAfterLast("/")?.ifBlank { null } ?: "nibras-download"
        val request = DownloadManager.Request(Uri.parse(raw))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        context.getSystemService(DownloadManager::class.java)?.enqueue(request) ?: return "endirilmədi"
        return name
    }

    private fun storage(context: Context): String {
        val root = File(context.getExternalFilesDir(null), "storage")
        listOf("shared", "downloads", "dcim", "pictures", "music", "movies").forEach { File(root, it).mkdirs() }
        return root.absolutePath
    }

    private fun open(context: Context, raw: String): String {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return "ünvan səhvdir"
        if (uri.scheme != "https") return "yalnız https"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return raw
    }

    private fun info(context: Context): String = listOf(
        "Nibras",
        "Tətbiq: telefon",
        "Model: ${Build.MANUFACTURER} ${Build.MODEL}",
        "Android: ${Build.VERSION.RELEASE}",
        "ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"}",
        "Ev: ${context.filesDir.absolutePath}",
    ).joinToString("\n")

    private fun play(context: Context, raw: String): String {
        if (raw == "stop" || raw.isBlank() && player != null) {
            player?.release()
            player = null
            return "səs dayandı"
        }
        val uri = runCatching { URI(raw) }.getOrNull() ?: return "ünvan səhvdir"
        if (uri.scheme != "https") return "yalnız https"
        player?.release()
        val next = MediaPlayer()
        next.setDataSource(context, Uri.parse(raw))
        next.prepare()
        next.start()
        player = next
        return raw
    }

    private fun contacts(context: Context): String {
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 20) {
                rows += cursor.getString(0) ?: ""
            }
        }
        return rows.filter { it.isNotBlank() }.joinToString("\n").ifBlank { "kontakt yoxdur" }
    }

    private fun calls(context: Context): String {
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 10) {
                rows += "${cursor.getString(0) ?: "?"} ${cursor.getInt(1)}"
            }
        }
        return rows.joinToString("\n").ifBlank { "zəng yoxdur" }
    }

    private fun smsList(context: Context): String {
        val rows = mutableListOf<String>()
        context.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body"), null, null, "date DESC")?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 10) {
                val body = cursor.getString(1).orEmpty().replace("\n", " ").take(80)
                rows += "${cursor.getString(0) ?: "?"} $body"
            }
        }
        return rows.joinToString("\n").ifBlank { "sms yoxdur" }
    }

    private fun smsSend(context: Context, rest: String): String {
        val number = rest.substringBefore(" ")
        val text = rest.substringAfter(" ", "")
        if (!Regex("^\\+?[0-9]{6,15}$").matches(number)) return "nömrə səhvdir"
        if (text.isBlank() || text.length > 160) return "mətn 1-160 hərf olmalıdır"
        val sms = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return "sms yoxdur"
        sms.sendTextMessage(number, null, text, null, null)
        return "göndərildi"
    }
}

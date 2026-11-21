package com.nibras.terminal

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Opt-in prayer reminders that fire even when the app is closed: times come from aladhan.com
 * (city + calculation method), the next one is set with AlarmManager, and every alarm sets the next.
 */
object PrayerAlarms {
    private val NAMES = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    private fun prefs(c: Context) = c.getSharedPreferences("prayer", Context.MODE_PRIVATE)

    fun command(c: Context, args: List<String>): String {
        val p = prefs(c)
        return when (args.firstOrNull() ?: "status") {
            "on" -> {
                val city = args.getOrNull(1) ?: p.getString("city", null) ?: return "ERR\nprayer remind on <city> [country] [method]"
                p.edit().putBoolean("on", true).putString("city", city).putString("country", args.getOrNull(2) ?: p.getString("country", "") ?: "")
                    .putString("method", args.getOrNull(3) ?: p.getString("method", "13")).putInt("before", p.getInt("before", 0)).apply()
                val next = scheduleNow(c)
                val exact = canExact(c)
                if (!exact && Build.VERSION.SDK_INT >= 31) runCatching {
                    c.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                "OK\non · $city · next: ${next ?: "?"}" + if (exact) "" else "\n(exact alarms not allowed yet: allow \"Alarms & reminders\" for Nibras for to-the-minute reminders)"
            }
            "off" -> { p.edit().putBoolean("on", false).apply(); cancel(c); "OK\noff" }
            "before" -> { p.edit().putInt("before", args.getOrNull(1)?.toIntOrNull() ?: 0).apply(); scheduleNow(c); "OK\nreminder ${p.getInt("before", 0)} min before" }
            "test" -> { notify(c, "Nibras", "🕌 test"); "OK" }
            else -> "OK\n" + (if (p.getBoolean("on", false)) "on" else "off") + " · ${p.getString("city", "-")} ${p.getString("country", "")} · method ${p.getString("method", "13")}" +
                " · next: ${p.getString("next", "-")} · exact: ${canExact(c)}"
        }
    }

    private fun canExact(c: Context) = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun intent(c: Context, name: String = "") = PendingIntent.getBroadcast(c, 4242, Intent(c, PrayerReceiver::class.java).putExtra("name", name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancel(c: Context) = c.getSystemService(AlarmManager::class.java).cancel(intent(c))

    /** Background-safe: does the network fetch on a thread. */
    fun schedule(c: Context) {
        if (!prefs(c).getBoolean("on", false)) return
        val app = c.applicationContext
        Thread { runCatching { scheduleNow(app) } }.start()
    }

    fun scheduleBlocking(c: Context) { if (prefs(c).getBoolean("on", false)) runCatching { scheduleNow(c) } }

    private fun fetch(c: Context, day: Calendar): Map<String, Long>? {
        val p = prefs(c)
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(day.time)
        val key = "times:$date"
        val json = runCatching {
            val q = "city=" + URLEncoder.encode(p.getString("city", ""), "UTF-8") + "&country=" + URLEncoder.encode(p.getString("country", ""), "UTF-8") + "&method=" + p.getString("method", "13")
            val conn = URL("https://api.aladhan.com/v1/timingsByCity/$date?$q").openConnection() as HttpURLConnection
            conn.connectTimeout = 15000; conn.readTimeout = 15000
            conn.inputStream.bufferedReader().use { it.readText() }.also { p.edit().putString(key, it).apply() }
        }.getOrNull() ?: p.getString(key, null) ?: return null
        val data = JSONObject(json).getJSONObject("data")
        val tz = TimeZone.getTimeZone(data.getJSONObject("meta").optString("timezone", TimeZone.getDefault().id))
        val timings = data.getJSONObject("timings")
        return NAMES.associateWith { n ->
            val hm = timings.getString(n).take(5).split(':')
            Calendar.getInstance(tz).apply {
                set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), hm[0].toInt(), hm[1].toInt(), 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    private fun scheduleNow(c: Context): String? {
        val p = prefs(c)
        val before = p.getInt("before", 0) * 60_000L
        val now = System.currentTimeMillis()
        var next: Pair<String, Long>? = null
        for (offset in 0..2) {
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
            val times = fetch(c, day) ?: continue
            next = times.entries.map { it.key to it.value - before }.filter { it.second > now + 5_000 }.minByOrNull { it.second }
            if (next != null) break
        }
        val (name, at) = next ?: return null
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = intent(c, name)
        if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        val label = "$name " + SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(at + before))
        p.edit().putString("next", label).apply()
        return label
    }

    fun notify(c: Context, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("prayer", "Prayer times", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(4242, NotificationCompat.Builder(c, "prayer").setSmallIcon(R.drawable.ic_stat_terminal).setContentTitle(title).setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
    }
}

class PrayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name").orEmpty()
        val local = mapOf("Fajr" to "Sübh", "Dhuhr" to "Zöhr", "Asr" to "Əsr", "Maghrib" to "Məğrib", "Isha" to "İşa")
        val lang = com.nibras.terminal.core.Lang.current(context)
        PrayerAlarms.notify(context, "🕌 " + (if (lang == "az") local[name] ?: name else name), context.getString(R.string.prayer_time_now))
        val pending = goAsync()
        Thread { try { PrayerAlarms.scheduleBlocking(context.applicationContext) } finally { pending.finish() } }.start()
    }
}

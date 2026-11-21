package com.nibras.terminal

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nibras.terminal.core.Rootfs
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NibrasWidgets {
    fun refreshAll(context: Context) {
        ShortcutsWidget.refreshAll(context)
        for (cls in listOf(ServerStatusWidget::class.java, PrayerWidget::class.java)) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) context.sendBroadcast(Intent(context, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }

    fun refreshIntent(context: Context, cls: Class<*>, ids: IntArray, code: Int): PendingIntent =
        PendingIntent.getBroadcast(context, code, Intent(context, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Lines of ~/.config/nibras/<name> in the default Linux home, without comments. */
    fun config(context: Context, name: String): List<String> = runCatching {
        File(Rootfs.homeOf(context), ".config/nibras/$name").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }.getOrDefault(emptyList())
}

/** Server status: one line per entry of ~/.config/nibras/servers ("name target", target = https://… or host[:port]). Tap = check again. */
class ServerStatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                val lines = NibrasWidgets.config(context, "servers")
                val text = if (lines.isEmpty()) context.getString(R.string.widget_status_empty) else lines.take(12).joinToString("\n") { line ->
                    val name = line.substringBefore(' '); val target = line.substringAfter(' ', name).trim()
                    val (ok, ms) = check(target)
                    (if (ok) "🟢 " else "🔴 ") + name + if (ok) "  ${ms} ms" else ""
                }
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                for (id in ids) {
                    val v = RemoteViews(context.packageName, R.layout.widget_text)
                    v.setTextViewText(R.id.widget_title, context.getString(R.string.widget_status_label) + " · " + time)
                    v.setTextViewText(R.id.widget_body, text)
                    v.setOnClickPendingIntent(R.id.widget_root, NibrasWidgets.refreshIntent(context, ServerStatusWidget::class.java, ids, 7401))
                    manager.updateAppWidget(id, v)
                }
            } finally { pending.finish() }
        }.start()
    }

    private fun check(target: String): Pair<Boolean, Long> {
        val t0 = System.currentTimeMillis()
        val ok = runCatching {
            if (target.startsWith("http://") || target.startsWith("https://")) {
                val c = URL(target).openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 6000; c.requestMethod = "GET"; c.instanceFollowRedirects = true
                val code = c.responseCode; c.disconnect(); code in 200..399
            } else {
                val host = target.substringBeforeLast(':').ifBlank { target }
                val port = target.substringAfterLast(':', "").toIntOrNull() ?: 443
                Socket().use { it.connect(InetSocketAddress(host, port), 6000) }; true
            }
        }.getOrDefault(false)
        return ok to (System.currentTimeMillis() - t0)
    }
}

/** Prayer times for today (city from `prayer city …`), next prayer highlighted. Tap = refresh. */
class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                val today = runCatching { PrayerAlarms.today(context) }.getOrNull()
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                val now = System.currentTimeMillis()
                val (title, body) = if (today == null) context.getString(R.string.widget_prayer_label) to context.getString(R.string.widget_prayer_empty)
                else {
                    val next = today.second.entries.filter { it.value > now }.minByOrNull { it.value }?.key
                    "🕌 " + today.first to today.second.entries.joinToString("\n") { (n, t) -> (if (n == next) "▶ " else "   ") + n.padEnd(8) + fmt.format(Date(t)) }
                }
                for (id in ids) {
                    val v = RemoteViews(context.packageName, R.layout.widget_text)
                    v.setTextViewText(R.id.widget_title, title)
                    v.setTextViewText(R.id.widget_body, body)
                    v.setOnClickPendingIntent(R.id.widget_root, NibrasWidgets.refreshIntent(context, PrayerWidget::class.java, ids, 7402))
                    manager.updateAppWidget(id, v)
                }
            } finally { pending.finish() }
        }.start()
    }
}

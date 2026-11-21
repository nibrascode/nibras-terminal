package com.nibras.terminal

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.Toast
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Rootfs
import java.io.File

/** Termux:Boot equivalent: runs scripts in ~/.nibras/boot (and ~/.termux/boot) after the phone starts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        val home = Rootfs.homeOf(context)
        val has = listOf(File(home, ".nibras/boot"), File(home, ".termux/boot")).any { (it.listFiles()?.isNotEmpty()) == true }
        if (!has) return
        context.startForegroundService(Intent(context, TerminalService::class.java).setAction(TerminalService.ACTION_BOOT))
    }
}

/** Termux:Widget equivalent: lists scripts in ~/.shortcuts; tasks/ run in the background. */
class ShortcutsWidget : AppWidgetProvider() {
    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ShortcutsWidget::class.java))
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget)
            val service = Intent(context, ShortcutsWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            service.data = Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_list, service)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            val template = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getActivity(context, 0, template, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
            )
            val refresh = Intent(context, ShortcutsWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            views.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getBroadcast(context, 1, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            manager.updateAppWidget(id, views)
        }
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }
}

class ShortcutsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var items: List<Pair<File, Boolean>> = emptyList()
        private lateinit var home: File

        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            home = Rootfs.homeOf(context)
            val dirs = listOf(File(home, ".shortcuts"), File(home, ".termux/shortcuts"))
            items = dirs.flatMap { dir ->
                (dir.listFiles()?.filter { it.isFile }?.map { it to false }.orEmpty()) +
                    (File(dir, "tasks").listFiles()?.filter { it.isFile }?.map { it to true }.orEmpty())
            }.sortedBy { it.first.name.lowercase() }
        }
        override fun onDestroy() = Unit
        override fun getCount() = items.size
        override fun getViewAt(position: Int): RemoteViews {
            val (file, task) = items[position]
            val views = RemoteViews(context.packageName, R.layout.widget_item)
            views.setTextViewText(R.id.widget_item, (if (task) "⚙ " else "▶ ") + file.name)
            val guest = "/root/" + file.relativeTo(home).path
            val fill = Intent()
                .putExtra(MainActivity.EXTRA_COMMAND, arrayOf(guest))
                .putExtra(MainActivity.EXTRA_TITLE, file.name)
                .putExtra(MainActivity.EXTRA_BACKGROUND, task)
            views.setOnClickFillInIntent(R.id.widget_item, fill)
            return views
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}

/**
 * Share-to-terminal (Termux "receive file/URL"): saves into ~/downloads and, when present,
 * runs ~/bin/nibras-url-opener <url> or ~/bin/nibras-file-editor <file> (termux-* names work too).
 */
class ShareActivity : Activity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { handle() }.onFailure { Toast.makeText(this, it.message ?: "error", Toast.LENGTH_LONG).show() }
        finish()
    }

    private fun handle() {
        val id = Rootfs.pick(this, null)
        val home = Rootfs.homeOf(this, id)
        val downloads = File(home, "downloads").apply { mkdirs() }
        val bin = File(home, "bin")
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val stream: Uri? = if (intent.action == Intent.ACTION_VIEW) intent.data else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val guestHome = if (id == Rootfs.ANDROID) home.absolutePath else "/root"
        if (stream != null) {
            val name = displayName(stream)?.replace('/', '_') ?: "shared-${System.currentTimeMillis()}"
            val out = File(downloads, name)
            contentResolver.openInputStream(stream)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            val editor = listOf("nibras-file-editor", "termux-file-editor").map { File(bin, it) }.firstOrNull { it.isFile }
            if (editor != null) {
                launch(id, listOf("$guestHome/bin/${editor.name}", "$guestHome/downloads/$name"), name)
            } else {
                Toast.makeText(this, getString(R.string.share_saved, name), Toast.LENGTH_LONG).show()
                launch(id, null, null)
            }
            return
        }
        if (!text.isNullOrBlank()) {
            val opener = listOf("nibras-url-opener", "termux-url-opener").map { File(bin, it) }.firstOrNull { it.isFile }
            if (opener != null && Regex("^[a-z]+://\\S+$").matches(text.trim())) {
                launch(id, listOf("$guestHome/bin/${opener.name}", text.trim()), "url")
            } else {
                val name = "shared-${System.currentTimeMillis()}.txt"
                File(downloads, name).writeText(text)
                Toast.makeText(this, getString(R.string.share_saved, name), Toast.LENGTH_LONG).show()
                launch(id, null, null)
            }
        }
    }

    private fun launch(env: String, command: List<String>?, title: String?) {
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (command != null) {
            open.putExtra(MainActivity.EXTRA_COMMAND, command.toTypedArray())
                .putExtra(MainActivity.EXTRA_ENV, env)
                .putExtra(MainActivity.EXTRA_TITLE, title)
        }
        startActivity(open)
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
}

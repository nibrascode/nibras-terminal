package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.ui.HostPaths
import com.nibras.terminal.ui.Ui
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File manager pane (MT Manager / Termux file picker style) over the Linux environments and the phone storage. */
class FilesActivity : Activity() {
    companion object {
        const val EXTRA_ENV = "env"
        const val EXTRA_PATH = "path"
        fun open(context: Context, env: String?, guestPath: String?) = context.startActivity(
            Intent(context, FilesActivity::class.java).putExtra(EXTRA_ENV, env).putExtra(EXTRA_PATH, guestPath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private lateinit var envId: String
    private lateinit var dir: File
    private lateinit var pathView: TextView
    private var entries: List<File> = emptyList()
    private var clip: Pair<File, Boolean>? = null // file, cut?
    private var showHidden = false

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        envId = HostPaths.envId(this, intent.getStringExtra(EXTRA_ENV))
        val start = intent.getStringExtra(EXTRA_PATH)?.let { HostPaths.toHost(this, envId, it) }
        dir = start?.takeIf { it.isDirectory } ?: Rootfs.homeOf(this, envId)
        val root = Ui.screen(this, getString(R.string.files_title))
        root.addView(Ui.row(this,
            Ui.button(this, "↑") { up() },
            Ui.button(this, "⌂") { go(Rootfs.homeOf(this, envId)) },
            Ui.button(this, getString(R.string.files_places)) { places() },
            Ui.button(this, "+") { create() },
            Ui.button(this, "⋯") { more() },
        ), Ui.lp(this, 2))
        pathView = Ui.label(this, "", 13f, Ui.GOLD).apply {
            typeface = Typeface.MONOSPACE; setPadding(Ui.dp(context, 16), Ui.dp(context, 4), Ui.dp(context, 16), Ui.dp(context, 4))
        }
        root.addView(pathView)
        val list = ListView(this).apply {
            adapter = this@FilesActivity.adapter; divider = null
            setOnItemClickListener { _, _, pos, _ -> entries.getOrNull(pos)?.let { tap(it) } }
            setOnItemLongClickListener { _, _, pos, _ -> entries.getOrNull(pos)?.let { menu(it) }; true }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        go(dir)
    }

    private fun guest(f: File) = HostPaths.toGuest(this, envId, f)

    private fun go(d: File) {
        dir = d
        entries = (d.listFiles()?.toList() ?: emptyList())
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        pathView.text = "${Rootfs.name(envId)}:${guest(d)}"
        adapter.notifyDataSetChanged()
        if (d.listFiles() == null) Toast.makeText(this, R.string.files_no_access, Toast.LENGTH_SHORT).show()
    }

    private fun up() {
        val root = if (envId == Rootfs.ANDROID) File("/") else Rootfs.dir(this, envId)
        val parent = dir.parentFile
        if (parent != null && (dir.absolutePath != root.absolutePath)) go(parent) else Toast.makeText(this, "/", Toast.LENGTH_SHORT).show()
    }

    private val adapter = object : BaseAdapter() {
        private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        override fun getCount() = entries.size
        override fun getItem(p: Int) = entries[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            val f = entries[p]
            val v = (convertView as? TextView) ?: TextView(this@FilesActivity).apply {
                setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8)); textSize = 15f; setTextColor(Ui.INK)
            }
            val icon = when {
                f.isDirectory -> "📁"
                f.name.endsWith(".apk") -> "📦"
                f.name.matches(Regex(".*\\.(png|jpe?g|webp|gif)$", RegexOption.IGNORE_CASE)) -> "🖼"
                f.name.matches(Regex(".*\\.(sh|py|js|kt|java|c|cpp|go|rs|rb|php|lua)$")) -> "📜"
                f.name.matches(Regex(".*\\.(zip|tar|gz|xz|7z|bz2)$")) -> "🗜"
                else -> "📄"
            }
            val size = if (f.isDirectory) "" else human(f.length())
            v.text = "$icon ${f.name}\n    $size  ${fmt.format(Date(f.lastModified()))}"
            return v
        }
    }

    private fun human(n: Long): String = when {
        n < 1024 -> "$n B"; n < 1 shl 20 -> "%.1f KB".format(n / 1024.0)
        n < 1 shl 30 -> "%.1f MB".format(n / 1048576.0); else -> "%.2f GB".format(n / 1073741824.0)
    }

    private fun tap(f: File) {
        if (f.isDirectory) { go(f); return }
        val ext = f.extension.lowercase()
        if (ext in setOf("apk", "png", "jpg", "jpeg", "webp", "gif", "mp4", "mp3", "pdf", "ogg", "wav", "zip")) openWith(f)
        else EditorActivity.open(this, envId, guest(f))
    }

    private fun uri(f: File) = FileProvider.getUriForFile(this, "$packageName.files", f)
    private fun mime(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    private fun openWith(f: File) = runCatching {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name))
    }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }

    private fun menu(f: File) {
        val items = mutableListOf(getString(R.string.files_open_terminal), getString(R.string.files_edit), getString(R.string.files_open_with),
            getString(R.string.files_share), getString(R.string.files_rename), getString(R.string.files_copy), getString(R.string.files_cut),
            getString(R.string.files_copy_path), getString(R.string.files_delete))
        if (f.name.endsWith(".apk")) items += "nibras-apk info"
        AlertDialog.Builder(this).setTitle(f.name).setItems(items.toTypedArray()) { _, w ->
            val q = "'" + guest(f).replace("'", "'\\''") + "'"
            when (w) {
                0 -> { Launcher.run(this, envId, "cd ${if (f.isDirectory) q else "'" + guest(f.parentFile!!).replace("'", "'\\''") + "'"} && exec \${SHELL:-sh} -l", f.name); finish() }
                1 -> EditorActivity.open(this, envId, guest(f))
                2 -> openWith(f)
                3 -> runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime(f)).putExtra(Intent.EXTRA_STREAM, uri(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name)) }
                4 -> ask(getString(R.string.files_rename), f.name) { n -> if (!f.renameTo(File(f.parentFile, n))) fail(); go(dir) }
                5 -> { clip = f to false; Toast.makeText(this, R.string.files_clip_ready, Toast.LENGTH_SHORT).show() }
                6 -> { clip = f to true; Toast.makeText(this, R.string.files_clip_ready, Toast.LENGTH_SHORT).show() }
                7 -> { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("path", guest(f))); Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show() }
                8 -> AlertDialog.Builder(this).setMessage(getString(R.string.files_delete) + ": ${f.name}?")
                    .setPositiveButton(R.string.ok) { _, _ -> if (!f.deleteRecursively()) fail(); go(dir) }.setNegativeButton(R.string.cancel, null).show()
                9 -> { Launcher.run(this, envId, "nibras-apk info $q", "apk info", keepShell = true); finish() }
            }
        }.show()
    }

    private fun fail() = Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show()

    private fun ask(title: String, value: String, then: (String) -> Unit) {
        val field = Ui.field(this, title, value)
        AlertDialog.Builder(this).setTitle(title).setView(field)
            .setPositiveButton(R.string.ok) { _, _ -> field.text.toString().trim().takeIf { it.isNotEmpty() && !it.contains('/') }?.let(then) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun create() {
        AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.files_new_file), getString(R.string.files_new_folder))) { _, w ->
            ask(if (w == 0) getString(R.string.files_new_file) else getString(R.string.files_new_folder), "") { n ->
                val f = File(dir, n)
                val ok = if (w == 0) runCatching { f.createNewFile() }.getOrDefault(false) else f.mkdirs()
                if (!ok) fail(); go(dir)
                if (ok && w == 0) EditorActivity.open(this, envId, guest(f))
            }
        }.show()
    }

    private fun places() {
        val envs = Rootfs.all.filter { Rootfs.isReady(this, it.id) }.map { it.id } + Rootfs.ANDROID
        val labels = envs.map { "🐧 " + Rootfs.name(it) } + listOf("📱 /sdcard", "📥 Download")
        AlertDialog.Builder(this).setItems(labels.toTypedArray()) { _, w ->
            when {
                w < envs.size -> { envId = envs[w]; go(Rootfs.homeOf(this, envId)) }
                w == envs.size -> go(Environment.getExternalStorageDirectory())
                else -> go(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            }
        }.show()
    }

    private fun more() {
        val items = mutableListOf(if (showHidden) getString(R.string.files_hide_hidden) else getString(R.string.files_show_hidden), getString(R.string.files_terminal_here))
        clip?.let { items += getString(R.string.files_paste) + ": " + it.first.name }
        AlertDialog.Builder(this).setItems(items.toTypedArray()) { _, w ->
            when (w) {
                0 -> { showHidden = !showHidden; go(dir) }
                1 -> { Launcher.run(this, envId, "cd '" + guest(dir).replace("'", "'\\''") + "' && exec \${SHELL:-sh} -l", dir.name); finish() }
                2 -> paste()
            }
        }.show()
    }

    private fun paste() {
        val (src, cut) = clip ?: return
        val dst = File(dir, src.name).let { if (it.exists()) File(dir, "copy-" + src.name) else it }
        Thread {
            val ok = runCatching { if (cut && src.renameTo(dst)) true else src.copyRecursively(dst, overwrite = false).also { if (it && cut) src.deleteRecursively() } }.getOrDefault(false)
            runOnUiThread { if (!ok) fail(); clip = null; go(dir) }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val root = if (envId == Rootfs.ANDROID) File("/") else Rootfs.dir(this, envId)
        if (dir.absolutePath == Rootfs.homeOf(this, envId).absolutePath || dir.absolutePath == root.absolutePath || dir.parentFile == null) super.onBackPressed() else up()
    }
}

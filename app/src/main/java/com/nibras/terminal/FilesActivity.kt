package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.system.Os
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.ui.HostPaths
import com.nibras.terminal.ui.Ui
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * File manager (MT Manager / ZArchiver / Solid Explorer class): two panes, multi-select, sort, live filter, recursive search,
 * bookmarks, zip/unzip natively (tar/7z/rar via the environment), trash with restore, properties, chmod, checksums.
 */
class FilesActivity : Activity() {
    companion object {
        const val EXTRA_ENV = "env"
        const val EXTRA_PATH = "path"
        fun open(context: Context, env: String?, guestPath: String?) = context.startActivity(
            Intent(context, FilesActivity::class.java).putExtra(EXTRA_ENV, env).putExtra(EXTRA_PATH, guestPath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        private val ARCHIVE = Regex(".*\\.(zip|jar|apk|tar|tgz|tar\\.gz|tar\\.xz|txz|tar\\.bz2|tbz2|tar\\.zst|gz|xz|bz2|7z|rar)$", RegexOption.IGNORE_CASE)
    }

    private class Pane(var envId: String, var dir: File)

    private lateinit var panes: Array<Pane>
    private var active = 0
    private val pane get() = panes[active]
    private val envId get() = pane.envId
    private val dir get() = pane.dir
    private lateinit var pathView: TextView
    private lateinit var paneButton: android.widget.Button
    private lateinit var bulkBar: LinearLayout
    private lateinit var bulkLabel: TextView
    private var entries: List<File> = emptyList()
    private var searchResults: List<File>? = null
    private val selected = linkedSetOf<File>()
    private var clip: Pair<List<File>, Boolean>? = null // files, cut?
    private var showHidden = false
    private var filter = ""
    private val prefs by lazy { getSharedPreferences("files", MODE_PRIVATE) }
    private var sortBy: String
        get() = prefs.getString("sort", "name") ?: "name"
        set(v) { prefs.edit().putString("sort", v).apply() }
    private var reverse: Boolean
        get() = prefs.getBoolean("reverse", false)
        set(v) { prefs.edit().putBoolean("reverse", v).apply() }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val env = HostPaths.envId(this, intent.getStringExtra(EXTRA_ENV))
        val start = intent.getStringExtra(EXTRA_PATH)?.let { HostPaths.toHost(this, env, it) }
        val home = Rootfs.homeOf(this, env)
        val sd = Environment.getExternalStorageDirectory()
        panes = arrayOf(Pane(env, start?.takeIf { it.isDirectory } ?: home), Pane(env, if (sd.canRead()) sd else home))
        showHidden = prefs.getBoolean("hidden", false)
        val root = Ui.screen(this, getString(R.string.files_title))
        paneButton = Ui.button(this, "A⇆B") { switchPane() }
        root.addView(Ui.row(this,
            Ui.button(this, "↑") { up() },
            Ui.button(this, "⌂") { go(Rootfs.homeOf(this, envId)) },
            Ui.button(this, "★") { places() },
            paneButton,
            Ui.button(this, "🔍") { search() },
            Ui.button(this, "+") { create() },
            Ui.button(this, "⋯") { more() },
        ), Ui.lp(this, 2))
        pathView = Ui.label(this, "", 13f, Ui.GOLD).apply {
            typeface = Typeface.MONOSPACE; setPadding(Ui.dp(context, 16), Ui.dp(context, 4), Ui.dp(context, 16), Ui.dp(context, 4))
            setOnClickListener { typePath() }
            setOnLongClickListener { bookmark(); true }
        }
        root.addView(pathView)
        bulkLabel = Ui.label(this, "", 14f, Ui.GOLD)
        bulkBar = Ui.row(this, bulkLabel,
            Ui.button(this, getString(R.string.files_copy)) { setClip(false) },
            Ui.button(this, getString(R.string.files_cut)) { setClip(true) },
            Ui.button(this, "🗑") { delete(selected.toList()) },
            Ui.button(this, "⋯") { bulkMore() },
            Ui.button(this, "✕") { selected.clear(); refreshSel() },
        ).apply { visibility = View.GONE }
        root.addView(bulkBar, Ui.lp(this, 2))
        val list = ListView(this).apply {
            adapter = this@FilesActivity.adapter; divider = null; isFastScrollEnabled = true
            setOnItemClickListener { _, _, pos, _ -> shown().getOrNull(pos)?.let { tap(it) } }
            setOnItemLongClickListener { _, _, pos, _ -> shown().getOrNull(pos)?.let { menu(it) }; true }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        go(dir)
    }

    override fun onResume() { super.onResume(); if (::pathView.isInitialized) go(dir) }

    private fun guest(f: File) = HostPaths.toGuest(this, envId, f)
    private fun q(f: File) = "'" + guest(f).replace("'", "'\\''") + "'"
    private fun shown(): List<File> = searchResults ?: entries.filter { filter.isBlank() || it.name.contains(filter, true) }
    private fun toast(s: Any) = Toast.makeText(this, if (s is Int) getString(s) else s.toString(), Toast.LENGTH_SHORT).show()

    private fun go(d: File) {
        pane.dir = d; searchResults = null
        val raw = d.listFiles()
        val cmp: Comparator<File> = when (sortBy) {
            "size" -> compareBy { if (it.isDirectory) 0L else it.length() }
            "date" -> compareBy { it.lastModified() }
            "type" -> compareBy({ it.extension.lowercase() }, { it.name.lowercase() })
            else -> compareBy { it.name.lowercase() }
        }
        entries = (raw?.toList() ?: emptyList())
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareBy<File> { !it.isDirectory }.then(if (reverse) cmp.reversed() else cmp))
        pathView.text = "${"AB"[active]} · ${Rootfs.name(envId)}:${guest(d)}"
        paneButton.text = if (active == 0) "A⇆b" else "a⇆B"
        selected.retainAll(entries.toSet()); refreshSel()
        adapter.notifyDataSetChanged()
        if (raw == null) toast(R.string.files_no_access)
    }

    private fun switchPane() { active = 1 - active; go(dir) }

    private fun up() {
        val root = if (envId == Rootfs.ANDROID) File("/") else Rootfs.dir(this, envId)
        val parent = dir.parentFile
        if (searchResults != null) go(dir)
        else if (parent != null && (dir.absolutePath != root.absolutePath)) go(parent) else toast("/")
    }

    private fun refreshSel() {
        bulkBar.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        bulkLabel.text = "✓ ${selected.size}"
        adapter.notifyDataSetChanged()
    }

    private val adapter = object : BaseAdapter() {
        private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        override fun getCount() = shown().size
        override fun getItem(p: Int) = shown()[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            val f = shown()[p]
            val v = (convertView as? TextView) ?: TextView(this@FilesActivity).apply {
                setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8)); textSize = 15f; setTextColor(Ui.INK)
            }
            val icon = when {
                f.isDirectory -> "📁"
                f.name.endsWith(".apk") -> "📦"
                f.name.matches(Regex(".*\\.(png|jpe?g|webp|gif|bmp|svg)$", RegexOption.IGNORE_CASE)) -> "🖼"
                f.name.matches(Regex(".*\\.(mp4|mkv|webm|avi|mov|3gp)$", RegexOption.IGNORE_CASE)) -> "🎬"
                f.name.matches(Regex(".*\\.(mp3|ogg|wav|flac|m4a|opus|aac)$", RegexOption.IGNORE_CASE)) -> "🎵"
                f.name.matches(Regex(".*\\.(pdf|docx?|odt|xlsx?|pptx?)$", RegexOption.IGNORE_CASE)) -> "📕"
                f.name.matches(Regex(".*\\.(sh|py|js|ts|kt|java|c|cpp|h|go|rs|rb|php|lua|html|css|json|xml|ya?ml|toml|md)$")) -> "📜"
                ARCHIVE.matches(f.name) -> "🗜"
                else -> "📄"
            }
            val mark = if (f in selected) "☑ " else if (selected.isNotEmpty()) "☐ " else ""
            val info = if (f.isDirectory) (f.list()?.size?.let { "$it ▸" } ?: "") else human(f.length())
            val where = if (searchResults != null) "  " + guest(f.parentFile ?: f) else ""
            v.text = "$mark$icon ${f.name}${if (java.nio.file.Files.isSymbolicLink(f.toPath())) " ↪" else ""}\n    $info  ${fmt.format(Date(f.lastModified()))}$where"
            v.setBackgroundColor(if (f in selected) 0x33D6B36A else 0)
            return v
        }
    }

    private fun human(n: Long): String = when {
        n < 1024 -> "$n B"; n < 1 shl 20 -> "%.1f KB".format(n / 1024.0)
        n < 1 shl 30 -> "%.1f MB".format(n / 1048576.0); else -> "%.2f GB".format(n / 1073741824.0)
    }

    private fun tap(f: File) {
        if (selected.isNotEmpty()) { if (!selected.remove(f)) selected += f; refreshSel(); return }
        if (f.isDirectory) { go(f); return }
        val ext = f.extension.lowercase()
        when {
            ext in setOf("zip", "jar") || (ARCHIVE.matches(f.name) && ext != "apk") -> archiveMenu(f)
            ext in setOf("apk", "png", "jpg", "jpeg", "webp", "gif", "bmp", "mp4", "mkv", "webm", "mov", "mp3", "pdf", "ogg", "wav", "flac", "m4a", "opus",
                "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt") -> openWith(f)
            f.length() > 4_000_000 -> openWith(f)
            else -> EditorActivity.open(this, envId, guest(f))
        }
    }

    private fun uri(f: File): Uri = SharedFiles.expose(this, f)
    private fun mime(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    private fun openWith(f: File) = runCatching {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name))
    }.onFailure { toast(it.message ?: "?") }

    private fun share(files: List<File>) = runCatching {
        val i = if (files.size == 1) Intent(Intent.ACTION_SEND).setType(mime(files[0])).putExtra(Intent.EXTRA_STREAM, uri(files[0]))
        else Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(it) }))
        startActivity(Intent.createChooser(i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), files.first().name))
    }.onFailure { toast(it.message ?: "?") }

    private fun terminalIn(d: File) { Launcher.run(this, envId, "cd ${q(d)} && exec \${SHELL:-sh} -l", d.name); finish() }

    private fun inTrash() = dir.absolutePath == trashFiles().absolutePath

    private fun menu(f: File) {
        val acts = mutableListOf<Pair<String, () -> Unit>>()
        acts += getString(R.string.files_select) to { selected += f; refreshSel() }
        if (inTrash()) acts += getString(R.string.files_restore) to { restore(f) }
        if (!f.isDirectory) acts += getString(R.string.files_edit) to { EditorActivity.open(this, envId, guest(f)) }
        acts += getString(R.string.files_open_terminal) to { terminalIn(if (f.isDirectory) f else f.parentFile!!) }
        if (!f.isDirectory) acts += getString(R.string.files_open_with) to { openWith(f) }
        acts += getString(R.string.files_share) to { share(listOf(f)) }
        acts += getString(R.string.files_rename) to { ask(getString(R.string.files_rename), f.name) { n -> if (!f.renameTo(File(f.parentFile, n))) fail(); go(dir) } }
        acts += getString(R.string.files_copy) to { clip = listOf(f) to false; toast(R.string.files_clip_ready) }
        acts += getString(R.string.files_cut) to { clip = listOf(f) to true; toast(R.string.files_clip_ready) }
        acts += getString(R.string.files_copy_to_other) to { transfer(listOf(f), panes[1 - active].dir, cut = false) }
        acts += getString(R.string.files_move_to_other) to { transfer(listOf(f), panes[1 - active].dir, cut = true) }
        acts += getString(R.string.files_compress) to { compress(listOf(f)) }
        if (ARCHIVE.matches(f.name)) acts += getString(R.string.files_extract) to { archiveMenu(f) }
        acts += getString(R.string.files_copy_path) to { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("path", guest(f))); toast(R.string.copied) }
        acts += getString(R.string.files_properties) to { properties(f) }
        acts += getString(R.string.files_chmod) to { chmod(listOf(f)) }
        if (!f.isDirectory) acts += getString(R.string.files_checksum) to { checksum(f) }
        if (f.name.endsWith(".apk")) acts += "APK: info / sign / decompile" to { Launcher.run(this, envId, "nibras-apk ${q(f)}", "apk", keepShell = true); finish() }
        if (f.name.matches(Regex(".*\\.(mp4|mkv|webm|mov|avi|mp3|wav|flac|m4a)$", RegexOption.IGNORE_CASE)))
            acts += "🎬 video / audio…" to { Launcher.run(this, envId, "cd ${q(f.parentFile!!)} && nibras-kit video", "video", keepShell = true); finish() }
        if (f.isDirectory) acts += "★ " + getString(R.string.files_bookmark) to { addBookmark(f) }
        acts += getString(R.string.files_delete) to { delete(listOf(f)) }
        AlertDialog.Builder(this).setTitle(f.name).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun bulkMore() {
        val files = selected.toList()
        val acts = listOf<Pair<String, () -> Unit>>(
            getString(R.string.files_select_all) to { selected.addAll(shown()); refreshSel() },
            getString(R.string.files_invert) to { val all = shown().toSet(); val now = all - selected; selected.clear(); selected.addAll(now); refreshSel() },
            getString(R.string.files_share) to { share(files.filter { it.isFile }) },
            getString(R.string.files_compress) to { compress(files) },
            getString(R.string.files_copy_to_other) to { transfer(files, panes[1 - active].dir, false) },
            getString(R.string.files_move_to_other) to { transfer(files, panes[1 - active].dir, true) },
            getString(R.string.files_chmod) to { chmod(files) },
            getString(R.string.files_copy_path) to { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("paths", files.joinToString("\n") { guest(it) })); toast(R.string.copied) },
        )
        AlertDialog.Builder(this).setTitle("✓ ${files.size}").setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun setClip(cut: Boolean) { clip = selected.toList() to cut; selected.clear(); refreshSel(); toast(R.string.files_clip_ready) }

    private fun fail() = toast(R.string.files_failed)

    private fun ask(title: String, value: String, allowSlash: Boolean = false, then: (String) -> Unit) {
        val field = Ui.field(this, title, value)
        AlertDialog.Builder(this).setTitle(title).setView(field)
            .setPositiveButton(R.string.ok) { _, _ -> field.text.toString().trim().takeIf { it.isNotEmpty() && (allowSlash || !it.contains('/')) }?.let(then) }
            .setNegativeButton(R.string.cancel, null).show()
        field.requestFocus()
    }

    private fun typePath() = ask(getString(R.string.files_go_to), guest(dir), allowSlash = true) { p ->
        val f = HostPaths.toHost(this, envId, p)
        when { f == null -> fail(); f.isDirectory -> go(f); f.isFile -> { go(f.parentFile!!); tap(f) }; else -> toast(R.string.files_no_access) }
    }

    private fun create() {
        AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.files_new_file), getString(R.string.files_new_folder))) { _, w ->
            ask(if (w == 0) getString(R.string.files_new_file) else getString(R.string.files_new_folder), "", allowSlash = w == 1) { n ->
                val f = File(dir, n)
                val ok = if (w == 0) runCatching { f.createNewFile() }.getOrDefault(false) else f.mkdirs()
                if (!ok) fail(); go(dir)
                if (ok && w == 0) EditorActivity.open(this, envId, guest(f))
            }
        }.show()
    }

    // ---- places & bookmarks
    private fun bookmarks(): List<String> = prefs.getString("bookmarks", "")!!.split('\n').filter { it.isNotBlank() }
    private fun addBookmark(f: File) { prefs.edit().putString("bookmarks", (bookmarks() + "${pane.envId}\t${f.absolutePath}").distinct().joinToString("\n")).apply(); toast("★") }
    private fun bookmark() = addBookmark(dir)
    private fun trashFiles() = File(Rootfs.homeOf(this, envId), ".local/share/Trash/files")

    private fun places() {
        val envs = Rootfs.all.filter { Rootfs.isReady(this, it.id) }.map { it.id } + Rootfs.ANDROID
        val acts = mutableListOf<Pair<String, () -> Unit>>()
        envs.forEach { e -> acts += "🐧 " + Rootfs.name(e) to { pane.envId = e; go(Rootfs.homeOf(this, e)) } }
        acts += "📱 /sdcard" to { go(Environment.getExternalStorageDirectory()) }
        acts += "📥 Download" to { go(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) }
        acts += "📷 DCIM" to { go(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)) }
        acts += "🎬 Nibras" to { go(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nibras").apply { mkdirs() }) }
        acts += "🗑 " + getString(R.string.files_trash) to { go(trashFiles().apply { mkdirs() }) }
        bookmarks().forEach { b ->
            val (e, p) = b.split('\t', limit = 2).let { it[0] to it.getOrElse(1) { "/" } }
            acts += "★ " + Rootfs.name(e) + ":" + HostPaths.toGuest(this, e, File(p)) to { pane.envId = e; go(File(p)) }
        }
        AlertDialog.Builder(this).setTitle(R.string.files_places).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }
            .setNeutralButton(R.string.files_bookmarks_clear) { _, _ -> prefs.edit().remove("bookmarks").apply() }.show()
    }

    // ---- search
    private fun search() {
        val field = Ui.field(this, getString(R.string.search_hint), filter)
        AlertDialog.Builder(this).setTitle("🔍").setView(field)
            .setPositiveButton(R.string.files_filter_here) { _, _ -> filter = field.text.toString(); searchResults = null; adapter.notifyDataSetChanged() }
            .setNeutralButton(R.string.files_search_sub) { _, _ -> deepSearch(field.text.toString()) }
            .setNegativeButton(R.string.files_clear) { _, _ -> filter = ""; go(dir) }.show()
    }

    private fun deepSearch(term: String) {
        if (term.isBlank()) return
        toast("…")
        val base = dir
        Thread {
            val out = mutableListOf<File>()
            val glob = term.contains('*') || term.contains('?')
            val rx = if (glob) Regex(term.split('*').joinToString(".*") { it.split('?').joinToString(".") { p -> Regex.escape(p) } }, RegexOption.IGNORE_CASE) else null
            base.walkTopDown().onEnter { showHidden || !it.name.startsWith(".") || it == base }.forEach {
                if (out.size < 1000 && it != base && (rx?.matches(it.name) ?: it.name.contains(term, true))) out += it
            }
            runOnUiThread { searchResults = out; pathView.text = "🔍 \"$term\" · ${out.size}"; adapter.notifyDataSetChanged() }
        }.start()
    }

    // ---- more
    private fun more() {
        val sorts = listOf("name", "size", "date", "type")
        val acts = mutableListOf<Pair<String, () -> Unit>>()
        clip?.let { (files, cut) -> acts += (if (cut) "✂ " else "⎘ ") + getString(R.string.files_paste) + ": " + (if (files.size == 1) files[0].name else "${files.size}") to { paste() } }
        acts += getString(R.string.files_sort) + ": " + sortBy + (if (reverse) " ↓" else " ↑") to {
            AlertDialog.Builder(this).setSingleChoiceItems(sorts.toTypedArray(), sorts.indexOf(sortBy)) { d, w -> if (sorts[w] == sortBy) reverse = !reverse else sortBy = sorts[w]; d.dismiss(); go(dir) }.show()
        }
        acts += (if (showHidden) getString(R.string.files_hide_hidden) else getString(R.string.files_show_hidden)) to { showHidden = !showHidden; prefs.edit().putBoolean("hidden", showHidden).apply(); go(dir) }
        acts += getString(R.string.files_select_all) to { selected.addAll(shown()); refreshSel() }
        acts += getString(R.string.files_terminal_here) to { terminalIn(dir) }
        acts += "★ " + getString(R.string.files_bookmark) to { bookmark() }
        acts += getString(R.string.files_properties) to { properties(dir) }
        acts += getString(R.string.files_same_dir) to { panes[1 - active].envId = envId; panes[1 - active].dir = dir; toast("A=B") }
        if (inTrash()) acts += getString(R.string.files_empty_trash) to { dir.listFiles()?.forEach { it.deleteRecursively() }; File(dir.parentFile, "info").listFiles()?.forEach { it.delete() }; go(dir) }
        acts += getString(R.string.files_storage_info) to { storageInfo() }
        AlertDialog.Builder(this).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun paste() {
        val (files, cut) = clip ?: return
        transfer(files, dir, cut); clip = null
    }

    private fun unique(target: File): File {
        if (!target.exists()) return target
        val base = target.nameWithoutExtension; val ext = target.extension.let { if (it.isEmpty() || target.isDirectory) "" else ".$it" }
        var i = 1
        while (true) { val f = File(target.parentFile, "$base ($i)$ext"); if (!f.exists()) return f; i++ }
    }

    private fun transfer(files: List<File>, to: File, cut: Boolean) {
        val dialog = AlertDialog.Builder(this).setMessage("… 0/${files.size}").setCancelable(false).show()
        Thread {
            var failed = 0
            files.forEachIndexed { i, src ->
                runOnUiThread { dialog.setMessage("${if (cut) "✂" else "⎘"} ${i + 1}/${files.size}\n${src.name}") }
                if (to.absolutePath.startsWith(src.absolutePath + "/") || to.absolutePath == src.absolutePath) { failed++; return@forEachIndexed }
                val dst = unique(File(to, src.name))
                val ok = runCatching { if (cut && src.renameTo(dst)) true else src.copyRecursively(dst, overwrite = false).also { if (it && cut) src.deleteRecursively() } }.getOrDefault(false)
                if (!ok) failed++
            }
            runOnUiThread { dialog.dismiss(); if (failed > 0) toast(getString(R.string.files_failed) + ": $failed"); go(dir) }
        }.start()
    }

    private fun delete(files: List<File>) {
        if (files.isEmpty()) return
        val name = if (files.size == 1) files[0].name else "${files.size}"
        val b = AlertDialog.Builder(this).setMessage(getString(R.string.files_delete) + ": $name?")
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.files_delete_forever) { _, _ ->
                Thread { val bad = files.count { !it.deleteRecursively() }; runOnUiThread { if (bad > 0) fail(); selected.clear(); go(dir) } }.start()
            }
        if (!inTrash()) b.setPositiveButton("🗑 " + getString(R.string.files_trash)) { _, _ -> trash(files) }
        b.show()
    }

    private fun trash(files: List<File>) {
        val tf = trashFiles().apply { mkdirs() }; val info = File(tf.parentFile, "info").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        var bad = 0
        files.forEach { f ->
            var n = f.name; if (File(tf, n).exists()) n += "." + System.currentTimeMillis() / 1000
            val dst = File(tf, n)
            val ok = f.renameTo(dst) || (f.copyRecursively(dst) && f.deleteRecursively())
            if (ok) File(info, "$n.trashinfo").writeText("[Trash Info]\nPath=${guest(f)}\nDeletionDate=$stamp\n") else bad++
        }
        if (bad > 0) fail() else toast("🗑 ${files.size}")
        selected.clear(); go(dir)
    }

    private fun restore(f: File) {
        val info = File(f.parentFile!!.parentFile, "info/${f.name}.trashinfo")
        val path = runCatching { info.readLines().first { it.startsWith("Path=") }.removePrefix("Path=") }.getOrNull()
        val dst = path?.let { HostPaths.toHost(this, envId, it) } ?: return fail()
        dst.parentFile?.mkdirs()
        if (f.renameTo(unique(dst))) { info.delete(); toast("→ $path") } else fail()
        go(dir)
    }

    // ---- archives
    private fun compress(files: List<File>) {
        if (files.isEmpty()) return
        val suggested = (if (files.size == 1) files[0].nameWithoutExtension.ifEmpty { files[0].name } else dir.name.ifEmpty { "archive" }) + ".zip"
        val formats = arrayOf("zip", "tar.gz", "tar.xz", "7z")
        AlertDialog.Builder(this).setTitle(R.string.files_compress).setItems(formats) { _, w ->
            ask(getString(R.string.files_compress), suggested.removeSuffix(".zip") + "." + formats[w]) { name ->
                val out = unique(File(dir, name))
                if (w == 0) zip(files, out)
                else {
                    val list = files.joinToString(" ") { "'" + it.name.replace("'", "'\\''") + "'" }
                    val cmd = when (w) { 1 -> "tar czvf ${q(out)} $list"; 2 -> "tar cJvf ${q(out)} $list"; else -> "nibras-need 7z && 7z a ${q(out)} $list" }
                    Launcher.run(this, envId, "cd ${q(dir)} && $cmd", name, keepShell = true); finish()
                }
            }
        }.show()
    }

    private fun zip(files: List<File>, out: File) {
        val dialog = AlertDialog.Builder(this).setMessage("🗜 …").setCancelable(false).show()
        Thread {
            val ok = runCatching {
                ZipOutputStream(out.outputStream().buffered()).use { zo ->
                    files.forEach { top ->
                        top.walkTopDown().forEach { f ->
                            val rel = top.name + f.absolutePath.removePrefix(top.absolutePath)
                            runOnUiThread { dialog.setMessage("🗜 $rel") }
                            if (f.isDirectory) zo.putNextEntry(ZipEntry("$rel/")).also { zo.closeEntry() }
                            else { zo.putNextEntry(ZipEntry(rel).apply { time = f.lastModified() }); f.inputStream().use { it.copyTo(zo) }; zo.closeEntry() }
                        }
                    }
                }
            }.isSuccess
            runOnUiThread { dialog.dismiss(); if (!ok) { out.delete(); fail() } else toast("✓ ${out.name}"); selected.clear(); go(dir) }
        }.start()
    }

    private fun archiveMenu(f: File) {
        val isZip = f.extension.lowercase() in setOf("zip", "jar", "apk")
        val acts = mutableListOf<Pair<String, () -> Unit>>()
        acts += getString(R.string.files_extract_here) to { extract(f, dir) }
        acts += getString(R.string.files_extract_to) + " ${f.name.substringBefore('.')}/" to { extract(f, unique(File(dir, f.name.substringBefore('.')))) }
        acts += getString(R.string.files_extract_other) to { extract(f, panes[1 - active].dir) }
        if (isZip) acts += getString(R.string.files_view_contents) to { listZip(f) }
        acts += getString(R.string.files_open_with) to { openWith(f) }
        AlertDialog.Builder(this).setTitle(f.name).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun listZip(f: File) {
        val names = runCatching { java.util.zip.ZipFile(f).use { z -> z.entries().toList().map { "${it.name}  ${if (it.isDirectory) "" else human(it.size)}" } } }.getOrElse { listOf(it.message ?: "?") }
        AlertDialog.Builder(this).setTitle("${f.name} · ${names.size}").setItems(names.take(2000).toTypedArray(), null).setPositiveButton(R.string.ok, null).show()
    }

    private fun extract(f: File, to: File) {
        val ext = f.extension.lowercase()
        if (ext in setOf("zip", "jar", "apk")) {
            val dialog = AlertDialog.Builder(this).setMessage("📂 …").setCancelable(false).show()
            Thread {
                val ok = runCatching {
                    to.mkdirs(); val base = to.canonicalPath + File.separator
                    ZipInputStream(f.inputStream().buffered()).use { zi ->
                        while (true) {
                            val e = zi.nextEntry ?: break
                            val out = File(to, e.name)
                            if (!out.canonicalPath.startsWith(base)) continue // zip-slip guard
                            runOnUiThread { dialog.setMessage("📂 ${e.name}") }
                            if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { zi.copyTo(it) } }
                        }
                    }
                }.isSuccess
                runOnUiThread { dialog.dismiss(); if (!ok) fail() else toast("✓"); go(dir) }
            }.start()
            return
        }
        val cmd = when {
            f.name.matches(Regex(".*\\.(tar|tgz|tar\\.gz|tar\\.xz|txz|tar\\.bz2|tbz2)$", RegexOption.IGNORE_CASE)) -> "tar xvf ${q(f)} -C ${q(to)}"
            f.name.endsWith(".tar.zst") -> "nibras-need zstd && tar --zstd -xvf ${q(f)} -C ${q(to)}"
            ext == "gz" -> "gunzip -k ${q(f)}"
            ext == "xz" -> "xz -dk ${q(f)}"
            ext == "bz2" -> "bzip2 -dk ${q(f)}"
            else -> "nibras-need 7z && 7z x ${q(f)} -o${q(to)}"
        }
        Launcher.run(this, envId, "mkdir -p ${q(to)} && cd ${q(to)} && $cmd", f.name, keepShell = true); finish()
    }

    // ---- info
    private fun perms(f: File): String = runCatching {
        val m = Os.lstat(f.absolutePath).st_mode
        val s = StringBuilder(if (f.isDirectory) "d" else "-")
        for (i in 8 downTo 0) s.append(if (m and (1 shl i) != 0) "rwx"[(8 - i) % 3] else '-')
        "$s (${Integer.toOctalString(m and 0x1FF)})"
    }.getOrDefault("?")

    private fun properties(f: File) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val base = "${getString(R.string.files_path)}: ${guest(f)}\n${getString(R.string.files_modified)}: ${fmt.format(Date(f.lastModified()))}\n${getString(R.string.files_permissions)}: ${perms(f)}" +
            (if (java.nio.file.Files.isSymbolicLink(f.toPath())) "\n↪ " + runCatching { java.nio.file.Files.readSymbolicLink(f.toPath()).toString() }.getOrDefault("?") else "") +
            "\nMIME: ${if (f.isDirectory) "inode/directory" else mime(f)}"
        val d = AlertDialog.Builder(this).setTitle(f.name).setMessage("$base\n${getString(R.string.files_size)}: …").setPositiveButton(R.string.ok, null).show()
        Thread {
            var size = 0L; var files = 0; var dirs = 0
            f.walkTopDown().forEach { if (it.isDirectory) dirs++ else { files++; size += it.length() } }
            runOnUiThread { d.setMessage("$base\n${getString(R.string.files_size)}: ${human(size)} ($size B)" + if (f.isDirectory) "\n📁 ${dirs - 1} · 📄 $files" else "") }
        }.start()
    }

    private fun chmod(files: List<File>) {
        val start = files.firstOrNull()?.let { runCatching { Integer.toOctalString(Os.stat(it.absolutePath).st_mode and 0x1FF) }.getOrNull() } ?: "644"
        val field = Ui.field(this, "755", start, mono = true).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("chmod").setView(field)
            .setItems(arrayOf("644  rw-r--r--", "755  rwxr-xr-x", "600  rw-------", "700  rwx------", "777  rwxrwxrwx")) { _, w -> applyChmod(files, listOf(420, 493, 384, 448, 511)[w]) }
            .setPositiveButton(R.string.ok) { _, _ -> field.text.toString().toIntOrNull(8)?.let { applyChmod(files, it) } }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun applyChmod(files: List<File>, mode: Int) {
        val bad = files.count { runCatching { Os.chmod(it.absolutePath, mode) }.isFailure }
        if (bad > 0) fail() else toast("chmod ${Integer.toOctalString(mode)} ✓"); go(dir)
    }

    private fun checksum(f: File) {
        val d = AlertDialog.Builder(this).setTitle(f.name).setMessage("…").setPositiveButton(R.string.ok, null).show()
        Thread {
            val algos = listOf("MD5", "SHA-1", "SHA-256").map { MessageDigest.getInstance(it) }
            runCatching { f.inputStream().buffered().use { i -> val buf = ByteArray(1 shl 16); while (true) { val n = i.read(buf); if (n < 0) break; algos.forEach { it.update(buf, 0, n) } } } }
            val text = algos.joinToString("\n\n") { a -> a.algorithm + "\n" + a.digest().joinToString("") { "%02x".format(it) } }
            runOnUiThread {
                d.setMessage(text)
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("hash", text)); toast(R.string.copied); d.dismiss() }
            }
        }.start()
    }

    private fun storageInfo() {
        val roots = listOf(filesDir, Environment.getExternalStorageDirectory())
        val text = roots.joinToString("\n\n") { r -> runCatching { val s = android.os.StatFs(r.absolutePath)
            "${r.absolutePath}\n${human(s.availableBytes)} / ${human(s.totalBytes)}" }.getOrDefault(r.absolutePath) }
        AlertDialog.Builder(this).setTitle(R.string.files_storage_info).setMessage(text).setPositiveButton(R.string.ok, null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selected.isNotEmpty()) { selected.clear(); refreshSel(); return }
        if (searchResults != null || filter.isNotEmpty()) { filter = ""; go(dir); return }
        val root = if (envId == Rootfs.ANDROID) File("/") else Rootfs.dir(this, envId)
        if (dir.absolutePath == Rootfs.homeOf(this, envId).absolutePath || dir.absolutePath == root.absolutePath || dir.parentFile == null) super.onBackPressed() else up()
    }
}

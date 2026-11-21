package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.shQuote
import com.nibras.terminal.ui.Ui
import java.io.File

/**
 * Visual builder for ffmpeg and yt-dlp (no other terminal has this): pick files and options with buttons,
 * see the exact command live, run it in a terminal session. Tools are installed on first use.
 */
class MediaActivity : Activity() {
    private data class Op(val id: String, val label: String, val fields: List<String>, val multi: Boolean = false)

    private val videoOps = listOf(
        Op("merge", "🔗 merge", listOf(), multi = true), Op("cut", "✂ cut", listOf("start", "end")), Op("compress", "🗜 compress", listOf("crf", "height")),
        Op("convert", "🔄 convert", listOf("format")), Op("audio", "🎵 → mp3", listOf("format")), Op("gif", "GIF", listOf("start", "seconds", "width", "fps")),
        Op("resize", "↕ resize", listOf("height")), Op("rotate", "⟳ rotate", listOf("angle")), Op("speed", "⏩ speed", listOf("factor")),
        Op("mute", "🔇 mute", listOf()), Op("thumb", "🖼 frame", listOf("start")), Op("subs", "💬 burn subtitles", listOf("subtitle")),
        Op("watermark", "© watermark", listOf("image")), Op("info", "ℹ info", listOf()),
    )
    private val audioOps = listOf(
        Op("aconvert", "🔄 convert", listOf("format", "bitrate")), Op("acut", "✂ cut", listOf("start", "end")), Op("amerge", "🔗 merge", listOf(), multi = true),
        Op("volume", "🔊 volume", listOf("factor")), Op("normalize", "📏 normalize", listOf()), Op("ringtone", "🔔 ringtone (30 s)", listOf("start")),
    )
    private val defaults = mapOf("start" to "00:00:00", "end" to "00:00:30", "crf" to "28", "height" to "720", "format" to "mp4", "seconds" to "5",
        "width" to "480", "fps" to "12", "angle" to "90", "factor" to "2", "bitrate" to "192k", "subtitle" to "", "image" to "")

    private var tab = "video"
    private var op: Op = videoOps[0]
    private val inputs = mutableListOf<String>()
    private val values = mutableMapOf<String, EditText>()
    private lateinit var body: LinearLayout
    private lateinit var preview: TextView
    private lateinit var outName: EditText
    // download tab
    private lateinit var url: EditText
    private var quality = "best"
    private val checks = mutableMapOf<String, CheckBox>()

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.screen(this, "🎬 " + getString(R.string.media_title))
        root.addView(Ui.row(this,
            Ui.button(this, "🎬 Video") { tab = "video"; op = videoOps[0]; build() },
            Ui.button(this, "🎵 Audio") { tab = "audio"; op = audioOps[0]; build() },
            Ui.button(this, "⬇ Download") { tab = "download"; build() },
        ), Ui.lp(this, 2))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        preview = Ui.label(this, "", 12f, Ui.GOLD).apply { typeface = Typeface.MONOSPACE; setPadding(Ui.dp(context, 12), Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 6)); setBackgroundColor(Ui.PANEL); setTextIsSelectable(true) }
        root.addView(preview)
        root.addView(Ui.row(this, Ui.button(this, "▶ " + getString(R.string.editor_run), accent = true) { run() }), Ui.lp(this, 2))
        tab = intent.getStringExtra("tab")?.takeIf { it in setOf("video", "audio", "download") } ?: "video"
        op = if (tab == "audio") audioOps[0] else videoOps[0]
        build()
    }

    private val watcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = update()
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
    }

    private fun section(text: String) = body.addView(Ui.label(this, text, 14f, Ui.GOLD).apply { setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 2)) })

    private fun chips(items: List<Pair<String, String>>, selected: String, pick: (String) -> Unit) {
        items.chunked(3).forEach { row ->
            body.addView(Ui.row(this, *row.map { (id, label) -> Ui.button(this, label, accent = id == selected) { pick(id) } }.toTypedArray()))
        }
    }

    private fun build() {
        body.removeAllViews(); values.clear(); checks.clear()
        if (tab == "download") { buildDownload(); update(); return }
        val ops = if (tab == "audio") audioOps else videoOps
        section(getString(R.string.media_operation))
        chips(ops.map { it.id to it.label }, op.id) { id -> op = ops.first { it.id == id }; if (!op.multi && inputs.size > 1) inputs.retainAll(listOf(inputs.first())); build() }
        section(getString(R.string.media_input) + if (op.multi) " (2+)" else "")
        inputs.forEach { p -> body.addView(Ui.row(this, Ui.label(this, "• " + p.substringAfterLast('/'), 14f, Ui.INK), Ui.button(this, "✕") { inputs.remove(p); build() })) }
        body.addView(Ui.row(this, Ui.button(this, "📂 " + getString(R.string.media_pick)) { pick() }, Ui.button(this, "⌨ " + getString(R.string.files_go_to)) { typePath() }))
        if (op.fields.isNotEmpty()) section(getString(R.string.media_options))
        op.fields.forEach { f ->
            val e = Ui.field(this, f, defaults[f].orEmpty(), mono = true).apply { addTextChangedListener(watcher) }
            values[f] = e
            body.addView(Ui.row(this, Ui.label(this, f, 13f), e).apply { (getChildAt(0).layoutParams as LinearLayout.LayoutParams).weight = 0.4f })
        }
        section(getString(R.string.media_output))
        outName = Ui.field(this, "out", "", mono = true).apply { addTextChangedListener(watcher) }
        body.addView(outName)
        update()
    }

    private fun buildDownload() {
        section("URL")
        url = Ui.field(this, "https://…", intent.getStringExtra("url").orEmpty(), mono = true).apply { addTextChangedListener(watcher) }
        body.addView(url)
        body.addView(Ui.row(this, Ui.button(this, "📋 " + getString(R.string.menu_paste)) {
            getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.let { url.setText(it) }
        }))
        section(getString(R.string.media_quality))
        chips(listOf("best" to "★ best", "2160" to "4K", "1080" to "1080p", "720" to "720p", "480" to "480p", "mp3" to "🎵 mp3", "m4a" to "🎵 m4a", "opus" to "🎵 opus", "file" to "📄 file"), quality) { quality = it; build() }
        section(getString(R.string.media_options))
        listOf("playlist" to "Playlist", "subs" to getString(R.string.media_subs), "thumb" to getString(R.string.media_thumb), "meta" to "Metadata", "sponsor" to "SponsorBlock", "limit" to getString(R.string.media_limit))
            .forEach { (k, label) -> val cb = CheckBox(this).apply { text = label; setTextColor(Ui.INK); isChecked = k == "meta"; setOnCheckedChangeListener { _, _ -> update() } }; checks[k] = cb; body.addView(cb) }
        body.addView(Ui.label(this, getString(R.string.media_rights), 12f))
    }

    private fun outDir() = "/sdcard/Download/Nibras"
    private fun v(k: String) = values[k]?.text?.toString()?.trim().orEmpty().ifEmpty { defaults[k].orEmpty() }
    private fun q(s: String) = shQuote(s)

    private fun out(ext: String): String {
        val base = outName.text.toString().trim().ifEmpty { (inputs.firstOrNull()?.substringAfterLast('/')?.substringBeforeLast('.') ?: "out") + "-" + op.id }
        return "${outDir()}/" + if (base.contains('.')) base else "$base.$ext"
    }

    private fun command(): String? {
        if (tab == "download") {
            val u = url.text.toString().trim().ifEmpty { return null }
            if (quality == "file") return "command -v aria2c >/dev/null || nibras-need aria2c; mkdir -p ${outDir()} && aria2c -x 8 -s 8 -c -d ${outDir()} ${q(u)}"
            val a = mutableListOf("yt-dlp")
            when (quality) {
                "mp3", "m4a", "opus" -> a += listOf("-x", "--audio-format", quality)
                "best" -> a += listOf("-f", "bv*+ba/b", "--merge-output-format", "mp4")
                else -> a += listOf("-f", "bv*[height<=$quality]+ba/b[height<=$quality]", "--merge-output-format", "mp4")
            }
            if (checks["subs"]?.isChecked == true) a += listOf("--write-subs", "--write-auto-subs", "--sub-langs", "az,tr,en,ru", "--embed-subs")
            if (checks["thumb"]?.isChecked == true) a += "--embed-thumbnail"
            if (checks["meta"]?.isChecked == true) a += "--add-metadata"
            if (checks["sponsor"]?.isChecked == true) a += listOf("--sponsorblock-remove", "sponsor")
            if (checks["limit"]?.isChecked == true) a += listOf("-r", "2M")
            a += if (checks["playlist"]?.isChecked == true) listOf("--yes-playlist", "-o", "${outDir()}/%(playlist_title)s/%(playlist_index)03d %(title)s.%(ext)s")
            else listOf("--no-playlist", "-o", "${outDir()}/%(title)s.%(ext)s")
            a += u
            return "command -v yt-dlp >/dev/null || nibras-kit install download; mkdir -p ${outDir()} && " + a.joinToString(" ") { q(it) }
        }
        if (inputs.isEmpty() || (op.multi && inputs.size < 2)) return null
        val i = q(inputs.first())
        val ff = "ffmpeg -hide_banner -y"
        val cmd = when (op.id) {
            "merge", "amerge" -> {
                val list = "/tmp/nibras-concat.txt"
                val ext = if (op.id == "amerge") "mp3" else "mp4"
                "printf \"file '%s'\\n\" ${inputs.joinToString(" ") { q(it) }} > $list && ($ff -f concat -safe 0 -i $list -c copy ${q(out(ext))} || $ff -f concat -safe 0 -i $list ${if (ext == "mp4") "-c:v libx264 -crf 23 -c:a aac" else ""} ${q(out(ext))})"
            }
            "cut" -> "$ff -ss ${v("start")} -to ${v("end")} -i $i -c copy ${q(out(inputs.first().substringAfterLast('.')))}"
            "acut" -> "$ff -ss ${v("start")} -to ${v("end")} -i $i ${q(out(inputs.first().substringAfterLast('.')))}"
            "compress" -> "$ff -i $i -vf \"scale=-2:'min(${v("height")},ih)'\" -c:v libx264 -preset veryfast -crf ${v("crf")} -c:a aac -b:a 128k ${q(out("mp4"))}"
            "convert" -> "$ff -i $i ${q(out(v("format")))}"
            "audio" -> "$ff -i $i -vn -q:a 2 ${q(out(if (v("format") == "mp4") "mp3" else v("format")))}"
            "gif" -> "$ff -ss ${v("start")} -t ${v("seconds")} -i $i -vf \"fps=${v("fps")},scale=${v("width")}:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse\" ${q(out("gif"))}"
            "resize" -> "$ff -i $i -vf scale=-2:${v("height")} -c:a copy ${q(out("mp4"))}"
            "rotate" -> "$ff -i $i -vf \"${when (v("angle")) { "180" -> "transpose=1,transpose=1"; "270" -> "transpose=2"; else -> "transpose=1" }}\" -c:a copy ${q(out("mp4"))}"
            "speed" -> { val f = v("factor").toDoubleOrNull() ?: 2.0
                "$ff -i $i -filter_complex \"[0:v]setpts=${"%.4f".format(java.util.Locale.US, 1 / f)}*PTS[v];[0:a]atempo=${"%.2f".format(java.util.Locale.US, f.coerceIn(0.5, 2.0))}[a]\" -map [v] -map [a] ${q(out("mp4"))}" }
            "mute" -> "$ff -i $i -an -c:v copy ${q(out("mp4"))}"
            "thumb" -> "$ff -ss ${v("start")} -i $i -frames:v 1 ${q(out("jpg"))}"
            "subs" -> "$ff -i $i -vf subtitles=${q(v("subtitle"))} -c:a copy ${q(out("mp4"))}"
            "watermark" -> "$ff -i $i -i ${q(v("image"))} -filter_complex \"[1]scale=iw/5:-1[w];[0][w]overlay=W-w-20:H-h-20\" -c:a copy ${q(out("mp4"))}"
            "info" -> "ffprobe -hide_banner $i"
            "aconvert" -> "$ff -i $i -vn -b:a ${v("bitrate")} ${q(out(v("format").takeIf { it != "mp4" } ?: "mp3"))}"
            "volume" -> "$ff -i $i -af volume=${v("factor")} ${q(out(inputs.first().substringAfterLast('.')))}"
            "normalize" -> "$ff -i $i -af loudnorm ${q(out(inputs.first().substringAfterLast('.')))}"
            "ringtone" -> "$ff -ss ${v("start")} -t 30 -i $i -af \"afade=t=in:d=1,afade=t=out:st=29:d=1\" ${q(out("mp3"))}"
            else -> return null
        }
        return "nibras-need ffmpeg && mkdir -p ${outDir()} && $cmd && nibras-media-scan ${outDir()} >/dev/null 2>&1; echo '→ ${outDir()}'"
    }

    private fun update() { preview.text = command() ?: getString(R.string.media_need_input) }

    private fun run() {
        val c = command() ?: return
        Launcher.run(this, null, c, if (tab == "download") "download" else op.id, keepShell = true)
        finish()
    }

    private fun typePath() {
        val f = Ui.field(this, "/sdcard/…", "/sdcard/", mono = true)
        AlertDialog.Builder(this).setView(f).setPositiveButton(R.string.ok) { _, _ -> addInput(f.text.toString().trim()) }.show()
    }

    private fun addInput(p: String) { if (p.isBlank()) return; if (!op.multi) inputs.clear(); inputs += p; build() }

    /** Newest media files from the usual folders, so picking is one tap. */
    private fun pick() {
        val sd = Environment.getExternalStorageDirectory()
        val exts = if (tab == "audio") setOf("mp3", "m4a", "ogg", "opus", "wav", "flac", "aac", "mp4", "mkv", "webm")
        else setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "ts", "m4v", "mp3", "m4a", "wav", "ogg", "gif")
        val dirs = listOf("DCIM", "Movies", "Download", "Music", "Pictures", "Recordings", "WhatsApp/Media", "Android/media/com.whatsapp/WhatsApp/Media", "Telegram")
        val found = dirs.flatMap { d -> File(sd, d).walkTopDown().maxDepth(4).filter { it.isFile && it.extension.lowercase() in exts }.toList() }
            .distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }.take(300)
        if (found.isEmpty()) { typePath(); return }
        val labels = found.map { it.name + "  ·  " + it.parentFile?.name }
        if (op.multi) {
            val picked = BooleanArray(found.size)
            AlertDialog.Builder(this).setMultiChoiceItems(labels.toTypedArray(), picked) { _, w, on -> picked[w] = on }
                .setPositiveButton(R.string.ok) { _, _ -> found.filterIndexed { k, _ -> picked[k] }.forEach { inputs += "/sdcard" + it.absolutePath.removePrefix(sd.absolutePath) }; build() }
                .setNegativeButton(R.string.cancel, null).show()
        } else AlertDialog.Builder(this).setItems(labels.toTypedArray()) { _, w -> addInput("/sdcard" + found[w].absolutePath.removePrefix(sd.absolutePath)) }.show()
    }
}

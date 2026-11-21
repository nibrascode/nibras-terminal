package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.nibras.terminal.core.Lang
import com.nibras.terminal.ui.HostPaths
import com.nibras.terminal.ui.Ui
import java.io.File

/**
 * Compact visual APK / project resource editor (APK Editor Pro / MT Manager style):
 * strings per language in a table, colors with swatches, drawable/icon replacement from the gallery,
 * app name / package / version, then one tap: rebuild → sign → install.
 * Works on an `apktool d` folder, a Gradle project, or a bare res/ folder.
 */
class ResEditorActivity : Activity() {
    companion object {
        private const val PICK = 41
        fun open(context: Context, env: String?, guestDir: String) = context.startActivity(
            Intent(context, ResEditorActivity::class.java).putExtra("env", env).putExtra("path", guestDir).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        private val STRING = Regex("<string\\s+name=\"([^\"]+)\"([^>]*)>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        private val COLOR = Regex("<color\\s+name=\"([^\"]+)\"[^>]*>\\s*([^<\\s]+)\\s*</color>")
        fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n")
            .let { if (it.startsWith("@") || it.startsWith("?")) "\\" + it else it }
    }

    private lateinit var envId: String
    private lateinit var guestDir: String
    private lateinit var dir: File
    private lateinit var res: File
    private var manifest: File? = null
    private var yml: File? = null
    private var gradle: File? = null
    private lateinit var body: LinearLayout
    private var locale = ""
    private var pickTarget: List<File> = emptyList()

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        envId = HostPaths.envId(this, intent.getStringExtra("env"))
        guestDir = intent.getStringExtra("path") ?: "/root"
        dir = HostPaths.toHost(this, envId, guestDir) ?: run { finish(); return }
        res = listOf(File(dir, "res"), File(dir, "app/src/main/res"), File(dir, "src/main/res"), dir).first { File(it, "values").isDirectory || it == dir }
        manifest = listOf(File(dir, "AndroidManifest.xml"), File(dir, "app/src/main/AndroidManifest.xml"), File(dir, "src/main/AndroidManifest.xml")).firstOrNull { it.isFile }
        yml = File(dir, "apktool.yml").takeIf { it.isFile }
        gradle = listOf("app/build.gradle", "app/build.gradle.kts", "build.gradle", "build.gradle.kts").map { File(dir, it) }.firstOrNull { it.isFile && it.readText().contains("defaultConfig") }
        val root = Ui.screen(this, "🎨 " + getString(R.string.res_title) + " · " + guestDir.substringAfterLast('/'))
        root.addView(Ui.row(this,
            Ui.button(this, "🔤 " + getString(R.string.res_strings)) { showStrings() },
            Ui.button(this, "🎨 " + getString(R.string.res_colors)) { showColors() },
            Ui.button(this, "🖼 " + getString(R.string.res_images)) { showImages() },
            Ui.button(this, "📦 " + getString(R.string.res_app)) { showApp() },
        ), Ui.lp(this, 2))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Ui.button(this, "🔨 " + getString(R.string.res_rebuild), accent = true) { rebuild() }, Ui.lp(this, 4))
        if (!File(res, "values").isDirectory) { body.addView(Ui.label(this, getString(R.string.res_none, guestDir), 15f)); return }
        showStrings()
    }

    private fun clear(title: String) { body.removeAllViews(); body.addView(Ui.label(this, title, 13f).apply { setPadding(Ui.dp(context, 14), Ui.dp(context, 4), Ui.dp(context, 14), Ui.dp(context, 4)) }) }

    private fun cell(main: String, sub: String, click: () -> Unit, left: View? = null): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 14), Ui.dp(context, 8))
            background = GradientDrawable().apply { setColor(Ui.PANEL); cornerRadius = Ui.dp(context, 6).toFloat() }
            setOnClickListener { click() }
        }
        left?.let { row.addView(it, LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)).apply { marginEnd = Ui.dp(this@ResEditorActivity, 10) }) }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = main.ifEmpty { "∅" }; setTextColor(Ui.INK); textSize = 15f; maxLines = 3 })
            addView(TextView(context).apply { text = sub; setTextColor(Ui.MUTED); textSize = 12f; maxLines = 2 })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row.also { v -> body.addView(v, Ui.lp(this, 3)) }
    }

    // ── strings ──
    private fun stringsFile(loc: String) = File(res, (if (loc.isEmpty()) "values" else "values-$loc") + "/strings.xml")
    private fun readStrings(f: File): LinkedHashMap<String, String> =
        LinkedHashMap<String, String>().apply { if (f.isFile) STRING.findAll(f.readText()).forEach { put(it.groupValues[1], unescape(it.groupValues[3].trim())) } }

    private fun writeString(loc: String, key: String, value: String) {
        val f = stringsFile(loc); f.parentFile?.mkdirs()
        var t = if (f.isFile) f.readText() else "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"
        val re = Regex("(<string\\s+name=\"${Regex.escape(key)}\"[^>]*>)(.*?)(</string>)", RegexOption.DOT_MATCHES_ALL)
        t = if (re.containsMatchIn(t)) re.replace(t) { it.groupValues[1] + escape(value) + it.groupValues[3] } else t.replace("</resources>", "    <string name=\"$key\">${escape(value)}</string>\n</resources>")
        f.writeText(t)
    }

    private fun locales() = res.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").isFile }
        .map { it.name.removePrefix("values-") }.sorted()

    private fun showStrings() {
        val base = readStrings(stringsFile(""))
        val tr = if (locale.isEmpty()) base else readStrings(stringsFile(locale))
        clear(getString(R.string.res_strings_hint, base.size))
        val locs = listOf("") + locales()
        body.addView(Ui.row(this,
            Ui.button(this, "🌐 " + (locale.ifEmpty { "default" })) {
                val names = locs.map { it.ifEmpty { "default (values)" } } + ("+ " + getString(R.string.res_add_lang))
                AlertDialog.Builder(this).setItems(names.toTypedArray()) { _, w ->
                    if (w < locs.size) { locale = locs[w]; showStrings() } else {
                        val f = Ui.field(this, "az, tr, ar, ru, fr, de, es, zh-rCN…", mono = true)
                        AlertDialog.Builder(this).setTitle(R.string.res_add_lang).setView(f).setPositiveButton(R.string.ok) { _, _ ->
                            val l = f.text.toString().trim(); if (l.isEmpty()) return@setPositiveButton
                            stringsFile(l).apply { parentFile?.mkdirs(); if (!isFile) writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n") }
                            locale = l; showStrings()
                        }.show()
                    }
                }.show()
            },
            Ui.button(this, "🔍") {
                val f = Ui.field(this, getString(R.string.search_hint))
                AlertDialog.Builder(this).setView(f).setPositiveButton(R.string.ok) { _, _ -> filterStrings(base, tr, f.text.toString()) }.show()
            },
        ), Ui.lp(this, 2))
        filterStrings(base, tr, "")
    }

    private fun filterStrings(base: Map<String, String>, tr: Map<String, String>, q: String) {
        while (body.childCount > 2) body.removeViewAt(2)
        base.entries.filter { q.isEmpty() || it.key.contains(q, true) || it.value.contains(q, true) || tr[it.key]?.contains(q, true) == true }.take(1500).forEach { (k, v) ->
            val cur = tr[k]
            val missing = locale.isNotEmpty() && cur == null
            cell(if (locale.isEmpty()) v else (cur ?: "⚠ $v"), if (locale.isEmpty()) k else "$k · $v", {
                val f = Ui.field(this, v, cur ?: (if (locale.isEmpty()) v else "")).apply { minLines = 2 }
                AlertDialog.Builder(this).setTitle(k).setMessage(if (locale.isEmpty()) null else "default: $v").setView(f)
                    .setPositiveButton(R.string.ok) { _, _ -> writeString(locale, k, f.text.toString()); showStrings() }
                    .setNegativeButton(R.string.cancel, null).show()
            }).also { if (missing) it.alpha = 0.75f }
        }
    }

    // ── colors ──
    private fun colorFiles() = res.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values") }.map { File(it, "colors.xml") }.filter { it.isFile }

    private fun parse(c: String): Int? = runCatching { Color.parseColor(if (c.length == 4 || c.length == 5) "#" + c.drop(1).map { "$it$it" }.joinToString("") else c) }.getOrNull()

    private fun showColors() {
        clear(getString(R.string.res_colors))
        colorFiles().forEach { f ->
            COLOR.findAll(f.readText()).forEach { m ->
                val name = m.groupValues[1]; val value = m.groupValues[2]
                val sw = View(this).apply { background = GradientDrawable().apply { setColor(parse(value) ?: Color.TRANSPARENT); cornerRadius = Ui.dp(context, 22).toFloat(); setStroke(2, Ui.MUTED) } }
                cell(name, "$value · ${f.parentFile?.name}", {
                    val fld = Ui.field(this, "#RRGGBB / #AARRGGBB", value, mono = true)
                    val prev = View(this).apply { setBackgroundColor(parse(value) ?: 0) }
                    fld.addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) { parse(s.toString())?.let { prev.setBackgroundColor(it) } }
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    })
                    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(fld); addView(prev, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 48))) }
                    AlertDialog.Builder(this).setTitle(name).setView(box).setPositiveButton(R.string.ok) { _, _ ->
                        val v = fld.text.toString().trim()
                        if (parse(v) == null) { Toast.makeText(this, "✗ $v", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                        f.writeText(f.readText().replace(Regex("(<color\\s+name=\"${Regex.escape(name)}\"[^>]*>)\\s*[^<]*(</color>)")) { it.groupValues[1] + v + it.groupValues[2] })
                        showColors()
                    }.setNegativeButton(R.string.cancel, null).show()
                }, sw)
            }
        }
        if (body.childCount == 1) body.addView(Ui.label(this, "—", 15f))
    }

    // ── images ──
    private val IMG = setOf("png", "webp", "jpg", "jpeg")
    private fun images() = res.listFiles().orEmpty().filter { it.isDirectory && (it.name.startsWith("drawable") || it.name.startsWith("mipmap")) }
        .flatMap { d -> d.listFiles().orEmpty().filter { it.extension.lowercase() in IMG && !it.name.endsWith(".9.png") } }

    private fun iconName(): String? = manifest?.readText()?.let { Regex("<application[^>]*?android:icon=\"@(?:mipmap|drawable)/([^\"]+)\"", RegexOption.DOT_MATCHES_ALL).find(it)?.groupValues?.get(1) }

    private fun showImages() {
        clear(getString(R.string.res_images_hint))
        val icon = iconName()
        val all = images()
        if (icon != null) {
            val icons = all.filter { it.nameWithoutExtension == icon || it.nameWithoutExtension == icon + "_round" || it.nameWithoutExtension == icon + "_foreground" }
            cell("⭐ " + getString(R.string.res_app_icon) + " ($icon)", "${icons.size} files: " + icons.joinToString(" ") { it.parentFile?.name ?: "" }, { pick(icons) }, thumb(icons.maxByOrNull { it.length() }))
        }
        all.sortedBy { it.nameWithoutExtension }.groupBy { it.nameWithoutExtension }.forEach { (name, files) ->
            cell(name, files.joinToString(" ") { it.parentFile!!.name + "/" + it.extension }, { pick(files) }, thumb(files.maxByOrNull { it.length() }))
        }
    }

    private fun thumb(f: File?): View = ImageView(this).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        f?.let { file -> runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull()?.let { setImageBitmap(it) } }
    }

    private fun pick(targets: List<File>) {
        if (targets.isEmpty()) return
        pickTarget = targets
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != PICK || resultCode != RESULT_OK) return
        val src = runCatching { contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) } }.getOrNull()
            ?: return Toast.makeText(this, "✗ image", Toast.LENGTH_LONG).show()
        var n = 0
        pickTarget.forEach { f ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
            val w = if (o.outWidth > 0) o.outWidth else src.width; val h = if (o.outHeight > 0) o.outHeight else src.height
            val scaled = Bitmap.createScaledBitmap(src, w, h, true)
            val fmt = when (f.extension.lowercase()) { "png" -> Bitmap.CompressFormat.PNG; "webp" -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP; else -> Bitmap.CompressFormat.JPEG }
            runCatching { f.outputStream().use { scaled.compress(fmt, 95, it) } }.onSuccess { n++ }
        }
        // Adaptive icon XML would keep showing the old foreground on Android 8+: move it aside (kept in .nibras-backup).
        iconName()?.let { icon ->
            if (pickTarget.any { it.nameWithoutExtension.startsWith(icon) }) res.listFiles().orEmpty().filter { it.name.startsWith("mipmap-anydpi") }.forEach { d ->
                d.listFiles().orEmpty().filter { it.nameWithoutExtension == icon || it.nameWithoutExtension == icon + "_round" }.forEach { x ->
                    val bak = File(dir, ".nibras-backup/${d.name}/${x.name}").apply { parentFile?.mkdirs() }; x.renameTo(bak)
                }
            }
        }
        Toast.makeText(this, "✓ $n", Toast.LENGTH_SHORT).show(); showImages()
    }

    // ── app info (name, package, version) ──
    private fun showApp() {
        clear(getString(R.string.res_app))
        val m = manifest?.readText().orEmpty()
        val labelRef = Regex("<application[^>]*?android:label=\"([^\"]*)\"", RegexOption.DOT_MATCHES_ALL).find(m)?.groupValues?.get(1).orEmpty()
        val label = if (labelRef.startsWith("@string/")) readStrings(stringsFile(""))[labelRef.removePrefix("@string/")].orEmpty() else labelRef
        val y = yml?.readText().orEmpty(); val g = gradle?.readText().orEmpty()
        val pkgNow = Regex("package=\"([^\"]+)\"").find(m)?.groupValues?.get(1)
        val pkg = Regex("renameManifestPackage:\\s*(\\S+)").find(y)?.groupValues?.get(1)?.takeIf { it != "null" }
            ?: Regex("applicationId\\s*=?\\s*[\"']([^\"']+)").find(g)?.groupValues?.get(1) ?: pkgNow.orEmpty()
        val vc = Regex("versionCode:\\s*'?(\\d+)").find(y)?.groupValues?.get(1) ?: Regex("versionCode\\s*=?\\s*(\\d+)").find(g)?.groupValues?.get(1).orEmpty()
        val vn = Regex("versionName:\\s*'?([^'\\n]+)").find(y)?.groupValues?.get(1)?.trim() ?: Regex("versionName\\s*=?\\s*[\"']([^\"']+)").find(g)?.groupValues?.get(1).orEmpty()
        val fName = Ui.field(this, getString(R.string.res_app_name), label)
        val fPkg = Ui.field(this, "com.example.app", pkg, mono = true)
        val fVc = Ui.field(this, "versionCode", vc, mono = true).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val fVn = Ui.field(this, "versionName", vn, mono = true)
        listOf(getString(R.string.res_app_name) to fName, getString(R.string.res_package) to fPkg, "versionCode" to fVc, "versionName" to fVn).forEach { (t, f) ->
            body.addView(Ui.label(this, t), Ui.lp(this, 4)); body.addView(f, Ui.lp(this, 4))
        }
        body.addView(Ui.button(this, "💾 " + getString(R.string.editor_save), accent = true) {
            val nl = fName.text.toString(); val np = fPkg.text.toString().trim(); val nc = fVc.text.toString().trim(); val nn = fVn.text.toString().trim()
            runCatching {
                if (nl != label) {
                    if (labelRef.startsWith("@string/")) writeString("", labelRef.removePrefix("@string/"), nl)
                    else manifest?.let { mf -> val t = mf.readText(); Regex("(<application[^>]*?android:label=\")[^\"]*", RegexOption.DOT_MATCHES_ALL).find(t)?.let { r -> mf.writeText(t.replaceRange(r.range, r.groupValues[1] + nl.replace("\"", "&quot;"))) } }
                }
                yml?.let { yf ->
                    var t = yf.readText()
                    if (np.isNotEmpty() && np != pkgNow) t = if (t.contains("renameManifestPackage:")) t.replace(Regex("renameManifestPackage:.*"), "renameManifestPackage: $np") else "$t\npackageInfo:\n  renameManifestPackage: $np\n"
                    if (nc.isNotEmpty()) t = t.replace(Regex("versionCode:.*"), "versionCode: $nc")
                    if (nn.isNotEmpty()) t = t.replace(Regex("versionName:.*"), "versionName: $nn")
                    yf.writeText(t)
                }
                gradle?.let { gf ->
                    var t = gf.readText()
                    if (np.isNotEmpty()) t = t.replace(Regex("(applicationId\\s*=?\\s*[\"'])[^\"']+")) { it.groupValues[1] + np }
                    if (nc.isNotEmpty()) t = t.replace(Regex("(versionCode\\s*=?\\s*)\\d+")) { it.groupValues[1] + nc }
                    if (nn.isNotEmpty()) t = t.replace(Regex("(versionName\\s*=?\\s*[\"'])[^\"']+")) { it.groupValues[1] + nn }
                    gf.writeText(t)
                }
            }.onSuccess { Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
        }, Ui.lp(this, 6))
        body.addView(Ui.label(this, getString(R.string.res_app_note)), Ui.lp(this, 6))
        body.addView(Ui.button(this, "📄 AndroidManifest.xml") { manifest?.let { EditorActivity.open(this, envId, HostPaths.toGuest(this, envId, it)) } }, Ui.lp(this, 4))
    }

    private fun rebuild() {
        val q = "'" + guestDir.replace("'", "'\\''") + "'"
        val cmd = when {
            yml != null -> "nibras-apk rebuild $q"
            gradle != null -> "nibras-apk run $q"
            else -> "echo 'Not an apktool folder or Gradle project: decode an APK first with  nibras-apk res app.apk'"
        }
        Launcher.run(this, envId, cmd, "rebuild", keepShell = true)
    }
}

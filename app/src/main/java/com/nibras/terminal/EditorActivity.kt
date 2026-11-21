package com.nibras.terminal

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.nibras.terminal.core.GuestPaths
import com.nibras.terminal.core.Lang
import com.nibras.terminal.ui.HostPaths
import com.nibras.terminal.ui.Ui
import java.io.File

/** EditText with a line-number gutter and current-line highlight (Acode / AndroidIDE style). */
@SuppressLint("AppCompatCustomView")
class CodeEdit(context: Context) : EditText(context) {
    var gutter = true
        set(v) { field = v; updateGutter() }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.MUTED; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT }
    private val linePaint = Paint().apply { color = 0x18FFFFFF }
    private val sepPaint = Paint().apply { color = 0x33FFFFFF }
    private var gutterW = 0
    private val pad = Ui.dp(context, 6)

    fun updateGutter() {
        numPaint.textSize = textSize * 0.85f
        val digits = text.count { it == '\n' }.plus(1).toString().length.coerceAtLeast(2)
        gutterW = if (gutter) (numPaint.measureText("9".repeat(digits)) + pad * 2).toInt() else 0
        setPadding(gutterW + pad, paddingTop, paddingRight, paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout
        if (l != null) {
            val cur = l.getLineForOffset(selectionStart.coerceAtLeast(0))
            canvas.drawRect(scrollX.toFloat(), (l.getLineTop(cur) + paddingTop).toFloat(), (scrollX + width).toFloat(), (l.getLineBottom(cur) + paddingTop).toFloat(), linePaint)
        }
        super.onDraw(canvas)
        if (!gutter || l == null) return
        val first = l.getLineForVertical(scrollY); val last = l.getLineForVertical(scrollY + height)
        canvas.drawRect(scrollX.toFloat(), scrollY.toFloat(), (scrollX + gutterW).toFloat(), (scrollY + height).toFloat(), Paint().apply { color = Ui.BG })
        canvas.drawLine((scrollX + gutterW).toFloat(), scrollY.toFloat(), (scrollX + gutterW).toFloat(), (scrollY + height).toFloat(), sepPaint)
        val t = text
        var number = t.subSequence(0, l.getLineStart(first)).count { it == '\n' } + 1
        for (i in first..last) {
            val start = l.getLineStart(i)
            if (i == first || (start > 0 && t[start - 1] == '\n')) {
                if (i != first) number++
                canvas.drawText(number.toString(), (scrollX + gutterW - pad).toFloat(), (l.getLineBaseline(i) + paddingTop).toFloat(), numPaint)
            }
        }
    }
}

/** Built-in code editor (Acode / AndroidIDE / QuickEdit class). */
class EditorActivity : Activity() {
    companion object {
        const val EXTRA_PATH = "path"    // guest path
        const val EXTRA_ENV = "env"      // environment id or name
        private val COMMON = "if|then|else|elif|fi|for|while|do|done|case|esac|function|return|in|break|continue|true|false|null"
        private val LANGS = mapOf(
            "sh" to "$COMMON|echo|export|local|read|set|unset|source|exit|shift|trap",
            "py" to "$COMMON|def|class|import|from|as|with|try|except|finally|raise|lambda|pass|yield|global|nonlocal|not|and|or|is|None|True|False|self|async|await|print",
            "kt" to "$COMMON|fun|val|var|object|when|is|package|import|class|interface|private|public|internal|override|data|sealed|companion|this|super|try|catch|throw|lateinit|by|lazy",
            "java" to "$COMMON|class|interface|public|private|protected|static|final|void|int|long|boolean|double|float|char|new|this|super|try|catch|throw|throws|import|package|extends|implements",
            "js" to "$COMMON|function|var|let|const|class|new|this|import|export|from|default|async|await|try|catch|throw|typeof|instanceof|undefined|yield|switch",
            "c" to "$COMMON|int|char|long|short|float|double|void|struct|union|enum|typedef|static|const|unsigned|signed|sizeof|switch|include|define|class|public|private|namespace|using|template|auto|std",
            "go" to "$COMMON|func|package|import|var|const|type|struct|interface|map|chan|go|defer|select|range|nil|switch",
            "rs" to "$COMMON|fn|let|mut|struct|enum|impl|trait|pub|use|mod|match|loop|self|Self|crate|where|as|ref|move|unsafe|Some|None|Ok|Err",
            "php" to "$COMMON|function|class|public|private|echo|new|this|namespace|use|try|catch|foreach|as|array",
            "rb" to "$COMMON|def|class|module|end|unless|until|begin|rescue|ensure|yield|self|nil|require|puts|attr_accessor",
            "lua" to "$COMMON|function|local|end|repeat|until|nil|not|and|or",
        )
        private val ALIAS = mapOf("bash" to "sh", "zsh" to "sh", "ts" to "js", "mjs" to "js", "jsx" to "js", "tsx" to "js", "cpp" to "c", "cc" to "c", "h" to "c", "hpp" to "c", "kts" to "kt")
        private val STRINGS = Regex("\"(\\\\.|[^\"\\\\\\n])*\"|'(\\\\.|[^'\\\\\\n])*'|`[^`]*`")
        private val NUMBERS = Regex("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)\\b")
        private val TAGS = Regex("</?[A-Za-z][\\w:-]*|/?>")
        private val ATTRS = Regex("\\s([\\w:-]+)=")
        private val JSON_KEYS = Regex("\"(\\\\.|[^\"\\\\])*\"(?=\\s*:)")
        private val MD_HEAD = Regex("^#{1,6} .*$|^\\s*([-*+]|\\d+\\.) ", RegexOption.MULTILINE)
        private val PAIRS = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`')

        fun open(context: Context, env: String?, guestPath: String) = context.startActivity(
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_ENV, env).putExtra(EXTRA_PATH, guestPath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
        )
    }

    private lateinit var envId: String
    private lateinit var guest: String
    private var file: File? = null
    private lateinit var editor: CodeEdit
    private lateinit var status: TextView
    private lateinit var findBar: LinearLayout
    private lateinit var findField: EditText
    private lateinit var replaceField: EditText
    private lateinit var caseBox: CheckBox
    private lateinit var regexBox: CheckBox
    private var saved = ""
    private val undo = ArrayDeque<Pair<String, Int>>()
    private val redo = ArrayDeque<Pair<String, Int>>()
    private var restoring = false
    private var lastSnap = 0L
    private var pendingInsert = -1
    private val main = Handler(Looper.getMainLooper())
    private val highlighter = Runnable { highlight() }
    private val prefs by lazy { getSharedPreferences("editor", MODE_PRIVATE) }
    private fun pref(k: String, d: Boolean) = prefs.getBoolean(k, d)
    private fun setPref(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()
    private val ext get() = guest.substringAfterLast('.', "").lowercase().let { ALIAS[it] ?: it }
    private val indentUnit get() = if (ext == "go" || guest.endsWith("Makefile")) "\t" else " ".repeat(prefs.getInt("tab", 4))

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        envId = HostPaths.envId(this, intent.getStringExtra(EXTRA_ENV))
        guest = intent.getStringExtra(EXTRA_PATH) ?: "/root/untitled.txt"
        file = HostPaths.toHost(this, envId, guest)
        remember(guest)
        val root = Ui.screen(this, "✎ " + guest.substringAfterLast('/'))
        root.addView(Ui.row(this,
            Ui.button(this, "💾", accent = true) { save() },
            Ui.button(this, "▶") { if (save()) run() },
            Ui.button(this, "↶") { undoOnce() },
            Ui.button(this, "↷") { redoOnce() },
            Ui.button(this, "🔍") { toggleFind() },
            Ui.button(this, "⋯") { more() },
        ), Ui.lp(this, 2))
        findField = Ui.field(this, getString(R.string.search_hint), mono = true).apply { isSingleLine = true }
        replaceField = Ui.field(this, getString(R.string.editor_replace_with), mono = true).apply { isSingleLine = true }
        caseBox = CheckBox(this).apply { text = "Aa"; setTextColor(Ui.INK) }
        regexBox = CheckBox(this).apply { text = ".*"; setTextColor(Ui.INK) }
        findBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            addView(Ui.row(context, findField, caseBox, regexBox))
            addView(Ui.row(context, replaceField,
                Ui.button(context, "◀") { findStep(false) }, Ui.button(context, "▶") { findStep(true) },
                Ui.button(context, "⇄") { replaceOne() }, Ui.button(context, getString(R.string.editor_replace_all)) { replaceAll() }))
        }
        findField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { main.removeCallbacks(highlighter); main.postDelayed(highlighter, 200) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
        root.addView(findBar, Ui.lp(this, 2))
        editor = CodeEdit(this).apply {
            typeface = Typeface.MONOSPACE; textSize = prefs.getFloat("size", 14f); setTextColor(Ui.INK); gravity = Gravity.TOP or Gravity.START
            setBackgroundColor(Ui.BG); isSingleLine = false
            setHorizontallyScrolling(!pref("wrap", false))
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(Ui.dp(context, 6), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
            gutter = pref("gutter", true)
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(symbolBar())
        status = Ui.label(this, guest).apply { setPadding(Ui.dp(context, 12), Ui.dp(context, 2), Ui.dp(context, 12), Ui.dp(context, 2)) }
        root.addView(status)
        if (!load()) return
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                if (restoring) return
                val now = System.currentTimeMillis()
                // group fast typing into one undo step (like Acode), but always snapshot before a newline or big edit
                if (now - lastSnap > 700 || c > 1 || b > 1) { undo.addLast(s.toString() to editor.selectionStart); if (undo.size > 300) undo.removeFirst(); redo.clear() }
                lastSnap = now
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pendingInsert = if (!restoring && before == 0 && count == 1) start else -1
            }
            override fun afterTextChanged(s: Editable) {
                val at = pendingInsert; pendingInsert = -1
                if (at >= 0 && at < s.length) smartInsert(s, at)
                updateStatus(); main.removeCallbacks(highlighter); main.postDelayed(highlighter, 350)
            }
        })
        editor.viewTreeObserver.addOnGlobalLayoutListener { updateStatus() }
    }

    private fun load(): Boolean {
        val f = file
        val text = when {
            f == null -> { Toast.makeText(this, R.string.editor_bad_path, Toast.LENGTH_LONG).show(); "" }
            !f.exists() -> ""
            f.length() > 8_000_000 -> { Toast.makeText(this, R.string.editor_too_big, Toast.LENGTH_LONG).show(); finish(); return false }
            else -> runCatching { f.readText() }.getOrElse { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show(); "" }
        }
        saved = text; restoring = true
        editor.setText(text); editor.updateGutter()
        restoring = false
        prefs.getInt("pos:$guest", 0).let { editor.setSelection(it.coerceIn(0, text.length)) }
        updateStatus(); highlight()
        return true
    }

    private fun smartInsert(s: Editable, at: Int) {
        val c = s[at]
        restoring = true
        try {
            if (c == '\n' && pref("indent", true)) {
                val lineStart = s.lastIndexOf('\n', at - 1).let { it + 1 }
                val prev = s.substring(lineStart, at)
                var ind = prev.takeWhile { it == ' ' || it == '\t' }
                val trimmed = prev.trimEnd()
                if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[") || trimmed.endsWith(":") ||
                    Regex("\\b(then|do|else)$").containsMatchIn(trimmed)) ind += indentUnit
                val next = s.getOrNull(at + 1)
                if (next != null && (next == '}' || next == ')' || next == ']') && (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("["))) {
                    s.insert(at + 1, ind + "\n" + ind.removeSuffix(indentUnit)); editor.setSelection(at + 1 + ind.length)
                } else if (ind.isNotEmpty()) s.insert(at + 1, ind)
            } else if (pref("autoclose", true)) {
                val next = s.getOrNull(at + 1)
                when {
                    (c == ')' || c == ']' || c == '}' || c == '"' || c == '\'' || c == '`') && next == c -> { s.delete(at + 1, at + 2) } // type-through
                    c in PAIRS.keys && (next == null || next.isWhitespace() || next in ")]};,") &&
                        !((c == '\'' || c == '"' || c == '`') && at > 0 && s[at - 1].isLetterOrDigit()) -> { s.insert(at + 1, PAIRS.getValue(c).toString()); editor.setSelection(at + 1) }
                }
            }
        } finally { restoring = false }
    }

    private fun symbolBar(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val keys = listOf("⇥", "←", "→", "↑", "↓", "{", "}", "(", ")", "[", "]", "<", ">", ";", ":", "\"", "'", "=", "+", "-", "*", "/", "\\", "|", "&", "$", "#", "_", "!", "?", "~", "`", "%", "^", "@")
        keys.forEach { k ->
            row.addView(TextView(this).apply {
                text = k; textSize = 17f; setTextColor(Ui.INK); typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
                setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8))
                setOnClickListener { key(k) }
            })
        }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(Ui.PANEL); addView(row) }
    }

    private fun key(k: String) {
        val t = editor.text; val a = editor.selectionStart.coerceAtLeast(0); val b = editor.selectionEnd.coerceAtLeast(0)
        val l = editor.layout
        when (k) {
            "⇥" -> t.replace(minOf(a, b), maxOf(a, b), indentUnit)
            "←" -> editor.setSelection((a - 1).coerceAtLeast(0))
            "→" -> editor.setSelection((b + 1).coerceAtMost(t.length))
            "↑", "↓" -> if (l != null) {
                val line = l.getLineForOffset(a); val target = if (k == "↑") line - 1 else line + 1
                if (target in 0 until l.lineCount) editor.setSelection(l.getOffsetForHorizontal(target, l.getPrimaryHorizontal(a)))
            }
            else -> t.replace(minOf(a, b), maxOf(a, b), k)
        }
    }

    private fun updateStatus() {
        val dirty = editor.text.toString() != saved
        val pos = editor.selectionStart.coerceAtLeast(0).coerceAtMost(editor.text.length)
        val before = editor.text.subSequence(0, pos)
        val line = before.count { it == '\n' } + 1
        val col = pos - (before.lastIndexOf('\n') + 1) + 1
        val lines = editor.text.count { it == '\n' } + 1
        status.text = (if (dirty) "● " else "") + "${getString(R.string.editor_line)} $line:$col / $lines · ${ext.ifEmpty { "txt" }} · ${if (indentUnit == "\t") "TAB" else "${indentUnit.length}sp"} · UTF-8"
    }

    private fun highlight() {
        val text = editor.text
        text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach { text.removeSpan(it) }
        text.getSpans(0, text.length, BackgroundColorSpan::class.java).forEach { text.removeSpan(it) }
        if (text.length > 200_000 || !pref("highlight", true)) { markFind(); return }
        fun paint(r: Regex, color: Int, group: Int = 0) = r.findAll(text).forEach { m ->
            val range = m.groups[group]?.range ?: m.range
            if (!range.isEmpty()) text.setSpan(ForegroundColorSpan(color), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        when (ext) {
            "html", "htm", "xml", "svg", "vue" -> { paint(TAGS, 0xFF569CD6.toInt()); paint(ATTRS, 0xFF9CDCFE.toInt(), 1); paint(STRINGS, 0xFFCE9178.toInt()); paint(Regex("<!--[\\s\\S]*?-->"), 0xFF6A9955.toInt()) }
            "json" -> { paint(NUMBERS, 0xFFB5CEA8.toInt()); paint(STRINGS, 0xFFCE9178.toInt()); paint(JSON_KEYS, 0xFF9CDCFE.toInt()); paint(Regex("\\b(true|false|null)\\b"), 0xFF569CD6.toInt()) }
            "md", "markdown" -> { paint(MD_HEAD, 0xFFD6B36A.toInt()); paint(Regex("`[^`\\n]+`|\\*\\*[^*\\n]+\\*\\*"), 0xFFCE9178.toInt()); paint(Regex("\\[[^\\]]*\\]\\([^)]*\\)"), 0xFF569CD6.toInt()) }
            "css", "scss" -> { paint(Regex("[\\w-]+(?=\\s*:)"), 0xFF9CDCFE.toInt()); paint(NUMBERS, 0xFFB5CEA8.toInt()); paint(STRINGS, 0xFFCE9178.toInt()); paint(Regex("/\\*[\\s\\S]*?\\*/"), 0xFF6A9955.toInt()) }
            "yml", "yaml", "toml", "ini", "conf", "properties" -> { paint(Regex("^\\s*[\\w.-]+(?=\\s*[:=])", RegexOption.MULTILINE), 0xFF9CDCFE.toInt()); paint(STRINGS, 0xFFCE9178.toInt()); paint(Regex("(^|\\s)[#;].*$", RegexOption.MULTILINE), 0xFF6A9955.toInt()) }
            else -> {
                val kw = LANGS[ext] ?: LANGS.getValue("sh")
                paint(NUMBERS, 0xFFB5CEA8.toInt()); paint(Regex("\\b($kw)\\b"), 0xFFC586C0.toInt())
                paint(Regex("\\b([A-Za-z_]\\w*)(?=\\()"), 0xFFDCDCAA.toInt(), 1)
                if (ext == "sh" || ext == "") paint(Regex("\\$\\{?\\w+\\}?"), 0xFF9CDCFE.toInt())
                paint(STRINGS, 0xFFCE9178.toInt())
                val comment = when (ext) { "py", "sh", "rb", "" -> "(^|\\s)#.*$"; "lua" -> "--.*$"; else -> "//.*$|/\\*[\\s\\S]*?\\*/" }
                paint(Regex(comment, RegexOption.MULTILINE), 0xFF6A9955.toInt())
            }
        }
        markFind()
    }

    private fun findRegex(): Regex? {
        val q = findField.text.toString(); if (q.isEmpty()) return null
        val opts = if (caseBox.isChecked) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return runCatching { Regex(if (regexBox.isChecked) q else Regex.escape(q), opts) }.getOrNull()
    }

    private fun markFind() {
        if (findBar.visibility != View.VISIBLE) return
        val r = findRegex() ?: return
        val t = editor.text; var n = 0
        r.findAll(t).take(5000).forEach { if (!it.range.isEmpty()) { n++; t.setSpan(BackgroundColorSpan(0x66D6B36A), it.range.first, it.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) } }
        status.text = "🔍 $n · " + status.text.toString().substringAfter(" · ", status.text.toString())
    }

    private fun toggleFind() {
        findBar.visibility = if (findBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (findBar.visibility == View.VISIBLE) {
            val sel = editor.text.substring(editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0))
            if (sel.isNotEmpty() && !sel.contains('\n')) findField.setText(sel)
            findField.requestFocus()
        }
        highlight()
    }

    private fun findStep(forward: Boolean) {
        val r = findRegex() ?: return
        val all = r.findAll(editor.text).filter { !it.range.isEmpty() }.toList()
        if (all.isEmpty()) { Toast.makeText(this, R.string.search_none, Toast.LENGTH_SHORT).show(); return }
        val m = if (forward) all.firstOrNull { it.range.first >= editor.selectionEnd } ?: all.first()
        else all.lastOrNull { it.range.last < editor.selectionStart } ?: all.last()
        editor.requestFocus(); editor.setSelection(m.range.first, m.range.last + 1)
        status.text = "🔍 ${all.indexOf(m) + 1}/${all.size}"
    }

    private fun replaceOne() {
        val r = findRegex() ?: return
        val a = editor.selectionStart; val b = editor.selectionEnd
        val sel = editor.text.substring(a, b)
        if (a != b && r.matchEntire(sel) != null) editor.text.replace(a, b, if (regexBox.isChecked) r.replace(sel, replaceField.text.toString()) else replaceField.text.toString())
        findStep(true)
    }

    private fun replaceAll() {
        val r = findRegex() ?: return
        val n = r.findAll(editor.text).count()
        val rep = replaceField.text.toString()
        editor.setText(if (regexBox.isChecked) r.replace(editor.text.toString(), rep) else r.replace(editor.text.toString(), Regex.escapeReplacement(rep)))
        Toast.makeText(this, "$n ×", Toast.LENGTH_SHORT).show()
    }

    private fun save(): Boolean {
        val f = file ?: return false
        return runCatching {
            f.parentFile?.mkdirs()
            var text = editor.text.toString()
            if (pref("trim", false)) text = text.lines().joinToString("\n") { it.trimEnd() }
            val tmp = File(f.parentFile, ".${f.name}.nibras-tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
            saved = text; updateStatus()
            Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show(); true
        }.getOrElse { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show(); false }
    }

    private fun run() {
        val first = editor.text.lineSequence().firstOrNull().orEmpty()
        when (ext) {
            "html", "htm", "md", "markdown" -> preview()
            else -> Launcher.run(this, envId, GuestPaths.runCommand(guest, first), guest.substringAfterLast('/'), keepShell = true)
        }
    }

    private fun snapshot() { undo.addLast(editor.text.toString() to editor.selectionStart); redo.clear() }

    private fun restore(s: Pair<String, Int>) {
        restoring = true
        editor.setText(s.first); editor.setSelection(s.second.coerceIn(0, s.first.length))
        restoring = false; highlight(); updateStatus()
    }

    private fun undoOnce() { val prev = undo.removeLastOrNull() ?: return; redo.addLast(editor.text.toString() to editor.selectionStart); restore(prev) }
    private fun redoOnce() { val next = redo.removeLastOrNull() ?: return; undo.addLast(editor.text.toString() to editor.selectionStart); restore(next) }

    private fun lineBounds(): IntRange {
        val t = editor.text
        val a = t.lastIndexOf('\n', (editor.selectionStart - 1).coerceAtLeast(0)).let { if (editor.selectionStart == 0) 0 else it + 1 }
        val e = t.indexOf('\n', editor.selectionEnd).let { if (it < 0) t.length else it }
        return a until e
    }

    private fun toggleComment() {
        val mark = when (ext) { "py", "sh", "rb", "yml", "yaml", "toml", "conf", "" -> "# "; "lua" -> "-- "; "html", "xml", "md" -> null; else -> "// " } ?: return
        snapshot()
        val r = lineBounds(); val block = editor.text.substring(r.first, r.last + 1)
        val lines = block.split('\n')
        val off = lines.filter { it.isNotBlank() }.all { it.trimStart().startsWith(mark.trim()) }
        val out = lines.joinToString("\n") { l ->
            val ind = l.takeWhile { it == ' ' || it == '\t' }
            if (l.isBlank()) l else if (off) ind + l.trimStart().removePrefix(mark).removePrefix(mark.trim()) else ind + mark + l.trimStart()
        }
        restoring = true; editor.text.replace(r.first, r.last + 1, out); restoring = false
    }

    private fun indentLines(add: Boolean) {
        snapshot()
        val r = lineBounds(); val block = editor.text.substring(r.first, r.last + 1)
        val out = block.split('\n').joinToString("\n") { if (add) indentUnit + it else it.removePrefix(indentUnit).removePrefix("\t") }
        restoring = true; editor.text.replace(r.first, r.last + 1, out); restoring = false
    }

    private fun recent(): List<String> = prefs.getString("recent", "")!!.split('\n').filter { it.isNotBlank() }
    private fun remember(p: String) = prefs.edit().putString("recent", (listOf("$envId\t$p") + recent().filter { it != "$envId\t$p" }).take(20).joinToString("\n")).apply()

    private fun preview() {
        val html = if (ext == "md" || ext == "markdown") markdown(editor.text.toString()) else editor.text.toString()
        val web = WebView(this).apply { settings.javaScriptEnabled = true; settings.allowFileAccess = true }
        val base = file?.parentFile?.let { "file://${it.absolutePath}/" }
        web.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
        AlertDialog.Builder(this).setView(web).setPositiveButton(R.string.ok, null).show().window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun markdown(md: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun inline(s: String) = esc(s).replace(Regex("`([^`]+)`"), "<code>$1</code>").replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")
            .replace(Regex("\\*([^*]+)\\*"), "<i>$1</i>").replace(Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)"), "<img alt=\"$1\" src=\"$2\" style=\"max-width:100%\">")
            .replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
        val sb = StringBuilder("<html><body style='background:#0b0f14;color:#ededed;font-family:sans-serif;padding:12px'><style>a{color:#d6b36a}code,pre{background:#151b22;padding:2px 4px;border-radius:4px}</style>")
        var inCode = false; var inList = false
        md.lines().forEach { l ->
            if (l.trimStart().startsWith("```")) { sb.append(if (inCode) "</pre>" else "<pre>"); inCode = !inCode; return@forEach }
            if (inCode) { sb.append(esc(l)).append('\n'); return@forEach }
            val li = Regex("^\\s*([-*+]|\\d+\\.) (.*)").find(l)
            if (li != null) { if (!inList) { sb.append("<ul>"); inList = true }; sb.append("<li>").append(inline(li.groupValues[2])).append("</li>"); return@forEach }
            if (inList) { sb.append("</ul>"); inList = false }
            val h = Regex("^(#{1,6}) (.*)").find(l)
            when {
                h != null -> sb.append("<h${h.groupValues[1].length}>${inline(h.groupValues[2])}</h${h.groupValues[1].length}>")
                l.startsWith(">") -> sb.append("<blockquote>${inline(l.removePrefix(">"))}</blockquote>")
                l.isBlank() -> sb.append("<p></p>")
                else -> sb.append(inline(l)).append("<br>")
            }
        }
        return sb.append("</body></html>").toString()
    }

    private fun more() {
        val acts = mutableListOf<Pair<String, () -> Unit>>()
        acts += getString(R.string.editor_goto) to { goToLine() }
        acts += getString(R.string.editor_recent) to {
            val r = recent(); AlertDialog.Builder(this).setItems(r.map { it.substringAfter('\t') }.toTypedArray()) { _, w -> r[w].split('\t').let { open(this, it[0], it[1]) } }.show()
        }
        acts += getString(R.string.editor_open_file) to {
            val f = Ui.field(this, "/root/file.txt", guest.substringBeforeLast('/') + "/", mono = true)
            AlertDialog.Builder(this).setView(f).setPositiveButton(R.string.ok) { _, _ -> open(this, envId, f.text.toString().trim()) }.show()
        }
        acts += getString(R.string.editor_save_as) to {
            val f = Ui.field(this, guest, guest, mono = true)
            AlertDialog.Builder(this).setView(f).setPositiveButton(R.string.ok) { _, _ ->
                val p = f.text.toString().trim(); val h = HostPaths.toHost(this, envId, p) ?: return@setPositiveButton
                guest = p; file = h; remember(p); save(); title = p
            }.show()
        }
        acts += "// " + getString(R.string.editor_comment) to { toggleComment() }
        acts += "⇥ " + getString(R.string.editor_indent) to { indentLines(true) }
        acts += "⇤ " + getString(R.string.editor_outdent) to { indentLines(false) }
        acts += getString(R.string.editor_dup_line) to { snapshot(); val r = lineBounds(); val s = editor.text.substring(r.first, r.last + 1); editor.text.insert(r.last + 1, "\n$s") }
        acts += getString(R.string.editor_del_line) to { snapshot(); val r = lineBounds(); editor.text.delete(r.first, (r.last + 2).coerceAtMost(editor.text.length)) }
        acts += getString(R.string.editor_select_all) to { editor.selectAll() }
        if (ext == "json") acts += "{ } " + getString(R.string.editor_format) to {
            runCatching { val t = editor.text.toString().trim(); snapshot(); editor.setText(if (t.startsWith("[")) org.json.JSONArray(t).toString(2) else org.json.JSONObject(t).toString(2)) }
                .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
        }
        if (ext in setOf("html", "htm", "md", "markdown", "svg")) acts += "👁 " + getString(R.string.editor_preview) to { preview() }
        acts += (if (pref("wrap", false)) "☑ " else "☐ ") + getString(R.string.editor_wrap) to { setPref("wrap", !pref("wrap", false)); editor.setHorizontallyScrolling(!pref("wrap", false)) }
        acts += (if (pref("gutter", true)) "☑ " else "☐ ") + getString(R.string.editor_line_numbers) to { setPref("gutter", !pref("gutter", true)); editor.gutter = pref("gutter", true); editor.invalidate() }
        acts += (if (pref("indent", true)) "☑ " else "☐ ") + getString(R.string.editor_auto_indent) to { setPref("indent", !pref("indent", true)) }
        acts += (if (pref("autoclose", true)) "☑ " else "☐ ") + getString(R.string.editor_auto_close) to { setPref("autoclose", !pref("autoclose", true)) }
        acts += (if (pref("highlight", true)) "☑ " else "☐ ") + getString(R.string.editor_highlight) to { setPref("highlight", !pref("highlight", true)); highlight() }
        acts += (if (pref("autosave", false)) "☑ " else "☐ ") + getString(R.string.editor_autosave) to { setPref("autosave", !pref("autosave", false)) }
        acts += (if (pref("trim", false)) "☑ " else "☐ ") + getString(R.string.editor_trim) to { setPref("trim", !pref("trim", false)) }
        acts += (if (!editor.isFocusable) "☑ " else "☐ ") + getString(R.string.editor_read_only) to { val ro = editor.isFocusable; editor.isFocusable = !ro; editor.isFocusableInTouchMode = !ro }
        acts += "Tab: ${prefs.getInt("tab", 4)}" to { val n = listOf(2, 4, 8); prefs.edit().putInt("tab", n[(n.indexOf(prefs.getInt("tab", 4)) + 1) % 3]).apply(); updateStatus() }
        acts += "A+" to { fontBy(1f) }
        acts += "A−" to { fontBy(-1f) }
        acts += getString(R.string.editor_open_terminal) to { Launcher.run(this, envId, "cd '${guest.substringBeforeLast('/').ifEmpty { "/" }.replace("'", "'\\''")}' && exec \${SHELL:-sh} -l", guest.substringBeforeLast('/')) }
        acts += getString(R.string.files_title) to { FilesActivity.open(this, envId, guest.substringBeforeLast('/')) }
        acts += getString(R.string.editor_reload) to { load() }
        AlertDialog.Builder(this).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun fontBy(d: Float) {
        val size = (editor.textSize / resources.displayMetrics.scaledDensity + d).coerceIn(8f, 32f)
        editor.textSize = size; prefs.edit().putFloat("size", size).apply(); editor.updateGutter()
    }

    private fun goToLine() {
        val f = Ui.field(this, "1").apply { inputType = EditorInfo.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle(R.string.editor_goto).setView(f).setPositiveButton(R.string.ok) { _, _ ->
            val n = f.text.toString().toIntOrNull() ?: return@setPositiveButton
            val lines = editor.text.split('\n'); var off = 0
            for (k in 0 until (n - 1).coerceIn(0, lines.size - 1)) off += lines[k].length + 1
            editor.requestFocus(); editor.setSelection(off.coerceAtMost(editor.text.length))
        }.show()
    }

    override fun onPause() {
        super.onPause()
        if (!::editor.isInitialized) return
        prefs.edit().putInt("pos:$guest", editor.selectionStart).apply()
        if (pref("autosave", false) && editor.text.toString() != saved) save()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (findBar.visibility == View.VISIBLE) { toggleFind(); return }
        if (editor.text.toString() == saved) { super.onBackPressed(); return }
        AlertDialog.Builder(this).setMessage(R.string.editor_unsaved)
            .setPositiveButton(R.string.editor_save) { _, _ -> if (save()) finish() }
            .setNeutralButton(R.string.editor_discard) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null).show()
    }
}

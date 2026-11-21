package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.nibras.terminal.core.GuestPaths
import com.nibras.terminal.core.Lang
import com.nibras.terminal.ui.HostPaths
import com.nibras.terminal.ui.Ui
import java.io.File

/** Built-in code editor (AndroidIDE / Acode style): open, save, find & replace, undo, highlight, run. */
class EditorActivity : Activity() {
    companion object {
        const val EXTRA_PATH = "path"    // guest path
        const val EXTRA_ENV = "env"      // environment id or name
        private val KEYWORDS = Regex("\\b(if|then|else|elif|fi|for|while|do|done|case|esac|function|return|in|def|class|import|from|as|with|try|except|finally|raise|lambda|fun|val|var|object|when|is|package|public|private|static|void|int|const|let|async|await|new|this|true|false|null|None|True|False|echo|export|local)\\b")
        private val STRINGS = Regex("\"(\\\\.|[^\"\\\\])*\"|'[^'\\n]*'")
        private val COMMENTS = Regex("(^|\\s)(#|//).*$", RegexOption.MULTILINE)
        private val NUMBERS = Regex("\\b\\d+(\\.\\d+)?\\b")

        fun open(context: Context, env: String?, guestPath: String) = context.startActivity(
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_ENV, env).putExtra(EXTRA_PATH, guestPath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
        )
    }

    private lateinit var envId: String
    private lateinit var guest: String
    private var file: File? = null
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private var saved = ""
    private val undo = ArrayDeque<String>()
    private var restoring = false
    private var wrap = false
    private val main = Handler(Looper.getMainLooper())
    private val highlighter = Runnable { highlight() }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        envId = HostPaths.envId(this, intent.getStringExtra(EXTRA_ENV))
        guest = intent.getStringExtra(EXTRA_PATH) ?: "/root/untitled.txt"
        file = HostPaths.toHost(this, envId, guest)
        val root = Ui.screen(this, "✎ " + guest.substringAfterLast('/'))
        root.addView(Ui.row(this,
            Ui.button(this, getString(R.string.editor_save), accent = true) { save() },
            Ui.button(this, "▶ " + getString(R.string.editor_run)) { save(); run() },
            Ui.button(this, "↶") { undoOnce() },
            Ui.button(this, "🔍") { find() },
            Ui.button(this, "⋯") { more() },
        ), Ui.lp(this, 2))
        editor = EditText(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 14f; setTextColor(Ui.INK); gravity = Gravity.TOP or Gravity.START
            setBackgroundColor(Ui.BG); setHorizontallyScrolling(true); isSingleLine = false
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = Ui.label(this, guest).apply { setPadding(Ui.dp(context, 12), Ui.dp(context, 4), Ui.dp(context, 12), Ui.dp(context, 4)) }
        root.addView(status)
        val f = file
        val text = when {
            f == null -> { Toast.makeText(this, R.string.editor_bad_path, Toast.LENGTH_LONG).show(); "" }
            !f.exists() -> ""
            f.length() > 4_000_000 -> { Toast.makeText(this, R.string.editor_too_big, Toast.LENGTH_LONG).show(); finish(); return }
            else -> runCatching { f.readText() }.getOrElse { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show(); "" }
        }
        saved = text
        editor.setText(text)
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                if (!restoring) { undo.addLast(s.toString()); if (undo.size > 100) undo.removeFirst() }
            }
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) { updateStatus(); main.removeCallbacks(highlighter); main.postDelayed(highlighter, 400) }
        })
        updateStatus(); highlight()
    }

    private fun updateStatus() {
        val dirty = editor.text.toString() != saved
        val pos = editor.selectionStart.coerceAtLeast(0)
        val line = editor.text.substring(0, pos.coerceAtMost(editor.text.length)).count { it == '\n' } + 1
        status.text = (if (dirty) "● " else "") + "$guest · ${getString(R.string.editor_line)} $line · ${editor.lineCount}"
    }

    private fun highlight() {
        val text = editor.text
        if (text.length > 80_000) return
        text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach { text.removeSpan(it) }
        fun paint(r: Regex, color: Int, group: Int = 0) = r.findAll(text).forEach { m ->
            val range = m.groups[group]?.range ?: m.range
            val start = if (group == 0 && r == COMMENTS) m.value.indexOfFirst { it == '#' || it == '/' }.let { m.range.first + it } else range.first
            text.setSpan(ForegroundColorSpan(color), start, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        paint(NUMBERS, 0xFFB5CEA8.toInt()); paint(KEYWORDS, 0xFFC586C0.toInt()); paint(STRINGS, 0xFFCE9178.toInt()); paint(COMMENTS, 0xFF6A9955.toInt())
    }

    private fun save(): Boolean {
        val f = file ?: return false
        return runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, ".${f.name}.nibras-tmp")
            tmp.writeText(editor.text.toString())
            if (!tmp.renameTo(f)) { f.writeText(editor.text.toString()); tmp.delete() }
            saved = editor.text.toString(); updateStatus()
            Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show(); true
        }.getOrElse { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show(); false }
    }

    private fun run() {
        val first = editor.text.lineSequence().firstOrNull().orEmpty()
        Launcher.run(this, envId, GuestPaths.runCommand(guest, first), guest.substringAfterLast('/'), keepShell = true)
    }

    private fun undoOnce() {
        val prev = undo.removeLastOrNull() ?: return
        restoring = true
        val cur = editor.selectionStart
        editor.setText(prev); editor.setSelection(cur.coerceIn(0, prev.length))
        restoring = false
    }

    private fun find() {
        val q = Ui.field(this, getString(R.string.search_hint))
        val r = Ui.field(this, getString(R.string.editor_replace_with))
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0); addView(q); addView(r) }
        AlertDialog.Builder(this).setView(form)
            .setPositiveButton(R.string.editor_find_next) { _, _ ->
                val s = q.text.toString(); if (s.isEmpty()) return@setPositiveButton
                val text = editor.text.toString()
                var i = text.indexOf(s, editor.selectionEnd, ignoreCase = true)
                if (i < 0) i = text.indexOf(s, 0, ignoreCase = true)
                if (i >= 0) { editor.requestFocus(); editor.setSelection(i, i + s.length) } else Toast.makeText(this, R.string.search_none, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.editor_replace_all) { _, _ ->
                val s = q.text.toString(); if (s.isEmpty()) return@setNeutralButton
                val n = Regex(Regex.escape(s)).findAll(editor.text).count()
                editor.setText(editor.text.toString().replace(s, r.text.toString()))
                Toast.makeText(this, "$n ×", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun more() {
        val items = arrayOf(getString(R.string.editor_goto), getString(R.string.editor_wrap), "A+", "A−", getString(R.string.editor_open_terminal), getString(R.string.editor_reload))
        AlertDialog.Builder(this).setItems(items) { _, w ->
            when (w) {
                0 -> { val f = Ui.field(this, "1").apply { inputType = EditorInfo.TYPE_CLASS_NUMBER }
                    AlertDialog.Builder(this).setView(f).setPositiveButton(R.string.ok) { _, _ ->
                        val n = f.text.toString().toIntOrNull() ?: return@setPositiveButton
                        val lines = editor.text.split('\n'); var off = 0
                        for (k in 0 until (n - 1).coerceIn(0, lines.size - 1)) off += lines[k].length + 1
                        editor.requestFocus(); editor.setSelection(off.coerceAtMost(editor.text.length))
                    }.show() }
                1 -> { wrap = !wrap; editor.setHorizontallyScrolling(!wrap) }
                2 -> editor.textSize = (editor.textSize / resources.displayMetrics.scaledDensity + 1).coerceAtMost(30f)
                3 -> editor.textSize = (editor.textSize / resources.displayMetrics.scaledDensity - 1).coerceAtLeast(8f)
                4 -> Launcher.run(this, envId, "cd '${guest.substringBeforeLast('/').ifEmpty { "/" }.replace("'", "'\\''")}' && exec \${SHELL:-sh} -l", guest.substringBeforeLast('/'))
                5 -> file?.takeIf { it.exists() }?.let { saved = it.readText(); editor.setText(saved) }
            }
        }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (editor.text.toString() == saved) { super.onBackPressed(); return }
        AlertDialog.Builder(this).setMessage(R.string.editor_unsaved)
            .setPositiveButton(R.string.editor_save) { _, _ -> if (save()) finish() }
            .setNeutralButton(R.string.editor_discard) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null).show()
    }
}

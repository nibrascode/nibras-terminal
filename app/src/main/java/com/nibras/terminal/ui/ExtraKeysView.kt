package com.nibras.terminal.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Termux-like extra keys rows. CTRL / ALT / SHIFT / FN are sticky modifiers consumed by the
 * next key (also by soft-keyboard input through [com.termux.view.TerminalViewClient]).
 * Holding a key repeats it.
 */
@SuppressLint("ViewConstructor")
class ExtraKeysView(context: Context, private val onKey: (String) -> Unit, private val onModifiersChanged: () -> Unit) :
    LinearLayout(context) {

    companion object {
        val DEFAULT = listOf(
            listOf("ESC", "/", "-", "HOME", "UP", "END", "PGUP"),
            listOf("TAB", "CTRL", "ALT", "LEFT", "DOWN", "RIGHT", "PGDN"),
        )
        private val LABELS = mapOf(
            "UP" to "↑", "DOWN" to "↓", "LEFT" to "←", "RIGHT" to "→",
            "BKSP" to "⌫", "ENTER" to "↵", "KBD" to "⌨", "DRAWER" to "☰",
            "PGUP" to "PGUP", "PGDN" to "PGDN",
        )
        val MODIFIERS = setOf("CTRL", "ALT", "SHIFT", "FN")
        private val REPEATABLE = setOf("UP", "DOWN", "LEFT", "RIGHT", "BKSP", "DEL", "PGUP", "PGDN")
    }

    var ctrl = false; private set
    var alt = false; private set
    var shift = false; private set
    var fn = false; private set
    private val modifierViews = mutableMapOf<String, TextView>()
    var keyColor: Int = Color.WHITE
        set(value) { field = value; refresh() }

    init {
        orientation = VERTICAL
        setRows(DEFAULT)
    }

    fun setRows(rows: List<List<String>>) {
        removeAllViews()
        modifierViews.clear()
        val height = (36 * resources.displayMetrics.density).toInt()
        for (row in rows) {
            val line = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (key in row) {
                val view = TextView(context).apply {
                    text = if (key.startsWith("MACRO:")) key.substringAfterLast('|') else LABELS[key] ?: key
                    gravity = Gravity.CENTER
                    typeface = Typeface.MONOSPACE
                    textSize = 13f
                    setTextColor(keyColor)
                    isClickable = true
                    isFocusable = false
                }
                attach(view, key)
                if (key in MODIFIERS) modifierViews[key] = view
                line.addView(view, LayoutParams(0, height, 1f))
            }
            addView(line, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        refresh()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(view: TextView, key: String) {
        var fired = false
        val repeat = object : Runnable {
            override fun run() {
                fired = true
                onKey(key)
                view.postDelayed(this, 60)
            }
        }
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.setBackgroundColor(0x44FFFFFF)
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    fired = false
                    if (key in REPEATABLE) v.postDelayed(repeat, 400)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    v.setBackgroundColor(Color.TRANSPARENT)
                    v.removeCallbacks(repeat)
                    if (key in MODIFIERS) toggle(key) else if (!fired) onKey(key)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.setBackgroundColor(Color.TRANSPARENT)
                    v.removeCallbacks(repeat)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggle(key: String) {
        when (key) {
            "CTRL" -> ctrl = !ctrl
            "ALT" -> alt = !alt
            "SHIFT" -> shift = !shift
            "FN" -> fn = !fn
        }
        refresh()
        onModifiersChanged()
    }

    fun readCtrl(): Boolean = ctrl.also { if (it) { ctrl = false; refresh() } }
    fun readAlt(): Boolean = alt.also { if (it) { alt = false; refresh() } }
    fun readShift(): Boolean = shift.also { if (it) { shift = false; refresh() } }
    fun readFn(): Boolean = fn.also { if (it) { fn = false; refresh() } }

    private fun refresh() {
        for (i in 0 until childCount) {
            val row = getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) (row.getChildAt(j) as? TextView)?.setTextColor(keyColor)
        }
        val on = 0xFF4FC3F7.toInt()
        modifierViews["CTRL"]?.setTextColor(if (ctrl) on else keyColor)
        modifierViews["ALT"]?.setTextColor(if (alt) on else keyColor)
        modifierViews["SHIFT"]?.setTextColor(if (shift) on else keyColor)
        modifierViews["FN"]?.setTextColor(if (fn) on else keyColor)
    }

    /** Android key code for a named extra key, or null for literal text. */
    fun keyCode(key: String): Int? = when (key) {
        "ESC" -> KeyEvent.KEYCODE_ESCAPE
        "TAB" -> KeyEvent.KEYCODE_TAB
        "UP" -> KeyEvent.KEYCODE_DPAD_UP
        "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
        "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
        "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "HOME" -> KeyEvent.KEYCODE_MOVE_HOME
        "END" -> KeyEvent.KEYCODE_MOVE_END
        "PGUP" -> KeyEvent.KEYCODE_PAGE_UP
        "PGDN" -> KeyEvent.KEYCODE_PAGE_DOWN
        "DEL" -> KeyEvent.KEYCODE_FORWARD_DEL
        "INS" -> KeyEvent.KEYCODE_INSERT
        "BKSP" -> KeyEvent.KEYCODE_DEL
        "ENTER" -> KeyEvent.KEYCODE_ENTER
        "SPACE" -> KeyEvent.KEYCODE_SPACE
        in (1..12).map { "F$it" } -> KeyEvent.KEYCODE_F1 + key.removePrefix("F").toInt() - 1
        else -> null
    }
}

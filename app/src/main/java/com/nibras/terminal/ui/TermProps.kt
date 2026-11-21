package com.nibras.terminal.ui

import android.content.Context
import com.nibras.terminal.Automation
import com.nibras.terminal.core.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Properties

/**
 * ~/.nibras/nibras.properties (or Termux's ~/.termux/termux.properties): the same keys Termux reads.
 * Values in the file win over the Settings screen, exactly like Termux; reload with nibras-reload-settings.
 */
object TermProps {
    var props = Properties(); private set
    var bell = "vibrate"; private set
    var marginH = 3; private set
    var marginV = 0; private set
    var blinkRate = 500; private set
    var hideKeyboardOnStart = false; private set
    var transcriptRows = 0; private set
    var urlOnClick = false; private set
    var disableHwShortcuts = false; private set
    var workingDir: String? = null; private set
    var shortcutKeys = mapOf<String, String>(); private set
    var extraKeys: List<List<String>>? = null; private set

    fun file(home: File?): File? = home?.let { h ->
        listOf(File(h, ".nibras/nibras.properties"), File(h, ".termux/termux.properties"), File(h, ".config/termux/termux.properties")).firstOrNull { it.isFile }
    }

    fun load(context: Context, home: File?) {
        val p = Properties()
        file(home)?.let { f -> runCatching { f.reader().use { p.load(it) } } }
        props = p
        val prefs = Prefs(context)
        fun get(k: String) = p.getProperty(k)?.trim()?.takeIf { it.isNotEmpty() }
        fun bool(k: String) = get(k)?.lowercase()?.let { it == "true" || it == "1" || it == "yes" }
        bool("fullscreen")?.let { prefs.fullscreen = it }
        get("back-key")?.let { prefs.backIsEscape = it == "escape" }
        get("volume-keys")?.let { prefs.volumeKeys = it != "volume" }
        bell = get("bell-character") ?: if (prefs.bellVibrate) "vibrate" else "ignore"
        get("terminal-cursor-style")?.let { prefs.cursorStyle = when (it) { "underline" -> 1; "bar" -> 2; else -> 0 } }
        blinkRate = get("terminal-cursor-blink-rate")?.toIntOrNull()?.coerceIn(0, 2000) ?: 500
        if (get("terminal-cursor-blink-rate") != null) prefs.cursorBlink = blinkRate >= 100
        marginH = get("terminal-margin-horizontal")?.toIntOrNull()?.coerceIn(0, 100) ?: 3
        marginV = get("terminal-margin-vertical")?.toIntOrNull()?.coerceIn(0, 100) ?: 0
        hideKeyboardOnStart = bool("hide-soft-keyboard-on-startup") ?: false
        transcriptRows = get("terminal-transcript-rows")?.toIntOrNull()?.coerceIn(100, 50000) ?: 0
        urlOnClick = bool("terminal-onclick-url-open") ?: false
        disableHwShortcuts = bool("disable-hardware-keyboard-shortcuts") ?: false
        workingDir = get("default-working-directory")
        bool("allow-external-apps")?.let { Automation.set(context, it) }
        bool("use-fullscreen-workaround")
        shortcutKeys = listOf("create-session", "next-session", "previous-session", "rename-session")
            .mapNotNull { k -> get("shortcut.$k")?.let { k to it.lowercase().replace(" ", "") } }.toMap()
        extraKeys = get("extra-keys")?.let { parseExtraKeys(it) }
        bool("extra-keys-hidden")?.let { prefs.extraKeys = !it }
    }

    private val ALIASES = mapOf(
        "ESCAPE" to "ESC", "CONTROL" to "CTRL", "KEYBOARD" to "KBD", "BACKSPACE" to "BKSP", "DELETE" to "DEL",
        "PAGEUP" to "PGUP", "PAGE_UP" to "PGUP", "PAGEDOWN" to "PGDN", "PAGE_DOWN" to "PGDN", "INSERT" to "INS",
        "BACKSLASH" to "\\", "QUOTE" to "\"", "APOSTROPHE" to "'", "LT" to "LEFT", "RT" to "RIGHT",
        "TERMINAL" to "DRAWER", "SCROLL" to "PGUP",
    )

    private fun norm(k: String) = ALIASES[k.uppercase()] ?: if (k.length > 1 && k.uppercase() == k) k else k

    /** Termux format: [['ESC','/','-'],[{key: 'CTRL', popup: 'ALT'}, {macro: 'CTRL d', display: 'exit'}]] */
    fun parseExtraKeys(text: String): List<List<String>>? = runCatching {
        val json = text.replace(Regex("""(?<=[{,\s])(key|popup|macro|display)\s*:"""), "\"$1\":").replace('\'', '"')
        val rows = JSONArray(json)
        (0 until rows.length()).map { r ->
            val row = rows.getJSONArray(r)
            (0 until row.length()).map { i ->
                when (val v = row.get(i)) {
                    is JSONObject -> when {
                        v.has("macro") -> "MACRO:" + v.getString("macro") + "|" + v.optString("display", v.getString("macro"))
                        v.has("key") -> norm(v.getString("key")).let { k -> if (v.has("display")) "MACRO:$k|" + v.getString("display") else k }
                        else -> "?"
                    }
                    else -> norm(v.toString())
                }
            }
        }.filter { it.isNotEmpty() }
    }.getOrNull()

    /** A key combo like "ctrl+t" matches the event? */
    fun matches(combo: String?, ctrl: Boolean, alt: Boolean, shift: Boolean, char: Char): Boolean {
        if (combo == null) return false
        val parts = combo.split('+')
        val key = parts.last()
        return key.length == 1 && key[0] == char.lowercaseChar() && ("ctrl" in parts) == ctrl && ("alt" in parts) == alt && ("shift" in parts) == shift
    }
}

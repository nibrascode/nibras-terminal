package com.nibras.terminal.ui

import android.graphics.Typeface
import com.termux.terminal.TerminalColors
import java.io.File
import java.util.Properties

/** Built-in color schemes (Termux:Styling equivalent) plus ~/.nibras/colors.properties. */
object Themes {
    val names = linkedMapOf(
        "nibras" to "Nibras (black)",
        "termux" to "Termux",
        "solarized-dark" to "Solarized Dark",
        "solarized-light" to "Solarized Light",
        "dracula" to "Dracula",
        "gruvbox" to "Gruvbox Dark",
        "nord" to "Nord",
        "monokai" to "Monokai",
        "one-dark" to "One Dark",
        "tokyo-night" to "Tokyo Night",
        "catppuccin" to "Catppuccin Mocha",
        "catppuccin-latte" to "Catppuccin Latte",
        "nibras-gold" to "Nibras green & gold",
        "matrix" to "Matrix",
        "custom" to "Custom (~/.nibras/colors.properties)",
        "light" to "Light",
        "green" to "Green on black",
    )

    private fun scheme(bg: String, fg: String, cursor: String, palette: String): Properties {
        val p = Properties()
        p["background"] = bg
        p["foreground"] = fg
        p["cursor"] = cursor
        palette.split(' ').filter { it.isNotBlank() }.forEachIndexed { i, c -> p["color$i"] = c }
        return p
    }

    fun properties(name: String): Properties = when (name) {
        "termux" -> Properties()
        "solarized-dark" -> scheme("#002b36", "#839496", "#93a1a1",
            "#073642 #dc322f #859900 #b58900 #268bd2 #d33682 #2aa198 #eee8d5 #002b36 #cb4b16 #586e75 #657b83 #839496 #6c71c4 #93a1a1 #fdf6e3")
        "solarized-light" -> scheme("#fdf6e3", "#657b83", "#586e75",
            "#073642 #dc322f #859900 #b58900 #268bd2 #d33682 #2aa198 #eee8d5 #002b36 #cb4b16 #586e75 #657b83 #839496 #6c71c4 #93a1a1 #fdf6e3")
        "dracula" -> scheme("#282a36", "#f8f8f2", "#f8f8f2",
            "#21222c #ff5555 #50fa7b #f1fa8c #bd93f9 #ff79c6 #8be9fd #f8f8f2 #6272a4 #ff6e6e #69ff94 #ffffa5 #d6acff #ff92df #a4ffff #ffffff")
        "gruvbox" -> scheme("#282828", "#ebdbb2", "#ebdbb2",
            "#282828 #cc241d #98971a #d79921 #458588 #b16286 #689d6a #a89984 #928374 #fb4934 #b8bb26 #fabd2f #83a598 #d3869b #8ec07c #ebdbb2")
        "nord" -> scheme("#2e3440", "#d8dee9", "#d8dee9",
            "#3b4252 #bf616a #a3be8c #ebcb8b #81a1c1 #b48ead #88c0d0 #e5e9f0 #4c566a #bf616a #a3be8c #ebcb8b #81a1c1 #b48ead #8fbcbb #eceff4")
        "monokai" -> scheme("#272822", "#f8f8f2", "#f8f8f0",
            "#272822 #f92672 #a6e22e #f4bf75 #66d9ef #ae81ff #a1efe4 #f8f8f2 #75715e #f92672 #a6e22e #f4bf75 #66d9ef #ae81ff #a1efe4 #f9f8f5")
        "one-dark" -> scheme("#282c34", "#abb2bf", "#528bff",
            "#282c34 #e06c75 #98c379 #e5c07b #61afef #c678dd #56b6c2 #abb2bf #5c6370 #e06c75 #98c379 #e5c07b #61afef #c678dd #56b6c2 #ffffff")
        "tokyo-night" -> scheme("#1a1b26", "#c0caf5", "#c0caf5",
            "#15161e #f7768e #9ece6a #e0af68 #7aa2f7 #bb9af7 #7dcfff #a9b1d6 #414868 #f7768e #9ece6a #e0af68 #7aa2f7 #bb9af7 #7dcfff #c0caf5")
        "light" -> scheme("#ffffff", "#1e1e1e", "#1e1e1e",
            "#000000 #c91b00 #00a600 #a68b00 #0225c7 #b42ab4 #00a6b2 #bfbfbf #686868 #ff6e67 #3fbf3f #c7b000 #6871ff #ff77ff #5fd7d7 #ffffff")
        "green" -> scheme("#000000", "#33ff66", "#33ff66", "")
        "catppuccin" -> scheme("#1e1e2e", "#cdd6f4", "#f5e0dc",
            "#45475a #f38ba8 #a6e3a1 #f9e2af #89b4fa #f5c2e7 #94e2d5 #bac2de #585b70 #f38ba8 #a6e3a1 #f9e2af #89b4fa #f5c2e7 #94e2d5 #a6adc8")
        "catppuccin-latte" -> scheme("#eff1f5", "#4c4f69", "#dc8a78",
            "#5c5f77 #d20f39 #40a02b #df8e1d #1e66f5 #ea76cb #179299 #acb0be #6c6f85 #d20f39 #40a02b #df8e1d #1e66f5 #ea76cb #179299 #bcc0cc")
        "nibras-gold" -> scheme("#06120c", "#e8f5e9", "#d6b36a",
            "#0d1f16 #e05d5d #3ddc84 #d6b36a #4fa3e0 #c792ea #4dd0c4 #cfd8dc #2e4a3c #ff7b7b #69f0ae #f2d48f #82c4ff #e1acff #84ffff #ffffff")
        "matrix" -> scheme("#000800", "#00ff41", "#00ff41",
            "#001a00 #008f11 #00ff41 #33ff33 #00b32c #39ff14 #66ff66 #a0ffa0 #003b00 #00c21f #5bff5b #8cff8c #00e03a #6bff6b #b3ffb3 #e0ffe0")
        "custom" -> Properties()
        else -> scheme("#000000", "#e6e6e6", "#d6b36a", "")
    }

    /** Applies the scheme globally; returns the background color for surrounding views. */
    fun apply(name: String, home: File?): Int {
        val props = Properties()
        props.putAll(properties(name))
        userFile(home, "colors.properties")?.let { f -> runCatching { f.inputStream().use { props.load(it) } } }
        TerminalColors.COLOR_SCHEME.updateWith(props)
        return TerminalColors.COLOR_SCHEME.mDefaultColors[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND]
    }

    fun typeface(home: File?): Typeface =
        userFile(home, "font.ttf")?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() } ?: Typeface.MONOSPACE

    fun extraKeys(home: File?): List<List<String>>? {
        val f = userFile(home, "extra-keys") ?: return null
        val rows = f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { row -> row.split(Regex("\\s+")) }
        return rows.takeIf { it.isNotEmpty() }
    }

    /** Background picture: ~/.nibras/background.jpg|png|webp (set with the `wallpaper` command). */
    fun backgroundImage(home: File?): File? = listOf("jpg", "png", "webp", "jpeg").firstNotNullOfOrNull { userFile(home, "background.$it") }

    /** ~/.nibras/<name>, falling back to ~/.termux/<name> for Termux users. */
    fun userFile(home: File?, name: String): File? {
        if (home == null) return null
        return listOf(File(home, ".nibras/$name"), File(home, ".termux/$name")).firstOrNull { it.isFile }
    }
}

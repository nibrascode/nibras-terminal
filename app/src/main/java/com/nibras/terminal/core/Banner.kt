package com.nibras.terminal.core

import android.content.Context
import com.nibras.terminal.R

/**
 * The "NIBRAS TERMINAL" logo shown at the start of every new session.
 * Option 2: a smooth horizontal blue → green gradient. No rainbow hues.
 */
object Banner {
    private const val RESET = "\u001b[0m"
    private const val DIM = "\u001b[38;2;140;150;170m"
    private const val BLUE_R = 40
    private const val BLUE_G = 120
    private const val BLUE_B = 255
    private const val GREEN_R = 30
    private const val GREEN_G = 210
    private const val GREEN_B = 90

    val NIBRAS = listOf(
        " _   _ ___ ____  ____      _    ____  ",
        "| \\ | |_ _| __ )|  _ \\    / \\  / ___| ",
        "|  \\| || ||  _ \\| |_) |  / _ \\ \\___ \\ ",
        "| |\\  || || |_) |  _ <  / ___ \\ ___) |",
        "|_| \\_|___|____/|_| \\_\\/_/   \\_\\____/ ",
    )

    val TERMINAL = listOf(
        " _____ _____ ____  __  __ ___ _   _    _    _     ",
        "|_   _| ____|  _ \\|  \\/  |_ _| \\ | |  / \\  | |    ",
        "  | | |  _| | |_) | |\\/| || ||  \\| | / _ \\ | |    ",
        "  | | | |___|  _ <| |  | || || |\\  |/ ___ \\| |___ ",
        "  |_| |_____|_| \\_\\_|  |_|___|_| \\_/_/   \\_\\_____|",
    )

    /** Paint one line with a blue (left) → green (right) gradient. */
    fun paintLine(s: String): String {
        if (s.isEmpty()) return ""
        val n = s.length
        return buildString {
            s.forEachIndexed { i, ch ->
                if (ch == ' ') {
                    append(' ')
                } else {
                    val t = if (n > 1) i.toFloat() / (n - 1) else 0f
                    val r = (BLUE_R + (GREEN_R - BLUE_R) * t).toInt()
                    val g = (BLUE_G + (GREEN_G - BLUE_G) * t).toInt()
                    val b = (BLUE_B + (GREEN_B - BLUE_B) * t).toInt()
                    append("\u001b[1;38;2;").append(r).append(';').append(g).append(';').append(b).append('m')
                    append(ch)
                }
            }
            append(RESET)
        }
    }

    /**
     * Static fallback used if the width-aware `nibras-banner` script cannot run.
     * Medium layout (NIBRAS figlet + "T E R M I N A L") fits a typical phone.
     */
    fun text(envName: String, abi: String, version: String): String = buildString {
        append('\n')
        NIBRAS.forEach { append(paintLine(it)).append('\n') }
        append('\n')
        append("  ").append(paintLine("T E R M I N A L")).append('\n')
        append("  ").append(DIM).append("· ").append(envName).append(" · ").append(abi)
            .append(" · ").append(version).append(RESET).append('\n')
        append('\n')
    }

    /** Motd help lines under the logo, without the redundant title line. */
    fun motdHelp(context: Context, envName: String, abi: String): String {
        val raw = runCatching { context.getString(R.string.motd, envName, abi) }
            .getOrElse { context.getString(R.string.motd) }
        val lines = raw.lines().toMutableList()
        val i = lines.indexOfFirst { it.isNotBlank() }
        if (i >= 0) lines.removeAt(i)
        return lines.joinToString("\n").trimStart()
    }
}

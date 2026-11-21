package com.nibras.terminal.core

/** The colored "Nibras Terminal" logo shown at the top of every new session (restored from 0.1). */
object Banner {
    private const val GOLD = "\u001b[1;38;2;214;179;106m"
    private const val CREAM = "\u001b[1;38;2;245;236;220m"
    private const val DIM = "\u001b[38;2;140;140;140m"
    private const val RESET = "\u001b[0m"

    val LOGO = listOf(
        " _   _ _ _                    ",
        "| \\ | (_) |__  _ __ __ _ ___ ",
        "|  \\| | | '_ \\| '__/ _` / __|",
        "| |\\  | | |_) | | | (_| \\__ \\",
        "|_| \\_|_|_.__/|_|  \\__,_|___/",
    )

    fun text(envName: String, abi: String, version: String): String = buildString {
        LOGO.forEach { append(GOLD).append(it).append(RESET).append('\n') }
        append(CREAM).append("  ▣ T E R M I N A L ").append(RESET)
        append(DIM).append("· $envName · $abi · $version").append(RESET).append("\n")
        append(GOLD).append("  ─────────────────────────────").append(RESET).append("\n")
    }
}

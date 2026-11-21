package com.nibras.terminal

/**
 * Pure checks for the 1.0.4 additions. No Android types, so the rules can be tested on their own.
 * A sentence never becomes a shell line the parser did not build from a known verb.
 */
object PlusRules {
    private val installVerbs = setOf(
        "install", "add", "qur", "yukle", "yüklə", "yükle", "kur",
        "установи", "установить", "installer", "instalar", "installe", "pasang",
    )
    private val runVerbs = setOf(
        "run", "işlət", "islet", "çalıştır", "calistir", "запусти", "запустить",
        "exécute", "ejecuta", "esegui", "jalankan",
    )
    private val themeVerbs = setOf("theme", "mövzu", "movzu", "tema", "тема", "thème", "tema")
    private val helpWords = setOf(
        "help", "kömək", "komek", "yardım", "yardim", "помощь", "aide", "hilfe", "ayuda",
        "ajuda", "aiuto", "bantuan", "帮助", "ヘルプ", "도움", "मदद", "সাহায্য", "مساعدة", "راهنما",
    )
    private val name = Regex("^[A-Za-z0-9_][A-Za-z0-9_+.-]*$")
    private val path = Regex("^[A-Za-z0-9_][A-Za-z0-9_./+-]*$")

    /** Commands an automation entry may start. Null list means "no file yet": anything except private phone commands. */
    fun allowAutomatic(command: String, list: List<String>?, sensitive: Set<String>): Boolean {
        val line = command.trim()
        if (line.isEmpty()) return false
        val tokens = line.split(Regex("\\s+"))
        if (tokens.any { bare(it) in sensitive }) return false
        if (list == null) return true
        if (list.isEmpty()) return false
        return list.any { entry ->
            val e = entry.trim()
            e.isNotEmpty() && (line == e || line.startsWith("$e ") || tokens.first() == e || bare(tokens.first()) == bare(e))
        }
    }

    fun bare(token: String) = token.removePrefix("./").removePrefix("nibras-").removePrefix("termux-")

    /**
     * Turns a sentence into existing commands, or null when any part is not recognised.
     * The caller must run only what this returns.
     */
    fun parsePhrase(raw: String): List<String>? {
        val line = raw.trim()
        if (line.isEmpty() || line.length > 240) return null
        if (line.any { it == ';' || it == '|' || it == '&' || it == '$' || it == '`' || it == '\n' || it == '\\' }) return null
        val parts = line.split(Regex("(?i)\\s*(?:,|\\bsonra\\b|\\bthen\\b|\\bпотом\\b|\\band then\\b)\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val out = ArrayList<String>(parts.size)
        for (part in parts) {
            out += clause(part) ?: return null
        }
        return out
    }

    private fun clause(part: String): String? {
        val words = part.split(Regex("\\s+")).map { it.trim().trimEnd(',') }.filter { it.isNotEmpty() }
        if (words.size == 1 && words[0].lowercase() in helpWords) return "help"
        if (words.size != 2) return null
        val a = words[0]
        val b = words[1]
        val al = a.lowercase()
        val bl = b.lowercase()
        return when {
            bl in installVerbs && safeName(a) -> "install $a"
            al in installVerbs && safeName(b) -> "install $b"
            bl in runVerbs && safePath(a) -> "run $a"
            al in runVerbs && safePath(b) -> "run $b"
            al in themeVerbs && safeName(b) -> "theme $b"
            else -> null
        }
    }

    private fun safeName(value: String) = name.matches(value) && ".." !in value && !value.startsWith("-")
    private fun safePath(value: String) = path.matches(value) && ".." !in value && !value.startsWith("/") && !value.startsWith("-")
}

package com.nibras.terminal.core

/**
 * Pure (Android-free) logic behind the Nibras tools, so it can be unit tested on the JVM:
 * record codec, saved hosts → ssh/mosh/sftp command lines, snippets, command suggestions,
 * session restore records and scrollback search.
 */
object Records {
    private fun esc(v: String) = v.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("=", "\\e")
    private fun unesc(v: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (v[i + 1]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'e' -> sb.append('='); else -> sb.append(v[i + 1])
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    fun encode(records: List<Map<String, String>>): String = records.joinToString("\n") { r ->
        r.entries.joinToString("") { "${esc(it.key)}=${esc(it.value)}\n" }
    }

    fun decode(text: String): List<Map<String, String>> {
        val out = mutableListOf<Map<String, String>>()
        var cur = linkedMapOf<String, String>()
        for (line in text.split('\n')) {
            if (line.isBlank()) { if (cur.isNotEmpty()) out += cur; cur = linkedMapOf(); continue }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            cur[unesc(line.substring(0, eq))] = unesc(line.substring(eq + 1))
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }
}

fun shQuote(value: String) = if (value.isNotEmpty() && value.all { it.isLetterOrDigit() || it in "@%+=:,./-_" }) value
    else "'" + value.replace("'", "'\\''") + "'"

/** A saved SSH / Mosh / Telnet host (Termius, JuiceSSH, ConnectBot style). */
data class Host(
    val id: String,
    val name: String,
    val host: String,
    val user: String = "",
    val port: Int = 22,
    val protocol: String = "ssh", // ssh | mosh | telnet
    val identity: String = "",     // private key path inside the environment, e.g. ~/.ssh/id_ed25519
    val jump: String = "",         // ProxyJump user@host:port
    val forwards: List<String> = emptyList(), // "L 8080:localhost:80", "R 9000:localhost:9000", "D 1080"
    val startup: String = "",      // remote command run after login
    val keepAlive: Boolean = true,
    val agent: Boolean = false,
    val group: String = "",
    val extra: String = "",        // extra raw ssh options
) {
    val target: String get() = if (user.isBlank()) host else "$user@$host"

    fun toRecord(): Map<String, String> = linkedMapOf(
        "id" to id, "name" to name, "host" to host, "user" to user, "port" to port.toString(), "protocol" to protocol,
        "identity" to identity, "jump" to jump, "forwards" to forwards.joinToString(";"), "startup" to startup,
        "keepAlive" to keepAlive.toString(), "agent" to agent.toString(), "group" to group, "extra" to extra,
    )

    companion object {
        fun fromRecord(r: Map<String, String>) = Host(
            id = r["id"].orEmpty().ifBlank { java.util.UUID.randomUUID().toString() },
            name = r["name"].orEmpty(), host = r["host"].orEmpty(), user = r["user"].orEmpty(),
            port = r["port"]?.toIntOrNull() ?: 22, protocol = r["protocol"].orEmpty().ifBlank { "ssh" },
            identity = r["identity"].orEmpty(), jump = r["jump"].orEmpty(),
            forwards = r["forwards"].orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() },
            startup = r["startup"].orEmpty(), keepAlive = r["keepAlive"] != "false", agent = r["agent"] == "true",
            group = r["group"].orEmpty(), extra = r["extra"].orEmpty(),
        )

        /** Parses "user@host:port" or "ssh://user@host:port" quick-connect text. */
        fun parseQuick(text: String): Host? {
            val t = text.trim().removePrefix("ssh://").removePrefix("mosh://")
            if (t.isEmpty() || t.contains(' ')) return null
            val user = t.substringBefore('@', "")
            val rest = t.substringAfter('@')
            val bracket = rest.startsWith("[")
            val host = if (bracket) rest.substringAfter('[').substringBefore(']') else rest.substringBeforeLast(':', rest)
            val portText = if (bracket) rest.substringAfter("]:", "") else if (rest.count { it == ':' } == 1) rest.substringAfter(':') else ""
            val port = if (portText.isEmpty()) 22 else portText.toIntOrNull() ?: return null
            if (host.isBlank() || port !in 1..65535) return null
            return Host(java.util.UUID.randomUUID().toString(), t, host, user, port)
        }
    }
}

object HostCommands {
    private fun forwardArgs(f: String): List<String> {
        val kind = f.substringBefore(' ').uppercase()
        val spec = f.substringAfter(' ', "").trim()
        if (spec.isEmpty()) return emptyList()
        return when (kind) { "L" -> listOf("-L", spec); "R" -> listOf("-R", spec); "D" -> listOf("-D", spec); else -> emptyList() }
    }

    fun sshArgs(h: Host): List<String> {
        val a = mutableListOf("ssh")
        if (h.port != 22) a += listOf("-p", h.port.toString())
        if (h.identity.isNotBlank()) a += listOf("-i", h.identity)
        if (h.jump.isNotBlank()) a += listOf("-J", h.jump)
        if (h.keepAlive) a += listOf("-o", "ServerAliveInterval=30", "-o", "ServerAliveCountMax=4")
        if (h.agent) a += "-A"
        a += listOf("-o", "StrictHostKeyChecking=accept-new")
        h.forwards.forEach { a += forwardArgs(it) }
        if (h.extra.isNotBlank()) a += h.extra.trim().split(Regex("\\s+"))
        a += h.target
        if (h.startup.isNotBlank()) { a += "-t"; a += h.startup }
        return a
    }

    /** Shell line that installs the client if needed and connects. */
    fun connectLine(h: Host): String {
        val cmd = when (h.protocol) {
            "mosh" -> buildList {
                add("mosh")
                val ssh = mutableListOf("ssh")
                if (h.port != 22) ssh += listOf("-p", h.port.toString())
                if (h.identity.isNotBlank()) ssh += listOf("-i", h.identity)
                if (h.jump.isNotBlank()) ssh += listOf("-J", h.jump)
                add("--ssh=" + ssh.joinToString(" "))
                add(h.target)
                if (h.startup.isNotBlank()) { add("--"); add("sh"); add("-c"); add(h.startup) }
            }
            "telnet" -> listOf("telnet", h.host, h.port.toString())
            else -> sshArgs(h)
        }
        val tool = when (h.protocol) { "mosh" -> "mosh"; "telnet" -> "telnet"; else -> "ssh" }
        return "nibras-need $tool && exec " + cmd.joinToString(" ") { shQuote(it) }
    }

    fun sftpLine(h: Host): String {
        val a = mutableListOf("sftp")
        if (h.port != 22) a += listOf("-P", h.port.toString())
        if (h.identity.isNotBlank()) a += listOf("-i", h.identity)
        if (h.jump.isNotBlank()) a += listOf("-J", h.jump)
        a += listOf("-o", "StrictHostKeyChecking=accept-new", h.target)
        return "nibras-need sftp && exec " + a.joinToString(" ") { shQuote(it) }
    }

    /** Copies a public key to the host (ssh-copy-id equivalent that only needs ssh). */
    fun copyIdLine(h: Host, pub: String): String {
        val a = mutableListOf("ssh")
        if (h.port != 22) a += listOf("-p", h.port.toString())
        if (h.jump.isNotBlank()) a += listOf("-J", h.jump)
        a += listOf("-o", "StrictHostKeyChecking=accept-new", h.target)
        a += "umask 077; mkdir -p ~/.ssh; cat >> ~/.ssh/authorized_keys"
        return "nibras-need ssh && " + a.joinToString(" ") { shQuote(it) } + " < " + shQuote(pub)
    }
}

/** A saved command (Termius snippets / JuiceSSH snippets). File format: one "name|command" per line. */
data class Snippet(val name: String, val command: String, val run: Boolean = true)

object Snippets {
    val defaults = listOf(
        Snippet("Update packages", "pkg upgrade"),
        Snippet("Disk usage", "df -h"),
        Snippet("Biggest files here", "du -ah . 2>/dev/null | sort -rh | head -20"),
        Snippet("My IP addresses", "ip -brief addr 2>/dev/null || ifconfig"),
        Snippet("Listening ports", "netstat -tlnp 2>/dev/null || ss -tlnp"),
        Snippet("Start SSH server", "nibras-sshd"),
        Snippet("Phone battery", "nibras-battery-status"),
        Snippet("Python HTTP server", "python3 -m http.server 8000"),
        Snippet("Git status", "git status -sb"),
        Snippet("Processes", "top"),
    )

    fun parse(text: String): List<Snippet> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || !line.contains('|')) return@mapNotNull null
        val name = line.substringBefore('|').trim()
        var cmd = line.substringAfter('|')
        val paste = cmd.endsWith("|paste")
        if (paste) cmd = cmd.removeSuffix("|paste")
        if (name.isEmpty() || cmd.isBlank()) null else Snippet(name, cmd.replace("\\n", "\n"), !paste)
    }

    fun format(list: List<Snippet>): String =
        "# Nibras snippets: name|command   (add |paste at the end to paste without running)\n" +
            list.joinToString("\n") { "${it.name.replace('|', '/')}|${it.command.replace("\n", "\\n")}" + if (it.run) "" else "|paste" } + "\n"
}

/** Command-line suggestions from history, snippets and commands on PATH (smarter than Tab alone). */
object Suggest {
    private val PROMPT_END = Regex("""^.*?[$#%>❯] """)

    /** The text the user is typing on the cursor line, after the prompt. */
    fun typed(cursorLine: String, cursorCol: Int): String {
        val upto = cursorLine.take(cursorCol.coerceIn(0, cursorLine.length))
        val m = PROMPT_END.find(upto) ?: return ""
        return upto.substring(m.range.last + 1)
    }

    fun rank(typed: String, history: List<String>, commands: Collection<String>, snippets: List<Snippet>, limit: Int = 8): List<String> {
        val t = typed.trimStart()
        if (t.isEmpty()) return history.asReversed().distinct().take(limit)
        val out = LinkedHashSet<String>()
        history.asReversed().forEach { if (it.startsWith(t) && it != t) out += it }
        snippets.forEach { if (it.command.startsWith(t) && it.command != t && !it.command.contains('\n')) out += it.command }
        if (!t.contains(' ')) commands.sorted().forEach { if (it.startsWith(t) && it != t) out += it }
        if (out.size < limit) history.asReversed().forEach { if (it.contains(t) && it != t) out += it }
        return out.take(limit)
    }

    /** What still has to be typed to turn [typed] into [choice] (or a full replacement). */
    fun completion(typed: String, choice: String): Pair<Int, String> {
        val t = typed.trimStart()
        return if (choice.startsWith(t)) 0 to choice.substring(t.length) else t.length to choice
    }
}

/** Session restore after the app was killed (Termux loses all sessions). */
data class SavedSession(val env: String, val name: String, val cwd: String, val key: String)

object SessionRecords {
    fun encode(list: List<SavedSession>) = Records.encode(list.map { mapOf("env" to it.env, "name" to it.name, "cwd" to it.cwd, "key" to it.key) })
    fun decode(text: String) = Records.decode(text).mapNotNull {
        val env = it["env"] ?: return@mapNotNull null
        SavedSession(env, it["name"].orEmpty(), it["cwd"].orEmpty(), it["key"].orEmpty())
    }
}

object Search {
    /** Line indexes (0-based in [lines]) that contain [query], case-insensitive. */
    fun lines(lines: List<String>, query: String): List<Int> {
        if (query.isEmpty()) return emptyList()
        return lines.indices.filter { lines[it].contains(query, ignoreCase = true) }
    }
}

/** Maps paths typed inside an environment to the app's files. */
object GuestPaths {
    fun normalize(guest: String, cwd: String = "/root"): String {
        val raw = when {
            guest == "~" -> "/root"
            guest.startsWith("~/") -> "/root/" + guest.removePrefix("~/")
            guest.startsWith("/") -> guest
            else -> cwd.trimEnd('/') + "/" + guest
        }
        val parts = ArrayDeque<String>()
        raw.split('/').forEach { p ->
            when (p) { "", "." -> Unit; ".." -> parts.removeLastOrNull(); else -> parts.addLast(p) }
        }
        return "/" + parts.joinToString("/")
    }

    /** Editor "Run" command for a file, by extension, shebang or project file (Makefile, CMake, Cargo, package.json, go.mod, gradle). */
    fun runCommand(guestPath: String, firstLine: String = ""): String {
        val q = shQuote(guestPath)
        val dir = shQuote(guestPath.substringBeforeLast('/').ifEmpty { "/" })
        val name = guestPath.substringAfterLast('/').substringBeforeLast('.')
        val bin = shQuote("/tmp/nibras-run/$name")
        val body = if (firstLine.startsWith("#!")) "chmod +x $q && $q" else when (guestPath.substringAfterLast('.', "").lowercase()) {
            "py" -> "nibras-need python3 && python3 $q"
            "js", "mjs", "cjs" -> "nibras-need node && node $q"
            "ts", "tsx" -> "nibras-need node && npx --yes tsx $q"
            "sh" -> "sh $q"
            "bash" -> "nibras-need bash && bash $q"
            "rb" -> "nibras-need ruby && ruby $q"
            "php" -> "nibras-need php && php $q"
            "pl" -> "nibras-need perl && perl $q"
            "lua" -> "nibras-need lua && lua $q"
            "r" -> "nibras-need Rscript && Rscript $q"
            "go" -> "nibras-need go && if [ -f go.mod ]; then go run .; else go run $q; fi"
            "c" -> "nibras-need gcc && mkdir -p /tmp/nibras-run && if [ -f Makefile ]; then make && echo; else gcc -O2 -Wall -o $bin $q -lm && $bin; fi"
            "cpp", "cc", "cxx" -> "nibras-need g++ && mkdir -p /tmp/nibras-run && if [ -f Makefile ]; then make; elif [ -f CMakeLists.txt ]; then nibras-need cmake && cmake -B build && cmake --build build; else g++ -O2 -Wall -std=c++17 -o $bin $q && $bin; fi"
            "rs" -> "if [ -f Cargo.toml ]; then nibras-need cargo && cargo run; else nibras-need rustc && mkdir -p /tmp/nibras-run && rustc -O -o $bin $q && $bin; fi"
            "java" -> "nibras-need java && java $q"
            "kt" -> "nibras-need kotlinc && mkdir -p /tmp/nibras-run && kotlinc $q -include-runtime -d $bin.jar && java -jar $bin.jar"
            "kts" -> "nibras-need kotlinc && kotlinc -script $q"
            "html", "htm" -> "nibras-open $q"
            "md" -> "cat $q"
            "json" -> "nibras-need jq && jq . $q"
            "sql" -> "nibras-need sqlite3 && sqlite3 < $q"
            "mk" -> "make -f $q"
            "dart", "jl", "hs", "ex", "exs", "zig", "nim", "ps1", "swift", "scala", "sc", "cs", "f90", "f95", "tcl", "fish", "zsh" -> "nibras-kit run $q"
            else -> if (guestPath.endsWith("Makefile")) "nibras-need make && make" else if (guestPath.endsWith("package.json")) "nibras-need npm && npm install && npm start" else "sh $q"
        }
        // IDE-style footer: exit code and run time (AndroidIDE / Pydroid show this too).
        return "cd $dir && t0=\$(date +%s); { $body; }; rc=\$?; echo; echo \"── exit \$rc · \$((\$(date +%s)-t0))s ──\""
    }
}

/** Spoken phrase (AZ/TR/EN/RU) → command text typed at the prompt (not executed). */
object VoiceCommands {
    private val table = listOf(
        listOf("birləşdir", "birlesdir", "birleştir", "merge", "склеить", "объедин") to "video merge ",
        listOf("sıx", "sikistir", "sıkıştır", "compress", "сжать") to "video compress ",
        listOf("yüklə", "yukle", "indir", "download", "скачать", "загруз") to "download ",
        listOf("mp3", "musiqi", "müzik", "music", "музык") to "download audio ",
        listOf("pdf") to "pdf ",
        listOf("şəkil", "sekil", "resim", "image", "photo", "фото", "изображ") to "image ",
        listOf("mətn", "metin", "ocr", "текст") to "ocr ",
        listOf("sürət", "hız", "speed", "скорость") to "net speed",
        listOf("namaz", "prayer", "намаз") to "prayer",
        listOf("quran", "kuran", "коран") to "quran random",
        listOf("təmizlə", "temizle", "clear", "очист") to "clear",
        listOf("yenilə", "güncelle", "update", "обнов") to "pkg upgrade",
        listOf("qur", "kur", "install", "установ") to "install ",
        listOf("siyahı", "liste", "list files", "список") to "ls -la",
        listOf("öyrən", "öğren", "learn", "учить") to "learn",
        listOf("oyun", "game", "игр") to "games",
    )

    fun toCommand(said: String): String {
        val t = said.lowercase().trim()
        for ((keys, cmd) in table) if (keys.any { t.contains(it) }) return cmd
        return t
    }
}

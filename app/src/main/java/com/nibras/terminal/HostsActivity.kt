package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.nibras.terminal.core.Host
import com.nibras.terminal.core.HostCommands
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Records
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.core.shQuote
import com.nibras.terminal.ui.Ui
import java.io.File

/** Saved SSH / Mosh / Telnet hosts with keys, jump hosts and port forwards (Termius / JuiceSSH / ConnectBot). */
class HostsActivity : Activity() {
    companion object {
        fun file(c: Context) = File(c.filesDir, "hosts.txt")
        fun load(c: Context): MutableList<Host> =
            runCatching { Records.decode(file(c).readText()).map { Host.fromRecord(it) } }.getOrDefault(emptyList()).toMutableList()
        fun save(c: Context, list: List<Host>) = file(c).writeText(Records.encode(list.map { it.toRecord() }))
    }

    private lateinit var hosts: MutableList<Host>
    private lateinit var list: ListView
    private var filter = ""

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hosts = load(this)
        val root = Ui.screen(this, getString(R.string.hosts_title))
        val quick = Ui.field(this, getString(R.string.hosts_quick_hint), mono = true).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(quick, Ui.lp(this))
        root.addView(Ui.row(this,
            Ui.button(this, getString(R.string.hosts_connect), accent = true) {
                val h = Host.parseQuick(quick.text.toString())
                if (h == null) Toast.makeText(this, R.string.hosts_bad_address, Toast.LENGTH_SHORT).show() else connect(h)
            },
            Ui.button(this, "+ " + getString(R.string.hosts_add)) { edit(null) },
            Ui.button(this, getString(R.string.hosts_keys)) { keys() },
            Ui.button(this, "⇅") { exportImport() },
        ), Ui.lp(this))
        root.addView(Ui.field(this, getString(R.string.search_hint)).apply {
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { filter = s.toString(); adapter.notifyDataSetChanged() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            })
        }, Ui.lp(this))
        list = ListView(this).apply {
            adapter = this@HostsActivity.adapter
            divider = null
            setOnItemClickListener { _, _, pos, _ -> visible().getOrNull(pos)?.let { connect(it) } }
            setOnItemLongClickListener { _, _, pos, _ -> visible().getOrNull(pos)?.let { menu(it) }; true }
            emptyView = null
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Ui.label(this, getString(R.string.hosts_tip)).apply { setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), Ui.dp(context, 8)) })
    }

    private val usage by lazy { getSharedPreferences("hosts_usage", MODE_PRIVATE) }
    private var sortMode: String
        get() = usage.getString("sort", "group") ?: "group"
        set(v) { usage.edit().putString("sort", v).apply() }
    private fun last(h: Host) = usage.getLong("last:${h.id}", 0L)
    private fun count(h: Host) = usage.getInt("count:${h.id}", 0)

    private fun visible() = hosts.filter { filter.isBlank() || "${it.name} ${it.target} ${it.group} ${it.protocol}".contains(filter, true) }
        .sortedWith(when (sortMode) {
            "recent" -> compareByDescending<Host> { last(it) }.thenBy { it.name.lowercase() }
            "used" -> compareByDescending<Host> { count(it) }.thenBy { it.name.lowercase() }
            "name" -> compareBy { it.name.lowercase().ifBlank { it.host } }
            else -> compareBy({ it.group.lowercase() }, { it.name.lowercase() })
        })

    private fun ago(t: Long): String {
        if (t == 0L) return ""
        val m = (System.currentTimeMillis() - t) / 60000
        return "· " + when { m < 1 -> "now"; m < 60 -> "${m}m"; m < 1440 -> "${m / 60}h"; else -> "${m / 1440}d" }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = visible().size
        override fun getItem(p: Int) = visible()[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            val h = getItem(p)
            val v = (convertView as? TextView) ?: TextView(this@HostsActivity).apply {
                setPadding(Ui.dp(context, 16), Ui.dp(context, 10), Ui.dp(context, 16), Ui.dp(context, 10)); textSize = 15f
                typeface = Typeface.MONOSPACE; setTextColor(Ui.INK)
            }
            val icon = when (h.protocol) { "mosh" -> "◆"; "telnet" -> "◇"; else -> "▶" }
            val extras = listOfNotNull(
                h.group.takeIf { it.isNotBlank() }?.let { "[$it]" },
                h.forwards.takeIf { it.isNotEmpty() }?.let { "⇄${it.size}" },
                h.jump.takeIf { it.isNotBlank() }?.let { "via $it" },
                h.identity.takeIf { it.isNotBlank() }?.let { "🔑" },
                count(h).takeIf { it > 0 }?.let { "×$it" },
                ago(last(h)).ifBlank { null },
            ).joinToString(" ")
            v.text = "$icon ${h.name.ifBlank { h.target }}\n   ${h.protocol}://${h.target}:${h.port} $extras"
            return v
        }
    }

    private fun touch(h: Host) { usage.edit().putLong("last:${h.id}", System.currentTimeMillis()).putInt("count:${h.id}", count(h) + 1).apply() }

    private fun connect(h: Host) {
        if (hosts.any { it.id == h.id }) touch(h)
        Launcher.run(this, null, HostCommands.connectLine(h), h.name.ifBlank { h.host })
        finish()
    }

    private fun menu(h: Host) {
        val acts = mutableListOf<Pair<String, () -> Unit>>()
        acts += getString(R.string.hosts_connect) to { connect(h) }
        acts += "SFTP" to { touch(h); Launcher.run(this, null, HostCommands.sftpLine(h), "sftp ${h.host}"); finish() }
        if (h.protocol != "telnet") {
            acts += "⇄ " + getString(R.string.hosts_tunnel_only) to {
                touch(h)
                val args = HostCommands.sshArgs(h.copy(startup = "")).toMutableList().apply { add(1, "-N"); add(1, "-v") }
                Launcher.run(this, null, "nibras-need ssh && echo '⇄ ${h.forwards.joinToString(", ").replace("'", "")}' && exec " + args.joinToString(" ") { shQuote(it) }, "⇄ ${h.host}", keepShell = true); finish()
            }
        }
        acts += "📡 " + getString(R.string.hosts_test) to {
            Launcher.run(this, null, "nibras-need ping; ping -c 3 -W 2 ${shQuote(h.host)}; nibras-need nc; nc -zv -w 4 ${shQuote(h.host)} ${h.port}", "test ${h.host}", keepShell = true); finish()
        }
        acts += getString(R.string.hosts_edit) to { edit(h) }
        acts += getString(R.string.hosts_duplicate) to { edit(h.copy(id = java.util.UUID.randomUUID().toString(), name = h.name + " (2)"), isNew = true) }
        acts += getString(R.string.hosts_send_key) to { pickKey { pub -> Launcher.run(this, null, HostCommands.copyIdLine(h, pub), "key → ${h.host}", keepShell = true); finish() } }
        acts += getString(R.string.hosts_copy_cmd) to { copy(HostCommands.connectLine(h).substringAfter("exec ")) }
        acts += getString(R.string.hosts_delete) to {
            AlertDialog.Builder(this).setMessage(getString(R.string.hosts_delete) + "?")
                .setPositiveButton(R.string.ok) { _, _ -> hosts.removeAll { it.id == h.id }; save(this, hosts); adapter.notifyDataSetChanged() }
                .setNegativeButton(R.string.cancel, null).show()
        }
        AlertDialog.Builder(this).setTitle(h.name.ifBlank { h.target }).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    /** Termius-style multi-exec: one command on many hosts, output labelled per host. */
    private fun multiExec() {
        val ssh = hosts.filter { it.protocol != "telnet" }
        if (ssh.isEmpty()) { Toast.makeText(this, R.string.hosts_none, Toast.LENGTH_SHORT).show(); return }
        val picked = BooleanArray(ssh.size)
        AlertDialog.Builder(this).setTitle(R.string.hosts_multi_exec).setMultiChoiceItems(ssh.map { it.name.ifBlank { it.target } }.toTypedArray(), picked) { _, w, on -> picked[w] = on }
            .setPositiveButton(R.string.ok) { _, _ ->
                val chosen = ssh.filterIndexed { i, _ -> picked[i] }
                if (chosen.isEmpty()) return@setPositiveButton
                val cmdField = Ui.field(this, "uptime && df -h", mono = true)
                AlertDialog.Builder(this).setTitle(R.string.hosts_multi_exec).setView(cmdField).setPositiveButton(R.string.ok) { _, _ ->
                    val cmd = cmdField.text.toString().trim().ifEmpty { return@setPositiveButton }
                    val par = cmd.isNotEmpty()
                    val lines = chosen.joinToString("\n") { h ->
                        val args = HostCommands.sshArgs(h.copy(startup = "", forwards = emptyList())).toMutableList().apply { add(1, "-o"); add(2, "BatchMode=yes") } + cmd
                        val label = shQuote("[" + h.name.ifBlank { h.host } + "] ")
                        "( " + args.joinToString(" ") { shQuote(it) } + " 2>&1 | while IFS= read -r l; do printf '%s%s\\n' $label \"\$l\"; done ) &"
                    }
                    chosen.forEach { touch(it) }
                    Launcher.run(this, null, "nibras-need ssh && {\n$lines\nwait; echo '✓ ${chosen.size}'; }", "multi-exec", keepShell = par); finish()
                }.show()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Import Host blocks from ~/.ssh/config (Termius / JuiceSSH "import"), and export everything as an ssh_config. */
    private fun sshConfig(import: Boolean) {
        val cfg = File(sshDir(), "config")
        if (import) {
            if (!cfg.isFile) { Toast.makeText(this, "~/.ssh/config ?", Toast.LENGTH_SHORT).show(); return }
            val found = mutableListOf<Host>(); var cur: MutableMap<String, String>? = null; var alias = ""
            fun flush() { cur?.let { m -> if (!alias.contains('*')) found += Host(java.util.UUID.randomUUID().toString(), alias, m["hostname"] ?: alias, m["user"].orEmpty(),
                m["port"]?.toIntOrNull() ?: 22, "ssh", m["identityfile"].orEmpty(), m["proxyjump"].orEmpty(), group = "ssh_config") } }
            cfg.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { l ->
                val k = l.substringBefore(' ').substringBefore('=').lowercase(); val v = l.substringAfter(' ').substringAfter('=').trim()
                if (k == "host") { flush(); alias = v.split(' ').first(); cur = mutableMapOf() } else cur?.put(k, v)
            }
            flush()
            val fresh = found.filter { f -> hosts.none { it.name == f.name && it.host == f.host } }
            hosts += fresh; save(this, hosts); adapter.notifyDataSetChanged()
            Toast.makeText(this, "+${fresh.size}", Toast.LENGTH_SHORT).show()
        } else {
            val text = hosts.filter { it.protocol != "telnet" }.joinToString("\n") { h ->
                buildString {
                    append("Host ${h.name.ifBlank { h.host }.replace(' ', '-')}\n  HostName ${h.host}\n")
                    if (h.user.isNotBlank()) append("  User ${h.user}\n"); if (h.port != 22) append("  Port ${h.port}\n")
                    if (h.identity.isNotBlank()) append("  IdentityFile ${h.identity}\n"); if (h.jump.isNotBlank()) append("  ProxyJump ${h.jump}\n")
                    if (h.keepAlive) append("  ServerAliveInterval 30\n"); if (h.agent) append("  ForwardAgent yes\n")
                    h.forwards.forEach { f -> when (f.substringBefore(' ').uppercase()) {
                        "L" -> append("  LocalForward ${f.substringAfter(' ').replaceFirst(':', ' ')}\n")
                        "R" -> append("  RemoteForward ${f.substringAfter(' ').replaceFirst(':', ' ')}\n")
                        "D" -> append("  DynamicForward ${f.substringAfter(' ')}\n") } }
                }
            }
            val out = File(sshDir().apply { mkdirs() }, "config.nibras")
            out.writeText(text); copy(text)
            Toast.makeText(this, "~/.ssh/config.nibras", Toast.LENGTH_LONG).show()
        }
    }

    /** Move saved hosts between phones (no cloud account needed). Keys stay in ~/.ssh. */
    private fun exportImport() {
        AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.hosts_export), getString(R.string.hosts_import),
            getString(R.string.hosts_import_config), getString(R.string.hosts_export_config), getString(R.string.hosts_multi_exec),
            getString(R.string.files_sort) + ": " + sortMode)) { _, w ->
            if (w == 2 || w == 3) { sshConfig(w == 2); return@setItems }
            if (w == 4) { multiExec(); return@setItems }
            if (w == 5) { val m = listOf("group", "recent", "used", "name"); sortMode = m[(m.indexOf(sortMode) + 1) % m.size]; adapter.notifyDataSetChanged(); return@setItems }
            if (w == 0) {
                save(this, hosts)
                val out = File(cacheDir, "nibras-hosts.txt").also { file(this).copyTo(it, overwrite = true) }
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", out)
                startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "hosts"))
            } else {
                startActivityForResult(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*"), 51)
            }
        }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 51 || resultCode != RESULT_OK) return
        val added = runCatching {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            Records.decode(text).map { Host.fromRecord(it) }.filter { it.host.isNotBlank() }
        }.getOrDefault(emptyList())
        added.forEach { h -> hosts.removeAll { it.id == h.id }; hosts += h }
        save(this, hosts); adapter.notifyDataSetChanged()
        Toast.makeText(this, "+${added.size}", Toast.LENGTH_SHORT).show()
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("nibras", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun sshDir(): File = File(Rootfs.homeOf(this), ".ssh")

    private fun pickKey(then: (String) -> Unit) {
        val pubs = sshDir().listFiles { f -> f.name.endsWith(".pub") }?.sortedBy { it.name }.orEmpty()
        if (pubs.isEmpty()) { Toast.makeText(this, R.string.hosts_no_keys, Toast.LENGTH_LONG).show(); keys(); return }
        AlertDialog.Builder(this).setItems(pubs.map { it.name }.toTypedArray()) { _, w -> then("~/.ssh/" + pubs[w].name) }.show()
    }

    /** Key manager: list, generate (ed25519 / rsa / ecdsa), copy public key, import. */
    private fun keys() {
        val pubs = sshDir().listFiles { f -> f.name.endsWith(".pub") }?.sortedBy { it.name }.orEmpty()
        val items = pubs.map { "🔑 " + it.name.removeSuffix(".pub") } +
            listOf(getString(R.string.keys_new, "ed25519"), getString(R.string.keys_new, "rsa 4096"), getString(R.string.keys_new, "ecdsa"))
        AlertDialog.Builder(this).setTitle(R.string.hosts_keys).setItems(items.toTypedArray()) { _, w ->
            if (w < pubs.size) {
                val pub = pubs[w]
                AlertDialog.Builder(this).setTitle(pub.name).setMessage(runCatching { pub.readText() }.getOrDefault(""))
                    .setPositiveButton(R.string.menu_copy_all) { _, _ -> copy(pub.readText().trim()) }
                    .setNegativeButton(R.string.cancel, null).show()
            } else {
                val (type, file) = when (w - pubs.size) { 0 -> "-t ed25519" to "id_ed25519"; 1 -> "-t rsa -b 4096" to "id_rsa"; else -> "-t ecdsa -b 521" to "id_ecdsa" }
                Launcher.run(this, null, "nibras-need ssh-keygen && mkdir -p ~/.ssh && chmod 700 ~/.ssh && ssh-keygen $type -f ~/.ssh/$file -C nibras@phone && cat ~/.ssh/$file.pub",
                    "ssh-keygen", keepShell = true)
                finish()
            }
        }.show()
    }

    private fun edit(old: Host?, isNew: Boolean = old == null) {
        val h = old ?: Host(java.util.UUID.randomUUID().toString(), "", "")
        val c = this
        val name = Ui.field(c, getString(R.string.host_name), h.name)
        val host = Ui.field(c, getString(R.string.host_address), h.host, mono = true)
        val user = Ui.field(c, getString(R.string.host_user), h.user, mono = true)
        val port = Ui.field(c, getString(R.string.host_port), h.port.toString(), mono = true).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        var protocol = h.protocol
        val proto = Ui.button(c, "") {}
        fun refreshProto() { proto.text = getString(R.string.host_protocol) + ": " + protocol }
        refreshProto()
        proto.setOnClickListener { protocol = when (protocol) { "ssh" -> "mosh"; "mosh" -> "telnet"; else -> "ssh" }; refreshProto() }
        val identity = Ui.field(c, getString(R.string.host_identity), h.identity, mono = true)
        val jump = Ui.field(c, getString(R.string.host_jump), h.jump, mono = true)
        val forwards = Ui.field(c, getString(R.string.host_forwards), h.forwards.joinToString("\n"), mono = true).apply {
            isSingleLine = false; minLines = 2
        }
        val startup = Ui.field(c, getString(R.string.host_startup), h.startup, mono = true)
        val group = Ui.field(c, getString(R.string.host_group), h.group)
        val extra = Ui.field(c, getString(R.string.host_extra), h.extra, mono = true)
        val keep = CheckBox(c).apply { text = getString(R.string.host_keepalive); isChecked = h.keepAlive; setTextColor(Ui.INK) }
        val agent = CheckBox(c).apply { text = getString(R.string.host_agent); isChecked = h.agent; setTextColor(Ui.INK) }
        val form = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), 0)
            listOf(name, host, user, port, proto, identity, jump, Ui.label(c, getString(R.string.host_forwards_hint)), forwards, startup, group, extra, keep, agent)
                .forEach { addView(it) }
        }
        AlertDialog.Builder(this).setTitle(if (isNew) R.string.hosts_add else R.string.hosts_edit)
            .setView(ScrollView(c).apply { addView(form) })
            .setPositiveButton(R.string.ok) { _, _ ->
                val updated = Host(h.id, name.text.toString().trim(), host.text.toString().trim(), user.text.toString().trim(),
                    port.text.toString().toIntOrNull()?.coerceIn(1, 65535) ?: 22, protocol, identity.text.toString().trim(),
                    jump.text.toString().trim(), forwards.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() },
                    startup.text.toString(), keep.isChecked, agent.isChecked, group.text.toString().trim(), extra.text.toString().trim())
                if (updated.host.isBlank()) { Toast.makeText(this, R.string.hosts_bad_address, Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                hosts.removeAll { it.id == updated.id }; hosts += updated; save(this, hosts); adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}

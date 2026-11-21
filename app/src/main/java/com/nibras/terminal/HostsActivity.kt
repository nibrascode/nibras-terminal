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

    private fun visible() = hosts.filter { filter.isBlank() || "${it.name} ${it.target} ${it.group}".contains(filter, true) }
        .sortedWith(compareBy({ it.group.lowercase() }, { it.name.lowercase() }))

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
            ).joinToString(" ")
            v.text = "$icon ${h.name.ifBlank { h.target }}\n   ${h.protocol}://${h.target}:${h.port} $extras"
            return v
        }
    }

    private fun connect(h: Host) {
        Launcher.run(this, null, HostCommands.connectLine(h), h.name.ifBlank { h.host })
        finish()
    }

    private fun menu(h: Host) {
        val items = arrayOf(getString(R.string.hosts_connect), "SFTP", getString(R.string.hosts_edit), getString(R.string.hosts_duplicate),
            getString(R.string.hosts_send_key), getString(R.string.hosts_copy_cmd), getString(R.string.hosts_delete))
        AlertDialog.Builder(this).setTitle(h.name.ifBlank { h.target }).setItems(items) { _, w ->
            when (w) {
                0 -> connect(h)
                1 -> { Launcher.run(this, null, HostCommands.sftpLine(h), "sftp ${h.host}"); finish() }
                2 -> edit(h)
                3 -> edit(h.copy(id = java.util.UUID.randomUUID().toString(), name = h.name + " (2)"), isNew = true)
                4 -> pickKey { pub -> Launcher.run(this, null, HostCommands.copyIdLine(h, pub), "key → ${h.host}", keepShell = true); finish() }
                5 -> copy(HostCommands.connectLine(h).substringAfter("exec "))
                6 -> AlertDialog.Builder(this).setMessage(getString(R.string.hosts_delete) + "?")
                    .setPositiveButton(R.string.ok) { _, _ -> hosts.removeAll { it.id == h.id }; save(this, hosts); adapter.notifyDataSetChanged() }
                    .setNegativeButton(R.string.cancel, null).show()
            }
        }.show()
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

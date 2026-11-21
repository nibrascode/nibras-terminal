package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.nibras.terminal.core.Lang
import com.nibras.terminal.ui.Ui
import org.json.JSONObject

/**
 * Script store: community scripts from catalog/scripts (bundled copy; `scripts update` fetches the latest).
 * Read the source first, then install / run in the terminal. Every script is checked against its sha256 on install.
 */
class ScriptsActivity : Activity() {
    data class Script(val id: String, val cat: String, val desc: String, val sha: String)

    private val lang by lazy { Lang.current(this) }
    private var cats = listOf<Pair<String, String>>()
    private var all = listOf<Script>()
    private var cat: String? = null
    private var query = ""
    private val rows = mutableListOf<Any>()

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    private fun loc(o: JSONObject?) = o?.optString(lang)?.takeIf { it.isNotBlank() } ?: o?.optString("en").orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val idx = JSONObject(assets.open("scripts/index.json").bufferedReader().use { it.readText() })
            val c = idx.getJSONObject("categories")
            cats = c.keys().asSequence().map { it to loc(c.getJSONObject(it)) }.toList()
            val a = idx.getJSONArray("scripts")
            all = (0 until a.length()).map { a.getJSONObject(it) }.map { Script(it.getString("id"), it.getString("category"), loc(it.getJSONObject("description")), it.getString("sha256")) }
        }
        val root = Ui.screen(this, getString(R.string.scripts_title))
        root.addView(Ui.label(this, getString(R.string.scripts_hint)).apply { setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), Ui.dp(context, 6)) })
        val search = EditText(this).apply {
            hint = getString(R.string.scripts_search); setSingleLine(); setTextColor(Ui.INK); setHintTextColor(0xFF8B949E.toInt())
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { query = s?.toString().orEmpty().trim().lowercase(); refresh() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            })
        }
        root.addView(search, Ui.lp(this, 2))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
        val names = listOf<Pair<String?, String>>(null to getString(R.string.scripts_all)) + cats.map { it.first to it.second }
        names.forEachIndexed { i, (key, name) ->
            chips.addView(Ui.button(this, name, accent = i == 0) { cat = key; refresh(); for (k in 0 until chips.childCount) (chips.getChildAt(k) as TextView).alpha = if (k == i) 1f else 0.6f },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(Ui.dp(this@ScriptsActivity, 4), 0, Ui.dp(this@ScriptsActivity, 4), 0) })
        }
        root.addView(scroll, Ui.lp(this, 2))
        val list = ListView(this).apply {
            adapter = this@ScriptsActivity.adapter; divider = null
            setOnItemClickListener { _, _, pos, _ -> (rows.getOrNull(pos) as? Script)?.let { open(it) } }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    private fun refresh() {
        rows.clear()
        for ((key, _) in cats) {
            if (cat != null && cat != key) continue
            val items = all.filter { it.cat == key && (query.isEmpty() || it.id.contains(query) || it.desc.lowercase().contains(query)) }
            if (items.isEmpty()) continue
            rows.add(key); rows.addAll(items)
        }
        adapter.notifyDataSetChanged()
    }

    private fun source(s: Script): String = runCatching { assets.open("scripts/${s.id}.sh").bufferedReader().use { it.readText() } }.getOrDefault("")

    private fun open(s: Script) {
        AlertDialog.Builder(this).setTitle("📜 ${s.id}")
            .setMessage(s.desc + "\n\n$ scripts install ${s.id}\n$ ${s.id}\n\nsha256 ${s.sha.take(16)}…")
            .setPositiveButton(R.string.scripts_install) { _, _ -> Launcher.run(this, null, "nibras-scripts install ${s.id}", s.id, keepShell = true); finish() }
            .setNeutralButton(R.string.scripts_source) { _, _ -> showSource(s) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showSource(s: Script) {
        val tv = TextView(this).apply {
            text = source(s); typeface = Typeface.MONOSPACE; textSize = 12f; setTextColor(Ui.INK); setTextIsSelectable(true)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 14), Ui.dp(context, 8)); textDirection = View.TEXT_DIRECTION_LTR
        }
        val sv = android.widget.HorizontalScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this).setTitle(s.id).setView(ScrollView(this).apply { addView(sv) })
            .setPositiveButton(R.string.scripts_run) { _, _ -> Launcher.run(this, null, "nibras-scripts install ${s.id} -y && ${s.id}", s.id, keepShell = true); finish() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(p: Int) = if (rows[p] is Script) 0 else 1
        override fun isEnabled(p: Int) = rows[p] is Script
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            val v = (convertView as? TextView) ?: TextView(this@ScriptsActivity)
            when (val r = rows[p]) {
                is Script -> v.apply {
                    setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8)); textSize = 15f; setTextColor(Ui.INK)
                    typeface = Typeface.DEFAULT; text = "📜  ${r.id}\n      ${r.desc}"
                }
                is String -> v.apply {
                    setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 2)); textSize = 13f; setTextColor(Ui.GOLD)
                    typeface = Typeface.DEFAULT_BOLD; text = cats.firstOrNull { it.first == r }?.second.orEmpty().uppercase()
                }
            }
            return v
        }
    }
}

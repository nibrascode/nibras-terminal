package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import com.nibras.terminal.core.Lang
import com.nibras.terminal.ui.Ui
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * nibras-dialog and nibras-storage-get: shows a dialog / document picker over the terminal and
 * hands the answer back to the waiting shell command (Termux:API dialog widgets).
 */
class PromptActivity : Activity() {
    companion object {
        private val ids = AtomicInteger(1)
        private val waiting = ConcurrentHashMap<Int, CompletableFuture<String>>()
        private const val REQ_DOC = 31

        /** Blocks the API worker thread until the user answers (max 10 min). */
        fun ask(context: Context, kind: String, args: List<String>, target: File? = null): String {
            val id = ids.getAndIncrement()
            val future = CompletableFuture<String>()
            waiting[id] = future
            context.startActivity(Intent(context, PromptActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("id", id).putExtra("kind", kind).putExtra("args", args.toTypedArray()).putExtra("target", target?.absolutePath))
            return try { future.get(10, TimeUnit.MINUTES) } catch (_: Throwable) { JSONObject().put("code", -2).put("text", "").toString() } finally { waiting.remove(id) }
        }
    }

    private var id = 0
    private var done = false

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    private fun answer(code: Int, text: String, extra: JSONObject.() -> Unit = {}) {
        if (done) return
        done = true
        waiting[id]?.complete(JSONObject().put("code", code).put("text", text).apply(extra).toString())
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getIntExtra("id", 0)
        val args = intent.getStringArrayExtra("args")?.toList().orEmpty()
        fun opt(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        if (intent.getStringExtra("kind") == "storage-get") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_DOC)
            return
        }
        val widget = args.firstOrNull { !it.startsWith("-") && args.indexOf(it).let { i -> i == 0 || !args[i - 1].startsWith("-") } } ?: "text"
        val title = opt("-t") ?: "Nibras"
        val hint = opt("-i") ?: ""
        val values = opt("-v")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val b = AlertDialog.Builder(this).setTitle(title).setOnCancelListener { answer(-2, "") }
        when (widget) {
            "confirm" -> b.setMessage(hint.ifBlank { "?" })
                .setPositiveButton(R.string.yes) { _, _ -> answer(-1, "yes") }.setNegativeButton(R.string.no) { _, _ -> answer(-1, "no") }
            "radio", "spinner", "sheet" -> b.setItems(values.toTypedArray()) { _, w -> answer(-1, values[w]) { put("index", w) } }
            "checkbox" -> {
                val checked = BooleanArray(values.size)
                b.setMultiChoiceItems(values.toTypedArray(), checked) { _, w, on -> checked[w] = on }
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val picked = values.indices.filter { checked[it] }
                        answer(-1, picked.joinToString(", ") { values[it] }) { put("values", org.json.JSONArray(picked.map { values[it] })) }
                    }
            }
            "counter" -> {
                val f = Ui.field(this, hint, opt("-r")?.split(',')?.getOrNull(2) ?: "0").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
                b.setView(f).setPositiveButton(R.string.ok) { _, _ -> answer(-1, f.text.toString()) }
            }
            else -> { // text / password / date / time fall back to a text field
                val f: EditText = Ui.field(this, hint).apply {
                    inputType = when {
                        widget == "password" || "-p" in args -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        "-n" in args -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                        "-m" in args -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        else -> InputType.TYPE_CLASS_TEXT
                    }
                }
                b.setView(f).setPositiveButton(R.string.ok) { _, _ -> answer(-1, f.text.toString()) }
            }
        }
        if (widget != "confirm") b.setNegativeButton(R.string.cancel) { _, _ -> answer(-2, "") }
        b.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val target = intent.getStringExtra("target")?.let { File(it) }
        if (resultCode != RESULT_OK || uri == null || target == null) { answer(-2, ""); return }
        Thread {
            val ok = runCatching {
                target.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
            }.isSuccess
            runOnUiThread { answer(if (ok) -1 else 1, if (ok) target.name else "copy failed") }
        }.start()
    }

    override fun onDestroy() {
        if (!done && !isChangingConfigurations) answer(-2, "")
        super.onDestroy()
    }
}

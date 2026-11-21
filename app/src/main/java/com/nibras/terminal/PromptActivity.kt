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
        private const val REQ_CAM = 32
        private const val REQ_STT = 33
        private const val REQ_TREE = 34

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
    private var camTmp: File? = null
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
        when (intent.getStringExtra("kind")) {
            "storage-get" -> { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_DOC); return }
            "camera-photo" -> {
                val target = File(intent.getStringExtra("target") ?: run { answer(1, "no target"); return })
                target.parentFile?.mkdirs()
                // Camera apps cannot write into the app's private rootfs; capture to cache, copy after.
                val tmp = File(cacheDir, "camera-${System.nanoTime()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", tmp.also { it.parentFile?.mkdirs(); it.createNewFile() })
                camTmp = tmp
                val i = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { startActivityForResult(i, REQ_CAM) }.onFailure { answer(1, it.message ?: "no camera app") }
                return
            }
            "nfc" -> { startNfc(args); return }
            "saf-tree" -> { runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE) }.onFailure { answer(1, it.message ?: "no picker") }; return }
            "fingerprint" -> { fingerprint(args); return }
            "speech-to-text" -> {
                val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                runCatching { startActivityForResult(i, REQ_STT) }.onFailure { answer(1, it.message ?: "no speech app") }
                return
            }
        }
        val widget = args.firstOrNull { !it.startsWith("-") && args.indexOf(it).let { i -> i == 0 || !args[i - 1].startsWith("-") } } ?: "text"
        val title = opt("-t") ?: "Nibras"
        val hint = opt("-i") ?: ""
        val values = opt("-v")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (widget == "speech") {
            val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, hint.ifBlank { title })
            runCatching { startActivityForResult(i, REQ_STT) }.onFailure { answer(1, it.message ?: "no speech app") }
            return
        }
        if (widget == "date") {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                val fmt = opt("-d")
                answer(-1, if (fmt != null) runCatching { java.text.SimpleDateFormat(fmt, java.util.Locale.US).format(cal.time) }.getOrDefault(cal.time.toString()) else cal.time.toString())
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                setTitle(title); setOnCancelListener { answer(-2, "") }; show()
            }
            return
        }
        if (widget == "time") {
            val cal = java.util.Calendar.getInstance()
            android.app.TimePickerDialog(this, { _, h, m -> answer(-1, "%02d:%02d".format(h, m)) },
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).apply {
                setTitle(title); setOnCancelListener { answer(-2, "") }; show()
            }
            return
        }
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
                        answer(-1, "[" + picked.joinToString(", ") { values[it] } + "]") { put("values", org.json.JSONArray(picked.map { JSONObject().put("index", it).put("text", values[it]) })) }
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
        if (requestCode == REQ_STT) {
            val text = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            answer(if (text != null) -1 else -2, text.orEmpty()); return
        }
        if (requestCode == REQ_TREE) {
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) { answer(-2, ""); return }
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            answer(-1, uri.toString()); return
        }
        if (requestCode == REQ_CAM) {
            val tmp = camTmp; val target = intent.getStringExtra("target")?.let { File(it) }
            if (resultCode != RESULT_OK || tmp == null || target == null || tmp.length() == 0L) { tmp?.delete(); answer(-2, ""); return }
            val ok = runCatching { tmp.copyTo(target, overwrite = true); tmp.delete() }.isSuccess
            answer(if (ok) -1 else 1, if (ok) target.name else "copy failed"); return
        }
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

    // ---------------------------------------------------------------- fingerprint (termux-fingerprint)
    private fun fingerprint(args: List<String>) {
        var failed = 0
        val errors = org.json.JSONArray()
        fun finish(result: String) = answer(-1, result) { put("auth_result", result).put("failed_attempts", failed).put("errors", errors) }
        if (android.os.Build.VERSION.SDK_INT < 28) { errors.put("ERROR_UNSUPPORTED_ANDROID_VERSION"); finish("AUTH_RESULT_UNKNOWN"); return }
        val b = android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle(args.getOrNull(0) ?: "Nibras Terminal")
        args.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { b.setDescription(it) }
        args.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { b.setSubtitle(it) }
        b.setNegativeButton(args.getOrNull(3) ?: getString(R.string.cancel), mainExecutor) { _, _ -> errors.put("ERROR_CANCELED"); finish("AUTH_RESULT_FAILURE") }
        runCatching {
            b.build().authenticate(android.os.CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) = finish("AUTH_RESULT_SUCCESS")
                override fun onAuthenticationFailed() { failed++ }
                override fun onAuthenticationError(code: Int, msg: CharSequence?) {
                    errors.put(when (code) { 7 -> "ERROR_LOCKOUT"; 9 -> "ERROR_LOCKOUT_PERMANENT"; 11 -> "ERROR_NO_BIOMETRICS"; 12 -> "ERROR_HW_NOT_PRESENT"; 10, 5 -> "ERROR_CANCELED"; 3 -> "ERROR_TIMEOUT"; else -> "ERROR_$code" })
                    finish("AUTH_RESULT_FAILURE")
                }
            })
        }.onFailure { errors.put(it.message ?: "ERROR"); finish("AUTH_RESULT_UNKNOWN") }
    }

    // ---------------------------------------------------------------- NFC (termux-nfc)
    private var nfcArgs: List<String> = emptyList()
    private var nfcDialog: AlertDialog? = null

    private fun startNfc(args: List<String>) {
        val adapter = android.nfc.NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) { answer(1, "this phone has no NFC"); return }
        if (!adapter.isEnabled) {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS)) }
            answer(1, "NFC is off, turn it on and run again"); return
        }
        nfcArgs = args
        val writing = "-w" in args
        nfcDialog = AlertDialog.Builder(this).setTitle("NFC")
            .setMessage(getString(if (writing) R.string.nfc_write_hint else R.string.nfc_read_hint))
            .setNegativeButton(R.string.cancel) { _, _ -> answer(-2, "") }.setOnCancelListener { answer(-2, "") }.show()
    }

    override fun onResume() {
        super.onResume()
        if (nfcArgs.isEmpty() && intent.getStringExtra("kind") != "nfc") return
        val adapter = android.nfc.NfcAdapter.getDefaultAdapter(this) ?: return
        val flags = android.nfc.NfcAdapter.FLAG_READER_NFC_A or android.nfc.NfcAdapter.FLAG_READER_NFC_B or android.nfc.NfcAdapter.FLAG_READER_NFC_F or
            android.nfc.NfcAdapter.FLAG_READER_NFC_V or android.nfc.NfcAdapter.FLAG_READER_NFC_BARCODE
        runCatching { adapter.enableReaderMode(this, { tag -> onTag(tag) }, flags, null) }
    }

    override fun onPause() {
        super.onPause()
        runCatching { android.nfc.NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) }
    }

    private fun onTag(tag: android.nfc.Tag) {
        val args = nfcArgs
        fun opt(n: String) = args.indexOf(n).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        val result = runCatching {
            if ("-w" in args) {
                val record = opt("-u")?.let { android.nfc.NdefRecord.createUri(it) }
                    ?: android.nfc.NdefRecord.createTextRecord(null, opt("-t") ?: args.lastOrNull { !it.startsWith("-") } ?: "")
                val msg = android.nfc.NdefMessage(arrayOf(record))
                val ndef = android.nfc.tech.Ndef.get(tag)
                if (ndef != null) ndef.use { it.connect(); if (!it.isWritable) error("tag is read-only"); it.writeNdefMessage(msg) }
                else android.nfc.tech.NdefFormatable.get(tag)?.use { it.connect(); it.format(msg) } ?: error("tag does not support NDEF")
                JSONObject().put("written", true).put("bytes", msg.byteArrayLength)
            } else {
                val o = JSONObject().put("id", tag.id.joinToString("") { "%02x".format(it) }).put("tech", org.json.JSONArray(tag.techList.map { it.substringAfterLast('.') }))
                android.nfc.tech.Ndef.get(tag)?.use { ndef ->
                    ndef.connect()
                    o.put("type", ndef.type).put("max_size", ndef.maxSize).put("writable", ndef.isWritable)
                    val recs = org.json.JSONArray()
                    ndef.ndefMessage?.records?.forEach { r ->
                        val rec = JSONObject().put("tnf", r.tnf).put("type", String(r.type))
                        r.toUri()?.let { rec.put("uri", it.toString()) }
                        if (r.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN && String(r.type) == "T") {
                            val p = r.payload; val lang = p[0].toInt() and 0x3F
                            rec.put("lang", String(p, 1, lang)).put("text", String(p, 1 + lang, p.size - 1 - lang, if (p[0].toInt() and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8))
                        }
                        if (opt("-r") == "full") rec.put("payload_hex", r.payload.joinToString("") { "%02x".format(it) })
                        recs.put(rec)
                    }
                    o.put("records", recs)
                }
                o
            }
        }
        runOnUiThread {
            nfcDialog?.dismiss()
            result.onSuccess { answer(-1, it.toString(2)) }.onFailure { answer(1, it.message ?: "NFC error") }
        }
    }

    override fun onDestroy() {
        if (!done && !isChangingConfigurations) answer(-2, "")
        super.onDestroy()
    }
}

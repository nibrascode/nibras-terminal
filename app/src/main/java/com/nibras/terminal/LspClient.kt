package com.nibras.terminal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Minimal Language Server Protocol client for the built-in editor (AndroidIDE / ACS style):
 * completion, diagnostics, hover and go-to-definition from real language servers that run inside the
 * environment (proot) over stdio. Servers come on demand with `install lsp-<lang>`.
 */
class LspClient(
    private val context: Context,
    private val env: String,
    val server: Server,
    private val rootGuest: String,
    private val onDiagnostics: (uri: String, items: List<Diag>) -> Unit,
    private val onLog: (String) -> Unit = {},
) {
    data class Server(val id: String, val pack: String, val check: String, val command: String, val languageId: String)
    data class Diag(val line: Int, val col: Int, val endLine: Int, val endCol: Int, val severity: Int, val message: String)
    data class Item(val label: String, val insert: String, val detail: String)
    data class Location(val path: String, val line: Int, val col: Int)

    companion object {
        /** Language → server. `check` decides if it is installed; `pack` is what `install` gets. */
        fun serverFor(ext: String, path: String): Server? = when (ext) {
            "py" -> Server("python", "lsp-python", "command -v pylsp", "exec pylsp", "python")
            "js" -> Server("typescript", "lsp-js", "command -v typescript-language-server", "exec typescript-language-server --stdio",
                when { path.endsWith(".ts") -> "typescript"; path.endsWith(".tsx") -> "typescriptreact"; path.endsWith(".jsx") -> "javascriptreact"; else -> "javascript" })
            "c" -> Server("clangd", "lsp-c", "command -v clangd", "exec clangd --background-index=false", if (path.endsWith(".c") || path.endsWith(".h")) "c" else "cpp")
            "java" -> Server("jdtls", "lsp-java", "command -v nibras-jdtls", "exec nibras-jdtls", "java")
            "kt" -> Server("kotlin", "lsp-kotlin", "command -v kotlin-language-server", "exec kotlin-language-server", "kotlin")
            "xml", "svg" -> Server("lemminx", "lsp-xml", "command -v nibras-lemminx", "exec nibras-lemminx", "xml")
            "sh" -> Server("bash", "lsp-bash", "command -v bash-language-server", "exec bash-language-server start", "shellscript")
            "go" -> Server("gopls", "lsp-go", "command -v gopls || [ -x ~/go/bin/gopls ]", "exec \$(command -v gopls || echo ~/go/bin/gopls)", "go")
            "rs" -> Server("rust-analyzer", "lsp-rust", "command -v rust-analyzer", "exec rust-analyzer", "rust")
            "html", "htm" -> Server("html", "lsp-web", "command -v vscode-html-language-server", "exec vscode-html-language-server --stdio", "html")
            "css", "scss" -> Server("css", "lsp-web", "command -v vscode-css-language-server", "exec vscode-css-language-server --stdio", "css")
            "json" -> Server("json", "lsp-web", "command -v vscode-json-language-server", "exec vscode-json-language-server --stdio", "json")
            else -> null
        }
        fun uri(guest: String) = "file://" + guest.split('/').joinToString("/") { Uri.encode(it) }
        fun path(uri: String) = Uri.decode(uri.removePrefix("file://"))
        private object Uri {
            fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
            fun decode(s: String) = java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        }
    }

    private var proc: Process? = null
    private var out: OutputStream? = null
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, (Any?) -> Unit>()
    private val versions = HashMap<String, Int>()
    @Volatile var ready = false; private set
    @Volatile var triggers: List<String> = listOf("."); private set
    @Volatile private var alive = false

    fun start(onReady: () -> Unit) {
        val p = Headless.start(context, env, "cd '${rootGuest.replace("'", "'\\''")}' 2>/dev/null; ${server.command}", rootGuest)
        proc = p; out = p.outputStream; alive = true
        thread(name = "lsp-err") { runCatching { p.errorStream.bufferedReader().forEachLine { onLog(it) } } }
        thread(name = "lsp-read") { readLoop(BufferedInputStream(p.inputStream)) }
        val caps = JSONObject()
            .put("textDocument", JSONObject()
                .put("synchronization", JSONObject().put("didSave", true).put("dynamicRegistration", false))
                .put("completion", JSONObject().put("completionItem", JSONObject().put("snippetSupport", false).put("documentationFormat", JSONArray().put("plaintext"))))
                .put("hover", JSONObject().put("contentFormat", JSONArray().put("plaintext").put("markdown")))
                .put("definition", JSONObject().put("linkSupport", false))
                .put("publishDiagnostics", JSONObject().put("relatedInformation", false)))
            .put("workspace", JSONObject().put("workspaceFolders", true).put("configuration", true))
        val params = JSONObject().put("processId", JSONObject.NULL).put("rootUri", uri(rootGuest)).put("capabilities", caps)
            .put("workspaceFolders", JSONArray().put(JSONObject().put("uri", uri(rootGuest)).put("name", rootGuest.substringAfterLast('/').ifEmpty { "root" })))
            .put("clientInfo", JSONObject().put("name", "Nibras Terminal"))
        request("initialize", params) { res ->
            (res as? JSONObject)?.optJSONObject("capabilities")?.optJSONObject("completionProvider")?.optJSONArray("triggerCharacters")?.let { a ->
                triggers = (0 until a.length()).map { a.optString(it) }
            }
            notify("initialized", JSONObject()); ready = true; onReady()
        }
    }

    fun stop() {
        if (!alive) return
        alive = false
        runCatching { request("shutdown", null) { notify("exit", null) } }
        thread { Thread.sleep(1500); runCatching { proc?.destroy() } }
    }

    fun didOpen(guest: String, text: String) {
        versions[guest] = 1
        notify("textDocument/didOpen", JSONObject().put("textDocument", JSONObject().put("uri", uri(guest)).put("languageId", server.languageId).put("version", 1).put("text", text)))
    }

    fun didChange(guest: String, text: String) {
        val v = (versions[guest] ?: 1) + 1; versions[guest] = v
        notify("textDocument/didChange", JSONObject().put("textDocument", JSONObject().put("uri", uri(guest)).put("version", v))
            .put("contentChanges", JSONArray().put(JSONObject().put("text", text))))
    }

    fun didSave(guest: String) = notify("textDocument/didSave", JSONObject().put("textDocument", JSONObject().put("uri", uri(guest))))

    private fun pos(guest: String, line: Int, col: Int) = JSONObject().put("textDocument", JSONObject().put("uri", uri(guest)))
        .put("position", JSONObject().put("line", line).put("character", col))

    fun completion(guest: String, line: Int, col: Int, cb: (List<Item>) -> Unit) = request("textDocument/completion", pos(guest, line, col)) { res ->
        val arr = when (res) { is JSONArray -> res; is JSONObject -> res.optJSONArray("items"); else -> null } ?: JSONArray()
        cb((0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.sortedBy { it.optString("sortText", it.optString("label")) }.take(60).map { o ->
            val edit = o.optJSONObject("textEdit")?.optString("newText")
            Item(o.optString("label").trim(), (edit ?: o.optString("insertText", "")).ifEmpty { o.optString("label").trim() }, o.optString("detail"))
        })
    }

    fun hover(guest: String, line: Int, col: Int, cb: (String) -> Unit) = request("textDocument/hover", pos(guest, line, col)) { res ->
        val c = (res as? JSONObject)?.opt("contents")
        fun one(x: Any?): String = when (x) { is String -> x; is JSONObject -> x.optString("value"); else -> "" }
        cb(when (c) { is JSONArray -> (0 until c.length()).joinToString("\n\n") { one(c.opt(it)) }; else -> one(c) }.trim())
    }

    fun definition(guest: String, line: Int, col: Int, cb: (Location?) -> Unit) = request("textDocument/definition", pos(guest, line, col)) { res ->
        val o = when (res) { is JSONArray -> res.optJSONObject(0); is JSONObject -> res; else -> null }
        val u = o?.optString("uri")?.ifEmpty { null } ?: o?.optString("targetUri")?.ifEmpty { null }
        val r = (o?.optJSONObject("range") ?: o?.optJSONObject("targetSelectionRange"))?.optJSONObject("start")
        cb(if (u == null || r == null) null else Location(path(u), r.optInt("line"), r.optInt("character")))
    }

    private fun request(method: String, params: Any?, cb: (Any?) -> Unit) {
        val id = ids.getAndIncrement(); pending[id] = cb
        send(JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params ?: JSONObject.NULL))
    }

    private fun notify(method: String, params: Any?) =
        send(JSONObject().put("jsonrpc", "2.0").put("method", method).apply { if (params != null) put("params", params) })

    @Synchronized private fun send(msg: JSONObject) {
        val o = out ?: return
        runCatching {
            val body = msg.toString().toByteArray()
            o.write("Content-Length: ${body.size}\r\n\r\n".toByteArray()); o.write(body); o.flush()
        }.onFailure { onLog("lsp: ${it.message}") }
    }

    private fun readLoop(input: BufferedInputStream) {
        try {
            while (alive) {
                var len = -1
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", true)) len = line.substringAfter(':').trim().toInt()
                }
                if (len < 0) continue
                val buf = ByteArray(len); var got = 0
                while (got < len) { val n = input.read(buf, got, len - got); if (n < 0) return; got += n }
                handle(JSONObject(String(buf)))
            }
        } catch (t: Throwable) { onLog("lsp stopped: ${t.message}") } finally { ready = false }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read(); if (c < 0) return null
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    private fun handle(msg: JSONObject) {
        val method = msg.optString("method")
        if (msg.has("id") && method.isNotEmpty()) { // server → client request: answer with something sensible
            val result: Any = when (method) {
                "workspace/configuration" -> JSONArray().apply { repeat(msg.optJSONObject("params")?.optJSONArray("items")?.length() ?: 1) { put(JSONObject.NULL) } }
                "workspace/workspaceFolders" -> JSONArray().put(JSONObject().put("uri", uri(rootGuest)).put("name", "root"))
                else -> JSONObject.NULL
            }
            send(JSONObject().put("jsonrpc", "2.0").put("id", msg.get("id")).put("result", result)); return
        }
        if (msg.has("id")) { pending.remove(msg.optInt("id"))?.invoke(if (msg.isNull("result")) null else msg.opt("result")); return }
        when (method) {
            "textDocument/publishDiagnostics" -> {
                val p = msg.optJSONObject("params") ?: return
                val a = p.optJSONArray("diagnostics") ?: JSONArray()
                onDiagnostics(path(p.optString("uri")), (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { d ->
                    val r = d.optJSONObject("range"); val s = r?.optJSONObject("start"); val e = r?.optJSONObject("end")
                    Diag(s?.optInt("line") ?: 0, s?.optInt("character") ?: 0, e?.optInt("line") ?: 0, e?.optInt("character") ?: 0, d.optInt("severity", 1), d.optString("message"))
                })
            }
            "window/logMessage", "window/showMessage" -> onLog(msg.optJSONObject("params")?.optString("message").orEmpty())
        }
    }
}

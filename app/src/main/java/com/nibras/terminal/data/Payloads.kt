package com.nibras.terminal.data

import android.content.Context
import com.nibras.terminal.Component
import com.nibras.terminal.InstallRecord
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val CATALOG_URL =
    "https://raw.githubusercontent.com/nibrascode/nibras-terminal/main/catalog/manifest.json"

class CatalogStore(private val context: Context) {
    fun bundled(): List<Component> {
        val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        return parse(text)
    }

    fun refresh(client: OkHttpClient): List<Component> {
        val request = Request.Builder().url(CATALOG_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Kataloq açılmadı: HTTP ${response.code}")
            val text = response.body?.string() ?: error("Kataloq boşdur")
            return parse(text)
        }
    }

    private fun parse(text: String): List<Component> {
        val root = JSONObject(text)
        val items = root.getJSONArray("components")
        return buildList {
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                add(
                    Component(
                        id = o.getString("id"),
                        group = o.getString("group"),
                        name = o.getString("name"),
                        summary = o.getString("summary"),
                        version = o.getString("version"),
                        abi = o.optString("abi", "any"),
                        filename = o.getString("filename"),
                        sizeBytes = o.getLong("sizeBytes"),
                        sha256 = o.getString("sha256").lowercase(),
                        checksumOrigin = o.getString("checksumOrigin"),
                        sourceUrl = o.getString("sourceUrl"),
                        sourceNote = o.optString("sourceNote", ""),
                        license = o.optString("license", ""),
                        kind = o.getString("kind"),
                        url = o.getString("url"),
                    ),
                )
            }
        }
    }
}

class InstallStore(private val context: Context) {
    private val file = File(context.filesDir, "installed.json")

    fun read(): Map<String, InstallRecord> {
        if (!file.exists()) return emptyMap()
        val array = JSONArray(file.readText())
        val out = linkedMapOf<String, InstallRecord>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val rec = InstallRecord(
                id = o.getString("id"),
                filename = o.getString("filename"),
                sha256 = o.getString("sha256"),
                bytes = o.getLong("bytes"),
                savedAt = o.getLong("savedAt"),
                extracted = o.optBoolean("extracted", false),
            )
            out[rec.id] = rec
        }
        return out
    }

    fun write(records: Map<String, InstallRecord>) {
        val array = JSONArray()
        records.values.forEach { rec ->
            array.put(
                JSONObject()
                    .put("id", rec.id)
                    .put("filename", rec.filename)
                    .put("sha256", rec.sha256)
                    .put("bytes", rec.bytes)
                    .put("savedAt", rec.savedAt)
                    .put("extracted", rec.extracted),
            )
        }
        file.writeText(array.toString())
    }
}

class PayloadClient {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    fun assertAllowed(url: String) {
        val uri = java.net.URI(url)
        if (uri.scheme != "https") error("Yalnız https qəbul edilir")
        val host = uri.host?.lowercase() ?: error("Ünvanın hostu yoxdur")
        val allowed = host == "github.com" ||
            host == "githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
        if (!allowed) error("Bu mənbə icazəli deyil: $host")
    }

    fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        assertAllowed(url)
        val request = Request.Builder().url(url).header("Accept", "application/octet-stream").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Yükləmə alınmadı: HTTP ${response.code}")
            val body = response.body ?: error("Boş cavab")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        readTotal += n
                        if (total > 0) onProgress(readTotal.toFloat() / total.toFloat())
                    }
                }
            }
            if (dest.exists() && !dest.delete()) error("Köhnə fayl silinmədi")
            if (!tmp.renameTo(dest)) error("Fayl saxlanılmadı")
        }
    }
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun payloadFile(context: Context, component: Component): File {
    return File(File(context.filesDir, "downloads/${component.id}"), component.filename)
}

fun isLargePayload(component: Component) = component.sizeBytes >= 1_048_576L

fun seedSmallPayloads(context: Context, components: List<Component>, installed: Map<String, InstallRecord>): Map<String, InstallRecord> {
    val next = installed.toMutableMap()
    for (component in components) {
        if (isLargePayload(component)) continue
        val asset = "payloads/${component.filename}"
        val exists = runCatching { context.assets.open(asset).close() }.isSuccess
        if (!exists) continue
        val dest = payloadFile(context, component)
        val prior = next[component.id]
        if (prior != null && prior.sha256 == component.sha256 && dest.exists()) continue
        dest.parentFile?.mkdirs()
        context.assets.open(asset).use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        val sum = sha256(dest)
        if (sum != component.sha256) {
            dest.delete()
            continue
        }
        var extracted = false
        if (component.kind == "archive") {
            extractArchive(dest, File(context.filesDir, "unpacked/${component.id}"))
            extracted = true
        }
        next[component.id] = InstallRecord(
            id = component.id,
            filename = component.filename,
            sha256 = sum,
            bytes = dest.length(),
            savedAt = System.currentTimeMillis(),
            extracted = extracted,
        )
    }
    return next
}

fun extractArchive(archive: File, destination: File) {
    if (destination.exists()) {
        destination.deleteRecursively()
    }
    destination.mkdirs()
    openArchive(archive).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            writeEntry(input, entry, destination)
        }
    }
}

private fun openArchive(archive: File): ArchiveInputStream<out ArchiveEntry> {
    val name = archive.name.lowercase()
    val raw = BufferedInputStream(archive.inputStream())
    return when {
        name.endsWith(".tar.gz") || name.endsWith(".tgz") ->
            TarArchiveInputStream(GzipCompressorInputStream(raw))
        name.endsWith(".tar.xz") || name.endsWith(".txz") ->
            TarArchiveInputStream(XZCompressorInputStream(raw))
        name.endsWith(".zip") -> ZipArchiveInputStream(raw)
        else -> error("Arxiv formatı tanınmır")
    }
}

private fun writeEntry(
    input: ArchiveInputStream<out ArchiveEntry>,
    entry: ArchiveEntry,
    destination: File,
) {
    val name = entry.name.replace('\\', '/')
    if (name.isBlank()) return
    val dest = safeChild(destination, name)
    val tar = entry as? TarArchiveEntry
    if (tar?.isSymbolicLink == true || tar?.isLink == true) {
        val target = tar.linkName ?: return
        if (!relativeLink(target)) return
        dest.parentFile?.mkdirs()
        runCatching { Files.createSymbolicLink(dest.toPath(), java.nio.file.Paths.get(target)) }
        return
    }
    if (entry.isDirectory) {
        dest.mkdirs()
        return
    }
    dest.parentFile?.mkdirs()
    dest.outputStream().use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
        }
    }
}

private fun relativeLink(target: String): Boolean {
    if (target.startsWith("/") || target.contains('\\')) return false
    return target.split('/').none { it == ".." }
}

private fun safeChild(root: File, name: String): File {
    if (name.startsWith("/") || name.split('/').any { it == ".." }) {
        error("Təhlükəli arxiv yolu")
    }
    val rootPath = root.toPath().toAbsolutePath().normalize()
    val dest = rootPath.resolve(name).normalize()
    if (!dest.startsWith(rootPath)) error("Təhlükəli arxiv yolu")
    return dest.toFile()
}

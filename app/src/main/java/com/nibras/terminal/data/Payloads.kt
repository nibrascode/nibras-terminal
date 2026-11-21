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

fun isLargePayload(component: Component) = component.sizeBytes > 5L * 1024 * 1024

fun seedSmallPayloads(context: Context, components: List<Component>, installed: Map<String, InstallRecord>): Map<String, InstallRecord> {
    val next = installed.toMutableMap()
    for (component in components) {
        if (isLargePayload(component)) continue
        if (component.kind == "archive" && component.group == "environment") continue // rootfs handled by Rootfs
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

/**
 * Unpacks tar.gz / tar.xz / zip into [destination].
 *
 * Linux root filesystems need three things the old extractor dropped:
 * absolute symlinks (Alpine: /bin/sh -> /bin/busybox), hard links, and the
 * executable bit. Symlinks are kept verbatim (they are resolved inside PRoot),
 * but every write is resolved through already-created links *inside* the
 * destination, so a crafted archive can never write outside it.
 */
fun extractArchive(archive: File, destination: File, onProgress: ((Long) -> Unit)? = null) {
    deleteTree(destination)
    destination.mkdirs()
    val root = destination.toPath().toAbsolutePath().normalize()
    val hardLinks = mutableListOf<Pair<String, String>>()
    var count = 0L
    openArchive(archive).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            writeEntry(input, entry, root, hardLinks)
            count++
            if (count % 200 == 0L) onProgress?.invoke(count)
        }
    }
    // Hard links last, so their targets exist.
    for ((name, target) in hardLinks) {
        runCatching {
            val dest = resolveInside(root, name, followLast = false)
            val src = resolveInside(root, target, followLast = true)
            if (!Files.exists(src)) return@runCatching
            dest.parent?.let { Files.createDirectories(it) }
            Files.deleteIfExists(dest)
            try {
                android.system.Os.link(src.toString(), dest.toString())
            } catch (_: Throwable) {
                Files.copy(src, dest)
                runCatching { android.system.Os.chmod(dest.toString(), android.system.Os.stat(src.toString()).st_mode and 0x1ff) }
            }
        }
    }
}

private fun openArchive(archive: File): ArchiveInputStream<out ArchiveEntry> {
    val name = archive.name.lowercase()
    val raw = BufferedInputStream(archive.inputStream(), 256 * 1024)
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
    root: java.nio.file.Path,
    hardLinks: MutableList<Pair<String, String>>,
) {
    val name = entry.name.replace('\\', '/').removePrefix("./").trimEnd('/')
    if (name.isBlank() || name == ".") return
    checkName(name)
    val tar = entry as? TarArchiveEntry
    if (tar?.isLink == true) {
        val target = tar.linkName?.removePrefix("./") ?: return
        checkName(target)
        hardLinks += name to target
        return
    }
    val dest = resolveInside(root, name, followLast = false)
    if (tar?.isSymbolicLink == true) {
        val target = tar.linkName ?: return
        dest.parent?.let { Files.createDirectories(it) }
        Files.deleteIfExists(dest)
        runCatching { Files.createSymbolicLink(dest, java.nio.file.Paths.get(target)) }
        return
    }
    if (entry.isDirectory) {
        if (Files.isSymbolicLink(dest)) return
        if (!Files.isDirectory(dest)) Files.createDirectories(dest)
        tar?.let { setMode(dest, it.mode or 0x1c0) } // keep u+rwx on dirs
        return
    }
    if (tar != null && !tar.isFile) return // devices, fifos: not possible without root
    dest.parent?.let { Files.createDirectories(it) }
    if (Files.isSymbolicLink(dest)) Files.delete(dest)
    Files.newOutputStream(dest).use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
        }
    }
    val mode = tar?.mode ?: ((entry as? org.apache.commons.compress.archivers.zip.ZipArchiveEntry)?.unixMode ?: 0)
    if (mode != 0) setMode(dest, mode or 0x180) // keep u+rw
}

private fun setMode(path: java.nio.file.Path, mode: Int) {
    runCatching { android.system.Os.chmod(path.toString(), mode and 0xfff) }
}

private fun checkName(name: String) {
    if (name.split('/').any { it == ".." }) error("Təhlükəli arxiv yolu: $name")
}

/**
 * Resolves [name] (relative to the archive root) to a host path inside [root],
 * following symlinks that were already extracted as the guest would see them
 * (absolute targets are relative to [root]). Throws if the result escapes.
 */
fun resolveInside(root: java.nio.file.Path, name: String, followLast: Boolean): java.nio.file.Path {
    var current = root
    val parts = name.split('/').filter { it.isNotEmpty() && it != "." }.toMutableList()
    var hops = 0
    var i = 0
    while (i < parts.size) {
        val part = parts[i]
        val last = i == parts.size - 1
        if (part == "..") {
            current = if (current == root) root else (current.parent ?: root)
            i++
            continue
        }
        val next = current.resolve(part)
        if ((!last || followLast) && Files.isSymbolicLink(next)) {
            if (++hops > 40) error("Çox simvolik keçid: $name")
            val target = Files.readSymbolicLink(next).toString()
            val rest = parts.subList(i + 1, parts.size).toList()
            val targetParts = target.split('/').filter { it.isNotEmpty() && it != "." }
            if (target.startsWith("/")) current = root
            parts.clear()
            parts.addAll(targetParts + rest)
            i = 0
            continue
        }
        current = next
        i++
    }
    val normal = current.normalize()
    if (!normal.startsWith(root)) error("Təhlükəli arxiv yolu: $name")
    return normal
}

/** Deletes a directory tree without ever following symlinks (rootfs links point at guest paths). */
fun deleteTree(dir: File) {
    val path = dir.toPath()
    if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(path, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
        override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
            runCatching { Files.delete(file) }
            return java.nio.file.FileVisitResult.CONTINUE
        }
        override fun visitFileFailed(file: java.nio.file.Path, exc: java.io.IOException): java.nio.file.FileVisitResult {
            runCatching { Files.delete(file) }
            return java.nio.file.FileVisitResult.CONTINUE
        }
        override fun preVisitDirectory(d: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
            runCatching { android.system.Os.chmod(d.toString(), 0x1c0) }
            return java.nio.file.FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(d: java.nio.file.Path, exc: java.io.IOException?): java.nio.file.FileVisitResult {
            runCatching { Files.delete(d) }
            return java.nio.file.FileVisitResult.CONTINUE
        }
    })
}

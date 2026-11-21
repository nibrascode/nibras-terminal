package com.nibras.terminal

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** FileProvider may only serve cache/shared and cache/camera. Other files are copied there first. */
object SharedFiles {
    fun expose(context: Context, source: File): Uri {
        val shared = File(context.cacheDir, "shared").apply { mkdirs() }
        val camera = File(context.cacheDir, "camera")
        val file = source.canonicalFile
        val allowed = file.path.startsWith(shared.canonicalPath + File.separator) ||
            file.path.startsWith(camera.canonicalPath + File.separator)
        val target = if (allowed) file else {
            val safe = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
            File(shared, safe).also { out ->
                source.inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }
}

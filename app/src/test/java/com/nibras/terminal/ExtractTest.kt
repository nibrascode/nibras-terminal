package com.nibras.terminal

import com.nibras.terminal.data.extractArchive
import com.nibras.terminal.data.resolveInside
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExtractTest {
    private fun asset(name: String) = File("src/main/assets/payloads/$name.bin")

    private fun extractAlpine(arch: String): File {
        val src = asset("alpine-minirootfs-3.24.4-$arch.tar.gz")
        val archive = File.createTempFile("alpine", ".tar.gz").apply { deleteOnExit() }
        src.copyTo(archive, overwrite = true)
        val out = Files.createTempDirectory("rootfs-$arch").toFile()
        extractArchive(archive, out)
        return out
    }

    @Test
    fun alpineArm32KeepsAbsoluteSymlinks() {
        val root = extractAlpine("armv7")
        val sh = File(root, "bin/sh").toPath()
        assertTrue("bin/sh must be a symlink", Files.isSymbolicLink(sh))
        assertEquals("/bin/busybox", Files.readSymbolicLink(sh).toString())
        assertTrue(File(root, "bin/busybox").length() > 100_000)
        assertTrue(File(root, "sbin/apk").exists() || Files.isSymbolicLink(File(root, "sbin/apk").toPath()))
        assertTrue(File(root, "usr/bin/env").toPath().let { Files.isSymbolicLink(it) || Files.exists(it) })
        assertTrue(File(root, "etc/apk/repositories").isFile)
    }

    @Test
    fun alpineArm64Extracts() {
        val root = extractAlpine("aarch64")
        assertEquals("/bin/busybox", Files.readSymbolicLink(File(root, "bin/sh").toPath()).toString())
        assertTrue(File(root, "lib/ld-musl-aarch64.so.1").toPath().let { Files.exists(it) || Files.isSymbolicLink(it) })
    }

    @Test
    fun symlinkCannotEscapeRoot() {
        val archive = File.createTempFile("evil", ".tar.gz")
        TarArchiveOutputStream(java.util.zip.GZIPOutputStream(archive.outputStream())).use { tar ->
            val link = TarArchiveEntry("evil", TarArchiveEntry.LF_SYMLINK).apply { linkName = "/" }
            tar.putArchiveEntry(link); tar.closeArchiveEntry()
            val up = TarArchiveEntry("up", TarArchiveEntry.LF_SYMLINK).apply { linkName = "../../../../.." }
            tar.putArchiveEntry(up); tar.closeArchiveEntry()
            val data = "pwned".toByteArray()
            val file = TarArchiveEntry("evil/etc/owned").apply { size = data.size.toLong() }
            tar.putArchiveEntry(file); tar.write(data); tar.closeArchiveEntry()
            val file2 = TarArchiveEntry("up/tmp-owned").apply { size = data.size.toLong() }
            tar.putArchiveEntry(file2); tar.write(data); tar.closeArchiveEntry()
        }
        val out = Files.createTempDirectory("evil").toFile()
        extractArchive(archive, out)
        assertTrue("write through absolute link stays inside", File(out, "etc/owned").isFile)
        assertTrue("write through ../ link stays inside", File(out, "tmp-owned").isFile)
        assertFalse(File("/etc/owned").exists())
        val root = out.toPath().toAbsolutePath().normalize()
        assertTrue(resolveInside(root, "evil/etc", followLast = true).startsWith(root))
    }
}

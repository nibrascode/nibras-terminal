package com.nibras.terminal

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase encryption for `backup drive` / `backup encrypt`:
 * AES-256-GCM, key from PBKDF2-HMAC-SHA256 (310 000 rounds, random 16-byte salt), random 12-byte IV.
 * File layout: "NBK1" | salt(16) | rounds(int) | iv(12) | ciphertext+tag. Wrong passphrase or a changed file → error.
 */
object BackupCrypt {
    private val MAGIC = "NBK1".toByteArray()
    private const val ROUNDS = 310_000

    private fun key(pass: CharArray, salt: ByteArray, rounds: Int) =
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass, salt, rounds, 256)).encoded, "AES")

    fun encrypt(src: File, dst: File, pass: CharArray) {
        require(pass.size >= 8) { "passphrase: at least 8 characters" }
        val rnd = SecureRandom(); val salt = ByteArray(16).also(rnd::nextBytes); val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(pass, salt, ROUNDS), GCMParameterSpec(128, iv)) }
        DataOutputStream(dst.outputStream().buffered()).use { o ->
            o.write(MAGIC); o.write(salt); o.writeInt(ROUNDS); o.write(iv); o.flush()
            CipherOutputStream(o, c).use { co -> src.inputStream().use { it.copyTo(co) } }
        }
    }

    fun decrypt(src: File, dst: File, pass: CharArray) {
        DataInputStream(src.inputStream().buffered()).use { i ->
            val m = ByteArray(4); i.readFully(m); require(m.contentEquals(MAGIC)) { "not a Nibras encrypted backup" }
            val salt = ByteArray(16); i.readFully(salt); val rounds = i.readInt(); val iv = ByteArray(12); i.readFully(iv)
            require(rounds in 10_000..10_000_000) { "damaged file" }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(pass, salt, rounds), GCMParameterSpec(128, iv)) }
            // GCM checks the tag at the end; on failure nothing partial is kept.
            val tmp = File(dst.path + ".part")
            try {
                runCatching { CipherInputStream(i, c).use { ci -> tmp.outputStream().use { ci.copyTo(it) } } }
                    .onFailure { throw IllegalArgumentException("wrong passphrase or damaged file") }
                if (tmp.length() == 0L && src.length() > 48) throw IllegalArgumentException("wrong passphrase or damaged file")
                if (!tmp.renameTo(dst)) { tmp.copyTo(dst, true) }
            } finally { tmp.delete() }
        }
    }
}

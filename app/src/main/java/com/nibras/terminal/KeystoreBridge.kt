package com.nibras.terminal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.RSAKeyGenParameterSpec

/** termux-keystore equivalent: keys live in the Android hardware-backed keystore and never leave it. */
object KeystoreBridge {
    private fun ks() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun run(context: Context, args: List<String>): String {
        if (GuestMode.on(context)) return "ERR\nguest mode"
        val cmd = args.firstOrNull() ?: return usage()
        if (cmd == "sign" || cmd == "delete") {
            val alias = args.getOrNull(1) ?: return usage()
            val which = if (cmd == "sign") "keystore-sign" else "keystore-delete"
            val raw = PromptActivity.ask(context, "sensitive", listOf(which, alias), timeoutSec = 30)
            if (JSONObject(raw).optString("text") != "yes") return "ERR\nrefused"
        }
        val ks = ks()
        return when (cmd) {
            "list" -> "OK\n" + JSONArray().apply {
                ks.aliases().toList().forEach { a ->
                    val o = JSONObject().put("alias", a)
                    runCatching {
                        val key = ks.getKey(a, null) as PrivateKey
                        val info = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)
                        o.put("algorithm", key.algorithm).put("size", info.keySize).put("hardware", @Suppress("DEPRECATION") info.isInsideSecureHardware)
                            .put("user_auth", info.isUserAuthenticationRequired)
                        if (args.contains("-d")) o.put("public_key", Base64.encodeToString(ks.getCertificate(a).publicKey.encoded, Base64.NO_WRAP))
                    }
                    put(o)
                }
            }.toString(2)
            "delete" -> { val a = args.getOrNull(1) ?: return usage(); ks.deleteEntry(a); "OK\ndeleted $a" }
            "generate" -> {
                val a = args.getOrNull(1) ?: return usage()
                var alg = "RSA"; var size = 0; var validity = -1
                var i = 2
                while (i < args.size) { when (args[i]) { "-a" -> alg = args.getOrElse(i + 1) { alg }.uppercase(); "-s" -> size = args.getOrNull(i + 1)?.toIntOrNull() ?: 0; "-u" -> validity = args.getOrNull(i + 1)?.toIntOrNull() ?: -1 }; i += 2 }
                val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                val b = KeyGenParameterSpec.Builder(a, purposes).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                if (alg == "EC") b.setAlgorithmParameterSpec(ECGenParameterSpec(when (size) { 384 -> "secp384r1"; 521 -> "secp521r1"; else -> "secp256r1" }))
                else b.setAlgorithmParameterSpec(RSAKeyGenParameterSpec(if (size in setOf(2048, 3072, 4096)) size else 2048, RSAKeyGenParameterSpec.F4))
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                if (validity >= 0) {
                    b.setUserAuthenticationRequired(true)
                    @Suppress("DEPRECATION") b.setUserAuthenticationValidityDurationSeconds(validity)
                }
                KeyPairGenerator.getInstance(if (alg == "EC") KeyProperties.KEY_ALGORITHM_EC else KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply { initialize(b.build()) }.generateKeyPair()
                "OK\n" + Base64.encodeToString(ks().getCertificate(a).publicKey.encoded, Base64.NO_WRAP)
            }
            "sign" -> {
                val a = args.getOrNull(1); val alg = args.getOrNull(2); val data = args.getOrNull(3)
                if (a == null || alg == null || data == null) return usage()
                val key = ks.getKey(a, null) as? PrivateKey ?: return "ERR\nno key $a"
                val s = Signature.getInstance(alg).apply { initSign(key); update(Base64.decode(data, Base64.DEFAULT)) }
                "OK\n" + Base64.encodeToString(s.sign(), Base64.NO_WRAP)
            }
            "verify" -> {
                val a = args.getOrNull(1); val alg = args.getOrNull(2); val data = args.getOrNull(3); val sig = args.getOrNull(4)
                if (a == null || alg == null || data == null || sig == null) return usage()
                val cert = ks.getCertificate(a) ?: return "ERR\nno key $a"
                val ok = Signature.getInstance(alg).apply { initVerify(cert.publicKey); update(Base64.decode(data, Base64.DEFAULT)) }.verify(Base64.decode(sig, Base64.DEFAULT))
                if (ok) "OK\ntrue" else "ERR\nfalse"
            }
            else -> usage()
        }
    }

    private fun usage() = "ERR\nnibras-keystore list [-d] | generate <alias> [-a RSA|EC] [-s size] [-u seconds] | delete <alias> | sign <alias> <SHA256withRSA|SHA256withECDSA|…> < data | verify <alias> <algorithm> <sigfile> < data"
}

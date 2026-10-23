package com.solis.launcher.billing

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Checks a Solis Pro licence issued by the checkout server after a paid Stripe session.
 *
 * Format: `base64url(payloadJson) + "." + base64url(signature)`, where the signature is ECDSA P-256
 * over SHA-256 of the first segment's ASCII bytes, in WebCrypto's raw `r || s` form. Only the
 * server holds the private key; the app embeds the public key, so verification works offline.
 *
 * Pure JVM so it can be unit tested without Android.
 */
class LicenseVerifier(publicKeyBase64: String) {

    private val publicKey: PublicKey? = runCatching {
        val der = Base64.getDecoder().decode(publicKeyBase64.trim())
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }.getOrNull()

    /** True when the build has a usable public key (direct builds must set one). */
    val isConfigured: Boolean get() = publicKey != null

    fun isValid(token: String?): Boolean {
        val key = publicKey ?: return false
        val parts = token?.trim()?.split('.') ?: return false
        if (parts.size != 2) return false
        return runCatching {
            val payload = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
            if (!payload.contains("\"product\":\"$PRODUCT\"")) return false
            val raw = Base64.getUrlDecoder().decode(parts[1])
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(parts[0].toByteArray(Charsets.US_ASCII))
                verify(rawToDer(raw))
            }
        }.getOrDefault(false)
    }

    companion object {
        const val PRODUCT = "solis_pro_lifetime"

        /** Converts a 64-byte IEEE P1363 `r || s` signature into the ASN.1 DER form Java expects. */
        internal fun rawToDer(raw: ByteArray): ByteArray {
            require(raw.size == 64) { "expected a 64-byte P-256 signature" }
            val r = BigInteger(1, raw.copyOfRange(0, 32)).toByteArray()
            val s = BigInteger(1, raw.copyOfRange(32, 64)).toByteArray()
            val body = ByteArrayOutputStream().apply {
                write(0x02); write(r.size); write(r)
                write(0x02); write(s.size); write(s)
            }.toByteArray()
            return byteArrayOf(0x30, body.size.toByte()) + body
        }
    }
}

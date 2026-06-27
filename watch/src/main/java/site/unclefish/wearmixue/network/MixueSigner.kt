package site.unclefish.wearmixue.network

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec

interface RequestSigner {
    val isConfigured: Boolean
    fun sign(params: Map<String, Any?>): String
}

class MixueSigner(private val privateKeyText: String) : RequestSigner {
    override val isConfigured: Boolean get() = privateKeyText.isNotBlank()

    override fun sign(params: Map<String, Any?>): String {
        require(isConfigured) {
            "Mixue private key is missing from BuildConfig.MIXUE_PRIVATE_KEY."
        }
        val privateKey = privateKeyText.toPrivateKey()
        val canonical = canonicalString(params)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(canonical.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    companion object {
        fun canonicalString(params: Map<String, Any?>): String {
            return params.keys.sorted().mapNotNull { key ->
                val value = params[key]
                if (value == null || value == "") {
                    null
                } else {
                    val encoded = when (value) {
                        is Map<*, *>, is Iterable<*>, is Array<*> -> JsonCodec.stringify(value)
                        else -> value.toString()
                    }
                    "$key=$encoded"
                }
            }.joinToString("&")
        }

        private fun String.toPrivateKey(): RSAPrivateKey {
            val pemText = replace("\\n", "\n")
                .replace("\\r", "\n")
            val isPkcs1 = pemText.contains("BEGIN RSA PRIVATE KEY")
            val normalized = pemText
                .replace(Regex("-----BEGIN (?:RSA )?PRIVATE KEY-----"), "")
                .replace(Regex("-----END (?:RSA )?PRIVATE KEY-----"), "")
                .replace("\\n", "")
                .replace("\\r", "")
                .replace("\\s".toRegex(), "")
            val keyBytes = Base64.decode(normalized, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(if (isPkcs1) pkcs1ToPkcs8(keyBytes) else keyBytes)
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey
        }

        private fun pkcs1ToPkcs8(pkcs1Bytes: ByteArray): ByteArray {
            val version = byteArrayOf(0x02, 0x01, 0x00)
            val rsaEncryptionOid = byteArrayOf(
                0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
                0x0D, 0x01, 0x01, 0x01
            )
            val algorithmIdentifier = derSequence(rsaEncryptionOid, byteArrayOf(0x05, 0x00))
            return derSequence(version, algorithmIdentifier, der(0x04, pkcs1Bytes))
        }

        private fun derSequence(vararg values: ByteArray): ByteArray =
            der(0x30, values.fold(ByteArray(0)) { acc, bytes -> acc + bytes })

        private fun der(tag: Int, value: ByteArray): ByteArray =
            byteArrayOf(tag.toByte()) + derLength(value.size) + value

        private fun derLength(length: Int): ByteArray {
            if (length < 128) return byteArrayOf(length.toByte())
            var remaining = length
            val bytes = mutableListOf<Byte>()
            while (remaining > 0) {
                bytes.add(0, (remaining and 0xff).toByte())
                remaining = remaining ushr 8
            }
            return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
        }
    }
}

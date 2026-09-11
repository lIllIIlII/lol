package com.yunx.app.data.backup

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object AuthCrypto {

    private const val MAGIC_V1 = "YUNX_AUTH_V1"
    private const val MAGIC_V2 = "YUNX_AUTH_V2"
    private const val ITERATIONS_V1 = 10_000
    private const val ITERATIONS_V2 = 210_000
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val GCM_TAG_BITS = 128

    fun encrypt(plain: String, password: String): String {
        require(password.length >= 8) { "备份口令至少 8 位" }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS_V2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val magic = MAGIC_V2.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(magic.size + salt.size + iv.size + ciphertext.size)
        System.arraycopy(magic, 0, payload, 0, magic.size)
        System.arraycopy(salt, 0, payload, magic.size, salt.size)
        System.arraycopy(iv, 0, payload, magic.size + salt.size, iv.size)
        System.arraycopy(ciphertext, 0, payload, magic.size + salt.size + iv.size, ciphertext.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(data: String, password: String): String {
        val payload = Base64.decode(data.trim(), Base64.NO_WRAP)
        val magicV1 = MAGIC_V1.toByteArray(Charsets.UTF_8)
        val magicV2 = MAGIC_V2.toByteArray(Charsets.UTF_8)
        val (magic, iterations) = when {
            payload.copyOfRange(0, minOf(payload.size, magicV2.size)).contentEquals(magicV2) ->
                magicV2 to ITERATIONS_V2
            payload.copyOfRange(0, minOf(payload.size, magicV1.size)).contentEquals(magicV1) ->
                magicV1 to ITERATIONS_V1
            else -> throw IllegalArgumentException("不是有效的加密备份文件")
        }
        require(payload.size >= magic.size + SALT_SIZE + IV_SIZE) { "加密备份文件已损坏" }
        val salt = payload.copyOfRange(magic.size, magic.size + SALT_SIZE)
        val iv = payload.copyOfRange(magic.size + SALT_SIZE, magic.size + SALT_SIZE + IV_SIZE)
        val ciphertext = payload.copyOfRange(magic.size + SALT_SIZE + IV_SIZE, payload.size)
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun isEncrypted(data: String): Boolean = runCatching {
        val payload = Base64.decode(data.trim(), Base64.NO_WRAP)
        val magicV1 = MAGIC_V1.toByteArray(Charsets.UTF_8)
        val magicV2 = MAGIC_V2.toByteArray(Charsets.UTF_8)
        (payload.size >= magicV1.size && payload.copyOfRange(0, magicV1.size).contentEquals(magicV1)) ||
            (payload.size >= magicV2.size && payload.copyOfRange(0, magicV2.size).contentEquals(magicV2))
    }.getOrDefault(false)

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        } finally {
            spec.clearPassword()
        }
    }
}

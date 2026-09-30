package com.adityoarr.securevaultnotes.crypto.cipher

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object AesGcmEngine {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12 // Standar GCM adalah 12 byte

    fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val cipherText = cipher.doFinal(data)
        // Format: [12-byte IV] + [CipherText + GCM Auth Tag]
        return iv + cipherText
    }

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        if (data.size <= IV_LENGTH_BYTES) throw IllegalArgumentException("Data terlalu pendek / corrupt.")

        val iv = data.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = data.copyOfRange(IV_LENGTH_BYTES, data.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        // Jika file dimanipulasi, doFinal akan melempar AEADBadTagException
        return cipher.doFinal(cipherText)
    }
}
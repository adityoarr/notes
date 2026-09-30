package com.adityoarr.securevaultnotes.crypto.kdf

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

object Argon2Engine {
    // Parameter Argon2id yang dioptimalkan untuk Mobile (Balance antara keamanan & kecepatan)
    private const val MEMORY_COST_KB = 32768 // 32 MB (Mencegah brute-force GPU/ASIC)
    private const val ITERATIONS = 4
    private const val PARALLELISM = 4
    private const val KEY_LENGTH = 32 // 256-bit untuk AES-256

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Men-derive password menjadi Master Key.
     * PENTING: Panggil password.fill('\u0000') setelah fungsi ini selesai untuk menghapus jejak di RAM.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(MEMORY_COST_KB)
            .withIterations(ITERATIONS)
            .withParallelism(PARALLELISM)
            .withSalt(salt)

        val generator = Argon2BytesGenerator()
        generator.init(builder.build())

        val result = ByteArray(KEY_LENGTH)
        generator.generateBytes(password, result)
        return result
    }
}
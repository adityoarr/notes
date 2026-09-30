package com.adityoarr.securevaultnotes.crypto.share

import com.adityoarr.securevaultnotes.crypto.cipher.AesGcmEngine
import com.adityoarr.securevaultnotes.crypto.kdf.Argon2Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SecureVaultEngine {

    private val MAGIC = "SVLT".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_LENGTH = 16
    private const val HEADER_LENGTH = 4 + 1 + SALT_LENGTH // 21 byte

    /**
     * Format file: [4 MAGIC][1 VERSION][16 SALT][AES-GCM payload (IV di depan payload)]
     */
    suspend fun createSecureVaultFile(
        zipPayload: ByteArray,
        password: CharArray,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        val salt = Argon2Engine.generateSalt()
        val key = Argon2Engine.deriveKey(password, salt)
        try {
            val encrypted = AesGcmEngine.encrypt(zipPayload, key)
            outputFile.outputStream().use { out ->
                out.write(MAGIC)
                out.write(VERSION.toInt())
                out.write(salt)
                out.write(encrypted)
            }
        } finally {
            password.fill('\u0000')
            key.fill(0)
        }
    }

    suspend fun decryptSecureVaultFile(
        inputFile: File,
        password: CharArray
    ): ByteArray = withContext(Dispatchers.IO) {
        val bytes = inputFile.readBytes()
        if (bytes.size <= HEADER_LENGTH) {
            password.fill('\u0000')
            throw SecurityException("File tidak valid atau corrupt.")
        }
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC)) {
            password.fill('\u0000')
            throw SecurityException("Bukan file Secure Vault.")
        }
        if (bytes[4] != VERSION) {
            password.fill('\u0000')
            throw SecurityException("Versi file tidak didukung.")
        }
        val salt = bytes.copyOfRange(5, 5 + SALT_LENGTH)
        val encrypted = bytes.copyOfRange(HEADER_LENGTH, bytes.size)
        val key = Argon2Engine.deriveKey(password, salt)
        try {
            AesGcmEngine.decrypt(encrypted, key)
        } catch (e: Exception) {
            throw SecurityException("Password salah atau file telah dimodifikasi.")
        } finally {
            password.fill('\u0000')
            key.fill(0)
        }
    }

    fun createZipPayload(manifestJson: String, attachments: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            attachments.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun extractZipPayload(zipBytes: ByteArray): Pair<String, Map<String, ByteArray>> {
        var manifest = ""
        val attachments = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val bytes = zis.readBytes()
                if (entry.name == "manifest.json") manifest = String(bytes, Charsets.UTF_8)
                else attachments[entry.name] = bytes
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return manifest to attachments
    }
}
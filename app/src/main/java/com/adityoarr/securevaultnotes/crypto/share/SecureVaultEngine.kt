package com.adityoarr.securevaultnotes.crypto.share

import com.adityoarr.securevaultnotes.crypto.cipher.AesGcmEngine
import com.adityoarr.securevaultnotes.crypto.kdf.Argon2Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SecureVaultEngine {

    private val MAGIC_BYTES = "SVLT".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 0x01
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val HEADER_LENGTH = 4 + 1 + SALT_LENGTH + IV_LENGTH // 33 bytes

    /**
     * Membuat file .securevault dari payload ZIP yang sudah disiapkan.
     * @param zipPayload ByteArray berisi ZIP archive (manifest.json + media/)
     * @param password Password yang dipilih user untuk sharing
     * @param outputFile File tujuan .securevault
     */
    suspend fun createSecureVaultFile(
        zipPayload: ByteArray,
        password: CharArray,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        val salt = Argon2Engine.generateSalt()
        val key = Argon2Engine.deriveKey(password, salt)

        try {
            val encryptedPayload = AesGcmEngine.encrypt(zipPayload, key)

            FileOutputStream(outputFile).use { fos ->
                fos.write(MAGIC_BYTES)          // 4 bytes
                fos.write(VERSION.toInt())       // 1 byte
                fos.write(salt)                  // 16 bytes
                fos.write(encryptedPayload.copyOfRange(0, IV_LENGTH)) // 12 bytes (IV dari AesGcmEngine)
                fos.write(encryptedPayload)      // N bytes (IV + CipherText + AuthTag)
                fos.flush()
            }
        } finally {
            password.fill('\u0000') // Wipe password dari memori
            key.fill(0)             // Wipe derived key dari memori
        }
    }

    /**
     * Membuka dan mendekripsi file .securevault.
     * @return ByteArray berisi ZIP payload yang sudah didekripsi
     * @throws SecurityException jika password salah atau file corrupt
     */
    suspend fun decryptSecureVaultFile(
        inputFile: File,
        password: CharArray
    ): ByteArray = withContext(Dispatchers.IO) {
        val fileBytes = inputFile.readBytes()

        // Validasi header
        if (fileBytes.size < HEADER_LENGTH) {
            password.fill('\u0000')
            throw SecurityException("File tidak valid atau corrupt.")
        }

        val magic = fileBytes.copyOfRange(0, 4)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            password.fill('\u0000')
            throw SecurityException("Bukan file Secure Vault yang valid.")
        }

        val version = fileBytes[4]
        if (version != VERSION) {
            password.fill('\u0000')
            throw SecurityException("Versi file tidak didukung.")
        }

        val salt = fileBytes.copyOfRange(5, 5 + SALT_LENGTH)
        val encryptedData = fileBytes.copyOfRange(5 + SALT_LENGTH, fileBytes.size)

        try {
            val key = Argon2Engine.deriveKey(password, salt)
            val decryptedPayload = AesGcmEngine.decrypt(encryptedData, key)
            key.fill(0)
            decryptedPayload
        } catch (e: Exception) {
            // AES-GCM akan throw AEADBadTagException jika password salah atau data corrupt
            password.fill('\u0000')
            throw SecurityException("Password salah atau file telah dimodifikasi.")
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Membuat ZIP payload dari list notes + attachments.
     */
    fun createZipPayload(
        notesJson: String,
        attachments: Map<String, ByteArray> // fileName -> fileBytes
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Tulis manifest.json
            val manifestEntry = ZipEntry("manifest.json")
            zos.putNextEntry(manifestEntry)
            zos.write(notesJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Tulis attachments
            attachments.forEach { (fileName, bytes) ->
                val entry = ZipEntry("media/$fileName")
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    /**
     * Ekstrak ZIP payload menjadi notes JSON + attachments.
     */
    fun extractZipPayload(zipBytes: ByteArray): Pair<String, Map<String, ByteArray>> {
        var manifestJson = ""
        val attachments = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryBytes = zis.readBytes()
                when {
                    entry.name == "manifest.json" -> {
                        manifestJson = String(entryBytes, Charsets.UTF_8)
                    }
                    entry.name.startsWith("media/") -> {
                        val fileName = entry.name.substringAfter("media/")
                        attachments[fileName] = entryBytes
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return Pair(manifestJson, attachments)
    }
}
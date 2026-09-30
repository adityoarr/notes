package com.adityoarr.securevaultnotes.crypto.backup

import com.adityoarr.securevaultnotes.crypto.cipher.AesGcmEngine
import com.adityoarr.securevaultnotes.crypto.kdf.Argon2Engine
import com.adityoarr.securevaultnotes.crypto.share.SecureVaultEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BackupEngine {

    private val BACKUP_MAGIC = "SVBK".toByteArray(Charsets.US_ASCII)

    /**
     * Export seluruh vault menjadi satu file terenkripsi.
     * Menggunakan format yang sama dengan .securevault tapi dengan magic bytes berbeda.
     */
    suspend fun createBackup(
        allNotesJson: String,
        allAttachments: Map<String, ByteArray>,
        password: CharArray,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        val zipPayload = SecureVaultEngine.createZipPayload(allNotesJson, allAttachments)
        val salt = Argon2Engine.generateSalt()
        val key = Argon2Engine.deriveKey(password, salt)

        try {
            val encryptedPayload = AesGcmEngine.encrypt(zipPayload, key)

            outputFile.outputStream().use { fos ->
                fos.write(BACKUP_MAGIC)
                fos.write(0x01) // version
                fos.write(salt)
                fos.write(encryptedPayload)
                fos.flush()
            }
        } finally {
            password.fill('\u0000')
            key.fill(0)
        }
    }

    /**
     * Import/Restore dari file backup.
     */
    suspend fun restoreBackup(
        backupFile: File,
        password: CharArray
    ): Pair<String, Map<String, ByteArray>> = withContext(Dispatchers.IO) {
        val fileBytes = backupFile.readBytes()

        if (fileBytes.size < 33) {
            password.fill('\u0000')
            throw SecurityException("File backup tidak valid.")
        }

        val magic = fileBytes.copyOfRange(0, 4)
        if (!magic.contentEquals(BACKUP_MAGIC)) {
            password.fill('\u0000')
            throw SecurityException("Bukan file backup Secure Vault.")
        }

        val salt = fileBytes.copyOfRange(5, 21)
        val encryptedData = fileBytes.copyOfRange(21, fileBytes.size)

        try {
            val key = Argon2Engine.deriveKey(password, salt)
            val decryptedZip = AesGcmEngine.decrypt(encryptedData, key)
            key.fill(0)
            SecureVaultEngine.extractZipPayload(decryptedZip)
        } catch (e: Exception) {
            password.fill('\u0000')
            throw SecurityException("Password backup salah atau file corrupt.")
        } finally {
            password.fill('\u0000')
        }
    }
}
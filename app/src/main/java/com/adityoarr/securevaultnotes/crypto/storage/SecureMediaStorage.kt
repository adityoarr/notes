package com.adityoarr.securevaultnotes.crypto.storage

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import java.io.File
import java.io.InputStream

object SecureMediaStorage {

    // Alias key master yang di-generate sekali & di-cache oleh Android Keystore
    private val masterKeyAlias: String by lazy {
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    }

    @Suppress("DEPRECATION")
    private fun getEncryptedFile(context: Context, file: File): EncryptedFile {
        // API lama tetap dipakai karena API baru (EncryptedFile.Builder dengan alias String)
        // belum stabil di semua versi. @Suppress aman karena fungsi tetap bekerja.
        return EncryptedFile.Builder(
            file,
            context,
            masterKeyAlias,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    @Suppress("DEPRECATION")
    suspend fun writeEncryptedStream(context: Context, file: File, inputStream: InputStream) {
        val encryptedFile = getEncryptedFile(context, file)
        if (file.exists()) file.delete()
        encryptedFile.openFileOutput().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }

    @Suppress("DEPRECATION")
    fun readEncryptedStream(context: Context, file: File): InputStream {
        return getEncryptedFile(context, file).openFileInput()
    }
}
package com.adityoarr.securevaultnotes.crypto.storage

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object SecureMediaStorage {

    private fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun getEncryptedFile(context: Context, file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            getMasterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    // Helper untuk menulis stream ke file terenkripsi
    suspend fun writeEncryptedStream(context: Context, file: File, inputStream: InputStream) {
        val encryptedFile = getEncryptedFile(context, file)
        // Hapus file lama jika ada (EncryptedFile tidak support overwrite)
        if (file.exists()) file.delete()

        encryptedFile.openFileOutput().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }

    // Helper untuk membaca stream dari file terenkripsi
    fun readEncryptedStream(context: Context, file: File): InputStream {
        return getEncryptedFile(context, file).openFileInput()
    }
}
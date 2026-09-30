package com.adityoarr.securevaultnotes.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityoarr.securevaultnotes.crypto.share.SecureVaultEngine
import com.adityoarr.securevaultnotes.crypto.storage.SecureMediaStorage
import com.adityoarr.securevaultnotes.data.repository.NoteRepository
import com.adityoarr.securevaultnotes.domain.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

data class ShareUiState(
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val shareFileUri: String? = null,
    val error: String? = null,
    val step: String = ""
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    /**
     * Share satu atau banyak note menjadi file .securevault
     */
    fun shareNotes(notes: List<Note>, password: CharArray) {
        _uiState.update { it.copy(isProcessing = true, step = "Menyiapkan data...", progress = 0.1f) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Siapkan manifest JSON
                _uiState.update { it.copy(step = "Menyiapkan metadata...", progress = 0.2f) }
                val notesJson = Json.encodeToString(notes)

                // 2. Kumpulkan semua attachment (dekripsi dari EncryptedFile lokal)
                _uiState.update { it.copy(step = "Mendekripsi lampiran...", progress = 0.4f) }
                val attachments = mutableMapOf<String, ByteArray>()
                var totalAttachments = notes.sumOf { it.attachments.size }
                var processedAttachments = 0

                notes.forEach { note ->
                    note.attachments.forEach { attachment ->
                        val file = File(attachment.filePath)
                        if (file.exists()) {
                            val decryptedBytes = SecureMediaStorage
                                .readEncryptedStream(context, file)
                                .use { it.readBytes() }
                            attachments[file.name] = decryptedBytes
                        }
                        processedAttachments++
                        val progress = 0.4f + (0.3f * processedAttachments / totalAttachments.coerceAtLeast(1))
                        _uiState.update { it.copy(progress = progress) }
                    }
                }

                // 3. Buat ZIP payload
                _uiState.update { it.copy(step = "Membungkus paket...", progress = 0.7f) }
                val zipPayload = SecureVaultEngine.createZipPayload(notesJson, attachments)

                // 4. Enkripsi dengan password sharing
                _uiState.update { it.copy(step = "Mengenkripsi dengan AES-256-GCM...", progress = 0.85f) }
                val outputFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.securevault")
                SecureVaultEngine.createSecureVaultFile(zipPayload, password, outputFile)

                // 5. Selesai - siap untuk di-share via Intent
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        progress = 1f,
                        step = "Selesai",
                        shareFileUri = outputFile.absolutePath
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isProcessing = false, error = e.message ?: "Terjadi kesalahan saat share.")
                    }
                }
            }
        }
    }

    /**
     * Terima dan dekripsi file .securevault dari luar
     */
    fun importSecureVaultFile(fileUri: File, password: CharArray, onComplete: (List<Note>) -> Unit) {
        _uiState.update { it.copy(isProcessing = true, step = "Mendekripsi file...", progress = 0.3f) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val zipPayload = SecureVaultEngine.decryptSecureVaultFile(fileUri, password)
                _uiState.update { it.copy(step = "Mengekstrak data...", progress = 0.7f) }

                val (manifestJson, attachments) = SecureVaultEngine.extractZipPayload(zipPayload)
                val notes: List<Note> = Json.decodeFromString(manifestJson)

                // Simpan attachment ke EncryptedFile lokal
                _uiState.update { it.copy(step = "Menyimpan lampiran terenkripsi...", progress = 0.85f) }
                attachments.forEach { (fileName, bytes) ->
                    val targetFile = File(context.filesDir, "imported_${System.currentTimeMillis()}_$fileName")
                    SecureMediaStorage.getEncryptedFile(context, targetFile).openFileOutput().use { out ->
                        out.write(bytes)
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isProcessing = false, progress = 1f) }
                    onComplete(notes)
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    password.fill('\u0000')
                    _uiState.update { it.copy(isProcessing = false, error = e.message) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    password.fill('\u0000')
                    _uiState.update { it.copy(isProcessing = false, error = "File tidak valid atau corrupt.") }
                }
            }
        }
    }

    fun resetState() {
        _uiState.update { ShareUiState() }
    }
}
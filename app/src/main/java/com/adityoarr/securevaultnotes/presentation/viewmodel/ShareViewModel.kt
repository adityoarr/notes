package com.adityoarr.securevaultnotes.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityoarr.securevaultnotes.crypto.share.SecureVaultEngine
import com.adityoarr.securevaultnotes.crypto.storage.SecureMediaStorage
import com.adityoarr.securevaultnotes.data.local.db.VaultDatabaseProvider
import com.adityoarr.securevaultnotes.data.local.entity.AttachmentEntity
import com.adityoarr.securevaultnotes.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ShareUiState(
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val step: String = "",
    val error: String? = null,
    val readyFile: File? = null,
    val importedCount: Int? = null,
    val pendingImportFile: File? = null // ← PINDAH KE STATE
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDatabaseProvider: VaultDatabaseProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    /** Set file pending import (dipanggil dari MainActivity) */
    fun setPendingImportFile(file: File?) {
        _uiState.update { it.copy(pendingImportFile = file) }
    }

    fun consumeReadyFile() = _uiState.update { it.copy(readyFile = null) }
    fun consumeImportResult() = _uiState.update { it.copy(importedCount = null) }
    fun resetError() = _uiState.update { it.copy(error = null) }

    // ================= EXPORT =================

    fun exportSecureVault(notes: List<com.adityoarr.securevaultnotes.domain.model.Note>, password: CharArray) {
        _uiState.update { it.copy(isProcessing = true, progress = 0.1f, step = "Menyiapkan paket...", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val attachments = mutableMapOf<String, ByteArray>()
                val manifest = buildManifest(notes, attachments)

                _uiState.update { it.copy(progress = 0.6f, step = "Membungkus ZIP...") }
                val zip = SecureVaultEngine.createZipPayload(manifest, attachments)

                _uiState.update { it.copy(progress = 0.8f, step = "Mengenkripsi AES-256-GCM...") }
                val outDir = File(context.cacheDir, "shared").apply { mkdirs() }
                val outFile = File(outDir, "vault_${System.currentTimeMillis()}.securevault")
                SecureVaultEngine.createSecureVaultFile(zip, password, outFile)

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isProcessing = false, progress = 1f, step = "Selesai", readyFile = outFile) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    password.fill('\u0000')
                    _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Gagal membuat file share.") }
                }
            }
        }
    }

    private suspend fun buildManifest(
        notes: List<com.adityoarr.securevaultnotes.domain.model.Note>,
        out: MutableMap<String, ByteArray>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val notesArr = JSONArray()
        notes.forEachIndexed { ni, note ->
            val nObj = JSONObject()
            nObj.put("title", note.title)
            nObj.put("description", note.description)
            nObj.put("createdAt", note.createdAt)
            nObj.put("updatedAt", note.updatedAt)
            val attArr = JSONArray()
            note.attachments.forEachIndexed { ai, att ->
                val src = File(att.filePath)
                if (src.exists()) {
                    val entry = "media/${ni}_${ai}.bin"
                    val bytes = SecureMediaStorage.readEncryptedStream(context, src).use { it.readBytes() }
                    out[entry] = bytes
                    val aObj = JSONObject()
                    aObj.put("file", entry)
                    aObj.put("mimeType", att.mimeType)
                    aObj.put("createdAt", att.createdAt)
                    attArr.put(aObj)
                }
            }
            nObj.put("attachments", attArr)
            notesArr.put(nObj)
        }
        root.put("notes", notesArr)
        return root.toString()
    }

    // ================= IMPORT =================

    fun importSecureVault(password: CharArray) {
        val file = _uiState.value.pendingImportFile
        if (file == null || !file.exists()) {
            password.fill('\u0000')
            _uiState.update { it.copy(error = "File tidak ditemukan. Coba pilih ulang file .securevault.") }
            return
        }

        _uiState.update { it.copy(isProcessing = true, progress = 0.2f, step = "Mendekripsi file...", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val zip = SecureVaultEngine.decryptSecureVaultFile(file, password)
                _uiState.update { it.copy(progress = 0.6f, step = "Mengekstrak data...") }
                val (manifest, attachments) = SecureVaultEngine.extractZipPayload(zip)

                _uiState.update { it.copy(progress = 0.8f, step = "Menyimpan ke vault...") }
                val db = vaultDatabaseProvider.requireDb()
                val notesArr = JSONObject(manifest).getJSONArray("notes")
                var count = 0

                for (i in 0 until notesArr.length()) {
                    val nObj = notesArr.getJSONObject(i)
                    val now = System.currentTimeMillis()
                    val noteId = db.noteDao().insertNote(
                        NoteEntity(
                            title = nObj.optString("title", ""),
                            description = nObj.optString("description", ""),
                            createdAt = nObj.optLong("createdAt", now),
                            updatedAt = now
                        )
                    )
                    val attArr = nObj.optJSONArray("attachments") ?: JSONArray()
                    for (j in 0 until attArr.length()) {
                        val aObj = attArr.getJSONObject(j)
                        val bytes = attachments[aObj.getString("file")] ?: continue
                        val target = File(context.filesDir, "media_${now}_${UUID.randomUUID()}.enc")
                        SecureMediaStorage.writeEncryptedStream(context, target, ByteArrayInputStream(bytes))
                        db.attachmentDao().insertAttachment(
                            AttachmentEntity(
                                noteId = noteId,
                                filePath = target.absolutePath,
                                mimeType = aObj.optString("mimeType", "application/octet-stream"),
                                createdAt = now
                            )
                        )
                    }
                    count++
                }

                // Hapus file cache setelah sukses import
                file.delete()
                _uiState.update { it.copy(pendingImportFile = null) }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isProcessing = false, progress = 1f, importedCount = count) }
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isProcessing = false, error = e.message) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isProcessing = false, error = "File tidak valid atau corrupt.") }
                }
            }
        }
    }
}
package com.adityoarr.securevaultnotes.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityoarr.securevaultnotes.core.security.VaultSaltStore
import com.adityoarr.securevaultnotes.crypto.kdf.Argon2Engine
import com.adityoarr.securevaultnotes.data.local.db.AppDatabase
import com.adityoarr.securevaultnotes.data.local.db.VaultDatabaseProvider
import com.adityoarr.securevaultnotes.data.repository.AttachmentInput
import com.adityoarr.securevaultnotes.data.repository.NoteRepository
import com.adityoarr.securevaultnotes.data.repository.NoteRepositoryImpl
import com.adityoarr.securevaultnotes.domain.model.Attachment
import com.adityoarr.securevaultnotes.domain.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDatabaseProvider: VaultDatabaseProvider,
    private val saltStore: VaultSaltStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var repository: NoteRepository? = null
    private var observeJob: Job? = null

    init {
        _uiState.update { it.copy(vaultExists = vaultDatabaseProvider.vaultExists()) }
    }

    fun getNoteById(id: Long): Note? = _uiState.value.notes.firstOrNull { it.id == id }

    private fun attachRepository(db: AppDatabase) {
        repository = NoteRepositoryImpl(
            context = context,
            noteDao = db.noteDao(),
            attachmentDao = db.attachmentDao()
        )
    }

    private fun observeNotes() {
        observeJob?.cancel()
        val repo = repository ?: return
        observeJob = viewModelScope.launch {
            repo.observeNotes().collect { notes ->
                _uiState.update { it.copy(notes = notes, isLoading = false) }
            }
        }
    }

    fun setupVault(password: CharArray) {
        _uiState.update { it.copy(isLoading = true, unlockError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val salt = Argon2Engine.generateSalt()
                saltStore.saveSalt(salt)
                val masterKey = Argon2Engine.deriveKey(password, salt)
                password.fill('\u0000')

                val db = vaultDatabaseProvider.createRealVault(masterKey)
                masterKey.fill(0)
                attachRepository(db)

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isUnlocked = true, isLoading = false, isDecoyVault = false, vaultExists = true)
                    }
                    observeNotes()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    password.fill('\u0000')
                    _uiState.update { it.copy(isLoading = false, unlockError = "Gagal membuat vault: ${e.message}") }
                }
            }
        }
    }

    fun unlockVault(password: CharArray) {
        _uiState.update { it.copy(isLoading = true, unlockError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val salt = saltStore.getSalt()
            if (salt == null) {
                withContext(Dispatchers.Main) {
                    password.fill('\u0000')
                    _uiState.update { it.copy(isLoading = false, unlockError = "Vault belum dibuat.") }
                }
                return@launch
            }
            try {
                val masterKey = Argon2Engine.deriveKey(password, salt)
                password.fill('\u0000')

                val db = vaultDatabaseProvider.openVault(masterKey)
                masterKey.fill(0)
                attachRepository(db)

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isUnlocked = true,
                            isLoading = false,
                            isDecoyVault = vaultDatabaseProvider.isDecoySession
                        )
                    }
                    observeNotes()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    password.fill('\u0000')
                    _uiState.update { it.copy(isLoading = false, unlockError = "Terjadi kesalahan sistem.") }
                }
            }
        }
    }

    fun lockVault() {
        observeJob?.cancel()
        observeJob = null
        repository = null
        vaultDatabaseProvider.closeVault()
        clearPlaybackFiles()
        _uiState.update { VaultUiState(vaultExists = vaultDatabaseProvider.vaultExists()) }
    }

    fun saveNote(note: Note, newAttachments: List<AttachmentInput>) {
        val repo = repository ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.saveNote(note, newAttachments)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSaving = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSaving = false, unlockError = "Gagal menyimpan: ${e.message}") }
                }
            }
        }
    }

    fun deleteSelectedNotes() {
        val repo = repository ?: return
        val ids = _uiState.value.selectedNoteIds
        val notesToDelete = _uiState.value.notes.filter { it.id in ids }
        if (notesToDelete.isEmpty()) return

        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteNotes(notesToDelete)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isDeleting = false, selectedNoteIds = emptySet(), isBulkMode = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isDeleting = false, unlockError = "Gagal menghapus: ${e.message}") }
                }
            }
        }
    }

    fun deleteAttachment(attachment: Attachment) {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteAttachment(attachment)
            } catch (_: Exception) {
            }
        }
    }

    /** Dekripsi lampiran tersimpan ke memori (untuk preview gambar). */
    fun loadAttachmentBytes(attachment: Attachment, onLoaded: (ByteArray?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = try {
                repository?.openAttachmentDecrypted(attachment)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) { onLoaded(bytes) }
        }
    }

    /** Baca byte dari Uri mentah (untuk preview lampiran baru sebelum disimpan). */
    fun loadBytesFromUri(uri: Uri, onLoaded: (ByteArray?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) { onLoaded(bytes) }
        }
    }

    /** Dekripsi lampiran audio/video tersimpan ke file cache sementara untuk playback. */
    fun preparePlaybackFile(attachment: Attachment, onReady: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val bytes = repository?.openAttachmentDecrypted(attachment)
                if (bytes == null) null else writePlaybackFile(bytes, attachment.mimeType)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) { onReady(result) }
        }
    }

    /** Salin Uri mentah ke file cache sementara untuk playback pratinjau sebelum save. */
    fun preparePlaybackFileFromUri(uri: Uri, mimeType: String, onReady: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) null else writePlaybackFile(bytes, mimeType)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) { onReady(result) }
        }
    }

    private fun writePlaybackFile(bytes: ByteArray, mimeType: String): File {
        val dir = File(context.cacheDir, "playback").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.${extFor(mimeType)}")
        f.writeBytes(bytes)
        bytes.fill(0)
        return f
    }

    private fun extFor(mimeType: String): String = when {
        mimeType.startsWith("audio") -> when {
            mimeType.contains("mp4") -> "m4a"
            mimeType.contains("mpeg") -> "mp3"
            mimeType.contains("ogg") -> "ogg"
            mimeType.contains("wav") -> "wav"
            else -> "tmp"
        }
        mimeType.startsWith("video") -> when {
            mimeType.contains("webm") -> "webm"
            mimeType.contains("3gpp") -> "3gp"
            mimeType.contains("matroska") -> "mkv"
            mimeType.contains("mpeg") -> "mpg"
            else -> "mp4"
        }
        else -> "tmp"
    }

    /** Hapus seluruh file playback sementara (dipanggil saat viewer tutup & saat lock). */
    fun clearPlaybackFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            File(context.cacheDir, "playback").deleteRecursively()
        }
    }

    fun toggleNoteSelection(noteId: Long) {
        _uiState.update { state ->
            val newSelection = state.selectedNoteIds.toMutableSet()
            if (!newSelection.remove(noteId)) newSelection.add(noteId)
            state.copy(selectedNoteIds = newSelection)
        }
    }

    fun toggleBulkMode() {
        _uiState.update { it.copy(isBulkMode = !it.isBulkMode, selectedNoteIds = emptySet()) }
    }
}

data class VaultUiState(
    val vaultExists: Boolean = false,
    val isUnlocked: Boolean = false,
    val isDecoyVault: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val notes: List<Note> = emptyList(),
    val selectedNoteIds: Set<Long> = emptySet(),
    val isBulkMode: Boolean = false,
    val unlockError: String? = null
)
package com.adityoarr.securevaultnotes.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityoarr.securevaultnotes.core.security.VaultSaltStore
import com.adityoarr.securevaultnotes.crypto.kdf.Argon2Engine
import com.adityoarr.securevaultnotes.data.local.db.AppDatabase
import com.adityoarr.securevaultnotes.data.local.db.VaultDatabaseProvider
import com.adityoarr.securevaultnotes.data.repository.NoteRepository
import com.adityoarr.securevaultnotes.data.repository.NoteRepositoryImpl
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

    /** First launch: buat vault asli dengan password user. */
    fun setupVault(password: CharArray) {
        _uiState.update { it.copy(isLoading = true, unlockError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val salt = Argon2Engine.generateSalt()
                saltStore.saveSalt(salt) // KRITIS: simpan salt agar vault bisa dibuka lagi
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

    /** Unlock: password benar -> vault asli; password salah -> decoy (senyap). */
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

    /** LOGOUT/LOCK: tutup DB, buang repository, batalkan observasi, reset UI ke unlock. */
    fun lockVault() {
        observeJob?.cancel()
        observeJob = null
        repository = null
        vaultDatabaseProvider.closeVault()
        _uiState.update { VaultUiState(vaultExists = vaultDatabaseProvider.vaultExists()) }
    }

    fun saveNote(note: Note) {
        val repo = repository ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.saveNote(note, emptyList())
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
package com.adityoarr.securevaultnotes.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityoarr.securevaultnotes.crypto.kdf.Argon2Engine
import com.adityoarr.securevaultnotes.data.local.db.VaultDatabaseProvider
import com.adityoarr.securevaultnotes.data.repository.NoteRepository
import com.adityoarr.securevaultnotes.data.repository.NoteRepositoryImpl
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
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDatabaseProvider: VaultDatabaseProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    // Repository di-initialize SETELAH vault dibuka
    private var repository: NoteRepository? = null

    init {
        checkVaultExists()
    }

    private fun checkVaultExists() {
        _uiState.update { it.copy(vaultExists = vaultDatabaseProvider.vaultExists()) }
    }

    private fun observeNotes() {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.observeNotes().collect { notes ->
                _uiState.update { it.copy(notes = notes, isLoading = false) }
            }
        }
    }

    fun setupVault(password: CharArray) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val salt = Argon2Engine.generateSalt()
                val masterKey = Argon2Engine.deriveKey(password, salt)

                val db = vaultDatabaseProvider.createRealVault(masterKey)
                password.fill('\u0000')

                // Initialize repository SETELAH vault dibuka
                repository = NoteRepositoryImpl(
                    context = context,
                    noteDao = db.noteDao(),
                    attachmentDao = db.attachmentDao()
                )

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isUnlocked = true,
                            isLoading = false,
                            isDecoyVault = false,
                            vaultExists = true
                        )
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
            try {
                val salt = ByteArray(16) { 0 } // TODO: Load dari EncryptedSharedPreferences
                val masterKey = Argon2Engine.deriveKey(password, salt)

                val db = vaultDatabaseProvider.openVault(masterKey)
                password.fill('\u0000')

                // Initialize repository SETELAH vault dibuka
                repository = NoteRepositoryImpl(
                    context = context,
                    noteDao = db.noteDao(),
                    attachmentDao = db.attachmentDao()
                )

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
                    _uiState.update { it.copy(isLoading = false, unlockError = "Password salah atau vault corrupt") }
                }
            }
        }
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
        val notesToDelete = _uiState.value.notes.filter { it.id in _uiState.value.selectedNoteIds }
        if (notesToDelete.isEmpty()) return

        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteNotes(notesToDelete)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isDeleting = false, selectedNoteIds = emptySet()) }
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
            if (newSelection.contains(noteId)) newSelection.remove(noteId) else newSelection.add(noteId)
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
    val isEncryptingMedia: Boolean = false,
    val notes: List<Note> = emptyList(),
    val selectedNoteIds: Set<Long> = emptySet(),
    val isBulkMode: Boolean = false,
    val unlockError: String? = null,
    val showFirstTimeWarning: Boolean = false
)
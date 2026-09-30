package com.adityoarr.securevaultnotes.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityoarr.securevaultnotes.crypto.kdf.Argon2Engine
import com.adityoarr.securevaultnotes.core.di.DatabaseModule
import com.adityoarr.securevaultnotes.data.repository.NoteRepository
import com.adityoarr.securevaultnotes.domain.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        observeNotes()
    }

    private fun observeNotes() {
        viewModelScope.launch {
            noteRepository.observeNotes().collect { notes ->
                _uiState.update { it.copy(notes = notes, isLoading = false) }
            }
        }
    }

    /**
     * Logika Unlock Vault dengan Decoy Routing.
     * Tidak ada password matching. Password langsung di-derive menjadi Key.
     * Jika Key salah, SQLCipher akan throw exception dan kita arahkan ke Decoy DB.
     */
    fun unlockVault(password: CharArray) {
        _uiState.update { it.copy(isLoading = true, unlockError = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Generate Salt (Diambil dari secure storage, disederhanakan di sini)
                val salt = getOrCreateVaultSalt()

                // 2. Derive Key menggunakan Argon2id
                val masterKey = Argon2Engine.deriveKey(password, salt)

                // 3. Coba buka Database. Jika password salah, DatabaseModule.provideDatabaseInstance
                // akan otomatis fallback ke Decoy Database tanpa melempar error ke UI.
                val db = DatabaseModule.provideDatabaseInstance(
                    context = getAppContext(), // Asumsi ada helper untuk context
                    derivedKey = masterKey
                )

                // 4. Wipe password dari memori
                password.fill('\u0000')

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isUnlocked = true,
                            isLoading = false,
                            isDecoyVault = isDecoyDatabase(db) // Deteksi apakah ini Decoy atau Real
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    password.fill('\u0000')
                    _uiState.update { it.copy(isLoading = false, unlockError = "Terjadi kesalahan sistem.") }
                }
            }
        }
    }

    fun saveNote(note: Note) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteRepository.saveNote(note, emptyList()) // Attachment handled di Editor
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSaving = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    fun deleteSelectedNotes() {
        val notesToDelete = _uiState.value.notes.filter { it.id in _uiState.value.selectedNoteIds }
        if (notesToDelete.isEmpty()) return

        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.deleteNotes(notesToDelete)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isDeleting = false, selectedNoteIds = emptySet()) }
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

    private fun getOrCreateVaultSalt(): ByteArray {
        // Implementasi nyata: Ambil dari EncryptedSharedPreferences atau generate saat first-launch
        return ByteArray(16) { 0 } // Simplifikasi untuk contoh
    }

    private fun isDecoyDatabase(db: Any): Boolean {
        // Implementasi nyata: Cek nama database atau flag internal
        return false // Simplifikasi
    }

    private fun getAppContext(): android.content.Context {
        // Implementasi nyata: Inject Application context
        throw NotImplementedError("Inject Application context di sini")
    }
}

data class VaultUiState(
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
    val showFirstTimeWarning: Boolean = false // Trigger warning dialog saat pertama kali
)
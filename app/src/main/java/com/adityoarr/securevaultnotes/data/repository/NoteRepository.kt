package com.adityoarr.securevaultnotes.data.repository

import com.adityoarr.securevaultnotes.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>
    suspend fun saveNote(note: Note, rawAttachments: List<java.io.InputStream>)
    suspend fun deleteNotes(notes: List<Note>)
    suspend fun exportNoteAsSecureVault(note: Note, targetFile: java.io.File, password: CharArray)
}
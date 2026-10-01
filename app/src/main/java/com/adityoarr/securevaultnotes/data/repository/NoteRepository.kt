package com.adityoarr.securevaultnotes.data.repository

import com.adityoarr.securevaultnotes.domain.model.Attachment
import com.adityoarr.securevaultnotes.domain.model.Note
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/** Stream mentah + mimeType untuk lampiran baru yang akan dienkripsi saat save. */
data class AttachmentInput(
    val stream: InputStream,
    val mimeType: String
)

interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>
    suspend fun saveNote(note: Note, rawAttachments: List<AttachmentInput>)
    suspend fun deleteNotes(notes: List<Note>)
    suspend fun deleteAttachment(attachment: Attachment)
    suspend fun openAttachmentDecrypted(attachment: Attachment): ByteArray
}
package com.adityoarr.securevaultnotes.data.repository

import android.content.Context
import com.adityoarr.securevaultnotes.crypto.storage.SecureMediaStorage
import com.adityoarr.securevaultnotes.data.local.db.dao.AttachmentDao
import com.adityoarr.securevaultnotes.data.local.db.dao.NoteDao
import com.adityoarr.securevaultnotes.data.local.entity.AttachmentEntity
import com.adityoarr.securevaultnotes.data.local.entity.NoteEntity
import com.adityoarr.securevaultnotes.domain.model.Attachment
import com.adityoarr.securevaultnotes.domain.model.Note
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val attachmentDao: AttachmentDao
) : NoteRepository {

    override fun observeNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { entities ->
            entities.map { entity ->
                val attachments = attachmentDao.getAttachmentsForNote(entity.id)
                Note(
                    id = entity.id,
                    title = entity.title,
                    description = entity.description,
                    attachments = attachments.map { it.toDomain() },
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }
    }

    override suspend fun saveNote(note: Note, rawAttachments: List<AttachmentInput>) =
        withContext(Dispatchers.IO) {
            val noteEntity = NoteEntity(
                id = note.id,
                title = note.title,
                description = note.description,
                createdAt = note.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            val noteId = noteDao.insertNote(noteEntity)

            rawAttachments.forEachIndexed { index, input ->
                val fileName = "media_${System.currentTimeMillis()}_$index.enc"
                val file = File(context.filesDir, fileName)
                SecureMediaStorage.writeEncryptedStream(context, file, input.stream)
                input.stream.close()

                attachmentDao.insertAttachment(
                    AttachmentEntity(
                        noteId = noteId,
                        filePath = file.absolutePath,
                        mimeType = input.mimeType,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

    override suspend fun deleteNotes(notes: List<Note>) = withContext(Dispatchers.IO) {
        notes.forEach { note ->
            note.attachments.forEach { attachment ->
                File(attachment.filePath).delete()
            }
        }
        noteDao.deleteNotes(notes.map { it.toEntity() })
    }

    override suspend fun deleteAttachment(attachment: Attachment) = withContext(Dispatchers.IO) {
        File(attachment.filePath).delete()
        attachmentDao.deleteAttachment(attachment.id)
    }

    override suspend fun openAttachmentDecrypted(attachment: Attachment): ByteArray =
        withContext(Dispatchers.IO) {
            SecureMediaStorage.readEncryptedStream(context, File(attachment.filePath))
                .use { it.readBytes() }
        }

    private fun AttachmentEntity.toDomain() =
        Attachment(id, noteId, filePath, mimeType, createdAt)

    private fun Note.toEntity() =
        NoteEntity(id, title, description, createdAt, updatedAt)
}
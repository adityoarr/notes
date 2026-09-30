package com.adityoarr.securevaultnotes.data.repository

import android.content.Context
import com.adityoarr.securevaultnotes.crypto.cipher.AesGcmEngine
import com.adityoarr.securevaultnotes.crypto.kdf.Argon2Engine
import com.adityoarr.securevaultnotes.crypto.storage.SecureMediaStorage
import com.adityoarr.securevaultnotes.data.local.db.dao.AttachmentDao
import com.adityoarr.securevaultnotes.data.local.db.dao.NoteDao
import com.adityoarr.securevaultnotes.data.local.entity.AttachmentEntity
import com.adityoarr.securevaultnotes.data.local.entity.NoteEntity
import com.adityoarr.securevaultnotes.domain.model.Attachment
import com.adityoarr.securevaultnotes.domain.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class NoteRepositoryImpl(
    private val context: Context,
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

    override suspend fun saveNote(note: Note, rawAttachments: List<InputStream>) = withContext(Dispatchers.IO) {
        val noteEntity = NoteEntity(
            id = note.id,
            title = note.title,
            description = note.description,
            createdAt = note.createdAt,
            updatedAt = System.currentTimeMillis()
        )

        val noteId = if (note.id == 0L) noteDao.insertNote(noteEntity) else {
            noteDao.insertNote(noteEntity)
            note.id
        }

        rawAttachments.forEach { stream ->
            val fileName = "media_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}.enc"
            val file = File(context.filesDir, fileName)
            SecureMediaStorage.writeEncryptedStream(context, file, stream)

            val attachmentEntity = AttachmentEntity(
                noteId = noteId,
                filePath = file.absolutePath,
                mimeType = "image/jpeg",
                createdAt = System.currentTimeMillis()
            )
            attachmentDao.insertAttachment(attachmentEntity)
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

    override suspend fun exportNoteAsSecureVault(note: Note, targetFile: File, password: CharArray) = withContext(Dispatchers.IO) {
        val salt = Argon2Engine.generateSalt()
        val key = Argon2Engine.deriveKey(password, salt)
        val payloadJson = "{'title':'${note.title}', 'desc':'${note.description}'}".toByteArray()
        val encryptedPayload = AesGcmEngine.encrypt(payloadJson, key)

        targetFile.outputStream().use { out ->
            out.write(salt)
            out.write(encryptedPayload)
        }

        password.fill('\u0000')
    }

    private fun AttachmentEntity.toDomain() = Attachment(id, noteId, filePath, mimeType, createdAt)
    private fun Note.toEntity() = NoteEntity(id, title, description, createdAt, updatedAt)
}
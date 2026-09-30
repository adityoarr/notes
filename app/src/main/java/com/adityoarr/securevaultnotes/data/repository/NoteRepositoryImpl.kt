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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
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
                // Map entity ke domain model (Termasuk load list attachment)
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
            noteDao.insertNote(noteEntity) // REPLACE strategy
            note.id
        }

        // Hapus attachment lama jika ini adalah update (Opsional, tergantung logic bisnis)
        // attachmentDao.deleteAttachmentsForNote(noteId)

        // Enkripsi dan simpan attachment baru
        rawAttachments.forEach { stream ->
            val fileName = "media_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}.enc"
            val file = File(context.filesDir, fileName)

            // Enkripsi file menggunakan Jetpack Security
            SecureMediaStorage.writeEncryptedStream(context, file, stream)

            // Simpan path ke database
            val attachmentEntity = AttachmentEntity(
                noteId = noteId,
                filePath = file.absolutePath,
                mimeType = "image/jpeg", // Simplifikasi, asumsikan ada logic deteksi mime
                createdAt = System.currentTimeMillis()
            )
            attachmentDao.insertAttachment(attachmentEntity)
        }
    }

    override suspend fun deleteNotes(notes: List<Note>) = withContext(Dispatchers.IO) {
        // 1. Hapus file fisik terenkripsi
        notes.forEach { note ->
            note.attachments.forEach { attachment ->
                File(attachment.filePath).delete()
            }
        }
        // 2. Hapus dari database (CASCADE akan otomatis menghapus AttachmentEntity)
        noteDao.deleteNotes(notes.map { it.toEntity() })
    }

    override suspend fun exportNoteAsSecureVault(note: Note, targetFile: File, password: CharArray) = withContext(Dispatchers.IO) {
        // 1. Generate Salt & Derived Key khusus untuk file sharing ini
        val salt = Argon2Engine.generateSalt()
        val key = Argon2Engine.deriveKey(password, salt)

        // 2. Siapkan Payload (Misal: JSON berisi Note + Base64 Media)
        // CATATAN: Untuk file besar, sebaiknya gunakan ZIP stream yang di-enkripsi per-chunk.
        // Di sini kita gunakan pendekatan ByteArray untuk kesederhanaan contoh.
        val payloadJson = "{'title':'${note.title}', 'desc':'${note.description}'}".toByteArray()

        // 3. Enkripsi dengan AES-GCM
        val encryptedPayload = AesGcmEngine.encrypt(payloadJson, key)

        // 4. Tulis ke file .securevault: [16-byte Salt] + [Encrypted Payload]
        targetFile.outputStream().use { out ->
            out.write(salt)
            out.write(encryptedPayload)
        }

        // 5. Wipe password dari memori
        password.fill('\u0000')
    }

    // Extension functions untuk mapping Entity <-> Domain
    private fun AttachmentEntity.toDomain() = Attachment(id, noteId, filePath, mimeType, createdAt)
    private fun Note.toEntity() = NoteEntity(id, title, description, createdAt, updatedAt)
}
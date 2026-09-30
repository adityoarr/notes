package com.adityoarr.securevaultnotes.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adityoarr.securevaultnotes.data.local.entity.AttachmentEntity

@Dao
interface AttachmentDao {

    /**
     * Mengambil semua lampiran (gambar/suara) yang terhubung ke sebuah Note.
     * Digunakan saat memetakan NoteEntity ke Domain Model.
     */
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getAttachmentsForNote(noteId: Long): List<AttachmentEntity>

    /**
     * Menyimpan metadata lampiran baru ke database.
     * File fisik aslinya (yang sudah terenkripsi) disimpan terpisah di Internal Storage.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    /**
     * Menghapus semua lampiran milik sebuah Note.
     * Biasanya dipanggil sebelum update note (untuk replace lampiran)
     * atau saat Note dihapus (sebagai fallback jika CASCADE gagal).
     */
    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteAttachmentsForNote(noteId: Long)

    /**
     * Mengambil semua lampiran (digunakan saat proses Backup/Export seluruh vault).
     */
    @Query("SELECT * FROM attachments")
    suspend fun getAllAttachments(): List<AttachmentEntity>
}
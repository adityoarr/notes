package com.adityoarr.securevaultnotes.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adityoarr.securevaultnotes.data.local.db.dao.AttachmentDao
import com.adityoarr.securevaultnotes.data.local.db.dao.NoteDao
import com.adityoarr.securevaultnotes.data.local.entity.AttachmentEntity
import com.adityoarr.securevaultnotes.data.local.entity.NoteEntity

@Database(
    entities = [NoteEntity::class, AttachmentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun attachmentDao(): AttachmentDao
}
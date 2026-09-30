package com.adityoarr.securevaultnotes.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.adityoarr.securevaultnotes.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    // Paging source untuk LazyColumn dengan Paging 3
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotesPaged(): PagingSource<Int, NoteEntity>

    // Flow biasa untuk observasi tanpa paging (jika diperlukan)
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Delete
    suspend fun deleteNotes(notes: List<NoteEntity>)

    @Query("SELECT count(*) FROM notes")
    suspend fun checkDbAccess(): Int

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getNotesByIds(ids: List<Long>): List<NoteEntity>

    @Query("SELECT 1")
    suspend fun ping(): Int
}
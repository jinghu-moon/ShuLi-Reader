package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY createdTime DESC")
    fun getNotesByBookId(bookId: Long): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    /** T-06: 查询脏笔记（同步用） */
    @Query("SELECT * FROM notes WHERE isDirty = 1 AND deleted = 0")
    suspend fun queryDirty(): List<NoteEntity>

    /** T-06: 查询所有未删除笔记（同步用） */
    @Query("SELECT * FROM notes WHERE deleted = 0")
    suspend fun queryAllActive(): List<NoteEntity>
}

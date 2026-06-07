package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.TagSuggestionDecisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagSuggestionDecisionDao {
    @Query("""
        SELECT * FROM tag_suggestion_decision
        WHERE book_id = :bookId
        ORDER BY decided_at DESC
    """)
    fun getDecisionsForBook(bookId: Long): Flow<List<TagSuggestionDecisionEntity>>

    @Query("""
        SELECT * FROM tag_suggestion_decision
        WHERE book_id = :bookId AND tag_name = :tagName
        LIMIT 1
    """)
    suspend fun getDecision(bookId: Long, tagName: String): TagSuggestionDecisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: TagSuggestionDecisionEntity): Long

    @Query("DELETE FROM tag_suggestion_decision WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: Long)
}

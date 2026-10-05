package com.assistant.branch.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): Conversation?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Conversation?>

    /**
     * Поиск по title. Возвращает совпадения в conversations; сообщения ищем
     * отдельно через [MessageDao.searchConversationIdsByContent].
     */
    @Query(
        "SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' " +
            "ORDER BY updatedAt DESC"
    )
    suspend fun searchByTitle(query: String): List<Conversation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: Conversation)

    @Update
    suspend fun update(conversation: Conversation)

    @Query("UPDATE conversations SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}

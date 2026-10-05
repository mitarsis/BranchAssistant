package com.assistant.branch.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    fun observeForConversation(cid: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    suspend fun forConversation(cid: String): List<Message>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): Message?

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Message?>

    @Query("SELECT * FROM messages WHERE parentId = :pid ORDER BY createdAt ASC")
    suspend fun childrenOf(pid: String): List<Message>

    @Query("SELECT * FROM messages WHERE conversationId = :cid AND parentId IS NULL ORDER BY createdAt ASC")
    suspend fun rootsOf(cid: String): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: Message)

    @Update
    suspend fun update(message: Message)

    @Query("UPDATE messages SET content = :content, isStreaming = :streaming WHERE id = :id")
    suspend fun updateContent(id: String, content: String, streaming: Boolean)

    @Query("UPDATE messages SET promptTokens = :pt, completionTokens = :ct, model = :model WHERE id = :id")
    suspend fun setTokens(id: String, pt: Int, ct: Int, model: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun clearConversation(cid: String)

    @Query("DELETE FROM messages")
    suspend fun clear()

    /**
     * Возвращает distinct id бесед, в сообщениях которых найден [query].
     * Используется для поиска по содержимому — title может не совпадать.
     */
    @Query(
        "SELECT DISTINCT conversationId FROM messages WHERE content LIKE '%' || :query || '%'"
    )
    suspend fun searchConversationIdsByContent(query: String): List<String>

    /**
     * Один сниппет совпадения для conversationId. Возвращает первое подходящее
     * сообщение и само совпадение (substring 60 символов вокруг первого вхождения).
     */
    @Query(
        "SELECT content FROM messages WHERE conversationId = :cid AND content LIKE '%' || :query || '%' " +
            "ORDER BY createdAt ASC LIMIT 1"
    )
    suspend fun firstSnippetForMatch(cid: String, query: String): String?
}

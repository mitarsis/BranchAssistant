package com.assistant.branch.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("conversationId"),
        Index("parentId"),
        Index(value = ["conversationId", "createdAt"]),
    ],
)
data class Message(
    @PrimaryKey val id: String,
    @ColumnInfo val conversationId: String,
    @ColumnInfo val parentId: String?,
    @ColumnInfo val role: String,
    @ColumnInfo val content: String,
    @ColumnInfo val createdAt: Long,
    @ColumnInfo val isStreaming: Boolean = false,
    @ColumnInfo val promptTokens: Int = 0,
    @ColumnInfo val completionTokens: Int = 0,
    @ColumnInfo val model: String? = null,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}

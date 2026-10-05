package com.assistant.branch.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index("updatedAt")]
)
data class Conversation(
    @PrimaryKey val id: String,
    @ColumnInfo val title: String,
    @ColumnInfo val createdAt: Long,
    @ColumnInfo val updatedAt: Long,
    @ColumnInfo val rootMessageId: String? = null,
)

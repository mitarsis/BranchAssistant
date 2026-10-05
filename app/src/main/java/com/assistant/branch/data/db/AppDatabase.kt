package com.assistant.branch.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Conversation::class, Message::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        const val NAME = "branch_assistant.db"
    }
}

package com.assistant.branch.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Conversation::class, Message::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        const val NAME = "branch_assistant.db"

        /**
         * Миграция v1→v2: добавлены колонки is_agent_suggestion и origin в messages.
         * DEFAULT 0 / 'user' для существующих строк. Без ALTER TABLE старые
         * сообщения не получат дефолт — поэтому задаём явно.
         */
        val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) { db ->
            db.execSQL("ALTER TABLE messages ADD COLUMN is_agent_suggestion INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN origin TEXT NOT NULL DEFAULT 'user'")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_origin ON messages(origin)")
        }
    }
}

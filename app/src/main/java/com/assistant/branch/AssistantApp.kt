package com.assistant.branch

import android.app.Application
import androidx.room.Room
import com.assistant.branch.data.db.AppDatabase
import com.assistant.branch.network.ApiClient
import com.assistant.branch.network.JevClient
import com.assistant.branch.repo.ChatRepository
import com.assistant.branch.repo.JevHistoryStore
import com.assistant.branch.settings.AssistantSettings
import com.assistant.branch.voice.SttEngine
import com.assistant.branch.voice.TtsEngine

class AssistantApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: ChatRepository
        private set

    lateinit var settings: AssistantSettings
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var jevClient: JevClient
        private set

    lateinit var sttEngine: SttEngine
        private set

    lateinit var ttsEngine: TtsEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = AssistantSettings(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
        apiClient = ApiClient()
        jevClient = JevClient() // baseUrl из настроек подхватывается в JevViewModel.run()
        repository = ChatRepository(database, apiClient, settings)
        sttEngine = SttEngine(this)
        ttsEngine = TtsEngine(applicationContext).also { it.init() }
        JevHistoryStore.init(this)
    }

    companion object {
        @Volatile
        lateinit var instance: AssistantApp
            private set
    }
}

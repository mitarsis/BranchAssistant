package com.assistant.branch.repo

import android.content.Context
import com.assistant.branch.ui.viewmodel.JevHistoryItem
import com.assistant.branch.ui.viewmodel.JevQuestionItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Простое persistent-хранилище для истории Jev.
 *
 * Файл: filesDir/jev_history.json
 * Лимит: 50 последних записей.
 */
object JevHistoryStore {

    @Serializable
    private data class Stored(val items: List<StoredItem>)

    @Serializable
    private data class StoredItem(
        val id: Long,
        val title: String,
        val summary: String,
        val stateText: String,
        val questions: List<StoredQuestion>,
        val response: StoredResponse?,
    )

    @Serializable
    private data class StoredQuestion(
        val instructions: String,
        val type: String, // "choice" | "score" | "noul"
        val choiceCriteria: Map<String, String> = emptyMap(),
        val scoreCriteria: List<String> = emptyList(),
    )

    @Serializable
    private data class StoredResponse(
        val model: String,
        val answers: Map<String, StoredAnswer>,
    )

    @Serializable
    private data class StoredAnswer(
        val type: String,
        val choice: String? = null,
        val score: Double? = null,
        val noul: Double? = null,
        val confidence: Double = 0.0,
        val probabilities: Map<String, Double> = emptyMap(),
    )

    private const val FILE_NAME = "jev_history.json"
    private const val MAX_ITEMS = 50
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: MutableList<JevHistoryItem>? = null
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun load(): List<JevHistoryItem> {
        cache?.let { return it.toList() }
        val ctx = appContext ?: return emptyList()
        val file = File(ctx.filesDir, FILE_NAME)
        if (!file.exists()) {
            cache = mutableListOf()
            return emptyList()
        }
        return try {
            val raw = file.readText()
            if (raw.isBlank()) {
                cache = mutableListOf()
                emptyList()
            } else {
                val parsed = json.decodeFromString<Stored>(raw)
                val list = parsed.items.map { it.toDomain() }.toMutableList()
                cache = list
                list.toList()
            }
        } catch (e: Throwable) {
            cache = mutableListOf()
            emptyList()
        }
    }

    @Synchronized
    fun add(item: JevHistoryItem) {
        val list = cache ?: load().toMutableList().also { cache = it }
        list.add(0, item)
        if (list.size > MAX_ITEMS) {
            while (list.size > MAX_ITEMS) list.removeAt(list.size - 1)
        }
        save()
    }

    @Synchronized
    fun remove(id: Long) {
        val list = cache ?: return
        list.removeAll { it.id == id }
        save()
    }

    @Synchronized
    fun clear() {
        cache?.clear()
        save()
    }

    private fun save() {
        val ctx = appContext ?: return
        val list = cache ?: return
        val stored = Stored(items = list.map { it.toStored() })
        runCatching {
            val file = File(ctx.filesDir, FILE_NAME)
            file.writeText(json.encodeToString(stored))
        }
    }

    private fun JevHistoryItem.toStored(): StoredItem = StoredItem(
        id = id,
        title = title,
        summary = summary,
        stateText = stateText,
        questions = questions.map { q ->
            when (val question = q.question) {
                is com.assistant.branch.network.JevChoice -> StoredQuestion(
                    instructions = q.instructions,
                    type = "choice",
                    choiceCriteria = question.criteria,
                )
                is com.assistant.branch.network.JevScore -> StoredQuestion(
                    instructions = q.instructions,
                    type = "score",
                    scoreCriteria = question.criteria,
                )
                is com.assistant.branch.network.JevNoul -> StoredQuestion(
                    instructions = q.instructions,
                    type = "noul",
                )
            }
        },
        response = response?.let { resp ->
            StoredResponse(
                model = resp.model,
                answers = resp.answers.mapValues { (_, ans) ->
                    when (ans) {
                        is com.assistant.branch.network.JevChoiceAnswer -> StoredAnswer(
                            type = "choice",
                            choice = ans.choice,
                            confidence = ans.confidence,
                            probabilities = ans.probabilities,
                        )
                        is com.assistant.branch.network.JevScoreAnswer -> StoredAnswer(
                            type = "score",
                            score = ans.score,
                            confidence = ans.confidence,
                            probabilities = ans.probabilities,
                        )
                        is com.assistant.branch.network.JevNoulAnswer -> StoredAnswer(
                            type = "noul",
                            noul = ans.noul,
                            probabilities = ans.probabilities,
                        )
                    }
                },
            )
        },
    )

    private fun StoredItem.toDomain(): JevHistoryItem = JevHistoryItem(
        id = id,
        title = title,
        summary = summary,
        stateText = stateText,
        questions = questions.map { q ->
            JevQuestionItem(
                instructions = q.instructions,
                question = when (q.type) {
                    "choice" -> com.assistant.branch.network.JevChoice(
                        instructions = q.instructions,
                        criteria = q.choiceCriteria,
                    )
                    "score" -> com.assistant.branch.network.JevScore(
                        instructions = q.instructions,
                        criteria = q.scoreCriteria,
                    )
                    else -> com.assistant.branch.network.JevNoul(instructions = q.instructions)
                },
            )
        },
        response = response?.let { resp ->
            com.assistant.branch.network.JevResponse(
                model = resp.model,
                answers = resp.answers.mapValues { (_, ans) ->
                    when (ans.type) {
                        "choice" -> com.assistant.branch.network.JevChoiceAnswer(
                            choice = ans.choice.orEmpty(),
                            confidence = ans.confidence,
                            probabilities = ans.probabilities,
                        )
                        "score" -> com.assistant.branch.network.JevScoreAnswer(
                            score = ans.score ?: 0.0,
                            confidence = ans.confidence,
                            probabilities = ans.probabilities,
                        )
                        else -> com.assistant.branch.network.JevNoulAnswer(
                            noul = ans.noul ?: 0.5,
                        )
                    }
                },
            )
        },
    )
}

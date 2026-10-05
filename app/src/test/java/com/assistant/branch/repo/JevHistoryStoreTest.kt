package com.assistant.branch.repo

import androidx.test.core.app.ApplicationProvider
import com.assistant.branch.network.JevChoiceAnswer
import com.assistant.branch.network.JevResponse
import com.assistant.branch.network.JevScoreAnswer
import com.assistant.branch.ui.viewmodel.JevHistoryItem
import com.assistant.branch.ui.viewmodel.JevQuestionItem
import com.assistant.branch.network.JevChoice
import com.assistant.branch.network.JevScore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class JevHistoryStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        JevHistoryStore.init(context)
        // Чистим файл, если остался от прошлого теста.
        File(context.filesDir, "jev_history.json").delete()
        JevHistoryStore.clear()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "jev_history.json").delete()
        JevHistoryStore.clear()
    }

    private fun makeItem(id: Long, title: String): JevHistoryItem = JevHistoryItem(
        id = id,
        title = title,
        summary = "summary for $title",
        stateText = "state",
        questions = listOf(
            JevQuestionItem(
                instructions = "do thing",
                question = JevChoice(
                    instructions = "do thing",
                    criteria = mapOf("yes" to "Да", "no" to "Нет"),
                ),
            ),
        ),
        response = JevResponse(
            model = "jev-latest",
            answers = mapOf(
                "q1" to JevChoiceAnswer(
                    choice = "yes",
                    probabilities = mapOf("yes" to 0.9, "no" to 0.1),
                ),
            ),
        ),
    )

    @Test
    fun `load returns empty list when file does not exist`() {
        assertEquals(emptyList<JevHistoryItem>(), JevHistoryStore.load())
    }

    @Test
    fun `add then load returns the item`() {
        JevHistoryStore.add(makeItem(1L, "first"))
        val items = JevHistoryStore.load()
        assertEquals(1, items.size)
        assertEquals("first", items[0].title)
        assertEquals(1L, items[0].id)
    }

    @Test
    fun `add prepends new items at the head`() {
        JevHistoryStore.add(makeItem(1L, "first"))
        JevHistoryStore.add(makeItem(2L, "second"))
        JevHistoryStore.add(makeItem(3L, "third"))
        val items = JevHistoryStore.load()
        assertEquals(listOf(3L, 2L, 1L), items.map { it.id })
        assertEquals(listOf("third", "second", "first"), items.map { it.title })
    }

    @Test
    fun `remove deletes by id`() {
        JevHistoryStore.add(makeItem(1L, "a"))
        JevHistoryStore.add(makeItem(2L, "b"))
        JevHistoryStore.add(makeItem(3L, "c"))
        JevHistoryStore.remove(2L)
        val items = JevHistoryStore.load()
        assertEquals(listOf(3L, 1L), items.map { it.id })
    }

    @Test
    fun `history is capped at 50 items`() {
        repeat(60) { i ->
            JevHistoryStore.add(makeItem(i.toLong(), "item $i"))
        }
        val items = JevHistoryStore.load()
        assertEquals(50, items.size)
        // Голова — самые новые (id 59), хвост — id 10.
        assertEquals(59L, items.first().id)
        assertEquals(10L, items.last().id)
    }

    @Test
    fun `corrupt json returns empty list and does not crash`() {
        File(context.filesDir, "jev_history.json").writeText("{garbage not json")
        assertEquals(emptyList<JevHistoryItem>(), JevHistoryStore.load())
    }

    @Test
    fun `score answer roundtrips through serialization`() {
        val item = JevHistoryItem(
            id = 42L,
            title = "score test",
            summary = "ok",
            stateText = "state",
            questions = listOf(
                JevQuestionItem(
                    instructions = "rate",
                    question = JevScore(
                        instructions = "rate",
                        criteria = listOf("Low", "High"),
                    ),
                ),
            ),
            response = JevResponse(
                model = "jev-latest",
                answers = mapOf(
                    "q" to JevScoreAnswer(
                        score = 0.75,
                        legend = mapOf("0-0.3" to "Low", "0.7-1" to "High"),
                        confidence = 0.8,
                        probabilities = mapOf("Low" to 0.25, "High" to 0.75),
                    ),
                ),
            ),
        )
        JevHistoryStore.add(item)
        val loaded = JevHistoryStore.load().single()
        val answer = loaded.response!!.answers["q"]
        assertTrue(answer is JevScoreAnswer)
        assertEquals(0.75, (answer as JevScoreAnswer).score, 0.001)
        assertEquals(0.8, answer.confidence, 0.001)
        assertEquals("Low", answer.legend["0-0.3"])
    }
}
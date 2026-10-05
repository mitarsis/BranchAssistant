package com.assistant.branch.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.assistant.branch.data.db.AppDatabase
import com.assistant.branch.data.db.Conversation
import com.assistant.branch.data.db.Message
import com.assistant.branch.network.ApiClient
import com.assistant.branch.network.ApiMessage
import com.assistant.branch.network.ChatRequest
import com.assistant.branch.network.StreamEvent
import com.assistant.branch.settings.AssistantConfig
import com.assistant.branch.settings.AssistantSettings
import com.assistant.branch.settings.ModelEntry
import com.assistant.branch.settings.RouteEntry
import com.assistant.branch.settings.ThemeMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ChatRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: AssistantSettings
    private lateinit var client: ApiClient
    private lateinit var repo: ChatRepository
    private val cfgFlow = MutableStateFlow(
        AssistantConfig(
            apiKey = "test-key",
            models = listOf(ModelEntry(name = "test-model")),
            maxContextMessages = 40,
            routes = listOf(RouteEntry(name = "default", modelId = null)),
            baseUrl = "https://example.com/v1",
            systemPrompt = "system",
            temperature = 0.5f,
            maxTokens = 100,
            ttsEnabled = false,
            sttLocale = "ru-RU",
            themeMode = ThemeMode.SYSTEM,
            accentPalette = "violet",
        )
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = mockk(relaxed = true)
        every { settings.config } returns cfgFlow
        coEvery { settings.snapshot() } coAnswers { cfgFlow.value }
        client = mockk(relaxed = true)
        repo = ChatRepository(db, client, settings)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedChain(vararg roleAndContent: Pair<String, String>): Conversation {
        val conv = repo.newConversation("seed")
        var parent: Message? = null
        for ((role, content) in roleAndContent) {
            val m = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                parentId = parent?.id,
                role = role,
                content = content,
                createdAt = System.currentTimeMillis(),
            )
            db.messageDao().upsert(m)
            parent = m
        }
        return conv
    }

    @Test
    fun `newConversation creates a conversation with timestamp`() = runTest {
        val conv = repo.newConversation()
        assertNotNull(conv.id)
        assertEquals("Новая беседа", conv.title)
        assertTrue(conv.createdAt > 0)
        assertEquals(conv.createdAt, conv.updatedAt)
    }

    @Test
    fun `branchTree returns nested structure with correct children`() = runTest {
        val conv = seedChain(
            "user" to "hi",
            "assistant" to "hello",
            "user" to "how are you",
            "assistant" to "fine",
        )
        val tree = repo.branchTree(conv.id)
        assertEquals(1, tree.size)
        val root = tree.first()
        assertEquals("hi", root.message.content)
        assertEquals(1, root.children.size)
        val a1 = root.children.first()
        assertEquals("hello", a1.message.content)
        assertEquals(1, a1.children.size)
        assertEquals("how are you", a1.children.first().message.content)
        assertEquals(1, a1.children.first().children.size)
        assertEquals("fine", a1.children.first().children.first().message.content)
    }

    @Test
    fun `pathTo returns ancestor chain from leaf to root`() = runTest {
        val conv = seedChain(
            "user" to "u1",
            "assistant" to "a1",
            "user" to "u2",
        )
        val all = db.messageDao().forConversation(conv.id)
        val leaf = all.last()
        val path = repo.pathTo(leaf.id)
        assertEquals(3, path.size)
        assertEquals("u1", path[0].content)
        assertEquals("a1", path[1].content)
        assertEquals("u2", path[2].content)
    }

    @Test
    fun `pathTo on root returns single-element list`() = runTest {
        val conv = seedChain("user" to "hi")
        val root = db.messageDao().forConversation(conv.id).first()
        val path = repo.pathTo(root.id)
        assertEquals(1, path.size)
        assertEquals("hi", path[0].content)
    }

    @Test
    fun `pathTo on missing id returns empty`() = runTest {
        seedChain("user" to "hi")
        val path = repo.pathTo("does-not-exist")
        assertEquals(0, path.size)
    }

    @Test
    fun `branchTree handles branching with multiple siblings`() = runTest {
        // Цепочка: user "hi" → assistant "answer A" → user "how?"
        val conv = seedChain(
            "user" to "hi",
            "assistant" to "answer A",
            "user" to "how?",
        )
        val all = db.messageDao().forConversation(conv.id)
        val rootMsg = all.first { it.content == "hi" }

        // Добавляем ещё один assistant-ответ как sibling к "answer A".
        // Оба assistant-сообщения теперь — дети "hi", а "how?" — ребёнок "answer A".
        val sibling = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conv.id,
            parentId = rootMsg.id,
            role = "assistant",
            content = "answer B",
            createdAt = System.currentTimeMillis(),
        )
        db.messageDao().upsert(sibling)

        val tree = repo.branchTree(conv.id)
        assertEquals(1, tree.size)
        val root = tree.first()
        // Корень ("hi") должен иметь двух assistant-детей: "answer A" и "answer B"
        assertEquals(2, root.children.size)
        val rootChildContents = root.children.map { it.message.content }.toSet()
        assertTrue("answer A" in rootChildContents)
        assertTrue("answer B" in rootChildContents)

        // У "answer A" должен быть один ребёнок — "how?"
        val answerA = root.children.first { it.message.content == "answer A" }
        assertEquals(1, answerA.children.size)
        assertEquals("how?", answerA.children.first().message.content)
    }

    @Test
    fun `regenerate cascades delete to all descendants of target`() = runTest {
        // Дерево: u1 → a1 → u2 → a2 → u3 → a3
        val conv = seedChain(
            "user" to "u1",
            "assistant" to "a1",
            "user" to "u2",
            "assistant" to "a2",
            "user" to "u3",
            "assistant" to "a3",
        )
        val allBefore = db.messageDao().forConversation(conv.id)
        assertEquals(6, allBefore.size)

        // Находим a1 (2-й элемент)
        val a1 = allBefore.first { it.content == "a1" }

        // Мок streamChat — сразу Done
        every {
            client.streamChat(
                request = any(),
                apiKey = any(),
                baseUrlOverride = any(),
            )
        } returns flowOf(StreamEvent.Done)

        // Регенерируем a1 — должны удалиться a1 и ВСЕ его потомки (u2, a2, u3, a3),
        // и появится один новый assistant с пустым контентом (заглушка от мока).
        val events = repo.regenerate(conv.id, a1.id).toList()

        assertTrue("Expected at least one Done event", events.any { it is StreamEvent.Done })
        assertTrue("Unexpected Failure event",
            events.none { it is StreamEvent.Failure })

        val allAfter = db.messageDao().forConversation(conv.id)
        // u1 (1 root, не удалялся) + 1 новый assistant (sibling старого a1)
        assertEquals(2, allAfter.size)

        // u1 — на месте
        assertTrue(allAfter.any { it.content == "u1" })

        // Новый assistant — пустой контент, parent = u1 (тот же parent, что был у a1),
        // и после Done-эвента isStreaming уже сброшен в false
        val newAssistant = allAfter.first { it.role == "assistant" && it.content != "a1" }
        assertEquals("", newAssistant.content)
        assertFalse(newAssistant.isStreaming)
    }

    @Test
    fun `regenerate leaf assistant only removes that assistant`() = runTest {
        val conv = seedChain(
            "user" to "u1",
            "assistant" to "a1",
            "user" to "u2",
            "assistant" to "a2",
        )
        val a2 = db.messageDao().forConversation(conv.id).first { it.content == "a2" }

        every {
            client.streamChat(
                request = any(),
                apiKey = any(),
                baseUrlOverride = any(),
            )
        } returns flowOf(StreamEvent.Done)

        repo.regenerate(conv.id, a2.id).toList()

        // После regen: u1, a1, u2 + новый assistant (замена a2)
        val after = db.messageDao().forConversation(conv.id)
        assertEquals(4, after.size)
        assertTrue(after.any { it.content == "u1" })
        assertTrue(after.any { it.content == "a1" })
        assertTrue(after.any { it.content == "u2" })
        // Старый a2 удалён
        assertTrue(after.none { it.content == "a2" })
        // Новый assistant с пустым контентом
        val newAssistant = after.first { it.role == "assistant" && it.content.isEmpty() }
        assertEquals("u2", newAssistant.parentId?.let { parentId ->
            after.firstOrNull { it.id == parentId }?.content
        })
    }

    @Test
    fun `regenerate non-existing message returns Failure`() = runTest {
        seedChain("user" to "hi")
        val events = repo.regenerate("non-existent-conv", "fake-id").toList()
        assertEquals(1, events.size)
        assertTrue(events.first() is StreamEvent.Failure)
    }

    @Test
    fun `regenerate root message returns Failure`() = runTest {
        val conv = seedChain("user" to "u1", "assistant" to "a1")
        val root = db.messageDao().forConversation(conv.id).first { it.parentId == null }
        val events = repo.regenerate(conv.id, root.id).toList()
        assertEquals(1, events.size)
        assertTrue(events.first() is StreamEvent.Failure)
    }

    @Test
    fun `renameConversation updates title`() = runTest {
        val conv = seedChain("user" to "hi")
        repo.renameConversation(conv.id, "  новый заголовок  ")
        val updated = db.conversationDao().byId(conv.id)
        assertEquals("  новый заголовок  ", updated?.title)
    }

    @Test
    fun `deleteConversation removes conversation and cascades messages`() = runTest {
        val conv = seedChain("user" to "hi", "assistant" to "hello")
        assertEquals(2, db.messageDao().forConversation(conv.id).size)
        repo.deleteConversation(conv.id)
        assertEquals(0, db.messageDao().forConversation(conv.id).size)
        assertNull(db.conversationDao().byId(conv.id))
    }

    @Test
    fun `search returns empty list for blank query`() = runTest {
        seedChain("user" to "anything")
        assertEquals(emptyList<ChatRepository.SearchHit>(), repo.search(""))
        assertEquals(emptyList<ChatRepository.SearchHit>(), repo.search("   "))
    }

    @Test
    fun `search finds conversations by title match`() = runTest {
        val conv = repo.newConversation("Как приготовить борщ")
        repo.renameConversation(conv.id, "Как приготовить борщ")
        val hits = repo.search("борщ")
        assertEquals(1, hits.size)
        assertEquals(conv.id, hits[0].conversation.id)
        assertNull("title match has no content snippet", hits[0].snippet)
    }

    @Test
    fun `search finds conversations by message content even when title does not match`() = runTest {
        val conv = repo.newConversation("пустой заголовок")
        repo.renameConversation(conv.id, "пустой заголовок")
        // Сообщения.
        db.messageDao().upsert(Message(
            id = java.util.UUID.randomUUID().toString(),
            conversationId = conv.id,
            parentId = null,
            role = "user",
            content = "Расскажи про квантовую механику",
            createdAt = 1L,
        ))

        val hits = repo.search("квантовую")
        assertEquals(1, hits.size)
        assertEquals(conv.id, hits[0].conversation.id)
        assertNotNull("content hit должна содержать snippet", hits[0].snippet)
        assertTrue(hits[0].snippet!!.contains("квантовую"))
    }

    @Test
    fun `search results deduplicate by conversation id`() = runTest {
        val conv = repo.newConversation("тире и слово")
        repo.renameConversation(conv.id, "тире и слово")
        // Несколько message со словом "слово" — должны вернуть один hit.
        repeat(2) { i ->
            db.messageDao().upsert(Message(
                id = java.util.UUID.randomUUID().toString(),
                conversationId = conv.id,
                parentId = null,
                role = if (i == 0) "user" else "assistant",
                content = "Это тестовое слово номер $i",
                createdAt = i.toLong(),
            ))
        }

        val hits = repo.search("слово")
        assertEquals(1, hits.size)
        assertEquals(conv.id, hits[0].conversation.id)
    }

    @Test
    fun `search snippet is trimmed to ~120 chars`() = runTest {
        val conv = repo.newConversation("x")
        repo.renameConversation(conv.id, "x")
        val padding = "x".repeat(200)
        db.messageDao().upsert(Message(
            id = java.util.UUID.randomUUID().toString(),
            conversationId = conv.id,
            parentId = null,
            role = "user",
            content = "$padding первое совпадение тут $padding",
            createdAt = 1L,
        ))

        val hits = repo.search("совпадение")
        assertEquals(1, hits.size)
        val snippet = hits[0].snippet!!
        assertTrue("snippet must be reasonably short, got len=${snippet.length}", snippet.length <= 140)
        assertTrue(snippet.contains("совпадение"))
    }

    @Test
    fun `search sorts by updatedAt desc`() = runTest {
        val c1 = repo.newConversation("альфа-проект"); repo.renameConversation(c1.id, "альфа-проект")
        db.conversationDao().touch(c1.id, 100L)
        val c2 = repo.newConversation("бета-проект"); repo.renameConversation(c2.id, "бета-проект")
        db.conversationDao().touch(c2.id, 300L)
        val c3 = repo.newConversation("гамма-проект"); repo.renameConversation(c3.id, "гамма-проект")
        db.conversationDao().touch(c3.id, 200L)

        // Один общий подзапрос "проект" — должен найти все три, отсортированные по updatedAt desc.
        val hits = repo.search("проект")
        assertEquals(3, hits.size)
        assertEquals(
            listOf("бета-проект", "гамма-проект", "альфа-проект"),
            hits.map { it.conversation.title },
        )
    }

    @Test
    fun `compress throws CompressException on empty active path`() = runTest {
        try {
            repo.compress("conv", emptyList())
            fail("expected CompressException")
        } catch (e: CompressException) {
            assertTrue(e.message!!.contains("пуста"))
        }
    }

    @Test
    fun `compress throws CompressException when api key is blank`() = runTest {
        cfgFlow.value = cfgFlow.value.copy(apiKey = "")
        val conv = seedChain("user" to "hi", "assistant" to "hello")
        val ids = db.messageDao().forConversation(conv.id).map { it.id }
        try {
            repo.compress(conv.id, ids)
            fail("expected CompressException")
        } catch (e: CompressException) {
            assertTrue(e.message!!.contains("API ключ"))
        }
    }

    @Test
    fun `compress throws CompressException when no models configured`() = runTest {
        cfgFlow.value = cfgFlow.value.copy(models = emptyList())
        val conv = seedChain("user" to "hi", "assistant" to "hello")
        val ids = db.messageDao().forConversation(conv.id).map { it.id }
        try {
            repo.compress(conv.id, ids)
            fail("expected CompressException")
        } catch (e: CompressException) {
            assertTrue(e.message!!.contains("модель"))
        }
    }

    @Test
    fun `compress concatenates streamed tokens into final text`() = runTest {
        every {
            client.streamChat(
                request = any(),
                apiKey = any(),
                baseUrlOverride = any(),
            )
        } returns flowOf(
            StreamEvent.Token("Сжала "),
            StreamEvent.Token("вет "),
            StreamEvent.Token("бесед."),
            StreamEvent.Done,
        )

        val conv = seedChain("user" to "hi", "assistant" to "hello")
        val ids = db.messageDao().forConversation(conv.id).map { it.id }
        val summary = repo.compress(conv.id, ids)
        assertEquals("Сжала вет бесед.", summary)
    }

    @Test
    fun `compress throws CompressException when LLM returns empty text`() = runTest {
        every {
            client.streamChat(
                request = any(),
                apiKey = any(),
                baseUrlOverride = any(),
            )
        } returns flowOf(StreamEvent.Token("   "), StreamEvent.Done)

        val conv = seedChain("user" to "hi", "assistant" to "hello")
        val ids = db.messageDao().forConversation(conv.id).map { it.id }
        try {
            repo.compress(conv.id, ids)
            fail("expected CompressException")
        } catch (e: CompressException) {
            assertTrue(e.message!!.contains("пустой"))
        }
    }

    @Test
    fun `compress throws CompressException on stream failure`() = runTest {
        every {
            client.streamChat(
                request = any(),
                apiKey = any(),
                baseUrlOverride = any(),
            )
        } returns flowOf(
            StreamEvent.Failure(
                throwable = RuntimeException("boom"),
                message = "HTTP 500 — server down",
            )
        )

        val conv = seedChain("user" to "hi", "assistant" to "hello")
        val ids = db.messageDao().forConversation(conv.id).map { it.id }
        try {
            repo.compress(conv.id, ids)
            fail("expected CompressException")
        } catch (e: CompressException) {
            assertEquals("HTTP 500 — server down", e.message)
        }
    }
}

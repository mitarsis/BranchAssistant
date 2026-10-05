package com.assistant.branch.repo

import com.assistant.branch.network.JevClient
import com.assistant.branch.network.JevChoice
import com.assistant.branch.network.JevChoiceAnswer
import com.assistant.branch.network.JevResponse
import com.assistant.branch.settings.RouteEntry
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive

/**
 * Классификатор интента пользователя через Jev.
 *
 * Набор категорий берётся из пользовательских [RouteEntry] — никакого
 * хардкоженного enum. Возвращает наиболее подходящий маршрут или null при
 * сбое / пустом списке маршрутов.
 */
class JevRouter(
    private val client: JevClient,
    private val apiKey: String,
    private val baseUrl: String,
) {
    /**
     * Классифицирует сообщение по списку, который задал пользователь.
     * Возвращает один из [routes] (или null при ошибке / пустом списке).
     */
    suspend fun classify(
        userMessage: String,
        routes: List<RouteEntry>,
        model: String = "jev-latest",
    ): RouteEntry? {
        if (routes.isEmpty()) return null
        val validRoutes = routes.filter { it.name.isNotBlank() }
        if (validRoutes.isEmpty()) return null

        val questions = mapOf(
            "intent" to JevChoice(
                instructions = "Выбери наиболее подходящий тип запроса пользователя.",
                criteria = validRoutes.associate { route ->
                    route.name.lowercase() to route.description.ifBlank { route.name }
                },
            )
        )

        val state = JsonPrimitive(userMessage.take(2000))

        val response: JevResponse = try {
            client.evaluate(
                state = state,
                questions = questions,
                apiKey = apiKey,
                baseUrlOverride = baseUrl,
                model = model,
            ).first()
        } catch (e: Throwable) {
            return null
        }

        val answer = response.answers["intent"] as? JevChoiceAnswer ?: return null
        val topKey = answer.probabilities.maxByOrNull { it.value }?.key
            ?: return validRoutes.firstOrNull()
        return validRoutes.firstOrNull { it.name.equals(topKey, ignoreCase = true) }
            ?: validRoutes.firstOrNull()
    }
}
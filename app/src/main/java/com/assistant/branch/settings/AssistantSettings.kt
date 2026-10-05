package com.assistant.branch.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device")

/** Одна модель: имя + стабильный id для ссылок из маршрутов. */
@Serializable
data class ModelEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
)

/**
 * Маршрут для Jev-классификатора: ключ, описание (инструкция для Jev) и
 * ссылка на [ModelEntry.id]. Имя маршрута используется как категория в Jev.
 */
@Serializable
data class RouteEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val modelId: String? = null,
)

data class AssistantConfig(
    val apiKey: String,
    val models: List<ModelEntry>,
    val routes: List<RouteEntry>,
    val baseUrl: String,
    val systemPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    /** Максимум сообщений активной ветки, которое уходит в LLM на каждый запрос.
     *  Скользящее окно: берутся последние N. Системный промпт всегда включается
     *  сверху. При длинных беседах экономит токены и деньги. */
    val maxContextMessages: Int = 40,
    val ttsEnabled: Boolean,
    val sttLocale: String,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentPalette: String = "violet",
    val jevApiKey: String = "",
    val jevBaseUrl: String = "https://api.typesafe.ai/v1",
    val jevModel: String = "jev-latest",
)

enum class ThemeMode {
    SYSTEM, DARK, LIGHT;

    override fun toString(): String = name.lowercase()

    companion object {
        fun fromString(s: String?): ThemeMode =
            s?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() } ?: SYSTEM
    }
}

class AssistantSettings(
    private val context: Context,
    val secure: SecureSettings = SecureSettings(context),
) {

    private object Keys {
        val API_KEY_LEGACY = stringPreferencesKey("api_key") // для миграции
        val MODEL_LEGACY = stringPreferencesKey("model")       // одиночная модель (v1)
        val MODEL_BY_INTENT_JSON = stringPreferencesKey("model_by_intent_json") // v2 (маппинг Intent)
        val MODELS_JSON = stringPreferencesKey("models_json") // v3: список моделей
        val ROUTES_JSON = stringPreferencesKey("routes_json") // v3: список маршрутов
        val BASE_URL = stringPreferencesKey("base_url")
        val SYSTEM = stringPreferencesKey("system_prompt")
        val TEMP = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TTS = intPreferencesKey("tts_enabled")
        val MAX_CONTEXT_MESSAGES = intPreferencesKey("max_context_messages")
        val STT_LOCALE = stringPreferencesKey("stt_locale")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val JEV_BASE_URL = stringPreferencesKey("jev_base_url")
        val JEV_MODEL = stringPreferencesKey("jev_model")
    }

    private fun secureFlow(): Flow<Pair<String, String>> = secure.changesFlow

    private val modelListSerializer = ListSerializer(ModelEntry.serializer())
    private val routeListSerializer = ListSerializer(RouteEntry.serializer())

    private val prefsJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun <T> decode(raw: String?, ser: kotlinx.serialization.KSerializer<T>): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching { prefsJson.decodeFromString(ser, raw) }.getOrNull()
    }

    /**
     * Цепочка миграций:
     *  1. Новый формат (models_json + routes_json) — использовать как есть.
     *  2. Прошлая попытка (model_by_intent_json + Intent enum) — конвертировать:
     *     каждая пара intent→model становится ModelEntry + RouteEntry.
     *  3. Самый старый формат (одиночная `model`) — одна модель + один маршрут «default».
     *  4. Ничего нет — пустые списки.
     */
    private fun migrateModelsAndRoutes(prefs: Preferences): Pair<List<ModelEntry>, List<RouteEntry>> {
        // 1. Текущий формат
        val newModels = decode(prefs[Keys.MODELS_JSON], modelListSerializer)
        val newRoutes = decode(prefs[Keys.ROUTES_JSON], routeListSerializer)
        if (newModels != null && newRoutes != null) return newModels to newRoutes

        // 2. v2: model_by_intent_json
        val byIntentRaw = prefs[Keys.MODEL_BY_INTENT_JSON]
        if (!byIntentRaw.isNullOrBlank()) {
            val byIntent = runCatching {
                prefsJson.decodeFromString(
                    MapSerializer(String.serializer(), String.serializer()),
                    byIntentRaw,
                )
            }.getOrNull()
            if (byIntent != null && byIntent.isNotEmpty()) {
                // Уникальные имена моделей: один ModelEntry на каждый уникальный name.
                val uniqueNames = byIntent.values.distinct()
                val models = uniqueNames.map { ModelEntry(name = it) }
                val nameToId = uniqueNames.zip(models).toMap()
                val routes = byIntent.map { (intentKey, modelName) ->
                    RouteEntry(
                        name = intentKey,
                        description = intentKey.replaceFirstChar { it.uppercase() },
                        modelId = nameToId[modelName]?.id,
                    )
                }
                return models to routes
            }
        }

        // 3. v1: одиночный `model`
        val legacy = prefs[Keys.MODEL_LEGACY]
        if (!legacy.isNullOrBlank()) {
            val model = ModelEntry(name = legacy)
            val route = RouteEntry(
                name = "default",
                description = "Базовый маршрут",
                modelId = model.id,
            )
            return listOf(model) to listOf(route)
        }

        // 4. Пусто
        return emptyList<ModelEntry>() to emptyList<RouteEntry>()
    }

    val config: Flow<AssistantConfig> = combine(
        context.dataStore.data,
        secureFlow(),
    ) { prefs, (chatKey, jevKey) ->
        val (models, routes) = migrateModelsAndRoutes(prefs)

        AssistantConfig(
            apiKey = chatKey.ifEmpty { prefs[Keys.API_KEY_LEGACY].orEmpty() },
            models = models,
            routes = routes,
            baseUrl = prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL,
            systemPrompt = prefs[Keys.SYSTEM] ?: DEFAULT_SYSTEM_PROMPT,
            temperature = prefs[Keys.TEMP] ?: 0.7f,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 2048,
            maxContextMessages = (prefs[Keys.MAX_CONTEXT_MESSAGES] ?: 0)
                .coerceIn(MIN_CONTEXT_MESSAGES, MAX_CONTEXT_MESSAGES_LIMIT)
                .let { if (it == 0) DEFAULT_MAX_CONTEXT_MESSAGES else it },
            ttsEnabled = (prefs[Keys.TTS] ?: 1) == 1,
            sttLocale = prefs[Keys.STT_LOCALE] ?: "ru-RU",
            themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
            accentPalette = prefs[Keys.ACCENT_PALETTE] ?: "violet",
            jevApiKey = jevKey,
            jevBaseUrl = prefs[Keys.JEV_BASE_URL] ?: DEFAULT_JEV_BASE_URL,
            jevModel = prefs[Keys.JEV_MODEL] ?: DEFAULT_JEV_MODEL,
        )
    }

    suspend fun snapshot(): AssistantConfig = config.first()

    suspend fun update(transform: (AssistantConfig) -> AssistantConfig) {
        val current = snapshot()
        val next = transform(current)
        // Чувствительные данные — в зашифрованное хранилище
        secure.chatApiKey = next.apiKey
        secure.jevApiKey = next.jevApiKey
        // Не-чувствительные — в DataStore
        context.dataStore.edit { prefs ->
            // Сохраняем списки как JSON. Пустые значения name у моделей/маршрутов
            // выкидываем, чтобы не плодить мусорные записи.
            val cleanedModels = next.models
                .map { it.copy(name = it.name.trim()) }
                .filter { it.name.isNotBlank() }
            val cleanedRoutes = next.routes
                .map { it.copy(name = it.name.trim(), description = it.description.trim()) }
                .filter { it.name.isNotBlank() }

            prefs[Keys.MODELS_JSON] = prefsJson.encodeToString(modelListSerializer, cleanedModels)
            prefs[Keys.ROUTES_JSON] = prefsJson.encodeToString(routeListSerializer, cleanedRoutes)

            // Миграция: вычищаем все legacy-поля, если они ещё там.
            if (prefs[Keys.MODEL_LEGACY] != null) prefs.remove(Keys.MODEL_LEGACY)
            if (prefs[Keys.MODEL_BY_INTENT_JSON] != null) prefs.remove(Keys.MODEL_BY_INTENT_JSON)
            if (prefs[Keys.API_KEY_LEGACY] != null) prefs.remove(Keys.API_KEY_LEGACY)

            prefs[Keys.BASE_URL] = next.baseUrl
            prefs[Keys.SYSTEM] = next.systemPrompt
            prefs[Keys.TEMP] = next.temperature
            prefs[Keys.MAX_TOKENS] = next.maxTokens
            prefs[Keys.MAX_CONTEXT_MESSAGES] = next.maxContextMessages
            prefs[Keys.TTS] = if (next.ttsEnabled) 1 else 0
            prefs[Keys.STT_LOCALE] = next.sttLocale
            prefs[Keys.THEME_MODE] = next.themeMode.toString()
            prefs[Keys.ACCENT_PALETTE] = next.accentPalette
            prefs[Keys.JEV_BASE_URL] = next.jevBaseUrl
            prefs[Keys.JEV_MODEL] = next.jevModel
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
        secure.clear()
    }

    companion object {
        const val DEFAULT_MODEL = "MiniMax-M3"
        const val DEFAULT_BASE_URL = "https://api.minimax.io/v1"
        const val DEFAULT_SYSTEM_PROMPT = "Ты — голосовой ИИ-ассистент в телефоне пользователя. " +
            "Отвечай кратко, дружелюбно и по делу. Используй язык пользователя."
        const val DEFAULT_JEV_BASE_URL = "https://api.typesafe.ai/v1"
        const val DEFAULT_JEV_MODEL = "jev-latest"

        const val DEFAULT_MAX_CONTEXT_MESSAGES = 40
        const val MIN_CONTEXT_MESSAGES = 4
        const val MAX_CONTEXT_MESSAGES_LIMIT = 200
    }
}
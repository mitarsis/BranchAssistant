package com.assistant.branch.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ============================================================
// Запрос
// ============================================================

@Serializable
data class JevRequest(
    val state: JsonElement,        // строка или структурированный JSON
    val model: String = "jev-latest",
    val questions: Map<String, JevQuestion>,
)

@Serializable
sealed class JevQuestion {
    abstract val instructions: String
}

@Serializable
data class JevChoice(
    override val instructions: String,
    val criteria: Map<String, String>,
) : JevQuestion()

@Serializable
data class JevScore(
    override val instructions: String,
    val criteria: List<String>,
) : JevQuestion()

@Serializable
data class JevNoul(
    override val instructions: String,
) : JevQuestion()

// ============================================================
// Ответ
// ============================================================

@Serializable
data class JevResponse(
    val model: String,
    val answers: Map<String, JevAnswer>,
    val usage: JevUsage? = null,
)

@Serializable
data class JevUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
sealed class JevAnswer {
    abstract val type: String
    abstract val probabilities: Map<String, Double>

    /** Возвращает «главный» ответ — имя выбранного варианта или числовую оценку. */
    abstract val displayValue: String

    /** Все варианты ответа (для бара), отсортированные по убыванию вероятности. */
    open val rankedOptions: List<Pair<String, Double>> get() =
        probabilities.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

/** Confidence ответа в нормализованном виде (0..1). Вычисляется по-разному для каждого типа. */
val JevAnswer.confidence: Double
    get() = when (this) {
        is JevChoiceAnswer -> confidence
        is JevScoreAnswer -> confidence
        is JevNoulAnswer -> kotlin.math.abs(noul - 0.5) * 2
    }

@Serializable
@kotlinx.serialization.SerialName("choice")
data class JevChoiceAnswer(
    override val type: String = "choice",
    val choice: String,
    val confidence: Double = 0.0,
    override val probabilities: Map<String, Double>,
) : JevAnswer() {
    override val displayValue: String get() = choice
}

@Serializable
@kotlinx.serialization.SerialName("score")
data class JevScoreAnswer(
    override val type: String = "score",
    val score: Double,
    val legend: Map<String, String> = emptyMap(),
    val confidence: Double = 0.0,
    override val probabilities: Map<String, Double>,
) : JevAnswer() {
    override val displayValue: String get() = "%.2f".format(score)
}

@Serializable
@kotlinx.serialization.SerialName("noul")
data class JevNoulAnswer(
    override val type: String = "noul",
    val noul: Double,
) : JevAnswer() {
    override val displayValue: String get() = "%.0f%%".format(noul * 100)
    override val probabilities: Map<String, Double> get() = mapOf("true" to noul, "false" to 1.0 - noul)
}

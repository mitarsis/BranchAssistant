package com.assistant.branch.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val temperature: Float = 0.7f,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float = 1.0f,
    val stream: Boolean = true,
)

@Serializable
data class ApiMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatResponseChunk(
    val id: String,
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val delta: Delta,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
)

@Serializable
data class ApiError(
    val message: String,
    val type: String? = null,
)

@Serializable
data class NonStreamResponse(
    val id: String,
    val choices: List<NonStreamChoice>,
    val model: String? = null,
)

@Serializable
data class NonStreamChoice(
    val message: NonStreamMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class NonStreamMessage(
    val role: String? = null,
    val content: String,
)

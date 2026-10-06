package com.assistant.branch.repo

/**
 * Прогресс research (multi-step разветвление дерева агентом-исследователем).
 * UI подписывается на Flow<ResearchEvent> и пошагово показывает, что
 * вырастает.
 *
 * Типичный сценарий (для breadth=3, depth=1):
 *   Started("квантовая запутанность", 3, 1)
 *   OutlineProposed(["простыми словами", "математика", "история"])
 *   BranchStarted("простыми словами", userMsgId)
 *   MessageCreated(userMsgId, "простыми словами")
 *   BranchCompleted("простыми словами", assistantMsgId)
 *   BranchStarted("математика", ...)
 *   ...
 *   SynthesisReady(summaryId, assistantMsgId, "краткое summary...")
 *   Completed
 *
 * Если юзер нажал «стоп»:
 *   Aborted("Юзер остановил")
 */
sealed class ResearchEvent {
    abstract val seedMessageId: String

    /** Research только что запущен. seed — отправная точка дерева. */
    data class Started(
        override val seedMessageId: String,
        val topic: String,
        val breadth: Int,
        val depth: Int,
    ) : ResearchEvent()

    /** Агент сгенерировал outline из [angles]. UI рендерит их как
     *  "предложения" (isAgentSuggestion=true). Сразу после этого идёт углубление. */
    data class OutlineProposed(
        override val seedMessageId: String,
        val angles: List<String>,
    ) : ResearchEvent()

    /** Агент начал раскрывать угол [angle]. Внутри будет MessageCreated (user)
     *  и BranchCompleted (assistant). */
    data class BranchStarted(
        override val seedMessageId: String,
        val angle: String,
        val userMessageId: String,
    ) : ResearchEvent()

    /** Создано новое сообщение (user-suggestion или assistant). */
    data class MessageCreated(
        override val seedMessageId: String,
        val messageId: String,
        val parentId: String?,
        val isSuggestion: Boolean,
    ) : ResearchEvent()

    /** Агент закончил раскрывать угол. assistantMessageId — финальный ответ. */
    data class BranchCompleted(
        override val seedMessageId: String,
        val angle: String,
        val assistantMessageId: String,
    ) : ResearchEvent()

    /** Финальный synthesis-ответ готов. */
    data class SynthesisReady(
        override val seedMessageId: String,
        val summaryMessageId: String,
        val summary: String,
    ) : ResearchEvent()

    /** Research завершён успешно. */
    data class Completed(
        override val seedMessageId: String,
    ) : ResearchEvent()

    /** Прервано юзером или по ошибке. */
    data class Aborted(
        override val seedMessageId: String,
        val reason: String,
    ) : ResearchEvent()
}
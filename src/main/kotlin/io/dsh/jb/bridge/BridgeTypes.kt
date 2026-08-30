package io.dsh.jb.bridge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** One question forwarded from the runtime-side jb-bridge answerer. */
data class PlanQuestion(
    val id: String,
    val question: String,
    val detail: String? = null,
    val header: String? = null,
    val options: List<String> = emptyList(),
    /** The option label that approves (from the harness `plan-review` intent). */
    val approveLabel: String? = null,
    /** The harness question intent kind (`plan-review` or generic asks; item 9). */
    val intentKind: String? = null,
    /** Whether the harness question accepts several options (item 9). */
    val multiple: Boolean = false,
)

/** The IDE's decision for one question; mirrors `AskUserQuestionAnswerItem`. */
data class PlanAnswer(
    val id: String,
    val selected: List<String>,
    val custom: String? = null,
)

/** One `approval/request` forwarded from the answerer (item 9). */
data class BridgeApproval(
    /** Tool whose operation requires a decision. */
    val toolName: String,
    /** Exact tool call being decided, when available. */
    val callId: String? = null,
    /** Human-readable reason supplied by the asker. */
    val reason: String? = null,
)

/**
 * Shapes on the wire between the runtime-side answerer and the IDE bridge.
 * Kept as plain JSON so the Node side and the Kotlin side share one contract.
 */
object BridgeProtocol {
    fun questionFromJson(node: JsonNode): PlanQuestion = PlanQuestion(
        id = node.path("id").asText(""),
        question = node.path("question").asText(""),
        detail = node.path("detail").takeIf { it.isTextual }?.asText(),
        header = node.path("header").takeIf { it.isTextual }?.asText(),
        options = node.path("options").mapNotNull { it.path("label").takeIf { l -> l.isTextual }?.asText() },
        approveLabel = node.path("intent").path("approve").takeIf { it.isTextual }?.asText(),
        intentKind = node.path("intent").path("kind").takeIf { it.isTextual }?.asText(),
        multiple = node.path("multiple").asBoolean(false),
    )

    fun answersToJson(answers: List<PlanAnswer>, mapper: ObjectMapper): String {
        val root = mapper.createObjectNode()
        val array = root.putArray("answers")
        for (answer in answers) {
            val entry = array.addObject()
            entry.put("id", answer.id)
            val selected = entry.putArray("selected")
            answer.selected.forEach(selected::add)
            if (answer.custom != null) entry.put("custom", answer.custom)
        }
        return mapper.writeValueAsString(root)
    }

    /** Parses an `approval/request` payload; null when the tool name is absent. */
    fun approvalFromJson(node: JsonNode): BridgeApproval? {
        val toolName = node.path("toolName").takeIf { it.isTextual }?.asText().orEmpty()
        if (toolName.isBlank()) return null
        return BridgeApproval(
            toolName = toolName,
            callId = node.path("callId").takeIf { it.isTextual }?.asText(),
            reason = node.path("reason").takeIf { it.isTextual }?.asText(),
        )
    }

    /** Serializes one approval outcome for the answerer (`{"outcome":"..."}`). */
    fun approvalAnswerToJson(outcome: String, mapper: ObjectMapper): String {
        val root = mapper.createObjectNode()
        root.put("outcome", outcome)
        return mapper.writeValueAsString(root)
    }
}

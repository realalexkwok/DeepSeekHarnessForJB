package io.dsh.jb.bridge

import com.fasterxml.jackson.databind.JsonNode

/** One question forwarded from the runtime-side jb-bridge answerer. */
data class PlanQuestion(
    val id: String,
    val question: String,
    val detail: String? = null,
    val header: String? = null,
    val options: List<String> = emptyList(),
    /** The option label that approves (from the harness `plan-review` intent). */
    val approveLabel: String? = null,
)

/** The IDE's decision for one question; mirrors `AskUserQuestionAnswerItem`. */
data class PlanAnswer(
    val id: String,
    val selected: List<String>,
    val custom: String? = null,
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
    )

    fun answersToJson(answers: List<PlanAnswer>, mapper: com.fasterxml.jackson.databind.ObjectMapper): String {
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
}

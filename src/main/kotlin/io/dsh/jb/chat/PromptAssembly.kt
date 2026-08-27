package io.dsh.jb.chat

/**
 * Composer context-action vocabulary (roadmap item 11 pulled forward). FIX is
 * pending semantics and is not selectable in the UI yet.
 */
enum class ComposerAction(val display: String) {
    ASK("Ask"),
    EXECUTE("Execute"),
    PLAN("Plan"),
    FIX("Fix"),
}

/** Everything the composer can inject into one prompt. */
data class PromptContext(
    val action: ComposerAction,
    val includeCurrentFile: Boolean,
    val includeAgents: Boolean,
    val currentFilePath: String? = null,
    val currentFileContent: String? = null,
    val selection: String? = null,
    val agentsContent: String? = null,
)

/**
 * Assembles the wire prompt text (pure, unit-tested). Order: Selected code (only
 * when the current file is included), File, AGENTS.md — then the user text — then
 * the action instruction. Ask's read-only and Plan's plan-first behavior are
 * ADVISORY instructions: true sandbox/plan enforcement is a follow-up.
 */
object PromptAssembly {

    private const val ASK_INSTRUCTION =
        "This is a read-only request: answer without modifying any files or running state-changing commands."

    private const val PLAN_INSTRUCTION =
        "Before making any changes, research the request and present a plan for approval."

    fun assemble(userText: String, ctx: PromptContext): String {
        val sections = mutableListOf<String>()
        if (ctx.includeCurrentFile && !ctx.selection.isNullOrBlank()) {
            sections += "[Selected code]\n" + ctx.selection!!
        }
        if (ctx.includeCurrentFile && ctx.currentFilePath != null && ctx.currentFileContent != null) {
            sections += "[File: ${ctx.currentFilePath}]\n" + ctx.currentFileContent
        }
        if (ctx.includeAgents && ctx.agentsContent != null) {
            sections += "[AGENTS.md]\n" + ctx.agentsContent
        }
        val instruction = when (ctx.action) {
            ComposerAction.ASK -> ASK_INSTRUCTION
            ComposerAction.PLAN -> PLAN_INSTRUCTION
            ComposerAction.EXECUTE, ComposerAction.FIX -> null
        }
        return buildString {
            if (sections.isNotEmpty()) {
                append(sections.joinToString("\n\n"))
                append("\n\n")
            }
            append(userText)
            if (instruction != null) append("\n\n").append(instruction)
        }
    }
}

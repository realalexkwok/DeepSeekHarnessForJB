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
        "Confirm before changing anything: do not modify files or run state-changing commands yet. " +
            "For changes INSIDE the session workspace, call the ask_user_question tool ONCE with a concise question and options so the user can approve or decline. " +
            "For changes OUTSIDE the workspace, do NOT ask first — attempt the change directly; the sandbox denies the first attempt, and the permission prompt that follows IS the user's confirmation, so retry exactly once with sandbox_permissions and a one-sentence justification. " +
            "Never double-ask: one dialog per change. If the user approves, proceed. Do not dump full prospective file contents into the chat."

    private const val PLAN_INSTRUCTION =
        "Before making any changes, research the request and present a plan for approval."

    private const val FIX_INSTRUCTION =
        "Diagnose and repair the selected code: make the changes needed to fix its problems, then explain what you changed."

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
            ComposerAction.FIX -> FIX_INSTRUCTION
            ComposerAction.EXECUTE -> null
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

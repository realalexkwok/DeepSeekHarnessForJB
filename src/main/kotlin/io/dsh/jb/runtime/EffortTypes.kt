package io.dsh.jb.runtime

import java.util.concurrent.TimeUnit

/**
 * Reasoning-effort levels exposed in the composer Model tab (roadmap item 10/6/11
 * pulled forward). Wire ids match the harness adapter vocabulary
 * (llm-deepseek serialize.spec.ts): off = thinking disabled; low/high/max =
 * thinking enabled with that wire reasoning_effort.
 */
enum class EffortLevel(val wire: String, val display: String) {
    OFF("off", "Off"),
    LOW("low", "Low"),
    HIGH("high", "High"),
    MAX("max", "Max"),
    ;

    companion object {
        fun fromWire(value: String): EffortLevel = entries.firstOrNull { it.wire == value } ?: MAX
    }
}

/**
 * Pool key for the runtime pool: one process per (model, effort) selection
 * (user's design, 2026-08-27). maxTokens is not exposed in the UI today.
 */
data class RuntimeKey(val model: String, val effort: EffortLevel)

/**
 * Patches the bundled agent composition for one effort level: the llm-deepseek
 * block's thinking/reasoningEffort lines are rewritten (each appears exactly once).
 * Pure string function — unit-tested headlessly.
 */
object CordisEffort {
    private val THINKING = Regex("(?m)^(\\s*)thinking:\\s*\\S+")
    private val EFFORT = Regex("(?m)^(\\s*)reasoningEffort:\\s*\\S+")

    fun apply(base: String, effort: EffortLevel): String {
        val thinking = if (effort == EffortLevel.OFF) "disabled" else "enabled"
        return base
            .replace(THINKING) { it.groupValues[1] + "thinking: " + thinking }
            .replace(EFFORT) { it.groupValues[1] + "reasoningEffort: " + effort.wire }
    }
}


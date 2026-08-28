package io.dsh.jb.runtime

/**
 * Reasoning-effort levels exposed in the composer Model tab (roadmap item 10/6/11
 * pulled forward). Wire ids match the harness adapter vocabulary: off = thinking
 * disabled; low/high/max = thinking enabled with that wire reasoning_effort.
 * Since dsh-v0.1.2-alpha.1 the effort rides the `initialize.reasoningEffort`
 * wire field — no cordis patching (the previous CordisEffort design was removed
 * in the 2026-08-28 DSH adaptation).
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

/** Builds a per-runtime session id; the nonce makes repeated runs collision-free. */
fun buildSessionId(locationHash: String, key: RuntimeKey, nonce: String): String =
    "jb-" + locationHash + "-" + key.effort.wire + "-" +
        key.model.replace(Regex("[^A-Za-z0-9._-]"), "-") + "-" + nonce

/** A fresh 8-hex-character runtime nonce. */
fun newSessionNonce(): String =
    java.util.UUID.randomUUID().toString().replace("-", "").take(8)



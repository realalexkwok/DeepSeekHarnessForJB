package io.dsh.jb.diff

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/** One applied fs write/edit hunk: the path plus the LF-normalized before/after text. */
data class FileChange(
    val path: String,
    val oldText: String?,
    val newText: String,
)

/**
 * Parses the fs tools' result-time diff meta (roadmap item 7):
 * \"{ diffs: [{ path, oldText: string|null, newText }] }\" — mirrors the harness's
 * \"diffsFromMeta\" in packages/fs/tool-fs/src/diff.ts: absent, malformed, or
 * empty diffs yield null so presentation falls back to the plain result card.
 */
object FsDiffParser {

    fun parse(meta: JsonNode?): List<FileChange>? {
        if (meta == null || !meta.isObject) return null
        val diffs = meta.get("diffs") ?: return null
        if (!diffs.isArray || diffs.isEmpty) return null
        val out = ArrayList<FileChange>(diffs.size())
        for (entry in diffs) {
            val path = entry.path("path").takeIf { it.isTextual }?.asText() ?: return null
            val newText = entry.path("newText").takeIf { it.isTextual }?.asText() ?: return null
            // The harness requires oldText to be PRESENT (null = creation; a
            // missing field is malformed) — mirror diffsFromMeta exactly.
            if (!entry.has("oldText")) return null
            val oldNode = entry.get("oldText")
            val oldText = when {
                oldNode == null || oldNode.isNull -> null
                oldNode.isTextual -> oldNode.asText()
                else -> return null
            }
            out += FileChange(path, oldText, newText)
        }
        return out
    }

    /**
     * The file_path argument of a write/edit tool call, or null. Enables the
     * whole-file diff fallback when the result carries no contextual hunks
     * (creates, or a backend-declined diff basis — the harness's documented
     * "fall back to a whole-file diff" behavior).
     */
    fun filePathFromArguments(toolName: String, argumentsJson: String): String? {
        if (toolName != "write" && toolName != "edit") return null
        return try {
            val node = jacksonObjectMapper().readTree(argumentsJson)
            node.path("file_path").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}

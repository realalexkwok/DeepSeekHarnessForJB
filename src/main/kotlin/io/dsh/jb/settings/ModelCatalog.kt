package io.dsh.jb.settings

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** One selectable model: wire id plus a human-readable name. */
data class ModelInfo(val id: String, val name: String)

/**
 * Model discovery (roadmap item 6/10 pulled forward): the plugin's own HTTP call
 * — \"GET {base}/models\" with the keychain key — NOT a harness API (the SDK wire
 * has no model-listing method). Falls back to the harness adapter catalog;
 * catalog membership is cosmetic for requests (unknown ids route text-only).
 */
object ModelCatalog {

    /** The harness llm-deepseek adapter catalog (ids + display names, verified 2026-08-27). */
    val KNOWN = listOf(
        ModelInfo("deepseek-v4-flash", "DeepSeek-V4-Flash"),
        ModelInfo("deepseek-v4-pro", "DeepSeek-V4-Pro"),
        ModelInfo("deepseek-v4-flash-vision-exp", "DeepSeek-V4-Flash-Vision-Exp"),
    )

    /** Initial default model: the harness SDK's own default for SDK-created agents. */
    const val DEFAULT_MODEL = "deepseek-v4-flash"

    /** The DeepSeek adapter's public endpoint (llm-deepseek PUBLIC_BASE_URL). */
    const val PUBLIC_BASE_URL = "https://api.deepseek.com"

    fun displayNameFor(id: String): String = KNOWN.firstOrNull { it.id == id }?.name ?: id

    /** Parses an OpenAI-shaped \"GET /models\" response ({\"data\":[{\"id\":…}]}). */
    fun parseModels(json: String): List<ModelInfo> = try {
        val node = jacksonObjectMapper().readTree(json)
        node.path("data").mapNotNull { entry ->
            entry.path("id").takeIf { it.isTextual }?.asText()
                ?.takeIf { it.isNotBlank() }
                ?.let { ModelInfo(it, displayNameFor(it)) }
        }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Fetches the model list over the plugin's own HTTP; on any failure returns the
     * known catalog. Detected ids keep the catalog's display names when they match.
     */
    fun fetch(baseUrl: String?, apiKey: String?): List<ModelInfo> {
        val base = baseUrl?.trim()?.trimEnd('/')?.ifBlank { PUBLIC_BASE_URL } ?: PUBLIC_BASE_URL
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty()) return KNOWN
        return try {
            val request = HttpRequest.newBuilder(URI.create("$base/models"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer $key")
                .GET()
                .build()
            val body = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString())
                .body()
            merge(KNOWN, parseModels(body))
        } catch (_: Exception) {
            KNOWN
        }
    }

    /** Known catalog first (keeps display names), then detected ids not already present. */
    fun merge(known: List<ModelInfo>, detected: List<ModelInfo>): List<ModelInfo> {
        val seen = known.map { it.id }.toMutableSet()
        val extras = detected.filter { seen.add(it.id) }
        return known + extras
    }
}

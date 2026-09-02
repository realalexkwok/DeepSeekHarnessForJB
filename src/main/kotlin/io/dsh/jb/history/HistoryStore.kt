package io.dsh.jb.history

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

/**
 * Item 22: per-project session history (Kilo model: project-scoped sessions,
 * per-session append-only chat log + an index of metadata — id, title,
 * created, updated). Stored under .idea/dsh/history/; .idea is gitignored
 * by convention, so the log travels with the checkout but not into VCS.
 *
 * Pure Jackson + java.io — no IntelliJ dependencies, so the pure-JVM suite
 * covers it. Appends are synchronized: prompt echoes arrive on the EDT,
 * batched event writes arrive from the transcript flush (also EDT), but the
 * lock keeps any future background writer safe.
 */
class HistoryStore(private val projectDir: File) {

    data class Entry(val id: String, val title: String, val created: Long, val updated: Long)

    data class Line(val t: Long, val kind: String, val payload: String)

    private val mapper = jacksonObjectMapper()
    private val lock = Any()

    private val root: File get() = File(projectDir, ".idea/dsh/history")
    private val indexFile: File get() = File(root, "index.json")
    private fun sessionFile(id: String): File = File(root, id + ".jsonl")

    /** Records one user prompt; the FIRST prompt line becomes the entry title. */
    fun appendPrompt(id: String, text: String) = synchronized(lock) {
        val title = readIndex().firstOrNull { it.id == id }?.title?.takeIf { it.isNotBlank() }
            ?: text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(TITLE_CHARS).orEmpty()
        appendEvents(id, listOf("prompt" to text))
        touch(id, title)
    }

    /** Bulk-appends pre-serialized event JSON in ONE write (per 150 ms flush). */
    fun appendEvents(id: String, events: List<Pair<String, String>>) = synchronized(lock) {
        if (events.isEmpty()) return
        root.mkdirs()
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        for ((kind, payload) in events) {
            val node = mapper.createObjectNode()
            node.put("t", now)
            node.put("kind", kind)
            node.put("payload", payload)
            sb.append(mapper.writeValueAsString(node)).append('\n')
        }
        sessionFile(id).appendText(sb.toString())
        touch(id, null)
    }

    fun hasContent(id: String): Boolean = synchronized(lock) {
        val f = sessionFile(id)
        f.isFile && f.length() > 0
    }

    /** Item 22 follow-up: renames an entry (updates the index title only). */
    fun rename(id: String, title: String) = synchronized(lock) {
        val trimmed = title.trim().take(TITLE_CHARS)
        if (trimmed.isBlank()) return
        touch(id, trimmed)
    }

    /** Item 22 follow-up: deletes the session log and its index entry. */
    fun delete(id: String) = synchronized(lock) {
        sessionFile(id).delete()
        writeIndex(readIndex().filter { it.id != id })
    }

    /** Index metadata, newest activity first. */
    fun entries(): List<Entry> = synchronized(lock) {
        readIndex().sortedByDescending { it.updated }
    }

    /** Stored lines in write order. */
    fun lines(id: String): List<Line> = synchronized(lock) {
        val f = sessionFile(id)
        if (!f.isFile) return emptyList()
        f.readLines().mapNotNull { raw ->
            runCatching {
                val node = mapper.readTree(raw)
                Line(node["t"].asLong(), node["kind"].asText(), node["payload"].asText())
            }.getOrNull()
        }
    }

    private fun touch(id: String, title: String?) {
        val now = System.currentTimeMillis()
        val existing = readIndex().firstOrNull { it.id == id }
        val entry = Entry(id, title ?: existing?.title.orEmpty(), existing?.created ?: now, now)
        writeIndex((readIndex().filter { it.id != id } + entry).sortedByDescending { it.updated })
    }

    private fun writeIndex(entries: List<Entry>) {
        root.mkdirs()
        val arr = mapper.createArrayNode()
        for (e in entries) {
            val o = mapper.createObjectNode()
            o.put("id", e.id)
            o.put("title", e.title)
            o.put("created", e.created)
            o.put("updated", e.updated)
            arr.add(o)
        }
        indexFile.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr))
    }

    private fun readIndex(): List<Entry> = runCatching {
        if (!indexFile.isFile) return emptyList()
        mapper.readTree(indexFile.readText()).map { node ->
            Entry(node["id"].asText(), node["title"].asText(), node["created"].asLong(), node["updated"].asLong())
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val TITLE_CHARS = 80
    }
}

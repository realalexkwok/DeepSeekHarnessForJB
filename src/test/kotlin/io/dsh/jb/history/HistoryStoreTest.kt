package io.dsh.jb.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Roadmap item 22: pure file-store round-trips for session history. */
class HistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun promptLinesAndEventLinesRoundTripInOrder() {
        val store = HistoryStore(tmp.root)
        store.appendPrompt("s1", "First line\nsecond line")
        store.appendEvents("s1", listOf("event" to "{\"type\":\"a\"}", "event" to "{\"type\":\"b\"}"))
        val lines = store.lines("s1")
        assertEquals(listOf("prompt", "event", "event"), lines.map { it.kind })
        assertEquals("First line\nsecond line", lines[0].payload)
        assertEquals("{\"type\":\"b\"}", lines[2].payload)
    }

    @Test
    fun firstPromptLineBecomesTitleAndIsKeptOnLaterAppends() {
        val store = HistoryStore(tmp.root)
        store.appendPrompt("s1", "Fix the build\nplease")
        store.appendPrompt("s1", "Also add tests")
        val entry = store.entries().single()
        assertEquals("Fix the build", entry.title)
        assertEquals("s1", entry.id)
    }

    @Test
    fun entriesComeNewestFirst() {
        val store = HistoryStore(tmp.root)
        store.appendPrompt("old", "old session")
        store.appendPrompt("new", "new session")
        val ids = store.entries().map { it.id }
        assertEquals(listOf("new", "old"), ids)
    }

    @Test
    fun hasContentTracksWrites() {
        val store = HistoryStore(tmp.root)
        assertFalse(store.hasContent("s1"))
        store.appendPrompt("s1", "hello")
        assertTrue(store.hasContent("s1"))
    }

    @Test
    fun missingSessionHasNoLines() {
        val store = HistoryStore(tmp.root)
        assertTrue(store.lines("ghost").isEmpty())
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun renameUpdatesTitleButKeepsCreated() {
        val store = HistoryStore(tmp.root)
        store.appendPrompt("s1", "old title")
        val created = store.entries().single().created
        store.rename("s1", "  new title  ")
        val entry = store.entries().single()
        assertEquals("new title", entry.title)
        assertEquals(created, entry.created)
        assertTrue(entry.updated >= created)
    }

    @Test
    fun blankRenameIsIgnored() {
        val store = HistoryStore(tmp.root)
        store.appendPrompt("s1", "keep me")
        store.rename("s1", "   ")
        assertEquals("keep me", store.entries().single().title)
    }

    @Test
    fun deleteRemovesLogAndIndexEntry() {
        val store = HistoryStore(tmp.root)
        store.appendPrompt("s1", "one")
        store.appendPrompt("s2", "two")
        store.delete("s1")
        assertEquals(listOf("s2"), store.entries().map { it.id })
        assertTrue(store.lines("s1").isEmpty())
        assertTrue(store.lines("s2").isNotEmpty())
    }
}

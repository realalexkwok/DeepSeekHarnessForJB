package io.dsh.jb.diff

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Roadmap item 7: fs diff meta parsing (pure). */
class FsDiffParserTest {

    private val mapper = jacksonObjectMapper()

    private fun node(json: String) = mapper.readTree(json)

    @Test
    fun `parses update hunks`() {
        val changes = FsDiffParser.parse(node("""{"diffs":[{"path":"a.kt","oldText":"old","newText":"new"},{"path":"b.kt","oldText":null,"newText":"born"}]}"""))
        assertEquals(2, changes!!.size)
        assertEquals(FileChange("a.kt", "old", "new"), changes[0])
        assertEquals(FileChange("b.kt", null, "born"), changes[1])
    }

    @Test
    fun `malformed entries yield null`() {
        assertNull(FsDiffParser.parse(node("""{"diffs":[{"path":"a.kt","newText":"x"}]}""")))
        assertNull(FsDiffParser.parse(node("""{"diffs":[{"path":1,"oldText":"o","newText":"n"}]}""")))
        assertNull(FsDiffParser.parse(node("""{"diffs":[{"path":"a.kt","oldText":5,"newText":"n"}]}""")))
    }

    @Test
    fun `absent empty or wrong shapes yield null`() {
        assertNull(FsDiffParser.parse(null))
        assertNull(FsDiffParser.parse(node("""{"other":1}""")))
        assertNull(FsDiffParser.parse(node("""{"diffs":[]}""")))
        assertNull(FsDiffParser.parse(node("""{"diffs":"x"}""")))
    }

    @Test
    fun `file path extraction for write and edit only`() {
        assertEquals("a.txt", FsDiffParser.filePathFromArguments("write", """{"file_path":"a.txt","content":"x"}"""))
        assertEquals("b.txt", FsDiffParser.filePathFromArguments("edit", """{"file_path":"b.txt","old_string":"o","new_string":"n"}"""))
        assertNull(FsDiffParser.filePathFromArguments("bash", """{"command":"ls"}"""))
        assertNull(FsDiffParser.filePathFromArguments("write", "not json"))
        assertNull(FsDiffParser.filePathFromArguments("write", """{"other":1}"""))
    }
}

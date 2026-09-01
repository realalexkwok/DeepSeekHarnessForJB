package io.dsh.jb.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.awt.Font

/**
 * Roadmap item 19: pure derivation of the editor-derived typography snapshot.
 * [DshEditorStyle.derive] takes the platform fonts as parameters, so the
 * derivation needs no IDE services and runs in the pure-JVM suite.
 */
class DshEditorStyleTest {

    // Logical font families: deriveFont in a headless JDK substitutes
    // UNKNOWN families with "Dialog", so tests use always-resolvable logical
    // families to prove family preservation and size overrides.
    private val uiRegular = Font(Font.SANS_SERIF, Font.PLAIN, 13)
    private val uiSmall = Font(Font.SANS_SERIF, Font.PLAIN, 11)
    private val header = Font(Font.SANS_SERIF, Font.BOLD, 16)

    @Test
    fun editorFontKeepsEditorFamilyAndSize() {
        val style = DshEditorStyle.derive("Monospaced", 15, uiRegular, uiSmall, header)
        assertEquals("Monospaced", style.editorFont.family)
        assertEquals(15, style.editorFont.size)
        assertEquals(Font.PLAIN, style.editorFont.style)
    }

    @Test
    fun transcriptFontUsesUiFamilyAtEditorSize() {
        val style = DshEditorStyle.derive("Monospaced", 15, uiRegular, uiSmall, header)
        assertEquals(Font.SANS_SERIF, style.transcriptFont.family)
        assertNotEquals("Monospaced", style.transcriptFont.family)
        assertEquals(15, style.transcriptFont.size)
        assertEquals(Font.PLAIN, style.transcriptFont.style)
    }

    @Test
    fun boldEditorFontIsUiFamilyBoldAtEditorSize() {
        val style = DshEditorStyle.derive("Monospaced", 15, uiRegular, uiSmall, header)
        assertEquals(Font.SANS_SERIF, style.boldEditorFont.family)
        assertEquals(Font.BOLD, style.boldEditorFont.style)
        assertEquals(15, style.boldEditorFont.size)
    }

    @Test
    fun smallEditorFontScalesBySmallToRegularRatio() {
        val style = DshEditorStyle.derive("Monospaced", 15, uiRegular, uiSmall, header)
        val expected = Math.round(15f * 11f / 13f).toInt().coerceAtLeast(1)
        assertEquals(expected, style.smallEditorFont.size)
        assertEquals(Font.PLAIN, style.smallEditorFont.style)
    }

    @Test
    fun headerAndPlatformFontsPassThrough() {
        val style = DshEditorStyle.derive("Monospaced", 15, uiRegular, uiSmall, header)
        assertEquals(header, style.headerFont)
        assertEquals(uiRegular, style.regularFont)
        assertEquals(uiSmall, style.smallFont)
        assertEquals(Font.BOLD, style.boldFont.style)
        assertEquals(13, style.boldFont.size) // platform size untouched by style derivation
    }
}

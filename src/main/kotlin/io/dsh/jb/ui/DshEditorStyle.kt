package io.dsh.jb.ui

import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBFont
import java.awt.Font
import kotlin.math.roundToInt

/**
 * Item 19: immutable editor-derived typography snapshot (Kilo
 * SessionEditorStyle model, reimplemented). All chat surfaces read their fonts
 * from ONE snapshot instead of inheriting component defaults, so theme/zoom
 * changes re-apply consistently through [DshStyleTarget].
 *
 * Core rule (user-selected 2026-09-01): the transcript body uses the UI font
 * FAMILY at the EDITOR font SIZE — chat text tracks Ctrl+wheel editor zoom —
 * while code surfaces keep the editor family+size.
 *
 * The [derive] factory is pure (no platform services) so the derivation is
 * unit-testable in the pure-JVM suite.
 */
data class DshEditorStyle(
    /** Editor family+size — code surfaces: tool args, raw meta. */
    val editorFont: Font,
    /** UI family at editor size — transcript body, composer input, dialog bodies. */
    val transcriptFont: Font,
    /** Small UI family scaled to the editor-size ratio — thinking preview, nested mono-ish text. */
    val smallEditorFont: Font,
    /** UI family, bold, editor size. */
    val boldEditorFont: Font,
    /** h4 bold — card headers (assistant/tool/user name). */
    val headerFont: Font,
    /** Platform regular UI font. */
    val regularFont: Font,
    /** Platform bold UI font. */
    val boldFont: Font,
    /** Platform small UI font — secondary text: status, usage, popup detail, notices. */
    val smallFont: Font,
) {
    companion object {
        /** Snapshot from the current global editor scheme (scaled by UI settings). */
        fun current(): DshEditorStyle {
            val scheme = EditorColorsManager.getInstance().globalScheme
            val size = UISettingsUtils.getInstance()
                .scaleFontSize(scheme.editorFontSize.toFloat())
                .roundToInt()
                .coerceAtLeast(1)
            return derive(scheme.editorFontName, size, JBFont.regular(), JBFont.small(), JBFont.h4().asBold())
        }

        /**
         * Pure derivation, platform-free so the unit tests need no IDE services.
         * The small-editor size scales [uiSmall] by its ratio to [uiRegular]
         * (Kilo's scaledEditorSize pattern: small :: label, applied to the
         * editor size).
         */
        internal fun derive(
            editorFamily: String,
            editorSize: Int,
            uiRegular: Font,
            uiSmall: Font,
            headerFont: Font,
        ): DshEditorStyle {
            val base = uiRegular.size.coerceAtLeast(1)
            val ratio = uiSmall.size.toFloat() / base
            val smallSize = (editorSize * ratio).roundToInt().coerceAtLeast(1)
            return DshEditorStyle(
                editorFont = Font(editorFamily, Font.PLAIN, editorSize),
                transcriptFont = uiRegular.deriveFont(Font.PLAIN, editorSize.toFloat()),
                smallEditorFont = uiSmall.deriveFont(Font.PLAIN, smallSize.toFloat()),
                boldEditorFont = uiRegular.deriveFont(Font.BOLD, editorSize.toFloat()),
                headerFont = headerFont,
                regularFont = uiRegular,
                boldFont = uiRegular.deriveFont(Font.BOLD),
                smallFont = uiSmall,
            )
        }
    }
}

/** Session component contract: apply a refreshed [DshEditorStyle] without rebuilding Swing nodes. */
interface DshStyleTarget {
    fun applyStyle(style: DshEditorStyle)
}

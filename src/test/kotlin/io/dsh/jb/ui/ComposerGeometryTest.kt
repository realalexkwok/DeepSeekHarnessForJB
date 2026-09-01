package io.dsh.jb.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap item 20: pure composer height clamp arithmetic. */
class ComposerGeometryTest {

    @Test
    fun contentBelowOneLineClampsToMinimum() {
        assertEquals(16, clampComposerHeight(4, 16, 10))
    }

    @Test
    fun contentAboveCapClampsToCap() {
        assertEquals(160, clampComposerHeight(900, 16, 10))
    }

    @Test
    fun midRangeContentPassesThrough() {
        assertEquals(64, clampComposerHeight(64, 16, 10))
    }

    @Test
    fun degenerateLineHeightFallsBackToOne() {
        assertEquals(1, clampComposerHeight(0, 0, 10))
        assertEquals(10, clampComposerHeight(999, 0, 10))
    }

    @Test
    fun degenerateMaxLinesFallsBackToOne() {
        assertEquals(16, clampComposerHeight(5, 16, 0))
        assertEquals(16, clampComposerHeight(999, 16, -3))
    }
}

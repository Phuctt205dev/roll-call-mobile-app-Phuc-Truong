package com.example.roll_call

import com.example.roll_call.domain.model.omr.OmrAnswerStatus
import com.example.roll_call.utils.omr.BubbleDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubbleDetectorTest {
    @Test
    fun classifySelection_returnsBlank_whenAllRatiosBelowBlankThreshold() {
        val result = BubbleDetector.classifySelection(mapOf("A" to 0.04, "B" to 0.07, "C" to 0.05, "D" to 0.03))

        assertEquals(OmrAnswerStatus.BLANK, result.status)
        assertNull(result.selected)
    }

    @Test
    fun classifySelection_returnsMultiple_whenMoreThanOneRatioIsFilled() {
        val result = BubbleDetector.classifySelection(mapOf("A" to 0.41, "B" to 0.12, "C" to 0.39, "D" to 0.04))

        assertEquals(OmrAnswerStatus.MULTIPLE, result.status)
        assertEquals("A", result.selected)
    }

    @Test
    fun classifySelection_returnsUncertain_whenTopRatioIsBetweenBlankAndFilled() {
        val result = BubbleDetector.classifySelection(mapOf("A" to 0.22, "B" to 0.10, "C" to 0.06, "D" to 0.05))

        assertEquals(OmrAnswerStatus.UNCERTAIN, result.status)
        assertEquals("A", result.selected)
    }

    @Test
    fun classifySelection_returnsUncertain_whenTopTwoRatiosAreTooClose() {
        val result = BubbleDetector.classifySelection(mapOf("A" to 0.38, "B" to 0.31, "C" to 0.08, "D" to 0.05))

        assertEquals(OmrAnswerStatus.UNCERTAIN, result.status)
        assertEquals("A", result.selected)
    }
}

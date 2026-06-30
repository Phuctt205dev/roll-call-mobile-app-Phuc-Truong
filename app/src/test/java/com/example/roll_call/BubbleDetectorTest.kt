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

    @Test
    fun classifyDigitColumn_returnsDigit_whenPrintedBubbleIsBelowAbsoluteFilledThresholdButDominant() {
        val result = BubbleDetector.classifyDigitColumn(
            mapOf(
                0 to 0.05,
                1 to 0.07,
                2 to 0.24,
                3 to 0.06,
                4 to 0.08,
                5 to 0.05,
                6 to 0.07,
                7 to 0.06,
                8 to 0.08,
                9 to 0.05,
            ),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.OK, result.status)
        assertEquals(2, result.selected)
    }

    @Test
    fun classifyDigitColumn_returnsUncertainWithBestDigit_whenPrintedColumnHasCloseCandidates() {
        val result = BubbleDetector.classifyDigitColumn(
            mapOf(
                0 to 0.06,
                1 to 0.07,
                2 to 0.20,
                3 to 0.18,
                4 to 0.08,
                5 to 0.06,
                6 to 0.07,
                7 to 0.06,
                8 to 0.08,
                9 to 0.06,
            ),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.UNCERTAIN, result.status)
        assertEquals(2, result.selected)
    }

    @Test
    fun classifyAnswerSelection_returnsBlank_whenBlankRowHasLightNoiseOnOneOption() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.05, "B" to 0.06, "C" to 0.04, "D" to 0.16),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.BLANK, result.status)
        assertNull(result.selected)
    }

    @Test
    fun classifyAnswerSelection_returnsBlank_whenNoBubbleReachesFilledThreshold() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.04, "B" to 0.06, "C" to 0.23, "D" to 0.07),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.BLANK, result.status)
        assertNull(result.selected)
    }

    @Test
    fun classifyAnswerSelection_returnsBlank_whenOnlyPrintedOutlineLooksDark() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.07, "B" to 0.09, "C" to 0.08, "D" to 0.26),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.BLANK, result.status)
        assertNull(result.selected)
    }

    @Test
    fun classifyAnswerSelection_returnsMultiple_whenTwoAnswersAreFilled() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.05, "B" to 0.06, "C" to 0.40, "D" to 0.43),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.MULTIPLE, result.status)
        assertEquals("D", result.selected)
    }

    @Test
    fun classifyAnswerSelection_returnsSingleAnswer_whenSecondCandidateIsOnlyPrintNoise() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.27, "B" to 0.46, "C" to 0.08, "D" to 0.07),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.OK, result.status)
        assertEquals("B", result.selected)
    }

    @Test
    fun classifyAnswerSelection_returnsMultiple_whenSecondFilledAnswerIsSlightlyLighter() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.06, "B" to 0.08, "C" to 0.36, "D" to 0.45),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.MULTIPLE, result.status)
        assertEquals("D", result.selected)
        assertEquals(listOf("D", "C"), result.filledCandidates)
    }

    @Test
    fun classifyAnswerSelection_returnsAnswer_whenSecondCandidateIsOutlineNoiseNearThreshold() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.06, "B" to 0.08, "C" to 0.33, "D" to 0.45),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.OK, result.status)
        assertEquals("D", result.selected)
        assertEquals(listOf("D"), result.filledCandidates)
    }
    @Test
    fun classifyAnswerSelection_returnsAnswer_whenSecondCandidateIsAboveFilledButClearlyWeaker() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.06, "B" to 0.08, "C" to 0.30, "D" to 0.46),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.OK, result.status)
        assertEquals("D", result.selected)
        assertEquals(listOf("D"), result.filledCandidates)
    }

    @Test
    fun classifyAnswerSelection_returnsAnswer_whenBubbleReachesFilledThreshold() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.04, "B" to 0.06, "C" to 0.31, "D" to 0.07),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.OK, result.status)
        assertEquals("C", result.selected)
    }
    @Test
    fun classifyAnswerSelection_returnsBlank_whenPrintedOutlinesAreUniformlyDark() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.29, "B" to 0.31, "C" to 0.30, "D" to 0.32),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.BLANK, result.status)
        assertNull(result.selected)
    }

    @Test
    fun classifyAnswerSelection_returnsAnswer_whenWholeRowIsDarkerButOneBubbleDominates() {
        val result = BubbleDetector.classifyAnswerSelection(
            mapOf("A" to 0.30, "B" to 0.32, "C" to 0.51, "D" to 0.33),
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08
        )

        assertEquals(OmrAnswerStatus.OK, result.status)
        assertEquals("C", result.selected)
    }
}

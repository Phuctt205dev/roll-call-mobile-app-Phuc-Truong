package com.example.roll_call.utils.omr

import com.example.roll_call.domain.model.omr.BubbleReadResult
import com.example.roll_call.domain.model.omr.BubbleRegion
import com.example.roll_call.domain.model.omr.OmrAnswerStatus
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

class BubbleDetector(
    private val filledThreshold: Double,
    private val blankThreshold: Double,
    private val uncertainDelta: Double
) {
    fun readBubble(binaryInverse: Mat, region: BubbleRegion): BubbleReadResult {
        val cropRadius = region.radius.toInt().coerceAtLeast(4)
        val left = (region.centerX - cropRadius).toInt().coerceAtLeast(0)
        val top = (region.centerY - cropRadius).toInt().coerceAtLeast(0)
        val right = (region.centerX + cropRadius).toInt().coerceAtMost(binaryInverse.cols() - 1)
        val bottom = (region.centerY + cropRadius).toInt().coerceAtMost(binaryInverse.rows() - 1)
        if (right <= left || bottom <= top) {
            return BubbleReadResult(region.id, 0.0, false)
        }

        val roiRect = Rect(left, top, right - left, bottom - top)
        val roi = Mat(binaryInverse, roiRect)
        val mask = Mat.zeros(roi.rows(), roi.cols(), CvType.CV_8UC1)
        val readRadius = (cropRadius * 0.76).toInt()
            .coerceAtLeast(3)
            .coerceAtMost(minOf(roi.cols(), roi.rows()) / 2)
        Imgproc.circle(
            mask,
            Point(roi.cols() / 2.0, roi.rows() / 2.0),
            readRadius,
            Scalar(255.0),
            -1
        )

        val masked = Mat()
        Core.bitwise_and(roi, mask, masked)
        val bubblePixels = Core.countNonZero(mask).coerceAtLeast(1)
        val filledPixels = Core.countNonZero(masked)
        val ratio = filledPixels.toDouble() / bubblePixels.toDouble()

        roi.release()
        mask.release()
        masked.release()

        return BubbleReadResult(
            id = region.id,
            fillRatio = ratio,
            isFilled = ratio >= filledThreshold
        )
    }

    fun <T> classifySelection(
        fillRatios: Map<T, Double>,
        allowSoftSelection: Boolean = false
    ): BubbleSelection<T> {
        return classifySelection(
            fillRatios = fillRatios,
            filledThreshold = filledThreshold,
            blankThreshold = blankThreshold,
            uncertainDelta = uncertainDelta,
            allowSoftSelection = allowSoftSelection
        )
    }

    fun <T> classifyAnswerSelection(fillRatios: Map<T, Double>): BubbleSelection<T> {
        return classifyAnswerSelection(
            fillRatios = fillRatios,
            filledThreshold = filledThreshold,
            blankThreshold = blankThreshold,
            uncertainDelta = uncertainDelta
        )
    }

    fun <T> classifyDigitColumn(fillRatios: Map<T, Double>): BubbleSelection<T> {
        return classifyDigitColumn(
            fillRatios = fillRatios,
            filledThreshold = filledThreshold,
            blankThreshold = blankThreshold,
            uncertainDelta = uncertainDelta
        )
    }

    companion object {
        private const val SOFT_SELECTION_RATIO = 1.35
        private const val ANSWER_FILLED_THRESHOLD_FACTOR = 1.0
        private const val ANSWER_BLANK_THRESHOLD_FACTOR = 2.25
        private const val ANSWER_MULTIPLE_THRESHOLD_FACTOR = 1.0
        private const val ANSWER_MULTIPLE_BLANK_FACTOR = 2.3
        private const val ANSWER_MULTIPLE_RELATIVE_FACTOR = 0.70
        private const val DIGIT_DOMINANCE_RATIO = 1.22
        private const val DIGIT_DELTA_FACTOR = 0.55
        private const val DIGIT_BASELINE_DELTA_FACTOR = 0.35

        fun <T> classifySelection(
            fillRatios: Map<T, Double>,
            filledThreshold: Double = 0.35,
            blankThreshold: Double = 0.18,
            uncertainDelta: Double = 0.08,
            allowSoftSelection: Boolean = false
        ): BubbleSelection<T> {
            if (fillRatios.isEmpty()) {
                return BubbleSelection(null, OmrAnswerStatus.BLANK, emptyList())
            }

            val sorted = fillRatios.entries.sortedByDescending { it.value }
            val top = sorted.first()
            val second = sorted.drop(1).firstOrNull()
            val filled = sorted.filter { it.value >= filledThreshold }

            return when {
                allowSoftSelection && second != null && filled.size > 1 &&
                    top.value - second.value >= uncertainDelta &&
                    top.value >= second.value * SOFT_SELECTION_RATIO -> BubbleSelection(top.key, OmrAnswerStatus.OK, listOf(top.key))
                filled.size > 1 -> BubbleSelection(top.key, OmrAnswerStatus.MULTIPLE, filled.map { it.key })
                filled.size == 1 && second != null && top.value - second.value < uncertainDelta -> {
                    BubbleSelection(top.key, OmrAnswerStatus.UNCERTAIN, listOf(top.key))
                }
                filled.size == 1 -> BubbleSelection(top.key, OmrAnswerStatus.OK, listOf(top.key))
                allowSoftSelection && second != null &&
                    top.value >= blankThreshold &&
                    top.value - second.value >= uncertainDelta &&
                    top.value >= second.value * SOFT_SELECTION_RATIO -> BubbleSelection(top.key, OmrAnswerStatus.OK, listOf(top.key))
                top.value < blankThreshold -> BubbleSelection(null, OmrAnswerStatus.BLANK, emptyList())
                else -> BubbleSelection(top.key, OmrAnswerStatus.UNCERTAIN, listOf(top.key))
            }
        }

        fun <T> classifyAnswerSelection(
            fillRatios: Map<T, Double>,
            filledThreshold: Double = 0.35,
            blankThreshold: Double = 0.18,
            uncertainDelta: Double = 0.08
        ): BubbleSelection<T> {
            if (fillRatios.isEmpty()) {
                return BubbleSelection(null, OmrAnswerStatus.BLANK, emptyList())
            }

            val sorted = fillRatios.entries.sortedByDescending { it.value }
            val top = sorted.first()
            val second = sorted.drop(1).firstOrNull()
            val effectiveFilledThreshold = maxOf(
                filledThreshold * ANSWER_FILLED_THRESHOLD_FACTOR,
                blankThreshold * ANSWER_BLANK_THRESHOLD_FACTOR
            )
            val multipleThreshold = maxOf(
                filledThreshold * ANSWER_MULTIPLE_THRESHOLD_FACTOR,
                blankThreshold * ANSWER_MULTIPLE_BLANK_FACTOR
            )
            val filled = sorted.filter { it.value >= effectiveFilledThreshold }
            val multipleCandidates = sorted.filter { it.value >= multipleThreshold }
            val strongMultipleCandidates = multipleCandidates.filterIndexed { index, candidate ->
                index == 0 || candidate.value >= top.value * ANSWER_MULTIPLE_RELATIVE_FACTOR
            }

            return when {
                filled.isEmpty() -> BubbleSelection(null, OmrAnswerStatus.BLANK, emptyList())
                strongMultipleCandidates.size > 1 -> {
                    BubbleSelection(top.key, OmrAnswerStatus.MULTIPLE, strongMultipleCandidates.map { it.key })
                }
                filled.size > 1 -> {
                    BubbleSelection(top.key, OmrAnswerStatus.UNCERTAIN, filled.map { it.key })
                }
                else -> BubbleSelection(top.key, OmrAnswerStatus.OK, listOf(top.key))
            }
        }

        fun <T> classifyDigitColumn(
            fillRatios: Map<T, Double>,
            filledThreshold: Double = 0.35,
            blankThreshold: Double = 0.18,
            uncertainDelta: Double = 0.08
        ): BubbleSelection<T> {
            if (fillRatios.isEmpty()) {
                return BubbleSelection(null, OmrAnswerStatus.BLANK, emptyList())
            }

            val sorted = fillRatios.entries.sortedByDescending { it.value }
            val top = sorted.first()
            val second = sorted.drop(1).firstOrNull()
            val values = sorted.map { it.value }.sorted()
            val median = values[values.size / 2]
            val secondValue = second?.value ?: 0.0
            val minUsableRatio = maxOf(
                blankThreshold * 0.85,
                median + uncertainDelta * DIGIT_BASELINE_DELTA_FACTOR
            )

            if (top.value < minUsableRatio) {
                return BubbleSelection(null, OmrAnswerStatus.BLANK, emptyList())
            }

            val delta = top.value - secondValue
            val dominatesByDelta = second == null || delta >= uncertainDelta * DIGIT_DELTA_FACTOR
            val dominatesByRatio = second == null ||
                secondValue <= 0.01 ||
                top.value >= secondValue * DIGIT_DOMINANCE_RATIO
            val isStrong = top.value >= filledThreshold

            return when {
                isStrong && (dominatesByDelta || dominatesByRatio) -> {
                    BubbleSelection(top.key, OmrAnswerStatus.OK, listOf(top.key))
                }
                dominatesByDelta && dominatesByRatio -> {
                    BubbleSelection(top.key, OmrAnswerStatus.OK, listOf(top.key))
                }
                else -> {
                    BubbleSelection(top.key, OmrAnswerStatus.UNCERTAIN, listOf(top.key))
                }
            }
        }
    }
}

data class BubbleSelection<T>(
    val selected: T?,
    val status: OmrAnswerStatus,
    val filledCandidates: List<T>
)

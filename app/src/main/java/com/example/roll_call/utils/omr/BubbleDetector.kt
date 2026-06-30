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
        val readRadius = (cropRadius * 0.68).toInt()
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

    companion object {
        private const val SOFT_SELECTION_RATIO = 1.35
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
    }
}

data class BubbleSelection<T>(
    val selected: T?,
    val status: OmrAnswerStatus,
    val filledCandidates: List<T>
)

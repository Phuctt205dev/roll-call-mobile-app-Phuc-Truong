package com.example.roll_call.utils.omr

import android.graphics.Bitmap
import com.example.roll_call.domain.model.omr.FixedOmrTemplate
import com.example.roll_call.domain.model.omr.OmrAnswerStatus
import com.example.roll_call.domain.model.omr.OmrDebugInfo
import com.example.roll_call.domain.model.omr.OmrQuestionAnswer
import com.example.roll_call.domain.model.omr.OmrScanResult
import com.example.roll_call.domain.model.omr.OmrTemplate
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OmrProcessor(
    private val template: OmrTemplate = FixedOmrTemplate.default,
    private val debugCacheDir: File? = null,
    private val debugEnabled: Boolean = true,
    private val correctAnswers: Map<String, String> = emptyMap(),
    private val debugAnswersOverride: List<OmrQuestionAnswer>? = null
) {
    private val bubbleDetector = BubbleDetector(
        filledThreshold = template.filledThreshold,
        blankThreshold = template.blankThreshold,
        uncertainDelta = template.uncertainDelta
    )

    fun process(bitmap: Bitmap): OmrScanResult {
        val source = Mat()
        Utils.bitmapToMat(bitmap, source)

        val preprocessed = preprocessImage(source)
        val markers = detectSheetMarkers(preprocessed.binaryInverse)
        if (markers.size < 4) {
            release(source, preprocessed.gray, preprocessed.binaryInverse)
            throw OmrProcessingException("Kh\u00f4ng nh\u1eadn di\u1ec7n \u0111\u01b0\u1ee3c phi\u1ebfu, vui l\u00f2ng ch\u1ee5p l\u1ea1i")
        }

        val warped = perspectiveCorrection(source, markers)
        val warpedPreprocessed = preprocessImage(warped)
        val sharpness = calculateSharpnessVariance(warpedPreprocessed.gray)
        val warnings = mutableListOf<String>()
        if (sharpness < template.minSharpnessVariance) {
            warnings += "\u1ea2nh h\u01a1i m\u1edd, ki\u1ec3m tra l\u1ea1i n\u1ebfu k\u1ebft qu\u1ea3 l\u1ec7ch"
        }
        val examCode = readDigitColumns("M\u00e3 \u0111\u1ec1", template.examCodeColumns, warpedPreprocessed.binaryInverse, warnings)
        val studentCode = readDigitColumns("S\u1ed1 b\u00e1o danh", template.studentIdColumns, warpedPreprocessed.binaryInverse, warnings)
        val answers = readAnswers(warpedPreprocessed.binaryInverse, warnings)
        val okCount = answers.count { it.status == OmrAnswerStatus.OK } +
            examCode.count { it.isDigit() } + studentCode.count { it.isDigit() }
        val confidence = (okCount.toDouble() / (answers.size + 9).toDouble()).coerceIn(0.0, 1.0)
        val debugPath = if (debugEnabled) saveDebugOverlay(warped, debugAnswersOverride ?: answers) else null

        val result = OmrScanResult(
            examCode = examCode,
            studentCode = studentCode,
            answers = answers,
            warnings = warnings,
            confidence = confidence,
            debugInfo = OmrDebugInfo(
                warpedWidth = template.warpedWidth,
                warpedHeight = template.warpedHeight,
                thresholdUsed = template.filledThreshold,
                markerCount = markers.size,
                sharpnessVariance = sharpness,
                debugOverlayPath = debugPath
            )
        )

        release(source, preprocessed.gray, preprocessed.binaryInverse, warped, warpedPreprocessed.gray, warpedPreprocessed.binaryInverse)
        return result
    }

    fun preprocessImage(input: Mat): PreprocessedImage {
        val gray = Mat()
        if (input.channels() == 1) {
            input.copyTo(gray)
        } else {
            Imgproc.cvtColor(input, gray, Imgproc.COLOR_RGBA2GRAY)
        }

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)

        val binaryInverse = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            binaryInverse,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            31,
            10.0
        )

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(2.0, 2.0))
        Imgproc.morphologyEx(binaryInverse, binaryInverse, Imgproc.MORPH_OPEN, kernel)
        blurred.release()
        kernel.release()

        return PreprocessedImage(gray, binaryInverse)
    }

    fun detectSheetMarkers(binaryInverse: Mat): List<SheetMarker> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        val contourSource = binaryInverse.clone()
        Imgproc.findContours(
            contourSource,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val imageArea = binaryInverse.rows() * binaryInverse.cols()
        val minArea = imageArea * 0.00006
        val maxArea = imageArea * 0.03

        val markers = contours.mapNotNull { contour ->
            val area = Imgproc.contourArea(contour)
            if (area < minArea || area > maxArea) {
                contour.release()
                return@mapNotNull null
            }
            val rect = Imgproc.boundingRect(contour)
            val aspect = rect.width.toDouble() / rect.height.toDouble().coerceAtLeast(1.0)
            val extent = area / (rect.width * rect.height).toDouble().coerceAtLeast(1.0)
            val contour2f = MatOfPoint2f(*contour.toArray())
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.04 * Imgproc.arcLength(contour2f, true), true)
            val vertexCount = approx.total().toInt()
            val isSquareLike = aspect in 0.62..1.45 && extent > 0.68 && vertexCount in 4..10
            contour2f.release()
            approx.release()
            contour.release()
            if (!isSquareLike) return@mapNotNull null
            SheetMarker(
                center = Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0),
                rect = rect,
                area = area
            )
        }

        hierarchy.release()
        contourSource.release()
        return markers.sortedByDescending { it.area }
    }

    fun perspectiveCorrection(source: Mat, markers: List<SheetMarker>): Mat {
        val corners = selectCornerMarkers(markers)
        val src = MatOfPoint2f(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
        val dst = MatOfPoint2f(
            Point(MARKER_TARGET_LEFT, MARKER_TARGET_TOP),
            Point(template.warpedWidth - MARKER_TARGET_LEFT, MARKER_TARGET_TOP),
            Point(template.warpedWidth - MARKER_TARGET_LEFT, MARKER_TARGET_BOTTOM),
            Point(MARKER_TARGET_LEFT, MARKER_TARGET_BOTTOM)
        )
        val transform = Imgproc.getPerspectiveTransform(src, dst)
        val warped = Mat()
        Imgproc.warpPerspective(
            source,
            warped,
            transform,
            Size(template.warpedWidth.toDouble(), template.warpedHeight.toDouble()),
            Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT,
            Scalar(255.0, 255.0, 255.0, 255.0)
        )
        src.release()
        dst.release()
        transform.release()
        return warped
    }

    private fun readDigitColumns(
        label: String,
        columns: List<com.example.roll_call.domain.model.omr.DigitBubbleColumn>,
        binaryInverse: Mat,
        warnings: MutableList<String>
    ): String {
        return columns.joinToString(separator = "") { column ->
            val ratios = column.bubblesByDigit.mapValues { (_, bubble) ->
                bubbleDetector.readBubble(binaryInverse, bubble).fillRatio
            }
            val selection = bubbleDetector.classifySelection(ratios)
            if (selection.status != OmrAnswerStatus.OK) {
                warnings += "$label c\u1ed9t ${column.columnIndex + 1}: ${selection.status.name}"
            }
            selection.selected?.toString() ?: "?"
        }
    }

    private fun readAnswers(binaryInverse: Mat, warnings: MutableList<String>): List<OmrQuestionAnswer> {
        return template.answerRows.map { row ->
            val ratios = row.bubblesByOption.mapValues { (_, bubble) ->
                bubbleDetector.readBubble(binaryInverse, bubble).fillRatio
            }
            val selection = bubbleDetector.classifySelection(ratios, allowSoftSelection = true)
            if (selection.status != OmrAnswerStatus.OK) {
                warnings += "C\u00e2u ${row.questionNumber}: ${selection.status.name}"
            }
            OmrQuestionAnswer(
                questionNumber = row.questionNumber,
                answer = if (selection.status == OmrAnswerStatus.OK) selection.selected else selection.selected,
                status = selection.status,
                fillRatios = ratios
            )
        }
    }

    private fun calculateSharpnessVariance(gray: Mat): Double {
        val laplacian = Mat()
        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
        val mean = MatOfDouble()
        val std = MatOfDouble()
        Core.meanStdDev(laplacian, mean, std)
        val variance = std.toArray().firstOrNull()?.let { it * it } ?: 0.0
        laplacian.release()
        mean.release()
        std.release()
        return variance
    }

    private fun selectCornerMarkers(markers: List<SheetMarker>): MarkerCorners {
        val candidates = markers
            .sortedByDescending { it.area }
            .take(MAX_CORNER_MARKER_CANDIDATES)
        return (findBestCornerMarkerQuad(candidates) ?: fallbackCornerMarkerQuad(markers)).toCorners()
    }

    private fun findBestCornerMarkerQuad(candidates: List<SheetMarker>): MarkerQuad? {
        var bestQuad: MarkerQuad? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (i in 0 until candidates.size - 3) {
            for (j in i + 1 until candidates.size - 2) {
                for (k in j + 1 until candidates.size - 1) {
                    for (l in k + 1 until candidates.size) {
                        val quad = orderCornerMarkers(listOf(candidates[i], candidates[j], candidates[k], candidates[l]))
                            ?: continue
                        val score = scoreCornerMarkerQuad(quad) ?: continue
                        if (score > bestScore) {
                            bestScore = score
                            bestQuad = quad
                        }
                    }
                }
            }
        }

        return bestQuad
    }

    private fun fallbackCornerMarkerQuad(markers: List<SheetMarker>): MarkerQuad {
        return orderCornerMarkers(markers)
            ?: throw OmrProcessingException("Kh\u00f4ng nh\u1eadn di\u1ec7n \u0111\u01b0\u1ee3c phi\u1ebfu, vui l\u00f2ng ch\u1ee5p l\u1ea1i")
    }

    private fun orderCornerMarkers(markers: List<SheetMarker>): MarkerQuad? {
        if (markers.size < 4) return null
        val topLeft = markers.minBy { it.center.x + it.center.y }
        val topRight = markers.maxBy { it.center.x - it.center.y }
        val bottomRight = markers.maxBy { it.center.x + it.center.y }
        val bottomLeft = markers.minBy { it.center.x - it.center.y }
        val ordered = listOf(topLeft, topRight, bottomRight, bottomLeft)
        if (ordered.toSet().size < 4) return null
        return MarkerQuad(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun scoreCornerMarkerQuad(quad: MarkerQuad): Double? {
        val topWidth = distance(quad.topLeft.center, quad.topRight.center)
        val bottomWidth = distance(quad.bottomLeft.center, quad.bottomRight.center)
        val leftHeight = distance(quad.topLeft.center, quad.bottomLeft.center)
        val rightHeight = distance(quad.topRight.center, quad.bottomRight.center)
        val avgWidth = (topWidth + bottomWidth) / 2.0
        val avgHeight = (leftHeight + rightHeight) / 2.0
        val markerSides = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
            .map { max(it.rect.width, it.rect.height).toDouble() }
        val avgMarkerSide = markerSides.average().coerceAtLeast(1.0)
        if (avgWidth < avgMarkerSide * 8.0 || avgHeight < avgMarkerSide * 8.0) return null

        val aspect = avgWidth / avgHeight.coerceAtLeast(1.0)
        if (aspect !in 1.05..2.40) return null

        val widthBalance = min(topWidth, bottomWidth) / max(topWidth, bottomWidth).coerceAtLeast(1.0)
        val heightBalance = min(leftHeight, rightHeight) / max(leftHeight, rightHeight).coerceAtLeast(1.0)
        if (widthBalance < 0.45 || heightBalance < 0.45) return null

        val markerAreas = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).map { it.area }
        val minMarkerArea = markerAreas.minOrNull() ?: 0.0
        val maxMarkerArea = (markerAreas.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val areaConsistency = minMarkerArea / maxMarkerArea
        if (areaConsistency < 0.30) return null

        val polygonArea = quadrilateralArea(quad)
        if (polygonArea < avgMarkerSide * avgMarkerSide * 80.0) return null

        val aspectPenalty = abs(aspect - markerTargetAspect()) / markerTargetAspect()
        val skewPenalty = abs(topWidth - bottomWidth) / avgWidth + abs(leftHeight - rightHeight) / avgHeight
        return polygonArea * (0.60 + 0.40 * areaConsistency) * widthBalance * heightBalance / (1.0 + aspectPenalty + skewPenalty)
    }

    private fun distance(a: Point, b: Point): Double {
        return hypot(a.x - b.x, a.y - b.y)
    }

    private fun quadrilateralArea(quad: MarkerQuad): Double {
        val points = listOf(quad.topLeft.center, quad.topRight.center, quad.bottomRight.center, quad.bottomLeft.center)
        var sum = 0.0
        points.forEachIndexed { index, point ->
            val next = points[(index + 1) % points.size]
            sum += point.x * next.y - point.y * next.x
        }
        return abs(sum) * 0.5
    }

    private fun markerTargetAspect(): Double {
        return (template.warpedWidth - MARKER_TARGET_LEFT * 2.0) / (MARKER_TARGET_BOTTOM - MARKER_TARGET_TOP)
    }

    private fun saveDebugOverlay(warped: Mat, answers: List<OmrQuestionAnswer>): String? {
        val dir = debugCacheDir ?: return null
        return try {
            if (!dir.exists()) dir.mkdirs()
            val overlay = warped.clone()
            template.examCodeColumns.flatMap { it.bubblesByDigit.values }.forEach { bubble ->
                drawBubble(overlay, bubble.centerX, bubble.centerY, bubble.radius, COLOR_CODE_BUBBLE)
            }
            template.studentIdColumns.flatMap { it.bubblesByDigit.values }.forEach { bubble ->
                drawBubble(overlay, bubble.centerX, bubble.centerY, bubble.radius, COLOR_CODE_BUBBLE)
            }
            template.answerRows.forEach { row ->
                val answer = answers.firstOrNull { it.questionNumber == row.questionNumber }
                val selected = answer?.answer?.uppercase()
                val correct = correctAnswers[row.questionNumber.toString()]?.trim()?.uppercase()
                val isBlank = selected.isNullOrBlank() || answer?.status == OmrAnswerStatus.BLANK
                row.bubblesByOption.forEach { (option, bubble) ->
                    val color = when {
                        correct.isNullOrBlank() && selected == option -> COLOR_SELECTED_NO_KEY
                        isBlank -> COLOR_BLANK
                        selected == option && selected == correct -> COLOR_CORRECT
                        selected == option -> COLOR_WRONG
                        option == correct -> COLOR_CORRECT
                        else -> COLOR_NEUTRAL
                    }
                    drawBubble(overlay, bubble.centerX, bubble.centerY, bubble.radius, color)
                }
            }
            val bitmap = Bitmap.createBitmap(template.warpedWidth, template.warpedHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(overlay, bitmap)
            val file = File(dir, "omr_debug_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            overlay.release()
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun drawBubble(mat: Mat, x: Float, y: Float, radius: Float, color: Scalar) {
        Imgproc.circle(mat, Point(x.toDouble(), y.toDouble()), radius.toInt(), color, 2)
    }

    private fun release(vararg mats: Mat) {
        mats.forEach { if (!it.empty()) it.release() }
    }

    private companion object {
        private val COLOR_CORRECT = Scalar(0.0, 180.0, 0.0, 255.0)
        private val COLOR_WRONG = Scalar(255.0, 0.0, 0.0, 255.0)
        private val COLOR_BLANK = Scalar(0.0, 120.0, 255.0, 255.0)
        private val COLOR_NEUTRAL = Scalar(180.0, 180.0, 180.0, 255.0)
        private val COLOR_CODE_BUBBLE = Scalar(255.0, 170.0, 0.0, 255.0)
        private val COLOR_SELECTED_NO_KEY = Scalar(0.0, 120.0, 255.0, 255.0)
        private const val MAX_CORNER_MARKER_CANDIDATES = 20
        private const val MARKER_TARGET_LEFT = 30.0
        private const val MARKER_TARGET_TOP = 35.0
        private const val MARKER_TARGET_BOTTOM = 744.0
    }
}

data class PreprocessedImage(
    val gray: Mat,
    val binaryInverse: Mat
)

data class SheetMarker(
    val center: Point,
    val rect: Rect,
    val area: Double
)

data class MarkerCorners(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point
)

private data class MarkerQuad(
    val topLeft: SheetMarker,
    val topRight: SheetMarker,
    val bottomRight: SheetMarker,
    val bottomLeft: SheetMarker
) {
    fun toCorners(): MarkerCorners {
        return MarkerCorners(
            topLeft = topLeft.center,
            topRight = topRight.center,
            bottomRight = bottomRight.center,
            bottomLeft = bottomLeft.center
        )
    }
}

class OmrProcessingException(message: String) : Exception(message)

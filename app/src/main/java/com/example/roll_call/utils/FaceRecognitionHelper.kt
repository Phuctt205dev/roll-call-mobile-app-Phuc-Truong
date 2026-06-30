package com.example.roll_call.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.example.roll_call.domain.model.Student
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceRecognitionHelper(private val context: Context? = null) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    private var tfliteInterpreter: Interpreter? = null
    private var inputSize = 112   // sẽ được detect tự động từ model
    private var embeddingSize = 128

    init {
        context?.let { loadModel(it) }
    }

    private fun loadModel(context: Context) {
        try {
            val assetManager = context.assets
            val fileDescriptor = assetManager.openFd("arcface.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val modelBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
            tfliteInterpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                numThreads = 4
            })
            // Auto-detect inputSize từ input tensor: shape [1, H, W, 3]
            val inputShape = tfliteInterpreter!!.getInputTensor(0).shape()
            inputSize = inputShape[1]  // index 1 = height = width
            // Auto-detect embedding size từ output tensor
            val outputShape = tfliteInterpreter!!.getOutputTensor(0).shape()
            embeddingSize = outputShape.last()
            android.util.Log.d("FaceHelper",
                "Model loaded: inputShape=${inputShape.toList()}, " +
                "inputSize=$inputSize, " +
                "inputType=${tfliteInterpreter!!.getInputTensor(0).dataType()}, " +
                "outputShape=${outputShape.toList()}, " +
                "outputType=${tfliteInterpreter!!.getOutputTensor(0).dataType()}, " +
                "embeddingSize=$embeddingSize"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Extract face embedding từ cropped face bitmap dùng ArcFace TFLite.
     * Tự động detect FLOAT32 hay UINT8 (quantized) model.
     */
    fun extractEmbedding(faceBitmap: Bitmap): FloatArray {
        val interpreter = tfliteInterpreter ?: return FloatArray(embeddingSize)

        // Resize đúng inputSize của model (auto-detected: 112 hoặc 160)
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // Tách pixel thành float array
        val numPixels = inputSize * inputSize * 3
        val floatPixels = FloatArray(numPixels)
        var idx = 0
        for (pixel in pixels) {
            floatPixels[idx++] = ((pixel shr 16) and 0xFF).toFloat()
            floatPixels[idx++] = ((pixel shr 8) and 0xFF).toFloat()
            floatPixels[idx++] = (pixel and 0xFF).toFloat()
        }

        // Prewhiten: chuẩn FaceNet gốc
        val mean = floatPixels.average().toFloat()
        var variance = 0f
        for (v in floatPixels) variance += (v - mean) * (v - mean)
        variance /= numPixels
        val std = maxOf(
            sqrt(variance),
            1f / sqrt(numPixels.toFloat())
        )

        // ByteBuffer: 1 * inputSize * inputSize * 3 * 4 bytes (float32)
        val bufferSize = numPixels * 4
        val inputBuffer = ByteBuffer.allocateDirect(bufferSize)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()
        for (v in floatPixels) inputBuffer.putFloat((v - mean) / std)

        android.util.Log.d("FaceHelper", "extractEmbedding: inputSize=$inputSize, bufferSize=$bufferSize")

        // Output tensor
        val outputTensor = interpreter.getOutputTensor(0)
        val actualSize = outputTensor.shape().last()

        return when (outputTensor.dataType()) {
            org.tensorflow.lite.DataType.UINT8 -> {
                val outputBuffer = ByteBuffer.allocateDirect(actualSize)
                outputBuffer.order(ByteOrder.nativeOrder())
                interpreter.run(inputBuffer, outputBuffer)
                outputBuffer.rewind()
                val scale = outputTensor.quantizationParams().scale
                val zeroPoint = outputTensor.quantizationParams().zeroPoint
                val embedding = FloatArray(actualSize) {
                    (outputBuffer.get().toInt() and 0xFF - zeroPoint) * scale
                }
                l2Normalize(embedding)
            }
            else -> {
                val outputBuffer = Array(1) { FloatArray(actualSize) }
                interpreter.run(inputBuffer, outputBuffer)
                l2Normalize(outputBuffer[0])
            }
        }
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (v in embedding) norm += v * v
        norm = sqrt(norm)
        return if (norm == 0f) embedding else FloatArray(embedding.size) { embedding[it] / norm }
    }

    /**
     * Nhận diện sinh viên với 3 điều kiện:
     * 1. Score phải >= threshold
     * 2. Score top-1 phải cao hơn top-2 một khoảng margin nhất định
     *    → tránh nhầm khi 2 người có điểm gần nhau
     * 3. Log tất cả scores để debug
     */
    fun recognizeStudent(
        faceEmbedding: FloatArray,
        students: List<Student>,
        threshold: Float = 0.75f,  // FaceNet 128-dim dùng threshold thấp hơn ArcFace
        margin: Float = 0.10f      // top-1 phải hơn top-2 ít nhất 0.10
    ): Student? {

        data class Candidate(val student: Student, val score: Float)

        val candidates = students
            .filter { it.faceEmbedding.isNotEmpty() }
            .map { student ->
                val score = cosineSimilarity(faceEmbedding, student.faceEmbedding.toFloatArray())
                Candidate(student, score)
            }
            .sortedByDescending { it.score }

        // Log để debug
        candidates.take(3).forEach {
            android.util.Log.d("FaceHelper", "  ${it.student.name}: ${String.format("%.4f", it.score)}")
        }

        if (candidates.isEmpty()) return null

        val top1 = candidates[0]

        // Điều kiện 1: phải đủ threshold
        if (top1.score < threshold) {
            android.util.Log.d("FaceHelper", "REJECT: top1=${top1.student.name} score=${top1.score} < threshold=$threshold")
            return null
        }

        // Điều kiện 2: nếu có top-2, phải hơn đủ margin
        if (candidates.size >= 2) {
            val top2 = candidates[1]
            val gap = top1.score - top2.score
            if (gap < margin) {
                android.util.Log.d("FaceHelper",
                    "REJECT: gap=${String.format("%.4f", gap)} < margin=$margin " +
                            "(${top1.student.name}=${String.format("%.4f", top1.score)} vs " +
                            "${top2.student.name}=${String.format("%.4f", top2.score)})")
                return null
            }
        }

        android.util.Log.d("FaceHelper", "ACCEPT: ${top1.student.name} score=${String.format("%.4f", top1.score)}")
        return top1.student
    }

    /**
     * Tính cosine similarity giữa 2 vectors
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    /**
     * Crop khuôn mặt từ bitmap theo bounding box
     */
    fun cropFace(bitmap: Bitmap, face: Face): Bitmap? {
        return try {
            val bounds = face.boundingBox
            // Mở rộng bounding box thêm 20% để lấy thêm context khuôn mặt
            val padding = (bounds.width() * 0.2f).toInt()
            val x = (bounds.left - padding).coerceAtLeast(0)
            val y = (bounds.top - padding).coerceAtLeast(0)
            val width = (bounds.width() + padding * 2).coerceAtMost(bitmap.width - x)
            val height = (bounds.height() + padding * 2).coerceAtMost(bitmap.height - y)
            if (width <= 0 || height <= 0) return null
            Bitmap.createBitmap(bitmap, x, y, width, height)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Chuyển ImageProxy sang Bitmap — hỗ trợ cả RGBA_8888 và YUV_420_888
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val bitmap = when (imageProxy.format) {
                android.graphics.PixelFormat.RGBA_8888 -> {
                    // RGBA_8888: plane[0] chứa toàn bộ pixel data
                    val plane = imageProxy.planes[0]
                    val buffer = plane.buffer
                    val bitmap = Bitmap.createBitmap(
                        imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    bitmap
                }
                35 /* YUV_420_888 */ -> {
                    val yBuffer = imageProxy.planes[0].buffer
                    val uBuffer = imageProxy.planes[1].buffer
                    val vBuffer = imageProxy.planes[2].buffer
                    val ySize = yBuffer.remaining()
                    val uSize = uBuffer.remaining()
                    val vSize = vBuffer.remaining()
                    val nv21 = ByteArray(ySize + uSize + vSize)
                    yBuffer.get(nv21, 0, ySize)
                    vBuffer.get(nv21, ySize, vSize)
                    uBuffer.get(nv21, ySize + vSize, uSize)
                    val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                    val out = java.io.ByteArrayOutputStream()
                    yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 95, out)
                    val jpegBytes = out.toByteArray()
                    BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                }
                else -> {
                    // Fallback: thử đọc như RGBA
                    val plane = imageProxy.planes[0]
                    val buffer = plane.buffer
                    val bitmap = Bitmap.createBitmap(
                        imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    bitmap
                }
            } ?: return null

            // Rotate theo rotation của camera
            val rotation = imageProxy.imageInfo.rotationDegrees
            return if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Kiểm tra độ sắc nét của ảnh khuôn mặt (Laplacian variance)
     * Nếu quá mờ → embedding kém → bỏ qua
     */
    fun checkFaceQuality(bitmap: Bitmap): Boolean {
        val gray = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(gray)
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply { setSaturation(0f) }
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        val pixels = IntArray(gray.width * gray.height)
        gray.getPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)

        // Tính variance của Laplacian (đơn giản hóa)
        var sum = 0.0
        var sumSq = 0.0
        val count = pixels.size.toDouble()
        for (p in pixels) {
            val lum = (p and 0xFF).toDouble()
            sum += lum
            sumSq += lum * lum
        }
        val variance = (sumSq / count) - (sum / count) * (sum / count)
        // Variance < 100 = ảnh quá tối/mờ
        return variance > 100
    }

    /**
     * Cân bằng histogram đơn giản để normalize ánh sáng
     */
    private fun histogramEqualize(bitmap: Bitmap): Bitmap {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Tách và equalize từng channel R, G, B riêng
            val result = IntArray(pixels.size)
            for (ch in 0..2) {
                val hist = IntArray(256)
                for (p in pixels) {
                    val v = when (ch) {
                        0 -> (p shr 16) and 0xFF
                        1 -> (p shr 8) and 0xFF
                        else -> p and 0xFF
                    }
                    hist[v]++
                }
                // CDF
                val cdf = IntArray(256)
                cdf[0] = hist[0]
                for (i in 1..255) cdf[i] = cdf[i - 1] + hist[i]
                val cdfMin = cdf.first { it > 0 }
                val total = pixels.size

                for (i in pixels.indices) {
                    val v = when (ch) {
                        0 -> (pixels[i] shr 16) and 0xFF
                        1 -> (pixels[i] shr 8) and 0xFF
                        else -> pixels[i] and 0xFF
                    }
                    val eq = ((cdf[v] - cdfMin) * 255 / (total - cdfMin)).coerceIn(0, 255)
                    result[i] = when (ch) {
                        0 -> (result[i] and 0xFF00FFFF.toInt()) or (eq shl 16)
                        1 -> (result[i] and 0xFFFF00FF.toInt()) or (eq shl 8)
                        else -> (result[i] and 0xFFFFFF00.toInt()) or eq
                    }
                    if (ch == 0) result[i] = result[i] or (pixels[i] and 0xFF000000.toInt()) // alpha
                }
            }

            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            out.setPixels(result, 0, width, 0, 0, width, height)
            out
        } catch (e: Exception) {
            bitmap // fallback: trả về bitmap gốc nếu lỗi
        }
    }

    /**
     * Nhận diện khuôn mặt giáo viên
     * So sánh embedding của khuôn mặt phát hiện với embedding của giáo viên
     * Sử dụng threshold thấp hơn vì chỉ cần so sánh với 1 người
     */
    fun recognizeTeacher(
        faceEmbedding: FloatArray,
        teacherEmbedding: FloatArray,
        threshold: Float = 0.55f  // Lowered from 0.65f to 0.55f for better detection
    ): Boolean {
        if (teacherEmbedding.isEmpty()) {
            android.util.Log.d("FaceHelper", "❌ Teacher embedding is empty")
            return false
        }

        if (faceEmbedding.isEmpty()) {
            android.util.Log.d("FaceHelper", "❌ Detected face embedding is empty")
            return false
        }

        if (faceEmbedding.size != teacherEmbedding.size) {
            android.util.Log.e(
                "FaceHelper",
                "⚠️ MISMATCH: Detected embedding size=${faceEmbedding.size}, Teacher embedding size=${teacherEmbedding.size}"
            )
            // Try to handle size mismatch gracefully - use min size
            if (faceEmbedding.size > teacherEmbedding.size) {
                android.util.Log.d("FaceHelper", "   Truncating detected embedding from ${faceEmbedding.size} to ${teacherEmbedding.size}")
                val truncated = faceEmbedding.sliceArray(0 until teacherEmbedding.size)
                val score = cosineSimilarity(truncated, teacherEmbedding)
                android.util.Log.d("FaceHelper", "   Score after truncation: ${String.format("%.4f", score)}")
                return score >= threshold
            }
        }

        val score = cosineSimilarity(faceEmbedding, teacherEmbedding)
        android.util.Log.d(
            "FaceHelper",
            "👨‍🏫 Teacher similarity: ${String.format("%.4f", score)} (thresh=$threshold), " +
            "detected_size=${faceEmbedding.size}, teacher_size=${teacherEmbedding.size}"
        )

        val isMatched = score >= threshold
        if (isMatched) {
            android.util.Log.d("FaceHelper", "✓✓✓ ACCEPT: Teacher matched with score=${String.format("%.4f", score)} >= threshold=$threshold")
        } else {
            android.util.Log.d("FaceHelper", "✗✗✗ REJECT: Teacher score=${String.format("%.4f", score)} < threshold=$threshold")
        }

        return isMatched
    }

    fun getDetector() = detector

    fun close() = detector.close()
}

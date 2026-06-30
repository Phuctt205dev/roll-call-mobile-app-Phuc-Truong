package com.example.roll_call.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.OmrRepository
import com.example.roll_call.domain.model.Student
import com.example.roll_call.domain.model.omr.OmrGrade
import com.example.roll_call.domain.model.omr.OmrPrintVersion
import com.example.roll_call.domain.model.omr.OmrScanResult
import com.example.roll_call.domain.omr.OmrGrader
import com.example.roll_call.utils.omr.OmrProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class OmrRealtimeResult(
    val scanResult: OmrScanResult,
    val student: Student?,
    val grade: OmrGrade?,
    val message: String,
    val isComplete: Boolean
)

data class OmrScannerUiState(
    val isInitializing: Boolean = false,
    val isProcessing: Boolean = false,
    val latestResult: OmrRealtimeResult? = null,
    val error: String? = null,
    val realtimeStatus: String = "C\u0103n 4 \u00f4 vu\u00f4ng \u0111en v\u00e0o 4 g\u00f3c khung",
    val stableFrameCount: Int = 0,
    val isSheetInFrame: Boolean = false,
    val isScanPaused: Boolean = false,
    val isSavingGrade: Boolean = false,
    val savedGradeId: String? = null
)

private data class ScanValidation(
    val answerKey: OmrPrintVersion?,
    val student: Student?,
    val message: String
) {
    val isComplete: Boolean get() = answerKey != null && student != null
}

class OmrScannerViewModel : ViewModel() {
    private val repository = OmrRepository()

    private val _uiState = MutableStateFlow(OmrScannerUiState())
    val uiState: StateFlow<OmrScannerUiState> = _uiState

    private var openCvReady = false

    private var initializedKey: String? = null
    private var classId: String = ""
    private var examId: String = ""
    private var classExamInstanceId: String? = null
    private var preferredVersionId: String? = null
    private var printVersions: List<OmrPrintVersion> = emptyList()
    private val studentCache = mutableMapOf<String, Student?>()

    fun initialize(
        classId: String,
        examId: String,
        classExamInstanceId: String?,
        preferredVersionId: String?
    ) {
        val normalizedClassExamId = classExamInstanceId?.takeIf { it.isNotBlank() && it != "_" }
        val normalizedVersionId = preferredVersionId?.takeIf { it.isNotBlank() && it != "_" }
        val key = listOf(classId, examId, normalizedClassExamId.orEmpty(), normalizedVersionId.orEmpty()).joinToString("|")
        if (initializedKey == key) return

        initializedKey = key
        this.classId = classId
        this.examId = examId
        this.classExamInstanceId = normalizedClassExamId
        this.preferredVersionId = normalizedVersionId
        this.printVersions = emptyList()
        this.studentCache.clear()

        _uiState.value = OmrScannerUiState(
            isInitializing = true,
            realtimeStatus = "\u0110ang n\u1ea1p \u0111\u00e1p \u00e1n OMR"
        )

        viewModelScope.launch {
            repository.getPrintVersions(examId, classId, normalizedClassExamId).fold(
                onSuccess = { versions ->
                    printVersions = versions
                    _uiState.value = _uiState.value.copy(
                        isInitializing = false,
                        error = null,
                        realtimeStatus = if (versions.isEmpty()) {
                            "Ch\u01b0a t\u00ecm th\u1ea5y \u0111\u00e1p \u00e1n cho b\u00e0i n\u00e0y"
                        } else {
                            "C\u0103n 4 \u00f4 vu\u00f4ng \u0111en v\u00e0o 4 g\u00f3c khung"
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isInitializing = false,
                        error = error.message ?: "Kh\u00f4ng n\u1ea1p \u0111\u01b0\u1ee3c \u0111\u00e1p \u00e1n OMR"
                    )
                }
            )
        }
    }
    fun processCapturedBitmap(bitmap: Bitmap, cacheDir: File?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                error = null,
                realtimeStatus = "\u0110ang x\u1eed l\u00fd \u1ea3nh ch\u1ee5p",
                isScanPaused = true
            )
            if (!ensureOpenCv()) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isScanPaused = false,
                    error = "Kh\u00f4ng kh\u1edfi t\u1ea1o \u0111\u01b0\u1ee3c OpenCV"
                )
                return@launch
            }

            val scanResult = runCatching {
                withContext(Dispatchers.Default) {
                    val processingBitmap = bitmap.scaleForOmrProcessing(MAX_CAPTURE_PROCESSING_SIDE)
                    try {
                        OmrProcessor(debugEnabled = false).process(processingBitmap)
                    } finally {
                        if (processingBitmap !== bitmap) processingBitmap.recycle()
                    }
                }
            }

            scanResult.fold(
                onSuccess = { scan ->
                    val validation = validateScan(scan)
                    if (!validation.isComplete) {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            latestResult = null,
                            realtimeStatus = validation.message,
                            stableFrameCount = 0,
                            isSheetInFrame = true,
                            isScanPaused = false,
                            error = null
                        )
                        return@fold
                    }

                    val scanWithDebug = attachColoredDebugOverlay(scan, bitmap, cacheDir, validation.answerKey!!)
                    val result = buildRealtimeResult(scanWithDebug, validation.answerKey, validation.student)
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        latestResult = result,
                        realtimeStatus = result.message,
                        isSheetInFrame = true,
                        isScanPaused = true,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isSheetInFrame = false,
                        isScanPaused = false,
                        error = error.message ?: "Kh\u00f4ng x\u1eed l\u00fd \u0111\u01b0\u1ee3c phi\u1ebfu"
                    )
                }
            )
        }
    }

    fun clearResult() {
        resumeScanning(clearStudentCache = true)
    }

    fun resumeScanning() {
        resumeScanning(clearStudentCache = false)
    }

    private fun resumeScanning(clearStudentCache: Boolean) {
        if (clearStudentCache) studentCache.clear()
        _uiState.value = _uiState.value.copy(
            latestResult = null,
            error = null,
            savedGradeId = null,
            realtimeStatus = "C\u0103n 4 \u00f4 vu\u00f4ng \u0111en v\u00e0o 4 g\u00f3c khung",
            stableFrameCount = 0,
            isSheetInFrame = false,
            isScanPaused = false
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun saveLatestGrade() {
        val state = _uiState.value
        if (state.isSavingGrade || state.savedGradeId != null) return
        val grade = state.latestResult?.grade ?: return
        val studentName = state.latestResult.student?.name

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingGrade = true, error = null)
            repository.saveOmrGrade(classId, grade).fold(
                onSuccess = { id ->
                    _uiState.value = _uiState.value.copy(
                        isSavingGrade = false,
                        savedGradeId = id,
                        realtimeStatus = "\u0110\u00e3 l\u01b0u \u0111i\u1ec3m${studentName?.let { ": $it" } ?: ""}"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSavingGrade = false,
                        error = error.message ?: "Kh\u00f4ng l\u01b0u \u0111\u01b0\u1ee3c \u0111i\u1ec3m"
                    )
                }
            )
        }
    }
    private suspend fun buildRealtimeResult(
        scanResult: OmrScanResult,
        answerKeyOverride: OmrPrintVersion? = null,
        studentOverride: Student? = null
    ): OmrRealtimeResult {
        val selectedAnswerKey = answerKeyOverride ?: OmrGrader.selectAnswerKey(printVersions, scanResult.examCode, preferredVersionId)
        val student = studentOverride ?: findStudent(scanResult.studentCode)
        val teacherId = repository.currentTeacherId
        val grade = if (selectedAnswerKey != null && teacherId != null) {
            OmrGrader.gradeAnswers(
                scanResult = scanResult,
                answerKey = selectedAnswerKey,
                classId = classId,
                examId = examId,
                classExamInstanceId = classExamInstanceId,
                teacherId = teacherId,
                studentId = student?.id,
                studentCodeOverride = student?.studentCode ?: scanResult.studentCode,
                examCodeOverride = scanResult.examCode
            )
        } else {
            null
        }

        val message = when {
            selectedAnswerKey == null -> "Ch\u01b0a kh\u1edbp m\u00e3 \u0111\u1ec1 ${scanResult.examCode}, ti\u1ebfp t\u1ee5c qu\u00e9t"
            student == null -> "Ch\u01b0a kh\u1edbp SBD ${scanResult.studentCode}, ti\u1ebfp t\u1ee5c qu\u00e9t"
            grade != null -> "${student.name} - ${grade.score}/${grade.maxScore}"
            else -> "\u0110\u00e3 nh\u1eadn di\u1ec7n phi\u1ebfu"
        }

        return OmrRealtimeResult(
            scanResult = scanResult,
            student = student,
            grade = grade,
            message = message,
            isComplete = selectedAnswerKey != null && student != null && grade != null
        )
    }
    private suspend fun attachColoredDebugOverlay(
        scanResult: OmrScanResult,
        bitmap: Bitmap,
        cacheDir: File?,
        answerKey: OmrPrintVersion
    ): OmrScanResult {
        val debugPath = withContext(Dispatchers.Default) {
            val processingBitmap = bitmap.scaleForOmrProcessing(MAX_CAPTURE_PROCESSING_SIDE)
            try {
                OmrProcessor(
                    debugCacheDir = cacheDir,
                    debugEnabled = true
                ).createDebugOverlay(
                    bitmap = processingBitmap,
                    answers = scanResult.answers,
                    correctAnswers = answerKey.answers
                )
            } finally {
                if (processingBitmap !== bitmap) processingBitmap.recycle()
            }
        }
        return if (debugPath.isNullOrBlank()) {
            scanResult
        } else {
            scanResult.copy(
                debugInfo = scanResult.debugInfo.copy(debugOverlayPath = debugPath)
            )
        }
    }
    private suspend fun validateScan(scanResult: OmrScanResult): ScanValidation {
        val selectedAnswerKey = OmrGrader.selectAnswerKey(printVersions, scanResult.examCode, preferredVersionId)
            ?: return ScanValidation(
                answerKey = null,
                student = null,
                message = "Ch\u01b0a kh\u1edbp m\u00e3 \u0111\u1ec1 ${scanResult.examCode}, ti\u1ebfp t\u1ee5c qu\u00e9t"
            )
        val student = findStudent(scanResult.studentCode)
            ?: return ScanValidation(
                answerKey = selectedAnswerKey,
                student = null,
                message = "Ch\u01b0a kh\u1edbp SBD ${scanResult.studentCode}, ti\u1ebfp t\u1ee5c qu\u00e9t"
            )
        return ScanValidation(
            answerKey = selectedAnswerKey,
            student = student,
            message = "\u0110\u00e3 kh\u1edbp m\u00e3 \u0111\u1ec1/SBD"
        )
    }

    private suspend fun findStudent(studentCode: String): Student? {
        val key = studentCode.filter { it.isDigit() }
        if (key.isBlank()) return null
        if (studentCache.containsKey(key)) return studentCache[key]
        val student = repository.findStudentByCode(classId, key).getOrNull()
        studentCache[key] = student
        return student
    }

    private fun Bitmap.scaleForOmrProcessing(maxSide: Int): Bitmap {
        val longestSide = max(width, height)
        if (longestSide <= maxSide) return this
        val scale = maxSide.toFloat() / longestSide.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun ensureOpenCv(): Boolean {
        if (!openCvReady) openCvReady = OpenCVLoader.initDebug()
        return openCvReady
    }

    companion object {
        private const val MAX_CAPTURE_PROCESSING_SIDE = 2200
    }
}

package com.example.roll_call.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.OmrRepository
import com.example.roll_call.domain.model.Student
import com.example.roll_call.domain.model.omr.FixedOmrTemplate
import com.example.roll_call.domain.model.omr.OmrAnswerStatus
import com.example.roll_call.domain.model.omr.OmrGrade
import com.example.roll_call.domain.model.omr.OmrPrintVersion
import com.example.roll_call.domain.model.omr.OmrQuestionAnswer
import com.example.roll_call.domain.model.omr.OmrScanResult
import com.example.roll_call.domain.omr.OmrGrader
import com.example.roll_call.utils.omr.BubbleDetector
import com.example.roll_call.utils.omr.OmrProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File

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
    val savedGradeId: String? = null,
    val isAutoCapturing: Boolean = false,
    val autoCaptureRequestId: Long = 0L
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
    private var realtimeProcessing = false
    private var lastRealtimeAttemptMs = 0L
    private var stableFrameCount = 0
    private var lastAcceptedSignature: String? = null
    private var lastAcceptedAtMs = 0L
    private var lastAutoCaptureAtMs = 0L
    private var lastValidIdentity: String? = null
    private var validFrameCount = 0
    private val validFrameResults = mutableListOf<OmrScanResult>()

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
        resetRealtimeTracking()

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

    fun shouldAnalyzeRealtimeFrame(): Boolean {
        val now = System.currentTimeMillis()
        val state = _uiState.value
        return !realtimeProcessing &&
            !state.isInitializing &&
            !state.isAutoCapturing &&
            !state.isScanPaused &&
            state.latestResult == null &&
            now - lastRealtimeAttemptMs >= REALTIME_INTERVAL_MS
    }

    fun processRealtimeBitmap(bitmap: Bitmap, cacheDir: File?) {
        if (!shouldAnalyzeRealtimeFrame()) return
        realtimeProcessing = true
        lastRealtimeAttemptMs = System.currentTimeMillis()

        viewModelScope.launch {
            try {
                if (!ensureOpenCv()) {
                    _uiState.value = _uiState.value.copy(
                        realtimeStatus = "Kh\u00f4ng kh\u1edfi t\u1ea1o \u0111\u01b0\u1ee3c OpenCV",
                        isSheetInFrame = false
                    )
                    return@launch
                }

                val quickResult = runCatching {
                    withContext(Dispatchers.Default) {
                        OmrProcessor(debugEnabled = false).process(bitmap)
                    }
                }

                quickResult.fold(
                    onSuccess = { result -> handleRealtimeCandidate(result, bitmap, cacheDir) },
                    onFailure = { error ->
                        resetRealtimeTracking()
                        _uiState.value = _uiState.value.copy(
                            realtimeStatus = error.message ?: "\u0110ang t\u00ecm phi\u1ebfu trong khung",
                            stableFrameCount = 0,
                            isSheetInFrame = false
                        )
                    }
                )
            } finally {
                realtimeProcessing = false
            }
        }
    }

    fun processCapturedBitmap(bitmap: Bitmap, cacheDir: File?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                isAutoCapturing = false,
                error = null,
                realtimeStatus = "\u0110ang x\u1eed l\u00fd phi\u1ebfu",
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

            val quickResult = runCatching {
                withContext(Dispatchers.Default) {
                    OmrProcessor(debugEnabled = false).process(bitmap)
                }
            }

            quickResult.fold(
                onSuccess = { scan ->
                    val validation = validateScan(scan)
                    if (!validation.isComplete) {
                        resetRealtimeTracking()
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            isAutoCapturing = false,
                            latestResult = null,
                            realtimeStatus = validation.message,
                            stableFrameCount = 0,
                            isSheetInFrame = true,
                            isScanPaused = false,
                            error = null
                        )
                        return@fold
                    }

                    val finalScan = runCatching {
                        withContext(Dispatchers.Default) {
                            OmrProcessor(
                                debugCacheDir = cacheDir,
                                debugEnabled = true,
                                correctAnswers = validation.answerKey!!.answers
                            ).process(bitmap)
                        }
                    }.getOrElse { scan }.copy(
                        examCode = scan.examCode,
                        studentCode = scan.studentCode
                    )
                    val realtimeResult = buildRealtimeResult(finalScan, validation.answerKey, validation.student)
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isAutoCapturing = false,
                        latestResult = realtimeResult,
                        realtimeStatus = realtimeResult.message,
                        isSheetInFrame = true,
                        isScanPaused = true,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isAutoCapturing = false,
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
        resetRealtimeTracking()
        lastAcceptedSignature = null
        lastAcceptedAtMs = 0L
        lastAutoCaptureAtMs = 0L
        if (clearStudentCache) studentCache.clear()
        _uiState.value = _uiState.value.copy(
            latestResult = null,
            error = null,
            savedGradeId = null,
            isAutoCapturing = false,
            realtimeStatus = "C\u0103n 4 \u00f4 vu\u00f4ng \u0111en v\u00e0o 4 g\u00f3c khung",
            stableFrameCount = 0,
            isSheetInFrame = false,
            isScanPaused = false
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun markAutoCaptureStarted() {
        _uiState.value = _uiState.value.copy(
            isAutoCapturing = true,
            isProcessing = true,
            error = null,
            realtimeStatus = "\u0110ang t\u1ef1 ch\u1ee5p phi\u1ebfu r\u00f5 n\u00e9t"
        )
    }

    fun onAutoCaptureFailed(message: String) {
        _uiState.value = _uiState.value.copy(
            isAutoCapturing = false,
            isProcessing = false,
            isScanPaused = false,
            error = message
        )
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

    private suspend fun handleRealtimeCandidate(result: OmrScanResult, bitmap: Bitmap, cacheDir: File?) {
        val sheetVisible = result.debugInfo.markerCount >= MIN_MARKERS_FOR_SHEET
        if (sheetVisible) {
            stableFrameCount += 1
            if (shouldAutoCaptureHighQualityFrame()) {
                requestAutoCapture()
                return
            }
        } else {
            stableFrameCount = 0
        }

        if (!isRealtimeCandidate(result)) {
            resetRealtimeTracking(keepSheetFrames = sheetVisible)
            _uiState.value = _uiState.value.copy(
                realtimeStatus = candidateMessage(result),
                stableFrameCount = if (sheetVisible) stableFrameCount else 0,
                isSheetInFrame = sheetVisible,
                isScanPaused = false
            )
            return
        }

        val validation = validateScan(result)
        if (!validation.isComplete) {
            resetRealtimeTracking()
            _uiState.value = _uiState.value.copy(
                latestResult = null,
                realtimeStatus = validation.message,
                stableFrameCount = 0,
                isSheetInFrame = true,
                isScanPaused = false,
                error = null
            )
            return
        }

        val identity = validIdentity(validation)
        if (identity != lastValidIdentity) {
            validFrameResults.clear()
        }
        validFrameResults += result
        while (validFrameResults.size > MAX_VALID_FRAME_BUFFER) {
            validFrameResults.removeAt(0)
        }
        validFrameCount = validFrameResults.size
        lastValidIdentity = identity
        stableFrameCount = validFrameCount

        if (validFrameCount < REQUIRED_VALID_FRAMES) {
            _uiState.value = _uiState.value.copy(
                realtimeStatus = "\u0110\u00e3 kh\u1edbp m\u00e3 \u0111\u1ec1/SBD (${validFrameCount}/${REQUIRED_VALID_FRAMES})",
                stableFrameCount = validFrameCount,
                isSheetInFrame = true,
                isScanPaused = false,
                error = null
            )
            return
        }

        val signature = resultSignature(result)
        val now = System.currentTimeMillis()
        if (signature == lastAcceptedSignature && now - lastAcceptedAtMs < DUPLICATE_RESULT_COOLDOWN_MS) {
            _uiState.value = _uiState.value.copy(
                realtimeStatus = _uiState.value.latestResult?.message ?: "\u0110\u00e3 nh\u1eadn di\u1ec7n phi\u1ebfu",
                stableFrameCount = validFrameCount,
                isSheetInFrame = true,
                isScanPaused = true
            )
            return
        }

        val votedAnswers = voteAnswers(validFrameResults)
        val finalResult = runCatching {
            withContext(Dispatchers.Default) {
                OmrProcessor(
                    debugCacheDir = cacheDir,
                    debugEnabled = true,
                    correctAnswers = validation.answerKey!!.answers,
                    debugAnswersOverride = votedAnswers
                ).process(bitmap)
            }
        }.getOrElse { result }.copy(
            examCode = result.examCode,
            studentCode = result.studentCode,
            answers = votedAnswers,
            confidence = averageConfidence(validFrameResults)
        )

        val realtimeResult = buildRealtimeResult(finalResult, validation.answerKey, validation.student)
        lastAcceptedSignature = signature
        lastAcceptedAtMs = now

        _uiState.value = _uiState.value.copy(
            latestResult = realtimeResult,
            realtimeStatus = realtimeResult.message,
            stableFrameCount = validFrameCount,
            isSheetInFrame = true,
            isScanPaused = true,
            error = null
        )
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

    private fun validIdentity(validation: ScanValidation): String {
        val answerKey = validation.answerKey
        val student = validation.student
        return "${answerKey?.id.orEmpty()}|${student?.id ?: student?.studentCode.orEmpty()}"
    }

    private fun voteAnswers(results: List<OmrScanResult>): List<OmrQuestionAnswer> {
        val answersByQuestion = results
            .flatMap { it.answers }
            .groupBy { it.questionNumber }
        val template = FixedOmrTemplate.default

        return answersByQuestion.keys.sorted().map { question ->
            val frames = answersByQuestion[question].orEmpty()
            val averagedRatios = averagedRatios(frames)
            val majorityAnswer = majorityOkAnswer(frames)
            val selection = BubbleDetector.classifySelection(
                fillRatios = averagedRatios,
                filledThreshold = template.filledThreshold,
                blankThreshold = template.blankThreshold,
                uncertainDelta = template.uncertainDelta,
                allowSoftSelection = true
            )
            val selected = majorityAnswer ?: selection.selected
            val status = if (majorityAnswer != null) OmrAnswerStatus.OK else selection.status
            OmrQuestionAnswer(
                questionNumber = question,
                answer = selected,
                status = status,
                fillRatios = averagedRatios
            )
        }
    }

    private fun averagedRatios(frames: List<OmrQuestionAnswer>): Map<String, Double> {
        val template = FixedOmrTemplate.default
        return ANSWER_OPTIONS.associateWith { option ->
            frames.map { answer ->
                answer.fillRatios[option]
                    ?: if (answer.status == OmrAnswerStatus.OK && OmrGrader.normalizeAnswer(answer.answer) == option) {
                        template.filledThreshold
                    } else {
                        0.0
                    }
            }.average().takeIf { !it.isNaN() } ?: 0.0
        }
    }

    private fun majorityOkAnswer(frames: List<OmrQuestionAnswer>): String? {
        val counts = frames
            .mapNotNull { answer ->
                if (answer.status == OmrAnswerStatus.OK) OmrGrader.normalizeAnswer(answer.answer) else null
            }
            .groupingBy { it }
            .eachCount()
        val ranked = counts.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return null
        val secondCount = ranked.drop(1).firstOrNull()?.value ?: 0
        val requiredCount = ((frames.size + 1) / 2).coerceAtLeast(1)
        return if (top.value >= requiredCount && top.value > secondCount) top.key else null
    }

    private fun averageConfidence(results: List<OmrScanResult>): Double {
        return results.map { it.confidence }.average().takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0) ?: 0.0
    }

    private suspend fun findStudent(studentCode: String): Student? {
        val key = studentCode.filter { it.isDigit() }
        if (key.isBlank()) return null
        if (studentCache.containsKey(key)) return studentCache[key]
        val student = repository.findStudentByCode(classId, key).getOrNull()
        studentCache[key] = student
        return student
    }

    private fun isRealtimeCandidate(result: OmrScanResult): Boolean {
        val examDigits = result.examCode.filter { it.isDigit() }
        val studentDigits = result.studentCode.filter { it.isDigit() }
        return result.debugInfo.markerCount >= MIN_MARKERS_FOR_SHEET &&
            result.confidence >= MIN_REALTIME_CONFIDENCE &&
            examDigits.length == 3 &&
            studentDigits.length == 6 &&
            '?' !in result.examCode &&
            '?' !in result.studentCode
    }

    private fun candidateMessage(result: OmrScanResult): String {
        val examDigits = result.examCode.filter { it.isDigit() }
        val studentDigits = result.studentCode.filter { it.isDigit() }
        return when {
            result.debugInfo.markerCount < MIN_MARKERS_FOR_SHEET -> "C\u0103n 4 \u00f4 vu\u00f4ng \u0111en v\u00e0o 4 g\u00f3c khung"
            examDigits.length != 3 || '?' in result.examCode -> "Ch\u01b0a \u0111\u1ecdc r\u00f5 m\u00e3 \u0111\u1ec1"
            studentDigits.length != 6 || '?' in result.studentCode -> "Ch\u01b0a \u0111\u1ecdc r\u00f5 s\u1ed1 b\u00e1o danh"
            result.confidence < MIN_REALTIME_CONFIDENCE -> "Gi\u1eef phi\u1ebfu ph\u1eb3ng v\u00e0 \u0111\u1ee7 s\u00e1ng"
            else -> "\u0110ang \u0111\u1ecdc phi\u1ebfu"
        }
    }

    private fun resultSignature(result: OmrScanResult): String {
        val answersSignature = result.answers.joinToString(separator = ",") { answer ->
            "${answer.questionNumber}:${answer.answer ?: answer.status.name}"
        }
        return "${OmrGrader.normalizeExamCode(result.examCode)}|${result.studentCode.filter { it.isDigit() }}|$answersSignature"
    }

    private fun shouldAutoCaptureHighQualityFrame(): Boolean {
        val now = System.currentTimeMillis()
        val state = _uiState.value
        return stableFrameCount >= REQUIRED_SHEET_FRAMES_FOR_AUTO_CAPTURE &&
            !state.isProcessing &&
            !state.isInitializing &&
            !state.isAutoCapturing &&
            !state.isScanPaused &&
            state.latestResult == null &&
            now - lastAutoCaptureAtMs >= AUTO_CAPTURE_COOLDOWN_MS
    }

    private fun requestAutoCapture() {
        lastAutoCaptureAtMs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            autoCaptureRequestId = _uiState.value.autoCaptureRequestId + 1L,
            realtimeStatus = "\u0110ang t\u1ef1 ch\u1ee5p phi\u1ebfu r\u00f5 n\u00e9t",
            stableFrameCount = stableFrameCount,
            isSheetInFrame = true,
            isScanPaused = false,
            error = null
        )
    }

    private fun resetRealtimeTracking(keepSheetFrames: Boolean = false) {
        if (!keepSheetFrames) stableFrameCount = 0
        lastValidIdentity = null
        validFrameCount = 0
        validFrameResults.clear()
    }

    private fun ensureOpenCv(): Boolean {
        if (!openCvReady) openCvReady = OpenCVLoader.initDebug()
        return openCvReady
    }

    companion object {
        private const val REALTIME_INTERVAL_MS = 320L
        private const val REQUIRED_SHEET_FRAMES_FOR_AUTO_CAPTURE = 2
        private const val AUTO_CAPTURE_COOLDOWN_MS = 3000L
        private const val MIN_MARKERS_FOR_SHEET = 4
        private const val REQUIRED_VALID_FRAMES = 2
        private const val MIN_REALTIME_CONFIDENCE = 0.25
        private const val DUPLICATE_RESULT_COOLDOWN_MS = 1800L
        private const val MAX_VALID_FRAME_BUFFER = 4
        private val ANSWER_OPTIONS = listOf("A", "B", "C", "D")
    }
}

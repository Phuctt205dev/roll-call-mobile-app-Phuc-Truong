package com.example.roll_call.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.OmrRepository
import com.example.roll_call.domain.model.Student
import com.example.roll_call.domain.model.omr.OmrAnswerStatus
import com.example.roll_call.domain.model.omr.OmrGrade
import com.example.roll_call.domain.model.omr.OmrPrintVersion
import com.example.roll_call.domain.model.omr.OmrScanResult
import com.example.roll_call.domain.omr.OmrGrader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OmrReviewUiState(
    val scanResult: OmrScanResult? = null,
    val printVersions: List<OmrPrintVersion> = emptyList(),
    val selectedPrintVersion: OmrPrintVersion? = null,
    val matchedStudent: Student? = null,
    val studentCode: String = "",
    val examCode: String = "",
    val editableAnswers: Map<Int, String?> = emptyMap(),
    val editableStatuses: Map<Int, OmrAnswerStatus> = emptyMap(),
    val grade: OmrGrade? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savedGradeId: String? = null,
    val error: String? = null
)

class OmrReviewViewModel : ViewModel() {
    private val repository = OmrRepository()

    private val _uiState = MutableStateFlow(OmrReviewUiState())
    val uiState: StateFlow<OmrReviewUiState> = _uiState

    private var classId: String = ""
    private var examId: String = ""
    private var classExamInstanceId: String? = null
    private var preferredVersionId: String? = null

    fun initialize(
        classId: String,
        examId: String,
        classExamInstanceId: String?,
        preferredVersionId: String?,
        scanResult: OmrScanResult
    ) {
        if (_uiState.value.scanResult != null) return
        this.classId = classId
        this.examId = examId
        this.classExamInstanceId = classExamInstanceId?.takeIf { it.isNotBlank() && it != "_" }
        this.preferredVersionId = preferredVersionId?.takeIf { it.isNotBlank() && it != "_" }

        _uiState.value = _uiState.value.copy(
            scanResult = scanResult,
            studentCode = scanResult.studentCode,
            examCode = scanResult.examCode,
            editableAnswers = OmrGrader.normalizeQuestionAnswers(scanResult.answers),
            editableStatuses = scanResult.answers.associate { it.questionNumber to it.status },
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            val versionsResult = repository.getPrintVersions(examId, classId, this@OmrReviewViewModel.classExamInstanceId)
            val versions = versionsResult.getOrDefault(emptyList())
            val selected = OmrGrader.selectAnswerKey(versions, scanResult.examCode, this@OmrReviewViewModel.preferredVersionId)
            val student = repository.findStudentByCode(classId, scanResult.studentCode).getOrNull()
            _uiState.value = _uiState.value.copy(
                printVersions = versions,
                selectedPrintVersion = selected,
                matchedStudent = student,
                isLoading = false,
                error = versionsResult.exceptionOrNull()?.message
                    ?: if (selected == null) "Kh\u00f4ng t\u00ecm th\u1ea5y \u0111\u00e1p \u00e1n \u0111\u00fang cho m\u00e3 \u0111\u1ec1 ${scanResult.examCode}" else null
            )
            recalculateGrade()
        }
    }

    fun updateExamCode(value: String) {
        val normalized = value.filter { it.isDigit() }.take(3)
        val selected = OmrGrader.selectAnswerKey(_uiState.value.printVersions, normalized, preferredVersionId)
        _uiState.value = _uiState.value.copy(
            examCode = normalized,
            selectedPrintVersion = selected,
            error = if (selected == null) "Kh\u00f4ng t\u00ecm th\u1ea5y \u0111\u00e1p \u00e1n \u0111\u00fang cho m\u00e3 \u0111\u1ec1 $normalized" else null
        )
        recalculateGrade()
    }

    fun updateStudentCode(value: String) {
        val normalized = value.filter { it.isDigit() }.take(6)
        _uiState.value = _uiState.value.copy(studentCode = normalized)
        viewModelScope.launch {
            val student = repository.findStudentByCode(classId, normalized).getOrNull()
            _uiState.value = _uiState.value.copy(matchedStudent = student)
            recalculateGrade()
        }
    }

    fun selectPrintVersion(version: OmrPrintVersion) {
        preferredVersionId = version.id
        _uiState.value = _uiState.value.copy(
            selectedPrintVersion = version,
            examCode = version.examCode.takeIf { it.isNotBlank() } ?: _uiState.value.examCode,
            error = null
        )
        recalculateGrade()
    }

    fun updateAnswer(questionNumber: Int, answer: String?) {
        val normalized = OmrGrader.normalizeAnswer(answer)
        val answers = _uiState.value.editableAnswers.toMutableMap()
        val statuses = _uiState.value.editableStatuses.toMutableMap()
        answers[questionNumber] = normalized
        statuses[questionNumber] = if (normalized == null) OmrAnswerStatus.BLANK else OmrAnswerStatus.OK
        _uiState.value = _uiState.value.copy(editableAnswers = answers, editableStatuses = statuses)
        recalculateGrade()
    }

    fun saveGrade() {
        val grade = _uiState.value.grade ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.saveOmrGrade(classId, grade).fold(
                onSuccess = { id ->
                    _uiState.value = _uiState.value.copy(isSaving = false, savedGradeId = id)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isSaving = false, error = error.message)
                }
            )
        }
    }

    private fun recalculateGrade() {
        val state = _uiState.value
        val scan = state.scanResult ?: return
        val answerKey = state.selectedPrintVersion ?: return
        val teacherId = repository.currentTeacherId ?: return
        val grade = OmrGrader.gradeAnswers(
            scanResult = scan,
            answerKey = answerKey,
            classId = classId,
            examId = examId,
            classExamInstanceId = classExamInstanceId,
            teacherId = teacherId,
            studentId = state.matchedStudent?.id,
            studentCodeOverride = state.matchedStudent?.studentCode ?: state.studentCode,
            examCodeOverride = state.examCode,
            answersOverride = state.editableAnswers,
            statusesOverride = state.editableStatuses
        )
        _uiState.value = state.copy(grade = grade)
    }
}

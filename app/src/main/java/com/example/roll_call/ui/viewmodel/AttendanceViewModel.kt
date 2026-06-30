package com.example.roll_call.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.AttendanceRepository
import com.example.roll_call.data.repository.AuthRepository
import com.example.roll_call.data.repository.StudentRepository
import com.example.roll_call.data.repository.TeacherRepository
import com.example.roll_call.domain.model.AttendanceRecord
import com.example.roll_call.domain.model.AttendanceStatus
import com.example.roll_call.domain.model.Student
import com.example.roll_call.domain.model.TeacherFaceProfile
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val students: List<Student> = emptyList(),
    val presentStudents: Set<String> = emptySet(),
    val lastRecognized: Student? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val sessionId: String? = null,
    val isEditingSession: Boolean = false,
    val teacherFaceEmbedding: List<Float>? = null,
    val showTeacherMessage: Boolean = false,
    val lateThreshold: Int = 15
)

class AttendanceViewModel : ViewModel() {
    private val studentRepository = StudentRepository()
    private val attendanceRepository = AttendanceRepository()
    private val authRepository = AuthRepository()
    private val teacherRepository = TeacherRepository()

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState

    fun loadStudents(classId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = studentRepository.getStudentsByClass(classId)
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(students = it, isLoading = false) },
                onFailure = { _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun createNewSession(
        classId: String, 
        className: String, 
        sessionName: String, 
        lateThreshold: Int, 
        onSessionCreated: (String) -> Unit
    ) {
        val teacherId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = studentRepository.getStudentsByClass(classId)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message)
                return@launch
            }

            val students = result.getOrThrow()
            val sessionResult = attendanceRepository.createSession(
                classId, className, sessionName, "", teacherId, students.size, lateThreshold
            )

            sessionResult.fold(
                onSuccess = { sessionId ->
                    _uiState.value = _uiState.value.copy(
                        students = students,
                        isLoading = false,
                        sessionId = sessionId,
                        isEditingSession = false,
                        lateThreshold = lateThreshold
                    )
                    onSessionCreated(sessionId)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.message
                    )
                }
            )
        }
    }

    fun loadStudentsForSession(classId: String, sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isEditingSession = true, sessionId = sessionId)
            try {
                val sessionResult = attendanceRepository.getSession(sessionId)
                val threshold = if (sessionResult.isSuccess) sessionResult.getOrThrow().lateThreshold else 15

                val studentsResult = studentRepository.getStudentsByClass(classId)
                if (studentsResult.isFailure) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = studentsResult.exceptionOrNull()?.message)
                    return@launch
                }
                val students = studentsResult.getOrThrow()

                val recordsResult = attendanceRepository.getRecords(sessionId)
                val presentIds = if (recordsResult.isSuccess) {
                    recordsResult.getOrThrow()
                        .filter { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
                        .map { it.studentId }
                        .toSet()
                } else {
                    emptySet()
                }

                _uiState.value = _uiState.value.copy(
                    students = students,
                    presentStudents = presentIds,
                    isLoading = false,
                    lateThreshold = threshold
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun markPresent(student: Student) {
        val updated = _uiState.value.presentStudents + student.id
        _uiState.value = _uiState.value.copy(
            presentStudents = updated,
            lastRecognized = student
        )
    }

    fun saveRecognition(sessionId: String, student: Student) {
        viewModelScope.launch {
            val record = AttendanceRecord(
                studentId = student.id,
                studentName = student.name,
                studentCode = student.studentCode,
                status = AttendanceStatus.PRESENT, 
                timestamp = Timestamp.now()
            )
            attendanceRepository.saveRecognition(sessionId, record)
        }
    }

    fun clearLastRecognized() {
        _uiState.value = _uiState.value.copy(lastRecognized = null)
    }

    fun loadTeacherFaceProfile() {
        val teacherId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                var attempts = 0
                var foundProfile: TeacherFaceProfile? = null

                while (attempts < 3 && foundProfile == null) {
                    attempts++
                    val result = teacherRepository.getTeacherFaceProfile(teacherId)
                    val profile = result.getOrNull()
                    if (profile != null && profile.faceEmbedding.isNotEmpty()) {
                        foundProfile = profile
                        _uiState.value = _uiState.value.copy(teacherFaceEmbedding = profile.faceEmbedding)
                    }
                    if (foundProfile == null) delay(500)
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceVM", "Error loading teacher profile", e)
            }
        }
    }

    fun showTeacherMessage() {
        _uiState.value = _uiState.value.copy(showTeacherMessage = true)
    }

    fun clearTeacherMessage() {
        _uiState.value = _uiState.value.copy(showTeacherMessage = false)
    }
}

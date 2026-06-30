package com.example.roll_call.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.AttendanceRepository
import com.example.roll_call.data.repository.AuthRepository
import com.example.roll_call.data.repository.StudentRepository
import com.example.roll_call.domain.model.AttendanceRecord
import com.example.roll_call.domain.model.AttendanceSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AttendanceHistoryUiState(
    val sessions: List<AttendanceSession> = emptyList(),
    val selectedSession: AttendanceSession? = null,
    val records: List<AttendanceRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingRecords: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null
)

class AttendanceHistoryViewModel : ViewModel() {
    private val repository = AttendanceRepository()
    private val authRepository = AuthRepository()
    private val studentRepository = StudentRepository()

    private val _uiState = MutableStateFlow(AttendanceHistoryUiState())
    val uiState: StateFlow<AttendanceHistoryUiState> = _uiState

    fun loadSessions(classId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getSessionsByClass(classId).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(sessions = it, isLoading = false) },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun selectSession(session: AttendanceSession) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedSession = session,
                isLoadingRecords = true,
                records = emptyList()
            )
            repository.getRecords(session.id).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(records = it, isLoadingRecords = false) },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoadingRecords = false) }
            )
        }
    }

    fun createSession(
        classId: String,
        className: String,
        sessionName: String,
        lateThreshold: Int,
        onSuccess: (String) -> Unit
    ) {
        val teacherId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            
            // Lấy danh sách sinh viên để biết tổng số
            val studentsResult = studentRepository.getStudentsByClass(classId)
            if (studentsResult.isFailure) {
                _uiState.value = _uiState.value.copy(isCreating = false, error = studentsResult.exceptionOrNull()?.message)
                return@launch
            }
            
            val totalStudents = studentsResult.getOrThrow().size
            
            repository.createSession(
                classId = classId,
                className = className,
                sessionName = sessionName,
                sessionNumber = "", // Có thể dùng sessionName thay thế
                teacherId = teacherId,
                totalStudents = totalStudents,
                lateThreshold = lateThreshold
            ).fold(
                onSuccess = { sessionId ->
                    _uiState.value = _uiState.value.copy(isCreating = false)
                    onSuccess(sessionId)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isCreating = false, error = it.message)
                }
            )
        }
    }

    fun clearSelectedSession() {
        _uiState.value = _uiState.value.copy(selectedSession = null, records = emptyList())
    }

    fun deleteSession(sessionId: String, classId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId).fold(
                onSuccess = {
                    val updated = _uiState.value.sessions.filter { it.id != sessionId }
                    _uiState.value = _uiState.value.copy(sessions = updated)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(error = it.message)
                }
            )
        }
    }
}

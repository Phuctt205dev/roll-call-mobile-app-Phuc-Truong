package com.example.roll_call.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.StudentRepository
import com.example.roll_call.domain.model.Student
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StudentManagementUiState(
    val students: List<Student> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class StudentManagementViewModel : ViewModel() {
    private val repository = StudentRepository()

    private val _uiState = MutableStateFlow(StudentManagementUiState())
    val uiState: StateFlow<StudentManagementUiState> = _uiState

    fun loadStudents(classId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getStudentsByClass(classId).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(students = it, isLoading = false) },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun addStudent(classId: String, name: String, studentCode: String) {
        if (name.isBlank() || studentCode.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Vui lòng nhập đầy đủ thông tin")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            // Kiểm tra mã SV trùng
            val exists = repository.isStudentCodeExists(classId, studentCode.trim())
            if (exists) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Mã sinh viên '${studentCode.trim()}' đã tồn tại trong lớp")
                return@launch
            }
            repository.addStudent(classId, name.trim(), studentCode.trim()).fold(
                onSuccess = { student ->
                    val updated = _uiState.value.students + student
                    _uiState.value = _uiState.value.copy(
                        students = updated,
                        isSaving = false,
                        successMessage = "Đã thêm sinh viên ${student.name}"
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
                }
            )
        }
    }

    fun updateStudent(classId: String, studentId: String, name: String, studentCode: String) {
        if (name.isBlank() || studentCode.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Vui lòng nhập đầy đủ thông tin")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            // Kiểm tra mã SV trùng (bỏ qua chính nó)
            val exists = repository.isStudentCodeExists(classId, studentCode.trim(), excludeId = studentId)
            if (exists) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Mã sinh viên '${studentCode.trim()}' đã tồn tại trong lớp")
                return@launch
            }
            repository.updateStudent(classId, studentId, name.trim(), studentCode.trim()).fold(
                onSuccess = {
                    val updated = _uiState.value.students.map { s ->
                        if (s.id == studentId) s.copy(name = name.trim(), studentCode = studentCode.trim()) else s
                    }
                    _uiState.value = _uiState.value.copy(
                        students = updated,
                        isSaving = false,
                        successMessage = "Đã cập nhật sinh viên"
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
                }
            )
        }
    }

    fun deleteStudent(classId: String, studentId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.deleteStudent(classId, studentId).fold(
                onSuccess = {
                    val updated = _uiState.value.students.filter { it.id != studentId }
                    _uiState.value = _uiState.value.copy(
                        students = updated,
                        isSaving = false,
                        successMessage = "Đã xóa sinh viên"
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
                }
            )
        }
    }

    fun saveFaceEmbedding(classId: String, studentId: String, embedding: FloatArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            repository.saveFaceEmbedding(classId, studentId, embedding).fold(
                onSuccess = {
                    val updated = _uiState.value.students.map { s ->
                        if (s.id == studentId) s.copy(faceEmbedding = embedding.toList()) else s
                    }
                    _uiState.value = _uiState.value.copy(
                        students = updated,
                        isSaving = false,
                        successMessage = "Đã lưu khuôn mặt thành công!"
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}

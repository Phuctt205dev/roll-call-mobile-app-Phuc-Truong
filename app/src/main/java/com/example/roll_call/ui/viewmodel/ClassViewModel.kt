package com.example.roll_call.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.AuthRepository
import com.example.roll_call.data.repository.ClassRepository
import com.example.roll_call.domain.model.ClassRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ClassListUiState(
    val classes: List<ClassRoom> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val teacherName: String = ""
)

class ClassViewModel : ViewModel() {
    private val classRepository = ClassRepository()
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow(ClassListUiState())
    val uiState: StateFlow<ClassListUiState> = _uiState

    init {
        loadClasses()
    }

    fun loadClasses() {
        val teacherId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = classRepository.getClassesByTeacher(teacherId)
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(classes = it, isLoading = false) },
                onFailure = { _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun logout() = authRepository.logout()
}


package com.example.roll_call.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.OmrRepository
import com.example.roll_call.domain.model.omr.OmrExam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OmrExamListUiState(
    val exams: List<OmrExam> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class OmrExamListViewModel : ViewModel() {
    private val repository = OmrRepository()

    private val _uiState = MutableStateFlow(OmrExamListUiState())
    val uiState: StateFlow<OmrExamListUiState> = _uiState

    fun loadExams(classId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getExamsForClass(classId).fold(
                onSuccess = { exams ->
                    _uiState.value = _uiState.value.copy(exams = exams, isLoading = false)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(error = error.message, isLoading = false)
                }
            )
        }
    }
}

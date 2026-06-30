package com.example.roll_call.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.AttendanceRepository
import com.example.roll_call.domain.model.AttendanceRecord
import com.example.roll_call.domain.model.AttendanceSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SummaryUiState(
    val session: AttendanceSession? = null,
    val records: List<AttendanceRecord> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SummaryViewModel : ViewModel() {
    private val repository = AttendanceRepository()

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState

    fun loadSummary(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val sessionResult = repository.getSession(sessionId)
            val recordsResult = repository.getRecords(sessionId)
            _uiState.value = _uiState.value.copy(
                session = sessionResult.getOrNull(),
                records = recordsResult.getOrDefault(emptyList()),
                isLoading = false,
                error = sessionResult.exceptionOrNull()?.message
            )
        }
    }
}


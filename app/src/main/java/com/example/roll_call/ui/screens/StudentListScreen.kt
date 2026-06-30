package com.example.roll_call.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.Student
import com.example.roll_call.ui.theme.*
import com.example.roll_call.ui.viewmodel.AttendanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentListScreen(
    classId: String,
    className: String,
    onStartAttendance: () -> Unit,
    onManageStudents: () -> Unit,
    onViewHistory: () -> Unit,
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(classId) {
        viewModel.loadStudents(classId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(className) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = onViewHistory) {
                        Icon(Icons.Default.DateRange, "Lịch sử điểm danh")
                    }
                    IconButton(onClick = onManageStudents) {
                        Icon(Icons.Default.PersonAdd, "Quản lý sinh viên")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.students.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onStartAttendance,
                    icon = { Icon(Icons.Default.PhotoCamera, null) },
                    text = { Text("Bắt đầu điểm danh") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        "Lỗi: ${uiState.error}",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Column {
                    Surface(color = EduBackground) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tổng: ${uiState.students.size}", fontWeight = FontWeight.Medium, color = EduTextPrimary)
                            Text(
                                "Embedding: ${uiState.students.count { it.faceEmbedding.isNotEmpty() }}",
                                color = EduBlue,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.students.sortedBy { it.studentCode }) { student ->
                                StudentItem(student = student)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentItem(student: Student) {
    val hasEmbedding = student.faceEmbedding.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = EduTextSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(student.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = EduTextPrimary)
                Text(
                    student.studentCode,
                    fontSize = 12.sp,
                    color = EduTextSecondary
                )
            }
            if (hasEmbedding) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Đã đăng ký khuôn mặt",
                    tint = EduGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}


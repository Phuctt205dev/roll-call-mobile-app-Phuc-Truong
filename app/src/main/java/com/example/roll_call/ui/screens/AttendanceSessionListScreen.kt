package com.example.roll_call.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.AttendanceSession
import com.example.roll_call.ui.theme.*
import com.example.roll_call.ui.viewmodel.AttendanceHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceSessionListScreen(
    classId: String,
    className: String,
    onSessionClick: (String, String, String) -> Unit,
    onManageStudents: () -> Unit,
    onBack: () -> Unit,
    viewModel: AttendanceHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(classId) {
        viewModel.loadSessions(classId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(className, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Danh sách buổi điểm danh", fontSize = 12.sp, color = EduTextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = onManageStudents) {
                        Icon(Icons.Default.People, "Sinh viên")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EduSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onSessionClick("", classId, className) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Điểm danh mới") },
                containerColor = EduBlue,
                contentColor = Color.White
            )
        },
        containerColor = EduBackground
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = EduBlue)
                }
                uiState.error != null -> {
                    Text(
                        "Lỗi: ${uiState.error}",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        color = EduRed
                    )
                }
                uiState.sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📅", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Chưa có buổi điểm danh nào", color = EduTextSecondary)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.sessions) { session ->
                            SessionItem(
                                session = session,
                                onClick = { onSessionClick(session.id, classId, className) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItem(session: AttendanceSession, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = remember(session.date) { dateFormat.format(session.date.toDate()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(EduBlueLight, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("📝", fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val displayName = session.sessionName.ifEmpty { "Buổi điểm danh" }
                Text(displayName, fontWeight = FontWeight.Bold, color = EduTextPrimary)
                Text(dateStr, fontSize = 13.sp, color = EduTextSecondary)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${session.presentCount}/${session.totalStudents}",
                    fontWeight = FontWeight.SemiBold,
                    color = EduBlue
                )
                Icon(Icons.Default.ChevronRight, null, tint = EduTextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

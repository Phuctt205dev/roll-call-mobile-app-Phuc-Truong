package com.example.roll_call.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.roll_call.domain.model.AttendanceRecord
import com.example.roll_call.domain.model.AttendanceSession
import com.example.roll_call.domain.model.AttendanceStatus
import com.example.roll_call.ui.theme.*
import com.example.roll_call.ui.viewmodel.AttendanceHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryScreen(
    classId: String,
    className: String,
    onBack: () -> Unit,
    viewModel: AttendanceHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var sessionToDelete by remember { mutableStateOf<AttendanceSession?>(null) }

    LaunchedEffect(classId) { viewModel.loadSessions(classId) }

    if (sessionToDelete != null) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Xóa buổi điểm danh", color = EduTextPrimary) },
            text = { Text("Bạn có chắc muốn xóa buổi ngày ${dateFormat.format(sessionToDelete!!.date.toDate())}?\nTất cả dữ liệu điểm danh sẽ bị xóa vĩnh viễn.", color = EduTextSecondary) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteSession(sessionToDelete!!.id, classId); sessionToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = EduRed)
                ) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text("Hủy") } },
            containerColor = EduSurface,
            titleContentColor = EduTextPrimary,
            textContentColor = EduTextSecondary
        )
    }

    if (uiState.selectedSession != null) {
        SessionDetailScreen(
            session = uiState.selectedSession!!,
            records = uiState.records,
            isLoading = uiState.isLoadingRecords,
            onBack = { viewModel.clearSelectedSession() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lịch sử điểm danh", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = EduTextPrimary)
                        Text(className, fontSize = 12.sp, color = EduTextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = EduTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EduSurface)
            )
        },
        containerColor = EduBackground
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = EduBlue)
                uiState.error != null -> Text("Lỗi: ${uiState.error}", modifier = Modifier.align(Alignment.Center), color = EduRed)
                uiState.sessions.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📋", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Chưa có buổi điểm danh nào", color = EduTextSecondary)
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Lịch sử", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = EduTextPrimary)
                                Text("${uiState.sessions.size} buổi điểm danh", fontSize = 13.sp, color = EduTextSecondary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    items(uiState.sessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            onClick = { viewModel.selectSession(session) },
                            onDelete = { sessionToDelete = session }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionCard(session: AttendanceSession, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateStr = remember(session.date) { dateFormat.format(session.date.toDate()) }
    val ratio = if (session.totalStudents > 0) session.presentCount.toFloat() / session.totalStudents else 0f
    val (ratioColor, ratioBg) = when {
        ratio >= 0.8f -> EduGreen to EduGreenLight
        ratio >= 0.5f -> EduOrange to EduOrangeLight
        else -> EduRed to EduRedLight
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📅", fontSize = 20.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val displayName = session.sessionName.ifEmpty { session.sessionNumber.ifEmpty { "Buổi điểm danh" } }
                Text(displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = EduTextPrimary)
                Text(dateStr, fontSize = 13.sp, color = EduTextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = ratioBg) {
                        Text(
                            "${(ratio * 100).toInt()}%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ratioColor
                        )
                    }
                    Text("${session.presentCount}/${session.totalStudents}", fontSize = 12.sp, color = EduTextSecondary)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Xóa buổi", tint = EduRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
            Icon(Icons.Default.ChevronRight, null, tint = EduTextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    session: AttendanceSession,
    records: List<AttendanceRecord>,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = remember(session.date) { dateFormat.format(session.date.toDate()) }
    val presentList = records.filter { it.status == AttendanceStatus.PRESENT }
    val lateList = records.filter { it.status == AttendanceStatus.LATE }
    val absentList = records.filter { it.status == AttendanceStatus.ABSENT }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val displayName = session.sessionName.ifEmpty { session.sessionNumber.ifEmpty { "Chi tiết" } }
                        Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = EduTextPrimary)
                        Text(dateStr, fontSize = 12.sp, color = EduTextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = EduTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EduSurface)
            )
        },
        containerColor = EduBackground
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EduBlue)
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = EduBlue),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatChip("Tổng", "${session.totalStudents}", Color.White, Color.White.copy(0.15f))
                            StatChip("Có mặt", "${presentList.size}", Color(0xFF86EFAC), Color.White.copy(0.15f))
                            StatChip("Trễ", "${lateList.size}", EduOrangeLight, Color.White.copy(0.15f))
                            StatChip("Vắng", "${absentList.size}", Color(0xFFFCA5A5), Color.White.copy(0.15f))
                        }
                    }
                }
            }
            if (presentList.isNotEmpty()) {
                item { SectionHeader("Có mặt", presentList.size, EduGreen, Icons.Default.CheckCircle) }
                items(presentList) { AttendanceRecordRow(it) }
            }
            if (lateList.isNotEmpty()) {
                item { SectionHeader("Trễ", lateList.size, EduOrange, Icons.Default.History) }
                items(lateList) { AttendanceRecordRow(it) }
            }
            if (absentList.isNotEmpty()) {
                item { SectionHeader("Vắng", absentList.size, EduRed, Icons.Default.Close) }
                items(absentList) { AttendanceRecordRow(it) }
            }
        }
    }
}

@Composable
fun SectionHeader(label: String, count: Int, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label ($count)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextPrimary)
    }
}

@Composable
fun StatChip(label: String, value: String, valueColor: Color, bgColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bgColor).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = valueColor)
        Text(label, fontSize = 11.sp, color = Color.White.copy(0.7f))
    }
}

@Composable
fun AttendanceRecordRow(record: AttendanceRecord) {
    val (bgColor, textColor, label) = when (record.status) {
        AttendanceStatus.PRESENT -> Triple(EduGreenLight, EduGreen, "Có mặt")
        AttendanceStatus.LATE -> Triple(EduOrangeLight, EduOrange, "Trễ")
        AttendanceStatus.ABSENT -> Triple(EduRedLight, EduRed, "Vắng")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when(record.status) {
                        AttendanceStatus.PRESENT -> Icons.Default.CheckCircle
                        AttendanceStatus.LATE -> Icons.Default.History
                        else -> Icons.Default.Close
                    },
                    null, tint = textColor, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.studentName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = EduTextPrimary)
                Text(record.studentCode, fontSize = 12.sp, color = EduTextSecondary)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = bgColor) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textColor
                )
            }
        }
    }
}

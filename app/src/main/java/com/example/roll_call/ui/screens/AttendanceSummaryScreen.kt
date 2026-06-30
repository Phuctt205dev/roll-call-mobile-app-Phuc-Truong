package com.example.roll_call.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.AttendanceRecord
import com.example.roll_call.domain.model.AttendanceStatus
import com.example.roll_call.ui.theme.*
import com.example.roll_call.ui.viewmodel.SummaryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceSummaryScreen(
    sessionId: String,
    onDone: () -> Unit,
    viewModel: SummaryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSummary(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kết quả điểm danh") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EduSurface)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = EduSurface) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
                ) {
                    Text("Về trang chủ", fontSize = 16.sp)
                }
            }
        },
        containerColor = EduBackground
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
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Summary Card
                        uiState.session?.let { session ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = EduBlueLight
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            session.className,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EduTextPrimary
                                        )
                                        if (session.sessionNumber.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Buổi: ${session.sessionNumber}",
                                                fontSize = 14.sp,
                                                color = EduTextSecondary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val dateStr = SimpleDateFormat(
                                            "dd/MM/yyyy HH:mm",
                                            Locale.getDefault()
                                        ).format(session.date.toDate())
                                        Text("Ngày: $dateStr", fontSize = 14.sp, color = EduTextPrimary)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            val presentRecords = uiState.records.filter { it.status == AttendanceStatus.PRESENT }
                                            val lateRecords = uiState.records.filter { it.status == AttendanceStatus.LATE }
                                            val absentRecords = uiState.records.filter { it.status == AttendanceStatus.ABSENT }

                                            StatItem(
                                                label = "Có mặt",
                                                value = presentRecords.size.toString(),
                                                color = EduGreen
                                            )
                                            StatItem(
                                                label = "Trễ",
                                                value = lateRecords.size.toString(),
                                                color = EduBlue
                                            )
                                            StatItem(
                                                label = "Vắng",
                                                value = absentRecords.size.toString(),
                                                color = EduRed
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Present list
                        val presentRecords = uiState.records.filter { it.status == AttendanceStatus.PRESENT }
                        val lateRecords = uiState.records.filter { it.status == AttendanceStatus.LATE }
                        val absentRecords = uiState.records.filter { it.status == AttendanceStatus.ABSENT }

                        if (presentRecords.isNotEmpty()) {
                            item {
                                Text(
                                    "✅ Có mặt (${presentRecords.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            items(presentRecords) { record ->
                                AttendanceRecordItem(record = record)
                            }
                        }

                        if (lateRecords.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "⏱️ Trễ (${lateRecords.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            items(lateRecords) { record ->
                                AttendanceRecordItem(record = record)
                            }
                        }

                        if (absentRecords.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "❌ Vắng mặt (${absentRecords.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            items(absentRecords) { record ->
                                AttendanceRecordItem(record = record)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 13.sp, color = EduTextSecondary)
    }
}

@Composable
fun AttendanceRecordItem(record: AttendanceRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = when (record.status) {
                AttendanceStatus.PRESENT -> Icons.Default.CheckCircle to EduGreen
                AttendanceStatus.LATE -> Icons.Default.CheckCircle to EduBlue
                AttendanceStatus.ABSENT -> Icons.Default.Close to EduRed
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.studentName, fontWeight = FontWeight.Medium, color = EduTextPrimary)
                Text(
                    record.studentCode,
                    fontSize = 13.sp,
                    color = EduTextSecondary
                )
            }
            if ((record.status == AttendanceStatus.PRESENT || record.status == AttendanceStatus.LATE) && record.timestamp != null) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(record.timestamp.toDate())
                Text(timeStr, fontSize = 12.sp, color = EduTextSecondary)
            }
        }
    }
}


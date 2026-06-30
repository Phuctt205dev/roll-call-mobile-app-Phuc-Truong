package com.example.roll_call.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.omr.OmrExam
import com.example.roll_call.ui.theme.EduBackground
import com.example.roll_call.ui.theme.EduBlue
import com.example.roll_call.ui.theme.EduBlueLight
import com.example.roll_call.ui.theme.EduBorder
import com.example.roll_call.ui.theme.EduRed
import com.example.roll_call.ui.theme.EduSurface
import com.example.roll_call.ui.theme.EduTextPrimary
import com.example.roll_call.ui.theme.EduTextSecondary
import com.example.roll_call.ui.viewmodel.OmrExamListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OMRExamListScreen(
    classId: String,
    className: String,
    onBack: () -> Unit,
    onScanExam: (examId: String, classExamInstanceId: String?, printVersionId: String?) -> Unit,
    viewModel: OmrExamListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(classId) { viewModel.loadExams(classId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ch\u1ea5m b\u00e0i OMR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(className, fontSize = 12.sp, color = EduTextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay l\u1ea1i")
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
                uiState.error != null -> Text(
                    text = "L\u1ed7i: ${uiState.error}",
                    color = EduRed,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
                uiState.exams.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(48.dp), tint = EduTextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Text("L\u1edbp n\u00e0y ch\u01b0a \u0111\u01b0\u1ee3c g\u00e1n b\u00e0i ki\u1ec3m tra", color = EduTextSecondary)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("B\u00e0i ki\u1ec3m tra c\u1ee7a l\u1edbp", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = EduTextPrimary)
                        Text("Ch\u1ec9 hi\u1ec3n th\u1ecb c\u00e1c b\u00e0i \u0111\u00e3 \u0111\u01b0\u1ee3c g\u00e1n cho l\u1edbp n\u00e0y", fontSize = 13.sp, color = EduTextSecondary)
                    }
                    items(uiState.exams, key = { it.classExamInstanceId ?: it.id }) { exam ->
                        OmrExamCard(exam = exam, onScanExam = onScanExam)
                    }
                }
            }
        }
    }
}

@Composable
private fun OmrExamCard(
    exam: OmrExam,
    onScanExam: (examId: String, classExamInstanceId: String?, printVersionId: String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = EduBlueLight) {
                    Icon(
                        Icons.Default.Description,
                        null,
                        tint = EduBlue,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exam.title.ifBlank { exam.id }, fontWeight = FontWeight.SemiBold, color = EduTextPrimary)
                    Text(
                        "${exam.totalQuestions} c\u00e2u - thang ${exam.maxScore}",
                        fontSize = 12.sp,
                        color = EduTextSecondary
                    )
                }
            }

            if (exam.printVersions.isEmpty()) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF7ED)) {
                    Text(
                        "Ch\u01b0a t\u00ecm th\u1ea5y \u0111\u00e1p \u00e1n trong b\u00e0i ki\u1ec3m tra n\u00e0y",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = Color(0xFF9A3412)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onScanExam(exam.id, exam.classExamInstanceId, null) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, EduBorder)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Qu\u00e9t")
                }
            }
        }
    }
}

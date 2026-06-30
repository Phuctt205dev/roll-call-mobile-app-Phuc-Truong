package com.example.roll_call.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.Student
import com.example.roll_call.domain.model.omr.OmrAnswerStatus
import com.example.roll_call.domain.model.omr.OmrPrintVersion
import com.example.roll_call.domain.model.omr.OmrScanResult
import com.example.roll_call.domain.omr.OmrGrader
import com.example.roll_call.ui.theme.EduBackground
import com.example.roll_call.ui.theme.EduBlue
import com.example.roll_call.ui.theme.EduBlueLight
import com.example.roll_call.ui.theme.EduGreen
import com.example.roll_call.ui.theme.EduGreenLight
import com.example.roll_call.ui.theme.EduOrange
import com.example.roll_call.ui.theme.EduOrangeLight
import com.example.roll_call.ui.theme.EduRed
import com.example.roll_call.ui.theme.EduRedLight
import com.example.roll_call.ui.theme.EduSurface
import com.example.roll_call.ui.theme.EduTextPrimary
import com.example.roll_call.ui.theme.EduTextSecondary
import com.example.roll_call.ui.viewmodel.OmrReviewViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OMRReviewScreen(
    classId: String,
    className: String,
    examId: String,
    classExamInstanceId: String?,
    printVersionId: String?,
    scanResult: OmrScanResult?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: OmrReviewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(scanResult) {
        scanResult?.let {
            viewModel.initialize(classId, examId, classExamInstanceId, printVersionId, it)
        }
    }

    if (uiState.savedGradeId != null) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("\u0110\u00e3 l\u01b0u \u0111i\u1ec3m") },
            text = { Text("K\u1ebft qu\u1ea3 OMR \u0111\u00e3 \u0111\u01b0\u1ee3c l\u01b0u v\u00e0o l\u1edbp $className") },
            confirmButton = { Button(onClick = onDone) { Text("Xong") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("X\u00e1c nh\u1eadn k\u1ebft qu\u1ea3", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        bottomBar = {
            Surface(color = EduSurface, shadowElevation = 8.dp) {
                Button(
                    onClick = { viewModel.saveGrade() },
                    enabled = uiState.grade != null && !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("L\u01b0u k\u1ebft qu\u1ea3")
                    }
                }
            }
        },
        containerColor = EduBackground
    ) { padding ->
        when {
            scanResult == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Kh\u00f4ng c\u00f3 d\u1eef li\u1ec7u qu\u00e9t. Vui l\u00f2ng qu\u00e9t l\u1ea1i.", color = EduRed)
            }
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EduBlue)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    GradeSummaryCard(
                        correctCount = uiState.grade?.correctCount ?: 0,
                        totalQuestions = uiState.grade?.totalQuestions ?: 40,
                        score = uiState.grade?.score ?: 0.0,
                        maxScore = uiState.grade?.maxScore ?: 10.0,
                        confidence = scanResult.confidence
                    )
                }
                item {
                    ReviewIdentityCard(
                        studentCode = uiState.studentCode,
                        examCode = uiState.examCode,
                        student = uiState.matchedStudent,
                        onStudentCodeChange = viewModel::updateStudentCode,
                        onExamCodeChange = viewModel::updateExamCode
                    )
                }
                item {
                    PrintVersionSelector(
                        versions = uiState.printVersions,
                        selected = uiState.selectedPrintVersion,
                        onSelect = viewModel::selectPrintVersion
                    )
                }
                if (uiState.error != null) {
                    item {
                        Text(
                            uiState.error!!,
                            color = EduRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                item { DebugImage(scanResult.debugInfo.debugOverlayPath) }
                item {
                    Text("\u0110\u00e1p \u00e1n nh\u1eadn di\u1ec7n", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EduTextPrimary)
                }
                items((1..(uiState.grade?.totalQuestions ?: 40)).toList()) { question ->
                    AnswerEditorRow(
                        questionNumber = question,
                        answer = uiState.editableAnswers[question],
                        status = uiState.editableStatuses[question] ?: OmrAnswerStatus.BLANK,
                        correctAnswer = uiState.grade?.correctAnswers?.get(question.toString()),
                        onAnswerChange = { answer -> viewModel.updateAnswer(question, answer) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeSummaryCard(
    correctCount: Int,
    totalQuestions: Int,
    score: Double,
    maxScore: Double,
    confidence: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduBlue),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("\u0110i\u1ec3m", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Text("${formatScore(score)}/${formatScore(maxScore)}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("\u0110\u00fang $correctCount/$totalQuestions", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("\u0110\u1ed9 tin c\u1eady ${(confidence * 100).toInt()}%", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ReviewIdentityCard(
    studentCode: String,
    examCode: String,
    student: Student?,
    onStudentCodeChange: (String) -> Unit,
    onExamCodeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = examCode,
                    onValueChange = onExamCodeChange,
                    label = { Text("M\u00e3 \u0111\u1ec1") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = studentCode,
                    onValueChange = onStudentCodeChange,
                    label = { Text("SBD") },
                    modifier = Modifier.weight(1.4f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Text(
                text = student?.let { "Sinh vi\u00ean: ${it.name} - MSSV ${it.studentCode}" } ?: "Ch\u01b0a kh\u1edbp sinh vi\u00ean trong l\u1edbp",
                color = if (student == null) EduOrange else EduGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PrintVersionSelector(
    versions: List<OmrPrintVersion>,
    selected: OmrPrintVersion?,
    onSelect: (OmrPrintVersion) -> Unit
) {
    if (versions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("\u0110\u00e1p \u00e1n \u0111\u00fang", fontWeight = FontWeight.SemiBold, color = EduTextPrimary)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            versions.forEach { version ->
                FilterChip(
                    selected = selected?.id == version.id,
                    onClick = { onSelect(version) },
                    label = { Text(version.examCode.ifBlank { version.id }) }
                )
            }
        }
    }
}

@Composable
private fun DebugImage(path: String?) {
    if (path.isNullOrBlank()) return
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    if (bitmap != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("\u1ea2nh debug", fontWeight = FontWeight.SemiBold, color = EduTextPrimary)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "OMR debug overlay",
                modifier = Modifier.fillMaxWidth().background(EduSurface, RoundedCornerShape(8.dp)).padding(6.dp)
            )
        }
    }
}

@Composable
private fun AnswerEditorRow(
    questionNumber: Int,
    answer: String?,
    status: OmrAnswerStatus,
    correctAnswer: String?,
    onAnswerChange: (String?) -> Unit
) {
    val normalizedAnswer = OmrGrader.normalizeAnswer(answer)
    val normalizedCorrect = OmrGrader.normalizeAnswer(correctAnswer)
    val hasCorrectAnswer = !normalizedCorrect.isNullOrBlank()
    val isBlank = normalizedAnswer.isNullOrBlank() || status == OmrAnswerStatus.BLANK
    val isCorrect = OmrGrader.isAnswerCorrect(normalizedAnswer, status, normalizedCorrect)
    val isWrong = hasCorrectAnswer && !isBlank && !isCorrect

    val accentColor = when {
        isBlank -> EduBlue
        isCorrect -> EduGreen
        isWrong -> EduRed
        else -> EduTextSecondary
    }
    val stateBg = when {
        isBlank -> EduBlueLight
        isCorrect -> EduGreenLight
        isWrong -> EduRedLight
        else -> EduBackground
    }
    val rowBg = when {
        isBlank -> EduBlueLight.copy(alpha = 0.42f)
        isCorrect -> EduGreenLight.copy(alpha = 0.42f)
        isWrong -> EduRedLight.copy(alpha = 0.50f)
        else -> EduSurface
    }
    val verdictText = when {
        isBlank && hasCorrectAnswer -> "B\u1ecf tr\u1ed1ng - \u0111\u00fang: $normalizedCorrect"
        isBlank -> "B\u1ecf tr\u1ed1ng"
        status == OmrAnswerStatus.MULTIPLE && hasCorrectAnswer -> "T\u00f4 nhi\u1ec1u - \u0111\u00fang: $normalizedCorrect"
        status == OmrAnswerStatus.MULTIPLE -> "T\u00f4 nhi\u1ec1u"
        status == OmrAnswerStatus.UNCERTAIN && hasCorrectAnswer -> "C\u1ea7n ki\u1ec3m tra - \u0111\u00fang: $normalizedCorrect"
        status == OmrAnswerStatus.UNCERTAIN -> "C\u1ea7n ki\u1ec3m tra"
        isCorrect -> "\u0110\u00fang"
        isWrong -> "Sai - \u0111\u00fang: $normalizedCorrect"
        else -> "\u0110\u00e3 nh\u1eadn: ${normalizedAnswer ?: "?"}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = rowBg),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = questionNumber.toString().padStart(2, '0'),
                    modifier = Modifier.width(34.dp),
                    fontWeight = FontWeight.Bold,
                    color = EduTextPrimary
                )
                Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("A", "B", "C", "D").forEach { option ->
                        FilterChip(
                            selected = normalizedAnswer == option,
                            onClick = { onAnswerChange(option) },
                            label = { Text(option) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = stateBg,
                                selectedLabelColor = accentColor
                            )
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { onAnswerChange(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isBlank) EduBlueLight else Color.Transparent,
                        contentColor = if (isBlank) EduBlue else EduTextSecondary
                    )
                ) { Text("X") }
            }
            Surface(shape = RoundedCornerShape(6.dp), color = stateBg) {
                Text(
                    verdictText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatScore(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

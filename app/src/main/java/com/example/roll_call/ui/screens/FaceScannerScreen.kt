package com.example.roll_call.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.Student
import com.example.roll_call.ui.viewmodel.AttendanceViewModel
import com.example.roll_call.ui.theme.*
import com.example.roll_call.utils.FaceRecognitionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceScannerScreen(
    sessionId: String,
    classId: String,
    className: String,
    onFinish: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        viewModel.loadTeacherFaceProfile()
        
        if (sessionId.isNotEmpty()) {
            viewModel.loadStudentsForSession(classId, sessionId)
        } else {
            viewModel.createNewSession(classId, className, "Buổi điểm danh", 15) { newSessionId ->
                // Session created
            }
        }
    }

    val faceHelper = remember { FaceRecognitionHelper(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var isProcessing by remember { mutableStateOf(false) }

    val recognitionVotes = remember { mutableMapOf<String, Int>() }
    val VOTES_REQUIRED = 3
    val CONFIDENCE_THRESHOLD = 0.75f
    val TEACHER_CONFIDENCE_THRESHOLD = 0.55f
    val studentCooldowns = remember { mutableMapOf<String, Long>() }
    val COOLDOWN_MS = 5000L

    DisposableEffect(Unit) {
        onDispose {
            faceHelper.close()
            cameraExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Điểm danh: $className", fontSize = 16.sp)
                        Text("Ngưỡng trễ: ${uiState.lateThreshold} phút", fontSize = 12.sp, color = EduTextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(0.6f).background(Color.Black)
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFaceDetected = { embedding ->
                            if (!isProcessing) {
                                isProcessing = true
                                scope.launch {
                                    val teacherEmbedding = uiState.teacherFaceEmbedding
                                    val isTeacherFace = if (!teacherEmbedding.isNullOrEmpty()) {
                                        faceHelper.recognizeTeacher(embedding, teacherEmbedding.toFloatArray(), TEACHER_CONFIDENCE_THRESHOLD)
                                    } else false

                                    if (isTeacherFace) {
                                        viewModel.showTeacherMessage()
                                        delay(1500)
                                        viewModel.clearTeacherMessage()
                                    } else if (uiState.students.isNotEmpty()) {
                                        val student = faceHelper.recognizeStudent(embedding, uiState.students, CONFIDENCE_THRESHOLD)
                                        if (student != null) {
                                            val now = System.currentTimeMillis()
                                            val lastTime = studentCooldowns[student.id] ?: 0L
                                            if (now - lastTime > COOLDOWN_MS) {
                                                val votes = (recognitionVotes[student.id] ?: 0) + 1
                                                recognitionVotes[student.id] = votes
                                                recognitionVotes.keys.filter { it != student.id }.forEach { recognitionVotes[it] = 0 }

                                                if (votes >= VOTES_REQUIRED && student.id !in uiState.presentStudents) {
                                                    recognitionVotes[student.id] = 0
                                                    studentCooldowns[student.id] = now
                                                    viewModel.markPresent(student)
                                                    uiState.sessionId?.let { viewModel.saveRecognition(it, student) }
                                                    delay(2000)
                                                    viewModel.clearLastRecognized()
                                                }
                                            }
                                        } else {
                                            recognitionVotes.clear()
                                        }
                                    }
                                    delay(300)
                                    isProcessing = false
                                }
                            }
                        },
                        faceHelper = faceHelper,
                        cameraExecutor = cameraExecutor
                    )

                    uiState.lastRecognized?.let { RecognitionOverlay(student = it) }

                    if (uiState.showTeacherMessage) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color.Yellow.copy(alpha = 0.9f)).padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text("👨‍🏫 Giáo viên đã xác nhận\nVui lòng đưa sinh viên vào camera", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().weight(0.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hiện diện: ${uiState.presentStudents.size}", fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { uiState.sessionId?.let { onFinish(it) } },
                        enabled = !uiState.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = EduGreen)
                    ) {
                        Icon(Icons.Default.Done, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kết thúc")
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presentList = uiState.students.filter { it.id in uiState.presentStudents }
                    items(presentList) { student ->
                        PresentStudentChip(student = student)
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFaceDetected: (FloatArray) -> Unit,
    faceHelper: FaceRecognitionHelper,
    cameraExecutor: java.util.concurrent.ExecutorService
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setImageAnalysisAnalyzer(cameraExecutor) { imageProxy ->
                val bitmap = faceHelper.imageProxyToBitmap(imageProxy)
                imageProxy.close()
                if (bitmap == null) return@setImageAnalysisAnalyzer

                scope.launch {
                    try {
                        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                        val faces = faceHelper.getDetector().process(inputImage).await()
                        if (faces.isNotEmpty()) {
                            val face = faces.maxByOrNull {
                                it.boundingBox.width() * it.boundingBox.height()
                            } ?: return@launch

                            if (face.boundingBox.width() < bitmap.width * 0.2f) return@launch

                            val cropped = faceHelper.cropFace(bitmap, face) ?: return@launch
                            if (!faceHelper.checkFaceQuality(cropped)) return@launch

                            val embedding = faceHelper.extractEmbedding(cropped)
                            onFaceDetected(embedding)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                controller = cameraController
            }
        },
        modifier = modifier
    )
}

@Composable
fun BoxScope.RecognitionOverlay(student: Student) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EduGreen)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    student.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    student.studentCode,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun PresentStudentChip(student: Student) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = EduSurface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = EduGreen, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(student.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EduTextPrimary)
                Text(student.studentCode, fontSize = 12.sp, color = EduTextSecondary)
            }
        }
    }
}

package com.example.roll_call.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.Student
import com.example.roll_call.ui.viewmodel.StudentManagementViewModel
import com.example.roll_call.ui.theme.*
import com.example.roll_call.utils.FaceRecognitionHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentScreen(
    classId: String,
    className: String,
    onBack: () -> Unit,
    viewModel: StudentManagementViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showFaceDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }

    LaunchedEffect(classId) { viewModel.loadStudents(classId) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        uiState.error?.let { snackbarHostState.showSnackbar("⚠️ $it"); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý SV - $className") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EduSurface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EduBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Thêm sinh viên")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.students.isEmpty() -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Chưa có sinh viên nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showAddDialog = true }) { Text("Thêm sinh viên đầu tiên") }
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.students.sortedBy { it.studentCode }, key = { it.id }) { student ->
                            StudentManagementItem(
                                student = student,
                                onRegisterFace = { selectedStudent = student; showFaceDialog = true },
                                onEdit = { selectedStudent = student; showEditDialog = true },
                                onDelete = { selectedStudent = student; showDeleteDialog = true }
                            )
                        }
                    }
                }
            }
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // Dialog thêm
    if (showAddDialog) {
        StudentFormDialog(
            title = "Thêm sinh viên",
            initialName = "",
            initialCode = "",
            confirmText = "Thêm",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, code ->
                viewModel.addStudent(classId, name, code)
                showAddDialog = false
            }
        )
    }

    // Dialog sửa
    if (showEditDialog && selectedStudent != null) {
        StudentFormDialog(
            title = "Chỉnh sửa sinh viên",
            initialName = selectedStudent!!.name,
            initialCode = selectedStudent!!.studentCode,
            confirmText = "Lưu",
            onDismiss = { showEditDialog = false },
            onConfirm = { name, code ->
                viewModel.updateStudent(classId, selectedStudent!!.id, name, code)
                showEditDialog = false
            }
        )
    }

    // Dialog xác nhận xóa
    if (showDeleteDialog && selectedStudent != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Xóa sinh viên", color = EduTextPrimary) },
            text = { Text("Bạn có chắc muốn xóa sinh viên \"${selectedStudent!!.name}\" (${selectedStudent!!.studentCode})?", color = EduTextSecondary) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteStudent(classId, selectedStudent!!.id); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EduRed)
                ) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Hủy") } },
            containerColor = EduSurface,
            titleContentColor = EduTextPrimary,
            textContentColor = EduTextSecondary
        )
    }

    // Dialog đăng ký khuôn mặt
    if (showFaceDialog && selectedStudent != null) {
        FaceRegistrationDialog(
            student = selectedStudent!!,
            classId = classId,
            onDismiss = { showFaceDialog = false },
            onEmbeddingSaved = { embedding ->
                viewModel.saveFaceEmbedding(classId, selectedStudent!!.id, embedding)
                showFaceDialog = false
            }
        )
    }
}

@Composable
fun StudentManagementItem(
    student: Student,
    onRegisterFace: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = EduTextSecondary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(student.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = EduTextPrimary)
                    Text(student.studentCode, fontSize = 12.sp, color = EduTextSecondary)
                }
                if (student.faceEmbedding.isNotEmpty()) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Đã đăng ký khuôn mặt",
                        tint = EduGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Nút đăng ký khuôn mặt
                OutlinedButton(
                    onClick = onRegisterFace,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Face, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (student.faceEmbedding.isNotEmpty()) "Cập nhật KM" else "Đăng ký KM", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                // Nút sửa
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "Sửa", tint = EduBlue, modifier = Modifier.size(18.dp))
                }
                // Nút xóa
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Xóa", tint = EduRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun StudentFormDialog(
    title: String,
    initialName: String,
    initialCode: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var studentCode by remember { mutableStateOf(initialCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = EduTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Họ và tên") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EduBlue,
                        unfocusedBorderColor = EduBorder
                    )
                )
                OutlinedTextField(
                    value = studentCode,
                    onValueChange = { studentCode = it },
                    label = { Text("Mã sinh viên (duy nhất)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Mã sinh viên phải là duy nhất trong lớp") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EduBlue,
                        unfocusedBorderColor = EduBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, studentCode) },
                enabled = name.isNotBlank() && studentCode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
        containerColor = EduSurface,
        titleContentColor = EduTextPrimary,
        textContentColor = EduTextSecondary
    )
}

@Composable
fun AddStudentDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    StudentFormDialog("Thêm sinh viên", "", "", "Thêm", onDismiss, onConfirm)
}

@Composable
fun FaceRegistrationDialog(
    student: Student,
    classId: String,
    onDismiss: () -> Unit,
    onEmbeddingSaved: (FloatArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    val faceHelper = remember { FaceRecognitionHelper(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val capturedEmbeddings = remember { mutableStateListOf<FloatArray>() }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var borderColor by remember { mutableStateOf(Color.White) }
    var detectedFaceRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var isRegistrationComplete by remember { mutableStateOf(false) }
    var showFlash by remember { mutableStateOf(false) }  // Flash animation

    // Threshold cho chất lượng mặt và kích thước tối thiểu
    val FACE_SIZE_THRESHOLD = 0.3f  // Mặt phải chiếm ít nhất 30% khung hình
    val MIN_GOOD_CAPTURES = 3  // Cần 3 ảnh tốt để hoàn tất
    val AUTO_SAVE_DELAY_MS = 500L  // Delay trước khi tự động lưu

    DisposableEffect(Unit) {
        onDispose {
            faceHelper.close()
            cameraExecutor.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = EduSurface),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Đăng ký khuôn mặt",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = EduTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        student.name,
                        color = EduBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Camera preview box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .border(2.dp, EduBlue, RoundedCornerShape(12.dp))
                ) {
                    if (hasCameraPermission) {
                        val cameraController = remember {
                            LifecycleCameraController(context).apply {
                                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                setImageAnalysisAnalyzer(cameraExecutor) { imageProxy ->
                                    val bitmap = faceHelper.imageProxyToBitmap(imageProxy)

                                    if (bitmap != null && !isProcessing && capturedEmbeddings.size < MIN_GOOD_CAPTURES) {
                                        imageProxy.close()
                                        scope.launch {
                                            try {
                                                val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                                                val faces = faceHelper.getDetector().process(inputImage).await()
                                                val face = faces.firstOrNull()

                                                if (face != null) {
                                                    detectedFaceRect = face.boundingBox

                                                    // Kiểm tra kích thước mặt
                                                    val faceSize = (face.boundingBox.width() * face.boundingBox.height()) /
                                                                  (bitmap.width * bitmap.height).toFloat()

                                                    if (faceSize >= FACE_SIZE_THRESHOLD) {
                                                        val cropped = faceHelper.cropFace(bitmap, face)
                                                        if (cropped != null) {
                                                            // Kiểm tra chất lượng ảnh
                                                            val isGoodQuality = faceHelper.checkFaceQuality(cropped)

                                                            if (isGoodQuality) {
                                                                borderColor = Color(0xFF4CAF50)  // Xanh
                                                                statusText = "✅ Mặt hợp lệ"

                                                                // Auto-capture nếu mặt tốt
                                                                isProcessing = true
                                                                showFlash = true  // Bật flash
                                                                val embedding = faceHelper.extractEmbedding(cropped)
                                                                capturedEmbeddings.add(embedding)
                                                                statusText = "✓ Đã chụp (${capturedEmbeddings.size}/$MIN_GOOD_CAPTURES)"

                                                                // Auto-save khi đủ ảnh
                                                                if (capturedEmbeddings.size >= MIN_GOOD_CAPTURES) {
                                                                    isRegistrationComplete = true
                                                                    statusText = "✓ Đã đăng ký khuôn mặt thành công!"
                                                                    borderColor = Color(0xFF4CAF50)

                                                                    // Delay trước khi auto-save
                                                                    kotlinx.coroutines.delay(AUTO_SAVE_DELAY_MS)
                                                                    val avgEmbedding = averageEmbeddings(capturedEmbeddings)
                                                                    onEmbeddingSaved(avgEmbedding)
                                                                }

                                                                // Delay trước frame tiếp theo để tránh duplicate
                                                                kotlinx.coroutines.delay(200L)  // Reduce để tắt flash nhanh hơn
                                                                showFlash = false  // Tắt flash
                                                                kotlinx.coroutines.delay(600L)
                                                                isProcessing = false
                                                            } else {
                                                                borderColor = Color(0xFFF44336)  // Đỏ
                                                                statusText = "❌ Ảnh mờ | Điều chỉnh ánh sáng & vị trí"
                                                            }
                                                        } else {
                                                            borderColor = Color(0xFFF44336)
                                                            statusText = "❌ Mặt mờ | Lau sạch camera & đứng cách 30-50cm"
                                                        }
                                                    } else {
                                                        borderColor = Color(0xFFFFC107)  // Vàng - mặt quá nhỏ
                                                        statusText = "⚠️ Mặt quá nhỏ | Di chuyển gần camera hơn"
                                                    }
                                                } else {
                                                    borderColor = Color.White
                                                    detectedFaceRect = null
                                                    statusText = "🔍 Tìm khuôn mặt | Hướng mặt thẳng vào camera"
                                                }
                                            } catch (e: Exception) {
                                                borderColor = Color(0xFFF44336)
                                                statusText = "❌ Lỗi: ${e.message?.take(20)}"
                                            }
                                        }
                                    } else {
                                        imageProxy.close()
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
                                PreviewView(ctx).apply { controller = cameraController }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Guide overlay with dynamic face frame and border color
                        Box(modifier = Modifier.fillMaxSize()) {
                            // ...existing code...

                            // Flash animation khi chụp thành công
                            if (showFlash) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.7f))
                                )
                            }
                            // Vẽ frame hướng dẫn và detected face rect
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height

                                // Tính toán kích thước frame hướng dẫn
                                val guideWidth = width * 0.65f
                                val guideHeight = height * 0.75f
                                val guideLeft = (width - guideWidth) / 2
                                val guideTop = (height - guideHeight) / 2
                                val centerX = width / 2
                                val centerY = height / 2

                                // Tối nền bên ngoài frame (giúp tập trung vào khung chính)
                                drawRect(
                                    color = Color(0xAA000000),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    size = androidx.compose.ui.geometry.Size(width, guideTop),
                                    style = androidx.compose.ui.graphics.drawscope.Fill
                                )
                                drawRect(
                                    color = Color(0xAA000000),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, guideTop + guideHeight),
                                    size = androidx.compose.ui.geometry.Size(width, height - guideTop - guideHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Fill
                                )
                                drawRect(
                                    color = Color(0xAA000000),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, guideTop),
                                    size = androidx.compose.ui.geometry.Size(guideLeft, guideHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Fill
                                )
                                drawRect(
                                    color = Color(0xAA000000),
                                    topLeft = androidx.compose.ui.geometry.Offset(guideLeft + guideWidth, guideTop),
                                    size = androidx.compose.ui.geometry.Size(width - guideLeft - guideWidth, guideHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Fill
                                )

                                // Vẽ viền frame chính (hình mặt - oval)
                                val faceOvalWidth = guideWidth * 0.8f
                                val faceOvalHeight = guideHeight * 0.85f
                                val faceOvalLeft = guideLeft + (guideWidth - faceOvalWidth) / 2
                                val faceOvalTop = guideTop + (guideHeight - faceOvalHeight) / 2

                                // Viền ngoài (đậm hơn, màu động)
                                drawOval(
                                    color = borderColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(faceOvalLeft, faceOvalTop),
                                    size = androidx.compose.ui.geometry.Size(faceOvalWidth, faceOvalHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                                )

                                // Viền trong (mỏng hơn, nhẹ hơn)
                                drawOval(
                                    color = borderColor.copy(alpha = 0.5f),
                                    topLeft = androidx.compose.ui.geometry.Offset(faceOvalLeft + 8f, faceOvalTop + 8f),
                                    size = androidx.compose.ui.geometry.Size(faceOvalWidth - 16f, faceOvalHeight - 16f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                )

                                // Vẽ điểm hướng dẫn cho mắt trái
                                val leftEyeX = faceOvalLeft + faceOvalWidth * 0.3f
                                val eyeY = faceOvalTop + faceOvalHeight * 0.35f
                                drawCircle(
                                    color = borderColor.copy(alpha = 0.6f),
                                    radius = 4f,
                                    center = androidx.compose.ui.geometry.Offset(leftEyeX, eyeY)
                                )

                                // Vẽ điểm hướng dẫn cho mắt phải
                                val rightEyeX = faceOvalLeft + faceOvalWidth * 0.7f
                                drawCircle(
                                    color = borderColor.copy(alpha = 0.6f),
                                    radius = 4f,
                                    center = androidx.compose.ui.geometry.Offset(rightEyeX, eyeY)
                                )

                                // Vẽ điểm hướng dẫn cho mũi
                                val noseX = centerX
                                val noseY = faceOvalTop + faceOvalHeight * 0.5f
                                drawCircle(
                                    color = borderColor.copy(alpha = 0.6f),
                                    radius = 3f,
                                    center = androidx.compose.ui.geometry.Offset(noseX, noseY)
                                )

                                // Vẽ đường hướng dẫn (ngang qua mắt)
                                drawLine(
                                    color = borderColor.copy(alpha = 0.3f),
                                    start = androidx.compose.ui.geometry.Offset(guideLeft + 10f, eyeY),
                                    end = androidx.compose.ui.geometry.Offset(guideLeft + guideWidth - 10f, eyeY),
                                    strokeWidth = 1f
                                )

                                // Vẽ đường hướng dẫn (dọc qua mũi)
                                drawLine(
                                    color = borderColor.copy(alpha = 0.3f),
                                    start = androidx.compose.ui.geometry.Offset(noseX, guideTop + 10f),
                                    end = androidx.compose.ui.geometry.Offset(noseX, guideTop + guideHeight - 10f),
                                    strokeWidth = 1f
                                )

                                // Vẽ 4 góc để định hướng
                                val cornerSize = 25f
                                val cornerWidth = 3f

                                // Góc trái trên
                                drawLine(
                                    color = borderColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(faceOvalLeft - 10f, faceOvalTop),
                                    end = androidx.compose.ui.geometry.Offset(faceOvalLeft - 10f, faceOvalTop + cornerSize),
                                    strokeWidth = cornerWidth
                                )
                                drawLine(
                                    color = borderColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(faceOvalLeft - 10f, faceOvalTop),
                                    end = androidx.compose.ui.geometry.Offset(faceOvalLeft - 10f + cornerSize, faceOvalTop),
                                    strokeWidth = cornerWidth
                                )

                                // Góc phải trên
                                drawLine(
                                    color = borderColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(faceOvalLeft + faceOvalWidth + 10f, faceOvalTop),
                                    end = androidx.compose.ui.geometry.Offset(faceOvalLeft + faceOvalWidth + 10f, faceOvalTop + cornerSize),
                                    strokeWidth = cornerWidth
                                )
                                drawLine(
                                    color = borderColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(faceOvalLeft + faceOvalWidth + 10f, faceOvalTop),
                                    end = androidx.compose.ui.geometry.Offset(faceOvalLeft + faceOvalWidth + 10f - cornerSize, faceOvalTop),
                                    strokeWidth = cornerWidth
                                )

                                // Góc trái dưới
                                drawLine(
                                    color = borderColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(faceOvalLeft - 10f, faceOvalTop + faceOvalHeight),
                                    end = androidx.compose.ui.geometry.Offset(faceOvalLeft - 10f, faceOvalTop + faceOvalHeight - cornerSize),
                                    strokeWidth = cornerWidth
                                )
                                drawLine(
                                    color = borderColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(faceOvalLeft - 10f, faceOvalTop + faceOvalHeight),
                                    end = androidx.compose.ui.geometry.Offset(faceOvalLeft - 10f + cornerSize, faceOvalTop + faceOvalHeight),
                                    strokeWidth = cornerWidth
                                )

                                // Góc phải dưới
                                drawLine(
                                    color = borderColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(faceOvalLeft + faceOvalWidth + 10f, faceOvalTop + faceOvalHeight),
                                    end = androidx.compose.ui.geometry.Offset(faceOvalLeft + faceOvalWidth + 10f, faceOvalTop + faceOvalHeight - cornerSize),
                                    strokeWidth = cornerWidth
                                )
                                drawLine(
                                    color = borderColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(faceOvalLeft + faceOvalWidth + 10f, faceOvalTop + faceOvalHeight),
                                    end = androidx.compose.ui.geometry.Offset(faceOvalLeft + faceOvalWidth + 10f - cornerSize, faceOvalTop + faceOvalHeight),
                                    strokeWidth = cornerWidth
                                )
                            }

                            // Status overlay - top center
                            if (statusText.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(12.dp)
                                        .background(Color(0xDD000000), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        statusText,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Progress counter - top right
                            if (capturedEmbeddings.size > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .background(borderColor.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "${capturedEmbeddings.size}/$MIN_GOOD_CAPTURES",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                    } else {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Face,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cần quyền truy cập camera", color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
                            ) {
                                Text("Cấp quyền camera")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress section
                if (capturedEmbeddings.size > 0) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { (capturedEmbeddings.size / MIN_GOOD_CAPTURES.toFloat()).coerceAtMost(1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = if (borderColor == Color(0xFF4CAF50)) Color(0xFF4CAF50) else Color(0xFFF44336),
                            trackColor = EduBorder
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${capturedEmbeddings.size}/$MIN_GOOD_CAPTURES ảnh hợp lệ",
                            fontSize = 12.sp,
                            color = EduTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        "Hướng dẫn: Đặt khuôn mặt vào khung hình để tự động đăng ký",
                        fontSize = 12.sp,
                        color = EduTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                if (!isRegistrationComplete) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EduRed)
                    ) {
                        Text("Hủy", fontWeight = FontWeight.Medium)
                    }
                } else {
                    // Hiển thị tin nhắn thành công
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "✓ Đăng ký khuôn mặt thành công!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                fontSize = 14.sp
                            )
                            Text(
                                "Hệ thống đã xác nhận đăng ký với ${capturedEmbeddings.size} ảnh tốt",
                                fontSize = 12.sp,
                                color = EduTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tính trung bình cộng của nhiều embedding vectors
 */
fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
    if (embeddings.isEmpty()) return FloatArray(512)
    val size = embeddings[0].size
    val result = FloatArray(size)
    for (emb in embeddings) {
        for (i in 0 until size) result[i] += emb[i]
    }
    for (i in 0 until size) result[i] /= embeddings.size
    // L2 normalize
    var norm = 0f
    for (v in result) norm += v * v
    norm = kotlin.math.sqrt(norm)
    return if (norm == 0f) result else FloatArray(size) { result[it] / norm }
}


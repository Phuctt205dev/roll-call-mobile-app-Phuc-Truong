package com.example.roll_call.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.omr.OmrScanResult
import com.example.roll_call.ui.theme.EduBackground
import com.example.roll_call.ui.theme.EduGreen
import com.example.roll_call.ui.theme.EduGreenLight
import com.example.roll_call.ui.theme.EduOrange
import com.example.roll_call.ui.theme.EduOrangeLight
import com.example.roll_call.ui.theme.EduRed
import com.example.roll_call.ui.theme.EduRedLight
import com.example.roll_call.ui.theme.EduSurface
import com.example.roll_call.ui.theme.EduTextPrimary
import com.example.roll_call.ui.theme.EduTextSecondary
import com.example.roll_call.ui.viewmodel.OmrRealtimeResult
import com.example.roll_call.ui.viewmodel.OmrScannerViewModel
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OMRScannerScreen(
    classId: String,
    className: String,
    examId: String,
    classExamInstanceId: String?,
    printVersionId: String?,
    onBack: () -> Unit,
    onOpenReview: (OmrScanResult) -> Unit,
    viewModel: OmrScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var localError by remember { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(classId, examId, classExamInstanceId, printVersionId) {
        viewModel.initialize(classId, examId, classExamInstanceId, printVersionId)
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
                if (!viewModel.shouldAnalyzeRealtimeFrame()) {
                    imageProxy.close()
                    return@setImageAnalysisAnalyzer
                }
                val bitmap = imageProxyToBitmap(imageProxy)
                imageProxy.close()
                if (bitmap != null) {
                    viewModel.processRealtimeBitmap(bitmap, context.cacheDir.resolve("omr_debug"))
                }
            }
        }
    }

    fun captureHighQualityFrame() {
        localError = null
        cameraController.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    if (bitmap == null) {
                        val message = "Kh\u00f4ng \u0111\u1ecdc \u0111\u01b0\u1ee3c \u1ea3nh t\u1eeb camera"
                        localError = message
                    } else {
                        viewModel.processCapturedBitmap(bitmap, context.cacheDir.resolve("omr_debug"))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    val message = exception.message ?: "Kh\u00f4ng ch\u1ee5p \u0111\u01b0\u1ee3c \u1ea3nh"
                    localError = message
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        if (hasCameraPermission) cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            analysisExecutor.shutdown()
        }
    }

    val statusColor = when {
        uiState.error != null || localError != null -> EduRed
        uiState.isSheetInFrame -> EduGreen
        else -> EduOrange
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Qu\u00e9t phi\u1ebfu OMR", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { PreviewView(it).apply { controller = cameraController } },
                    modifier = Modifier.fillMaxSize()
                )
                OmrGuideOverlay(statusColor = if (uiState.isSheetInFrame) EduGreen else EduOrange)
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("C\u1ea7n quy\u1ec1n camera \u0111\u1ec3 qu\u00e9t phi\u1ebfu", color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("C\u1ea5p quy\u1ec1n camera")
                    }
                }
            }

            StatusPill(
                text = uiState.error ?: localError ?: uiState.realtimeStatus,
                color = statusColor,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            )

            if (uiState.isProcessing || uiState.isInitializing) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (uiState.isInitializing) "\u0110ang n\u1ea1p d\u1eef li\u1ec7u OMR..." else "\u0110ang x\u1eed l\u00fd phi\u1ebfu...",
                        color = Color.White
                    )
                }
            }

            uiState.latestResult?.let { result ->
                RealtimeResultCard(
                    result = result,
                    onClick = { onOpenReview(result.scanResult) },
                    onSave = { viewModel.saveLatestGrade() },
                    isSaving = uiState.isSavingGrade,
                    isSaved = uiState.savedGradeId != null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 92.dp)
                )
            }

            Button(
                onClick = {
                    localError = null
                    if (uiState.isScanPaused || uiState.latestResult != null) {
                        viewModel.resumeScanning()
                    } else {
                        captureHighQualityFrame()
                    }
                },
                enabled = hasCameraPermission && !uiState.isProcessing && !uiState.isInitializing,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isScanPaused || uiState.latestResult != null) "Qu\u00e9t ti\u1ebfp" else "Ch\u1ee5p ngay")
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RealtimeResultCard(
    result: OmrRealtimeResult,
    onClick: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
    isSaved: Boolean,
    modifier: Modifier = Modifier
) {
    val grade = result.grade
    val title = result.student?.name ?: result.message
    val scoreText = grade?.let { "${formatScore(it.score)}/${formatScore(it.maxScore)}" }

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = EduTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            if (scoreText != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = scoreText,
                    color = EduGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (result.isComplete && grade != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onSave,
                    enabled = !isSaving && !isSaved,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = EduGreen
                        )
                    } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "L\u01b0u \u0111i\u1ec3m",
                            tint = if (isSaved) EduGreen else EduTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OmrGuideOverlay(statusColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val guideWidth = size.width * 0.86f
        val guideHeight = guideWidth * 0.684f
        val left = (size.width - guideWidth) / 2f
        val top = (size.height - guideHeight) / 2f
        val shade = Color.Black.copy(alpha = 0.42f)

        drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, top))
        drawRect(shade, topLeft = Offset(0f, top + guideHeight), size = Size(size.width, size.height - top - guideHeight))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, guideHeight))
        drawRect(shade, topLeft = Offset(left + guideWidth, top), size = Size(size.width - left - guideWidth, guideHeight))

        drawRect(
            color = statusColor,
            topLeft = Offset(left, top),
            size = Size(guideWidth, guideHeight),
            style = Stroke(width = 5f)
        )

        val corner = 44f
        val cornerStroke = 9f
        listOf(
            Offset(left, top),
            Offset(left + guideWidth, top),
            Offset(left, top + guideHeight),
            Offset(left + guideWidth, top + guideHeight)
        ).forEachIndexed { index, p ->
            val sx = if (index == 1 || index == 3) -1f else 1f
            val sy = if (index >= 2) -1f else 1f
            drawLine(statusColor, p, Offset(p.x + corner * sx, p.y), cornerStroke)
            drawLine(statusColor, p, Offset(p.x, p.y + corner * sy), cornerStroke)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            "\u0110\u1eb7t 4 \u00f4 vu\u00f4ng \u0111en tr\u00f9ng 4 g\u00f3c khung",
            modifier = Modifier
                .padding(top = 68.dp)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val bitmap = when (imageProxy.format) {
            ImageFormat.JPEG -> {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            PixelFormat.RGBA_8888 -> {
                val plane = imageProxy.planes[0]
                val buffer = plane.buffer
                Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888).apply {
                    copyPixelsFromBuffer(buffer)
                }
            }
            ImageFormat.YUV_420_888 -> {
                val nv21 = yuv420ToNv21(imageProxy)
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
                val bytes = out.toByteArray()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> null
        } ?: return null

        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation == 0) bitmap else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    } catch (_: Exception) {
        null
    }
}

private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
    val width = imageProxy.width
    val height = imageProxy.height
    val ySize = width * height
    val nv21 = ByteArray(ySize + width * height / 2)

    val yPlane = imageProxy.planes[0]
    val uPlane = imageProxy.planes[1]
    val vPlane = imageProxy.planes[2]
    val yBuffer = yPlane.buffer.duplicate()
    val uBuffer = uPlane.buffer.duplicate()
    val vBuffer = vPlane.buffer.duplicate()

    var outputOffset = 0
    for (row in 0 until height) {
        val rowOffset = row * yPlane.rowStride
        for (col in 0 until width) {
            nv21[outputOffset++] = yBuffer.get(rowOffset + col * yPlane.pixelStride)
        }
    }

    var uvOutputOffset = ySize
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    for (row in 0 until chromaHeight) {
        val uRowOffset = row * uPlane.rowStride
        val vRowOffset = row * vPlane.rowStride
        for (col in 0 until chromaWidth) {
            nv21[uvOutputOffset++] = vBuffer.get(vRowOffset + col * vPlane.pixelStride)
            nv21[uvOutputOffset++] = uBuffer.get(uRowOffset + col * uPlane.pixelStride)
        }
    }

    return nv21
}

private fun formatScore(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

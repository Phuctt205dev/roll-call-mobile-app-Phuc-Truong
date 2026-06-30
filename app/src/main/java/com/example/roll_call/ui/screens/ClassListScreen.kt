package com.example.roll_call.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.ClassRoom
import com.example.roll_call.ui.theme.EduBackground
import com.example.roll_call.ui.theme.EduBlue
import com.example.roll_call.ui.theme.EduBlueLight
import com.example.roll_call.ui.theme.EduBorder
import com.example.roll_call.ui.theme.EduGreen
import com.example.roll_call.ui.theme.EduRed
import com.example.roll_call.ui.theme.EduSurface
import com.example.roll_call.ui.theme.EduTextMuted
import com.example.roll_call.ui.theme.EduTextPrimary
import com.example.roll_call.ui.theme.EduTextSecondary
import com.example.roll_call.ui.viewmodel.ClassViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassListScreen(
    onClassClick: (String, String) -> Unit,
    onOmrClick: (String, String) -> Unit,
    onLogout: () -> Unit,
    onRegisterTeacherFace: () -> Unit,
    viewModel: ClassViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Đăng xuất", color = EduTextPrimary) },
            text = { Text("Bạn có chắc muốn đăng xuất?", color = EduTextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(); onLogout() }) {
                    Text("Đăng xuất", color = EduRed)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Hủy") } },
            containerColor = EduSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(EduBlue),
                            contentAlignment = Alignment.Center
                        ) { Text("RC", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.padding(top = 2.dp)) {
                            Text("EduManage", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EduTextPrimary)
                            Text("Điểm danh & kiểm tra", fontSize = 11.sp, color = EduTextSecondary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRegisterTeacherFace) {
                        Icon(Icons.Default.Face, "Đăng ký khuôn mặt", tint = EduTextSecondary)
                    }
                    IconButton(onClick = { viewModel.loadClasses() }) {
                        Icon(Icons.Default.Refresh, "Tải lại", tint = EduTextSecondary)
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Đăng xuất", tint = EduTextSecondary)
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
                uiState.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Lỗi: ${uiState.error}", color = EduRed)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadClasses() }, colors = ButtonDefaults.buttonColors(containerColor = EduBlue)) {
                        Text("Thử lại")
                    }
                }
                uiState.classes.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(48.dp), tint = EduTextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("Chưa có lớp học nào", color = EduTextSecondary)
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lớp học", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EduTextPrimary)
                                Text("Quản lý điểm danh và chấm bài", fontSize = 13.sp, color = EduTextSecondary)
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = EduBlueLight) {
                                Text(
                                    "${uiState.classes.size} lớp",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = EduBlue
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    items(uiState.classes) { classRoom ->
                        ClassCard(
                            classRoom = classRoom,
                            onClick = { onClassClick(classRoom.id, classRoom.name) },
                            onOmrClick = { onOmrClick(classRoom.id, classRoom.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClassCard(
    classRoom: ClassRoom,
    onClick: () -> Unit,
    onOmrClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (classRoom.classCode.isNotBlank()) classRoom.classCode else classRoom.id,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = EduTextPrimary
                )
                Surface(shape = RoundedCornerShape(16.dp), color = EduBlueLight) {
                    Text(
                        "${classRoom.studentCount} SV",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = EduBlue
                    )
                }
            }

            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(classRoom.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = EduTextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(classRoom.subject, fontSize = 12.sp, color = EduTextSecondary)
                }
                Icon(Icons.Default.ChevronRight, null, tint = EduTextMuted, modifier = Modifier.size(18.dp))
            }

            HorizontalDivider(color = EduBorder, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CheckCircle, null, tint = EduGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Điểm danh", fontSize = 12.sp, color = EduTextSecondary)
                }
                OutlinedButton(
                    onClick = onOmrClick,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Description, null, tint = EduBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chấm OMR", fontSize = 12.sp)
                }
            }
        }
    }
}


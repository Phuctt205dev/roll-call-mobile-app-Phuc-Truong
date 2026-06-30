package com.example.roll_call.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.ui.theme.*
import com.example.roll_call.ui.viewmodel.LoginUiState
import com.example.roll_call.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
            viewModel.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EduBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            // Logo + Brand
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(EduBlue, EduBlueMid))),
                contentAlignment = Alignment.Center
            ) {
                Text("📋", fontSize = 32.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "EduManage",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = EduTextPrimary
            )
            Text(
                "Hệ thống điểm danh khuôn mặt",
                fontSize = 14.sp,
                color = EduTextSecondary,
                modifier = Modifier.padding(bottom = 36.dp)
            )

            // Card đăng nhập
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EduSurface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Đăng nhập", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = EduTextPrimary)
                    Text("Dành cho giáo viên", fontSize = 13.sp, color = EduTextSecondary)
                    Spacer(modifier = Modifier.height(24.dp))

                    // Email
                    Text("Email", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = EduTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("teacher@example.com", color = EduTextMuted) },
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = EduTextSecondary, modifier = Modifier.size(18.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder,
                            focusedContainerColor = EduSurface,
                            unfocusedContainerColor = EduSurface,
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password
                    Text("Mật khẩu", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = EduTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("••••••••", color = EduTextMuted) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = EduTextSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = EduTextSecondary, modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduBlue,
                            unfocusedBorderColor = EduBorder,
                            focusedContainerColor = EduSurface,
                            unfocusedContainerColor = EduSurface,
                        )
                    )

                    // Error
                    if (uiState is LoginUiState.Error) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(EduRedLight)
                                .padding(12.dp)
                        ) {
                            Text("⚠️ ${(uiState as LoginUiState.Error).message}", fontSize = 13.sp, color = EduRed)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Button
                    Button(
                        onClick = { viewModel.login(email, password) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = email.isNotBlank() && password.isNotBlank() && uiState !is LoginUiState.Loading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
                    ) {
                        if (uiState is LoginUiState.Loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Đăng nhập", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

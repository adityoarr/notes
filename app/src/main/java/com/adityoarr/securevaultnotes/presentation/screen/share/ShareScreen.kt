package com.adityoarr.securevaultnotes.presentation.screen.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityoarr.securevaultnotes.presentation.viewmodel.ShareViewModel

@Composable
fun SharePasswordDialog(
    viewModel: ShareViewModel,
    noteCount: Int,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMismatch by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!uiState.isProcessing) onDismiss() },
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                if (noteCount > 1) "Share $noteCount Catatan" else "Share Catatan",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Penerima WAJIB memasukkan password ini untuk membuka file .securevault.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordMismatch = false },
                    label = { Text("Password Sharing", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; passwordMismatch = false },
                    label = { Text("Konfirmasi Password", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = passwordMismatch,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (passwordMismatch) Color.Red else Color.White,
                        unfocusedBorderColor = if (passwordMismatch) Color.Red else Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (passwordMismatch) {
                    Text("Password tidak cocok.", color = Color.Red, fontSize = 12.sp)
                }

                // Progress indicator saat enkripsi
                if (uiState.isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { uiState.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.step,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error!!,
                        color = Color.Red,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password != confirmPassword) {
                        passwordMismatch = true
                    } else if (password.length < 6) {
                        // Optional: enforce minimum password length
                    } else {
                        onConfirm(password.toCharArray())
                    }
                },
                enabled = !uiState.isProcessing && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text("ENKRIPSI & SHARE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !uiState.isProcessing
            ) {
                Text("BATAL", color = Color.Gray)
            }
        }
    )
}
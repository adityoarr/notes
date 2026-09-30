package com.adityoarr.securevaultnotes.presentation.screen.incoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityoarr.securevaultnotes.presentation.viewmodel.ShareViewModel

@Composable
fun IncomingShareScreen(
    viewModel: ShareViewModel,
    fileName: String,
    onDecryptSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var password by remember { mutableStateOf("") }

    LaunchedEffect(uiState.progress) {
        if (uiState.progress == 1f && !uiState.isProcessing && uiState.error == null) {
            onDecryptSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "FILE TERENKRIPSI DITERIMA",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = fileName,
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Masukkan password untuk mendekripsi file ini.",
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password Dekripsi", color = Color.Gray) },
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

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error!!,
                    color = Color.Red,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isProcessing) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.step, color = Color.Gray, fontSize = 12.sp)
            } else {
                Button(
                    onClick = {
                        // Logic untuk mendapatkan File dari URI dan memanggil importSecureVaultFile
                        // viewModel.importSecureVaultFile(file, password.toCharArray()) { notes -> ... }
                    },
                    enabled = password.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("DEKRIPSI & IMPOR", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onCancel) {
                Text("BATAL", color = Color.Gray)
            }
        }
    }
}
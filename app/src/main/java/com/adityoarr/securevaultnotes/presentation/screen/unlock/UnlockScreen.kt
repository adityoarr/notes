package com.adityoarr.securevaultnotes.presentation.screen.unlock

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityoarr.securevaultnotes.presentation.viewmodel.VaultViewModel

@Composable
fun UnlockScreen(
    viewModel: VaultViewModel,
    onUnlocked: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var password by remember { mutableStateOf("") }

    // Trigger navigation saat berhasil unlock
    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
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
                text = "SECURE VAULT",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "100% Offline • Zero Cloud • Zero Logs",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Masukkan Password", color = Color.Gray) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (password.isNotEmpty()) {
                        viewModel.unlockVault(password.toCharArray())
                    }
                },
                enabled = !uiState.isLoading && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("BUKA VAULT", fontWeight = FontWeight.Bold)
                }
            }
        }

        // First Time Warning Dialog
        if (uiState.showFirstTimeWarning) {
            FirstTimeWarningDialog(
                onDismiss = { /* Hide dialog, lanjut ke setup */ }
            )
        }
    }
}

@Composable
fun FirstTimeWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Tidak bisa di-dismiss dengan klik luar */ },
        containerColor = Color(0xFF1A1A1A),
        titleContentColor = Color.Red,
        textContentColor = Color.White,
        title = {
            Text(
                "PERINGATAN KERAS",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                "TIDAK ADA FITUR LUPA PASSWORD.\n\n" +
                        "Data Anda dienkripsi dengan kunci yang hanya bisa diturunkan dari password ini. " +
                        "Jika Anda melupakan password, DATA AKAN HILANG PERMANEN DAN TIDAK BISA DIPULIHKAN.\n\n" +
                        "Aplikasi ini 100% Offline. Tidak ada server, tidak ada recovery, tidak ada backdoor.",
                fontSize = 14.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("SAYA MENGERTI RISIKONYA", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}
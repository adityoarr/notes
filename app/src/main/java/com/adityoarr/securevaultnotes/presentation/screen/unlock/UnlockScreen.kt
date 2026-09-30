package com.adityoarr.securevaultnotes.presentation.screen.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun UnlockScreen(viewModel: VaultViewModel, onUnlocked: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var password by remember { mutableStateOf("") }
    var showFirstTimeWarning by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
    }

    LaunchedEffect(Unit) {
        if (!uiState.vaultExists) showFirstTimeWarning = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SECURE VAULT",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text("100% Offline • Zero Cloud • Zero Logs", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = Color.Gray) },
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

        if (uiState.unlockError != null) {
            Spacer(Modifier.height(8.dp))
            Text(uiState.unlockError!!, color = Color.Red, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (password.isEmpty()) return@Button
                val chars = password.toCharArray()
                password = ""
                if (uiState.vaultExists) viewModel.unlockVault(chars)
                else viewModel.setupVault(chars)
            },
            enabled = !uiState.isLoading && password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = if (uiState.vaultExists) "BUKA VAULT" else "BUAT VAULT",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showFirstTimeWarning) {
        FirstTimeWarningDialog(onDismiss = { showFirstTimeWarning = false })
    }
}

@Composable
fun FirstTimeWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                "PERINGATAN KERAS",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                "TIDAK ADA FITUR LUPA PASSWORD.\n\n" +
                        "Data Anda dienkripsi dengan kunci yang hanya bisa diturunkan dari password ini. " +
                        "Jika password terlupakan, DATA HILANG PERMANEN DAN TIDAK BISA DIPULIHKAN.\n\n" +
                        "Aplikasi ini 100% offline: tidak ada server, tidak ada recovery, tidak ada backdoor.",
                color = Color.White,
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
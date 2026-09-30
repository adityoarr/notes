package com.adityoarr.securevaultnotes.presentation.screen.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adityoarr.securevaultnotes.domain.model.Note
import com.adityoarr.securevaultnotes.presentation.viewmodel.ShareViewModel
import java.io.File

@Composable
fun SharePasswordDialog(
    shareViewModel: ShareViewModel,
    notes: List<Note>,
    onDismiss: () -> Unit,
    onFileReady: (File) -> Unit
) {
    val state by shareViewModel.uiState.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    LaunchedEffect(state.readyFile) {
        state.readyFile?.let { file ->
            shareViewModel.consumeReadyFile()
            onFileReady(file)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!state.isProcessing) onDismiss() },
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                text = if (notes.size > 1) "Share ${notes.size} Catatan" else "Share 1 Catatan",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Penerima WAJIB memasukkan password ini untuk membuka file .securevault. Sampaikan password lewat jalur terpisah.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; mismatch = false },
                    label = { Text("Password Sharing", color = Color.Gray) },
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; mismatch = false },
                    label = { Text("Konfirmasi Password", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (mismatch) Color.Red else Color.White,
                        unfocusedBorderColor = if (mismatch) Color.Red else Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mismatch) Text("Password tidak cocok.", color = Color.Red, fontSize = 12.sp)
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error!!, color = Color.Red, fontSize = 13.sp)
                }
                if (state.isProcessing) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = Color.DarkGray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(state.step, color = Color.Gray, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password != confirm) {
                        mismatch = true
                    } else {
                        shareViewModel.exportSecureVault(notes, password.toCharArray())
                    }
                },
                enabled = !state.isProcessing && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) { Text("ENKRIPSI & SHARE", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isProcessing) {
                Text("BATAL", color = Color.Gray)
            }
        }
    )
}
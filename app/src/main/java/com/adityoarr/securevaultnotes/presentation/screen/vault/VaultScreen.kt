package com.adityoarr.securevaultnotes.presentation.screen.vault

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.adityoarr.securevaultnotes.domain.model.Note
import com.adityoarr.securevaultnotes.presentation.screen.share.SharePasswordDialog
import com.adityoarr.securevaultnotes.presentation.viewmodel.ShareViewModel
import com.adityoarr.securevaultnotes.presentation.viewmodel.VaultViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    shareViewModel: ShareViewModel,
    onAddNote: () -> Unit,
    onEditNote: (Long) -> Unit,
    onImport: () -> Unit,
    onLock: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val cacheDir = File(context.cacheDir, "incoming").apply { mkdirs() }
            val target = File(cacheDir, "import_${System.currentTimeMillis()}.securevault")
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                shareViewModel.setPendingImportFile(target)
                onImport()
            } catch (_: Exception) {
                // Gagal copy: user bisa coba pilih ulang
            }
        }
    }

    val selectedNotes = uiState.notes.filter { it.id in uiState.selectedNoteIds }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Notes", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    if (uiState.isBulkMode) {
                        TextButton(
                            onClick = { showShareDialog = true },
                            enabled = selectedNotes.isNotEmpty()
                        ) { Text("SHARE", color = Color.White, fontWeight = FontWeight.Bold) }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedNotes.isNotEmpty()
                        ) { Text("HAPUS", color = Color.Red, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { viewModel.toggleBulkMode() }) {
                            Text("BATAL", color = Color.Gray)
                        }
                    } else {
                        IconButton(onClick = { importLauncher.launch(arrayOf("application/octet-stream")) }) {
                            Icon(Icons.Default.Upload, contentDescription = "Import Vault", tint = Color.White)
                        }
                        IconButton(onClick = onLock) {
                            Icon(Icons.Default.Lock, contentDescription = "Kunci Vault", tint = Color.White)
                        }
                        TextButton(onClick = { viewModel.toggleBulkMode() }) {
                            Text("PILIH", color = Color.White)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNote, containerColor = Color.White, contentColor = Color.Black) {
                Icon(Icons.Default.Add, contentDescription = "Tambah catatan")
            }
        }
    ) { padding ->
        if (uiState.notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vault kosong.", color = Color.Gray, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ketuk + untuk menambah catatan.\nKetuk ikon Upload untuk import file .securevault.",
                        color = Color.DarkGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        isSelected = note.id in uiState.selectedNoteIds,
                        onClick = {
                            if (uiState.isBulkMode) viewModel.toggleNoteSelection(note.id)
                            else onEditNote(note.id)
                        },
                        onLongClick = {
                            if (!uiState.isBulkMode) {
                                viewModel.toggleBulkMode()
                                viewModel.toggleNoteSelection(note.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // ===== DIALOG KONFIRMASI HAPUS =====
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Hapus catatan?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${selectedNotes.size} catatan terpilih akan dihapus PERMANEN dari vault, termasuk seluruh lampiran terenkripsinya. Tindakan ini tidak dapat dibatalkan.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelectedNotes()
                }) { Text("HAPUS", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("BATAL", color = Color.Gray) }
            }
        )
    }

    // ===== DIALOG PASSWORD SHARE =====
    if (showShareDialog) {
        SharePasswordDialog(
            shareViewModel = shareViewModel,
            notes = selectedNotes,
            onDismiss = { showShareDialog = false },
            onFileReady = { file ->
                showShareDialog = false
                viewModel.toggleBulkMode()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Kirim file Secure Vault"))
            }
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF333333) else Color(0xFF1A1A1A)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = note.description,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (note.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${note.attachments.size} lampiran terenkripsi",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
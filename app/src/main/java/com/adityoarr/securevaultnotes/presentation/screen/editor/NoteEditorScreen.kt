package com.adityoarr.securevaultnotes.presentation.screen.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adityoarr.securevaultnotes.data.repository.AttachmentInput
import com.adityoarr.securevaultnotes.domain.model.Attachment
import com.adityoarr.securevaultnotes.domain.model.Note
import com.adityoarr.securevaultnotes.presentation.viewmodel.VaultViewModel
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: VaultViewModel,
    noteId: Long?,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val existing = noteId?.let { viewModel.getNoteById(it) }

    var title by remember(noteId) { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember(noteId) { mutableStateOf(existing?.description.orEmpty()) }
    var existingAttachments by remember(noteId) {
        mutableStateOf(existing?.attachments ?: emptyList())
    }
    val newUris = remember { SnapshotStateList<Uri>() }

    var viewingExisting by remember { mutableStateOf<Attachment?>(null) }
    var viewingNewUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) newUris.add(uri) }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) newUris.add(uri) }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) newUris.add(uri) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existing == null) "Catatan Baru" else "Edit Catatan",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    TextButton(
                        onClick = {
                            val now = System.currentTimeMillis()
                            val inputs = newUris.mapNotNull { uri ->
                                val mime = context.contentResolver.getType(uri)
                                    ?: "application/octet-stream"
                                context.contentResolver.openInputStream(uri)
                                    ?.let { AttachmentInput(it, mime) }
                            }
                            viewModel.saveNote(
                                Note(
                                    id = existing?.id ?: 0L,
                                    title = title.trim(),
                                    description = description,
                                    attachments = existingAttachments,
                                    createdAt = existing?.createdAt ?: now,
                                    updatedAt = now
                                ),
                                inputs
                            )
                            onBack()
                        },
                        enabled = title.isNotBlank() && !uiState.isSaving
                    ) { Text("SIMPAN", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Judul", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Deskripsi", color = Color.Gray) },
                minLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Text("Lampiran terenkripsi", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Ketuk lampiran untuk preview/putar.", color = Color.DarkGray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) { Text("+ GAMBAR", color = Color.White) }
                TextButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                    Text("+ AUDIO", color = Color.White)
                }
                TextButton(onClick = { pickVideo.launch(arrayOf("video/*")) }) {
                    Text("+ VIDEO", color = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))

            existingAttachments.forEach { att ->
                ExistingAttachmentItem(
                    attachment = att,
                    viewModel = viewModel,
                    onClick = { viewingExisting = att },
                    onRemove = {
                        viewModel.deleteAttachment(att)
                        existingAttachments = existingAttachments - att
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            newUris.forEach { uri ->
                NewAttachmentItem(
                    context = context,
                    uri = uri,
                    onClick = { viewingNewUri = uri },
                    onRemove = { newUris.remove(uri) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (viewingExisting != null || viewingNewUri != null) {
        AttachmentViewerDialog(
            attachment = viewingExisting,
            uri = viewingNewUri,
            viewModel = viewModel,
            onDismiss = {
                viewingExisting = null
                viewingNewUri = null
                viewModel.clearPlaybackFiles()
            }
        )
    }
}

// ================= ITEM LIST =================

@Composable
private fun ExistingAttachmentItem(
    attachment: Attachment,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var bitmap by remember(attachment.id) { mutableStateOf<Bitmap?>(null) }
    val isImage = attachment.mimeType.startsWith("image")
    val isVideo = attachment.mimeType.startsWith("video")

    LaunchedEffect(attachment.id) {
        if (isImage) {
            viewModel.loadAttachmentBytes(attachment) { bytes ->
                bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        } else if (isVideo) {
            // Thumbnail video: dekripsi ke temp lalu ambil frame pertama
            viewModel.preparePlaybackFile(attachment) { file ->
                bitmap = file?.let { extractVideoFrame(it.absolutePath) }
            }
        }
    }

    AttachmentRow(
        bitmap = bitmap,
        isImage = isImage,
        isVideo = isVideo,
        title = when {
            isImage -> "Gambar terenkripsi"
            isVideo -> "Video terenkripsi"
            else -> "Audio terenkripsi"
        },
        subtitle = attachment.mimeType,
        onClick = onClick,
        onRemove = onRemove
    )
}

@Composable
private fun NewAttachmentItem(
    context: Context,
    uri: Uri,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val isImage = mime.startsWith("image")
    val isVideo = mime.startsWith("video")

    LaunchedEffect(uri) {
        if (isImage) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } else if (isVideo) {
            bitmap = extractVideoFrameFromUri(context, uri)
        }
    }

    AttachmentRow(
        bitmap = bitmap,
        isImage = isImage,
        isVideo = isVideo,
        title = uri.lastPathSegment?.take(40) ?: "lampiran baru",
        subtitle = "baru • akan dienkripsi saat SIMPAN",
        onClick = onClick,
        onRemove = onRemove
    )
}

@Composable
private fun AttachmentRow(
    bitmap: Bitmap?,
    isImage: Boolean,
    isVideo: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = when {
                    isImage -> Icons.Default.Image
                    isVideo -> Icons.Default.Videocam
                    else -> Icons.Default.AudioFile
                },
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(subtitle, color = Color.DarkGray, fontSize = 12.sp)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Hapus lampiran", tint = Color.Red)
        }
    }
}

// ================= HELPERS THUMBNAIL VIDEO =================

private fun extractVideoFrame(path: String): Bitmap? {
    var retriever: MediaMetadataRetriever? = null
    return try {
        retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        retriever.getFrameAtTime(0L) // ✅ nama method yang benar
    } catch (_: Exception) {
        null
    } finally {
        try { retriever?.release() } catch (_: Exception) {} // ✅ release manual, aman API 26
    }
}

private fun extractVideoFrameFromUri(context: Context, uri: Uri): Bitmap? {
    var retriever: MediaMetadataRetriever? = null
    return try {
        retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0L)
    } catch (_: Exception) {
        null
    } finally {
        try { retriever?.release() } catch (_: Exception) {}
    }
}

// ================= VIEWER FULL-SCREEN =================

@Composable
private fun AttachmentViewerDialog(
    attachment: Attachment?,
    uri: Uri?,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val mime = attachment?.mimeType
        ?: (uri?.let { context.contentResolver.getType(it) } ?: "application/octet-stream")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            when {
                mime.startsWith("image") -> ImageViewer(attachment, uri, viewModel, onDismiss)
                mime.startsWith("audio") -> AudioViewer(attachment, uri, mime, viewModel, onDismiss)
                mime.startsWith("video") -> VideoViewer(attachment, uri, mime, viewModel, onDismiss)
                else -> {
                    Text(
                        "Tipe lampiran tidak didukung untuk preview.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    ViewerCloseButton(onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ViewerCloseButton(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopEnd
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
        }
    }
}

// ---------- GAMBAR (zoomable) ----------

@Composable
private fun ImageViewer(
    attachment: Attachment?,
    uri: Uri?,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(attachment?.id, uri) {
        val onBytes: (ByteArray?) -> Unit = { bytes ->
            bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        if (attachment != null) viewModel.loadAttachmentBytes(attachment, onBytes)
        else if (uri != null) viewModel.loadBytesFromUri(uri, onBytes)
    }

    Box(Modifier.fillMaxSize()) {
        val bmp = bitmap
        if (bmp != null) {
            ZoomableImage(bmp)
        } else {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        ViewerCloseButton(onDismiss)
    }
}

@Composable
private fun ZoomableImage(bitmap: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInputZoom(scale, offset) { newScale, newOffset ->
                scale = newScale
                offset = newOffset
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                })
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )
    }
}

private fun Modifier.pointerInputZoom(
    scale: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit
): Modifier = this.then(
    Modifier.pointerInput(scale) {
        detectTransformGestures { _, pan, zoom, _ ->
            val newScale = (scale * zoom).coerceIn(1f, 6f)
            val newOffset = if (newScale > 1f) offset + pan else Offset.Zero
            onTransform(newScale, newOffset)
        }
    }
)

// ---------- AUDIO ----------

@Composable
private fun AudioViewer(
    attachment: Attachment?,
    uri: Uri?,
    mime: String,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val onFile: (File?) -> Unit = { file ->
            if (file != null) {
                player = MediaPlayer().apply {
                    setDataSource(file.path)
                    prepare()
                    start()
                }
            }
        }
        if (attachment != null) viewModel.preparePlaybackFile(attachment, onFile)
        else if (uri != null) viewModel.preparePlaybackFileFromUri(uri, mime, onFile)
    }

    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            progress = p.currentPosition.toFloat() / p.duration.coerceAtLeast(1).toFloat()
            delay(300.milliseconds)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.AudioFile, null, tint = Color.White, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.8f).height(4.dp),
            color = Color.White,
            trackColor = Color.DarkGray
        )
        Spacer(Modifier.height(24.dp))
        IconButton(onClick = {
            player?.let { p -> if (p.isPlaying) p.pause() else p.start() }
        }) {
            Icon(
                imageVector = if (player?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
    }
    ViewerCloseButton(onDismiss)
}

// ---------- VIDEO (TextureView + MediaPlayer) ----------

@Composable
private fun VideoViewer(
    attachment: Attachment?,
    uri: Uri?,
    mime: String,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    var file by remember { mutableStateOf<File?>(null) }
    var surface by remember { mutableStateOf<Surface?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        if (attachment != null) viewModel.preparePlaybackFile(attachment) { file = it }
        else if (uri != null) viewModel.preparePlaybackFileFromUri(uri, mime) { file = it }
    }

    // Siapkan player begitu file DAN surface keduanya siap
    LaunchedEffect(file, surface) {
        val f = file ?: return@LaunchedEffect
        val s = surface ?: return@LaunchedEffect
        if (player != null) return@LaunchedEffect
        try {
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                setSurface(s)
                setOnPreparedListener { mp ->
                    durationMs = mp.duration.coerceAtLeast(1)
                    mp.start()
                    isPlaying = true
                }
                setOnErrorListener { _, what, extra ->
                    errorMsg = "Gagal memutar video (code $what/$extra)."
                    true
                }
                setOnCompletionListener { isPlaying = false }
                prepareAsync()
            }
        } catch (e: Exception) {
            errorMsg = "Gagal memutar: ${e.message}"
        }
    }

    LaunchedEffect(player, isPlaying) {
        val p = player ?: return@LaunchedEffect
        while (isPlaying) {
            position = p.currentPosition.toFloat() / p.duration.coerceAtLeast(1).toFloat()
            delay(200.milliseconds)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
            surface?.release()
            surface = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        // TextureView: render ke hierarchy view biasa, aman di Dialog/Compose
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            surface = Surface(st)
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            surface?.release()   // ✅ Surface dibebaskan sebelum dibuang
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        when {
            file == null -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            errorMsg != null -> Text(
                text = errorMsg!!,
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

        // Kontrol bawah
        if (player != null && errorMsg == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    player?.let { p ->
                        if (p.isPlaying) { p.pause(); isPlaying = false }
                        else { p.start(); isPlaying = true }
                    }
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }
                Slider(
                    value = position,
                    onValueChange = { v ->
                        position = v
                        player?.seekTo((v * durationMs).toInt())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ViewerCloseButton(onDismiss)
    }
}
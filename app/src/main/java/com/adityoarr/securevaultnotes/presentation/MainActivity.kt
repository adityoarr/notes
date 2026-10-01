package com.adityoarr.securevaultnotes.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adityoarr.securevaultnotes.core.security.RootDetector
import com.adityoarr.securevaultnotes.presentation.screen.editor.NoteEditorScreen
import com.adityoarr.securevaultnotes.presentation.screen.incoming.IncomingShareScreen
import com.adityoarr.securevaultnotes.presentation.screen.security.RootWarningScreen
import com.adityoarr.securevaultnotes.presentation.screen.unlock.UnlockScreen
import com.adityoarr.securevaultnotes.presentation.screen.vault.VaultScreen
import com.adityoarr.securevaultnotes.presentation.theme.SecureVaultTheme
import com.adityoarr.securevaultnotes.presentation.viewmodel.ShareViewModel
import com.adityoarr.securevaultnotes.presentation.viewmodel.VaultViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var rootDetector: RootDetector

    private val vaultViewModel: VaultViewModel by viewModels()
    private val shareViewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val isRooted = rootDetector.isDeviceRooted()

        // Set pending import file ke StateFlow (instance activity-scoped)
        shareViewModel.setPendingImportFile(handleIncomingIntent(intent))

        setContent {
            SecureVaultTheme {
                val navController = rememberNavController()
                val shareState by shareViewModel.uiState.collectAsState()

                if (isRooted) {
                    RootWarningScreen(onProceed = { finishAffinity() })
                } else {
                    NavHost(navController = navController, startDestination = "unlock") {
                        composable("unlock") {
                            UnlockScreen(
                                viewModel = vaultViewModel,
                                onUnlocked = {
                                    val target = if (shareState.pendingImportFile != null) "incoming" else "vault"
                                    navController.navigate(target) {
                                        popUpTo("unlock") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("vault") {
                            VaultScreen(
                                viewModel = vaultViewModel,
                                shareViewModel = shareViewModel,
                                onAddNote = { navController.navigate("editor/new") },
                                onEditNote = { id -> navController.navigate("editor/$id") },
                                onImport = { navController.navigate("incoming") },
                                onLock = {
                                    vaultViewModel.lockVault()
                                    shareViewModel.setPendingImportFile(null)
                                    navController.navigate("unlock") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("editor/{noteId}") { entry ->
                            val arg = entry.arguments?.getString("noteId")
                            val id = if (arg == null || arg == "new") null else arg.toLongOrNull()
                            NoteEditorScreen(
                                viewModel = vaultViewModel,
                                noteId = id,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("incoming") {
                            IncomingShareScreen(
                                shareViewModel = shareViewModel,
                                fileName = shareState.pendingImportFile?.name ?: "file.securevault",
                                onSuccess = {
                                    shareViewModel.setPendingImportFile(null)
                                    navController.navigate("vault") {
                                        popUpTo("unlock") { inclusive = true }
                                    }
                                },
                                onCancel = {
                                    shareViewModel.setPendingImportFile(null)
                                    navController.navigate("vault") {
                                        popUpTo("unlock") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?): File? {
        val action = intent?.action ?: return null

        // uri dideklarasikan NON-NULL: setiap cabang gagal langsung return null
        val uri: Uri = when (action) {
            Intent.ACTION_VIEW ->
                intent.data ?: return null
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: return null
            else -> return null
        }

        return try {
            val dir = File(cacheDir, "incoming").apply { mkdirs() }
            val rawName = (uri.lastPathSegment ?: "incoming.securevault")
                .substringAfterLast('/')
                .take(60)
            val target = File(dir, "${System.currentTimeMillis()}_$rawName")

            val copied = contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            if (copied == null) {
                target.delete()
                return null
            }
            target
        } catch (_: Exception) {
            null
        }
    }
}
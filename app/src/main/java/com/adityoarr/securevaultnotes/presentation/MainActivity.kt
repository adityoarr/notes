package com.adityoarr.securevaultnotes.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adityoarr.securevaultnotes.core.security.RootDetector
import com.adityoarr.securevaultnotes.presentation.screen.incoming.IncomingShareScreen
import com.adityoarr.securevaultnotes.presentation.screen.unlock.UnlockScreen
import com.adityoarr.securevaultnotes.presentation.screen.vault.VaultScreen
import com.adityoarr.securevaultnotes.presentation.screen.editor.NoteEditorScreen
import com.adityoarr.securevaultnotes.presentation.screen.security.RootWarningScreen
import com.adityoarr.securevaultnotes.presentation.theme.SecureVaultTheme
import com.adityoarr.securevaultnotes.presentation.viewmodel.ShareViewModel
import com.adityoarr.securevaultnotes.presentation.viewmodel.VaultViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var rootDetector: RootDetector

    private val vaultViewModel: VaultViewModel by viewModels()
    private val shareViewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val isRooted = rootDetector.isDeviceRooted()

        // Cek apakah app dibuka dari Intent .securevault
        val incomingFile = handleIncomingIntent(intent)

        setContent {
            SecureVaultTheme {
                val navController = rememberNavController()

                if (isRooted) {
                    RootWarningScreen(onProceed = {
                        // Opsi 1: Blokir total (paling aman)
                        finishAffinity()

                        // Opsi 2: Lanjut dengan warning (kurang aman, untuk testing)
                        // navController.navigate("unlock")
                    })
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = if (incomingFile != null) "incoming_share" else "unlock"
                    ) {
                        composable("unlock") {
                            UnlockScreen(
                                viewModel = vaultViewModel,
                                onUnlocked = { navController.navigate("vault") { popUpTo("unlock") { inclusive = true } } }
                            )
                        }
                        composable("vault") {
                            VaultScreen(
                                viewModel = vaultViewModel,
                                onAddNote = { navController.navigate("editor") },
                                onEditNote = { note -> navController.navigate("editor/${note.id}") }
                            )
                        }
                        composable("editor/{noteId}") { backStackEntry ->
                            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull()
                            // Logic untuk load note by ID
                            NoteEditorScreen(
                                viewModel = vaultViewModel,
                                existingNote = null, // Load from ViewModel
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("incoming_share") {
                            IncomingShareScreen(
                                viewModel = shareViewModel,
                                fileName = incomingFile?.name ?: "unknown.securevault",
                                onDecryptSuccess = { navController.navigate("vault") { popUpTo(0) } },
                                onCancel = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?): File? {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            if (uri != null) {
                return try {
                    // Copy file dari URI ke cache directory agar bisa dibaca
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "incoming.securevault"
                    val cacheFile = File(cacheDir, fileName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    cacheFile
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }
}
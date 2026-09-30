package com.adityoarr.securevaultnotes.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adityoarr.securevaultnotes.core.security.RootDetector
import com.adityoarr.securevaultnotes.presentation.screen.editor.NoteEditorScreen
import com.adityoarr.securevaultnotes.presentation.screen.security.RootWarningScreen
import com.adityoarr.securevaultnotes.presentation.screen.unlock.UnlockScreen
import com.adityoarr.securevaultnotes.presentation.screen.vault.VaultScreen
import com.adityoarr.securevaultnotes.presentation.theme.SecureVaultTheme
import com.adityoarr.securevaultnotes.presentation.viewmodel.VaultViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var rootDetector: RootDetector

    private val vaultViewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val isRooted = rootDetector.isDeviceRooted()

        setContent {
            SecureVaultTheme {
                val navController = rememberNavController()

                if (isRooted) {
                    RootWarningScreen(onProceed = { finishAffinity() })
                } else {
                    NavHost(navController = navController, startDestination = "unlock") {
                        composable("unlock") {
                            UnlockScreen(
                                viewModel = vaultViewModel,
                                onUnlocked = {
                                    navController.navigate("vault") {
                                        popUpTo("unlock") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("vault") {
                            VaultScreen(
                                viewModel = vaultViewModel,
                                onAddNote = { navController.navigate("editor/new") },
                                onEditNote = { id -> navController.navigate("editor/$id") }
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
                    }
                }
            }
        }
    }
}
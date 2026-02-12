package com.lifeos.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lifeos.assistant.audio.AudioRecorder
import com.lifeos.assistant.data.ModelRepository
import com.lifeos.assistant.ui.screens.MainScreen
import com.lifeos.assistant.ui.screens.SettingsScreen
import com.lifeos.assistant.ui.theme.LifeOsTheme
import kotlinx.coroutines.launch

/**
 * MainActivity - Entry point for LifeOs Voice Assistant
 *
 * Handles permissions, navigation, and core component initialization.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // Core components
    lateinit var audioRecorder: AudioRecorder
        private set
    lateinit var modelRepository: ModelRepository
        private set

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.i(TAG, "All permissions granted")
            initializeComponents()
        } else {
            Log.w(TAG, "Some permissions denied")
            // Show explanation or exit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize core components
        audioRecorder = AudioRecorder(this)
        modelRepository = (application as LifeOsApp).modelRepository

        // Check and request permissions
        checkAndRequestPermissions()

        setContent {
            LifeOsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LifeOsNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup
        lifecycleScope.launch {
            audioRecorder.release()
        }
    }

    override fun onPause() {
        super.onPause()
        // Release resources when backgrounded
        lifecycleScope.launch {
            if (::audioRecorder.isInitialized) {
                audioRecorder.stopRecording()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Audio permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Storage permissions based on SDK version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initializeComponents()
        }
    }

    private fun initializeComponents() {
        lifecycleScope.launch {
            try {
                // Pre-initialize audio recorder
                audioRecorder.initialize()
                Log.i(TAG, "Audio recorder initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing components", e)
            }
        }
    }
}

/**
 * Navigation setup for LifeOs
 */
@Composable
fun LifeOsNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val activity = context as MainActivity

    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                audioRecorder = activity.audioRecorder,
                modelRepository = activity.modelRepository,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                modelRepository = activity.modelRepository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Screen routes
 */
sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
}
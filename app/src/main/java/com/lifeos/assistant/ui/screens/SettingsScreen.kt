package com.lifeos.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifeos.assistant.LifeOsApp
import com.lifeos.assistant.data.ModelRepository
import com.lifeos.assistant.ui.components.ModelManager
import com.lifeos.assistant.ui.theme.*
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit

/**
 * SettingsScreen - Configuration UI for LifeOs
 *
 * Features:
 * - Model management
 * - Language selection
 * - Voice feedback toggle
 * - Cache management
 * - About section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modelRepository: ModelRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = (context.applicationContext as LifeOsApp).dataStore

    // Scroll state
    val scrollState = rememberScrollState()

    // Settings state
    var selectedLanguage by remember { mutableStateOf("auto") }
    var voiceFeedbackEnabled by remember { mutableStateOf(true) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Load saved preferences
    LaunchedEffect(Unit) {
        // Load from DataStore
        dataStore.data.collect { preferences ->
            selectedLanguage = preferences[LANGUAGE_KEY] ?: "auto"
            voiceFeedbackEnabled = preferences[VOICE_FEEDBACK_KEY] ?: true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = OnSurface
                )
            )
        },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model Management Section
            SectionHeader(title = "Model Management", icon = Icons.Default.Memory)
            ModelManager(modelRepository = modelRepository)

            Divider(color = SurfaceVariant)

            // Language Settings
            SectionHeader(title = "Language", icon = Icons.Default.Language)
            LanguageSelector(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { language: String ->
                    selectedLanguage = language
                    scope.launch {
                        dataStore.edit { preferences ->
                            preferences[LANGUAGE_KEY] = language
                        }
                    }
                }
            )

            Divider(color = SurfaceVariant)

            // Voice Feedback
            SectionHeader(title = "Voice Feedback", icon = Icons.Default.VolumeUp)
            VoiceFeedbackToggle(
                enabled = voiceFeedbackEnabled,
                onToggle = { enabled: Boolean ->
                    voiceFeedbackEnabled = enabled
                    scope.launch {
                        dataStore.edit { preferences ->
                            preferences[VOICE_FEEDBACK_KEY] = enabled
                        }
                    }
                }
            )

            Divider(color = SurfaceVariant)

            // Cache Management
            SectionHeader(title = "Storage", icon = Icons.Default.Storage)
            CacheManagementSection(
                onClearCache = { showClearCacheDialog = true }
            )

            Divider(color = SurfaceVariant)

            // About Section
            SectionHeader(title = "About", icon = Icons.Default.Info)
            AboutSection(
                onShowAbout = { showAboutDialog = true }
            )

            // Bottom padding
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Clear Cache Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache") },
            text = { Text("This will delete all temporary files and cached data. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            clearCache(context)
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        }
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About LifeOs") },
            text = {
                Column {
                    Text("LifeOs - Offline Voice Assistant")
                    Text("Version 1.0.0")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A privacy-focused voice assistant that works entirely offline.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Features:")
                    Text("• Offline speech recognition")
                    Text("• English and Farsi support")
                    Text("• No internet required")
                    Text("• Privacy-first design")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Section Header
 */
@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OnSurface
        )
    }
}

/**
 * Language Selector
 */
@Composable
private fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf(
        "auto" to "Auto-detect",
        "en" to "English",
        "fa" to "فارسی (Farsi)"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        languages.forEach { (code: String, name: String) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageSelected(code) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface
                )
                if (selectedLanguage == code) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Success,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Voice Feedback Toggle
 */
@Composable
private fun VoiceFeedbackToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = "Voice Feedback",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Text(
                text = if (enabled) "TTS enabled" else "TTS disabled",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Primary,
                checkedTrackColor = Primary.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * Cache Management Section
 */
@Composable
private fun CacheManagementSection(
    onClearCache: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = "Clear Cache",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Text(
                text = "Delete temporary files",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = onClearCache,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Error
            )
        ) {
            Text("Clear")
        }
    }
}

/**
 * About Section
 */
@Composable
private fun AboutSection(
    onShowAbout: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowAbout)
            .padding(vertical = 8.dp)
    ) {
        Column {
            Text(
                text = "About LifeOs",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = OnSurfaceVariant
        )
    }
}

/**
 * Clear cache files
 */
private suspend fun clearCache(context: android.content.Context) {
    try {
        // Clear app cache
        context.cacheDir.listFiles()?.forEach { it.delete() }

        // Clear external cache
        context.externalCacheDir?.listFiles()?.forEach { it.delete() }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// DataStore keys
private val LANGUAGE_KEY = stringPreferencesKey("language")
private val VOICE_FEEDBACK_KEY = booleanPreferencesKey("voice_feedback")
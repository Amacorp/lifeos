package com.lifeos.assistant.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.content.Context
import com.lifeos.assistant.audio.VoiceOption
import com.lifeos.assistant.viewmodel.MainViewModel

private val Context.dataStore by preferencesDataStore(name = "settings")

private object PreferenceKeys {
    val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
    val DARK_MODE = booleanPreferencesKey("dark_mode")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPress: () -> Unit,
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val uiState by mainViewModel.uiState.collectAsState()

    var showDeleteVoskDialog by remember { mutableStateOf(false) }
    var showDeleteLLMDialog by remember { mutableStateOf(false) }
    var showVoiceSelector by remember { mutableStateOf(false) }

    val voskPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { mainViewModel.importVoskModel(it) }
    }
    val llmPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { mainViewModel.importLLMModel(it) }
    }

    val wakeWordEnabled by context.dataStore.data
        .map { prefs: androidx.datastore.preferences.core.Preferences -> prefs[PreferenceKeys.WAKE_WORD_ENABLED] ?: true }
        .collectAsState(initial = true)
    val darkMode by context.dataStore.data
        .map { prefs: androidx.datastore.preferences.core.Preferences -> prefs[PreferenceKeys.DARK_MODE] ?: false }
        .collectAsState(initial = false)

    // Delete dialogs
    if (showDeleteVoskDialog) {
        SimpleDeleteDialog("Delete Speech Model", "Delete Vosk model?",
            { mainViewModel.deleteVoskModel(); showDeleteVoskDialog = false },
            { showDeleteVoskDialog = false })
    }
    if (showDeleteLLMDialog) {
        SimpleDeleteDialog("Delete AI Model", "Delete AI model?",
            { mainViewModel.deleteLLMModel(); showDeleteLLMDialog = false },
            { showDeleteLLMDialog = false })
    }

    // Voice selector
    if (showVoiceSelector) {
        AlertDialog(
            onDismissRequest = { showVoiceSelector = false },
            title = { Text("Select Voice") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.availableVoices.forEach { voice: VoiceOption ->
                        val sel = voice.displayName == uiState.currentVoiceName
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { mainViewModel.setVoice(voice); showVoiceSelector = false }
                                .then(
                                    if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    else Modifier
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(voice.displayName, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                                    Text("Pitch: ${"%.1f".format(voice.pitch)}x  Speed: ${"%.1f".format(voice.speed)}x",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (sel) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVoiceSelector = false }) { Text("Close") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBackPress) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== VOICE SETTINGS =====
            SectionHeader(Icons.Default.RecordVoiceOver, "Voice Settings", MaterialTheme.colorScheme.secondary)

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current: ${uiState.currentVoiceName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Languages: ${uiState.installedTTSLanguages.joinToString(", ").ifEmpty { "Loading..." }}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("✅ Farsi supported (via transliteration if native TTS unavailable)",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Button(onClick = { showVoiceSelector = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Change Voice")
            }

            SliderCard("Pitch", uiState.currentPitch, { mainViewModel.setCustomPitch(it) }, 0.3f..2.0f, 16, "Deep", "High")
            SliderCard("Speed", uiState.currentSpeed, { mainViewModel.setCustomSpeed(it) }, 0.3f..2.5f, 21, "Slow", "Fast")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { mainViewModel.testVoice() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Voice", fontSize = 13.sp)
                }
                OutlinedButton(onClick = { mainViewModel.refreshVoices() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", fontSize = 13.sp)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // ===== SPEECH MODEL =====
            SectionHeader(Icons.Default.Mic, "Speech Model (Vosk)", MaterialTheme.colorScheme.primary)

            ModelStatusCard(uiState.isModelLoaded, uiState.isModelLoading, uiState.voskModelInfo,
                if (uiState.isModelLoading) uiState.statusMessage else null, "Speech Ready", "No Speech Model")

            Button(onClick = { voskPicker.launch("application/zip") }, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isModelLoading) {
                Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse Vosk Model (.zip)")
            }

            OutlinedButton(onClick = { showDeleteVoskDialog = true }, modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isModelLoaded,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Speech Model")
            }

            InstructionCard("Vosk speech models (alphacephei.com/vosk/models):",
                "English:\n• vosk-model-small-en-us-0.15 (40MB)\n\n" +
                        "Farsi (Persian):\n• vosk-model-small-fa-0.4 (40MB)\n• vosk-model-small-fa-0.5 (40MB)\n\n" +
                        "⚠️ Only one model at a time.\nSwitch between English and Farsi by importing different models.")

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // ===== AI BRAIN =====
            SectionHeader(Icons.Default.Psychology, "AI Brain (GGUF)", MaterialTheme.colorScheme.tertiary)

            ModelStatusCard(uiState.isLLMLoaded, uiState.isLLMLoading, uiState.llmModelInfo,
                if (uiState.isLLMLoading) uiState.statusMessage else null, "AI Brain Ready", "No AI Brain")

            Button(onClick = { llmPicker.launch("*/*") }, modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLLMLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse AI Model (.gguf)")
            }

            OutlinedButton(onClick = { showDeleteLLMDialog = true }, modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isLLMLoaded,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete AI Model")
            }

            InstructionCard("Recommended AI models (Farsi + English):",
                "🏆 Qwen2.5-1.5B-Instruct (Q4_K_M) ~1.1GB\n" +
                        "   Best for both Farsi & English\n\n" +
                        "⚡ Qwen2.5-0.5B (Q4_K_M) ~400MB\n" +
                        "   Smaller & faster\n\n" +
                        "Download .gguf from huggingface.co")

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // ===== GENERAL =====
            SectionHeader(Icons.Default.Settings, "General", MaterialTheme.colorScheme.primary)

            SettingSwitchItem("Wake Word", "Say \"Hey Assistant\"", wakeWordEnabled) { enabled: Boolean ->
                scope.launch { context.dataStore.edit { it[PreferenceKeys.WAKE_WORD_ENABLED] = enabled } }
            }
            SettingSwitchItem("Dark Mode", "Enable dark theme", darkMode) { enabled: Boolean ->
                scope.launch { context.dataStore.edit { it[PreferenceKeys.DARK_MODE] = enabled } }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { mainViewModel.reloadModels() }, modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isModelLoading && !uiState.isLLMLoading) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reload All Models")
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // About
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LifeOS Assistant v1.0.0", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("100% Offline • 100% Private\n" +
                            "• Speech: Vosk (English or Farsi)\n" +
                            "• AI: Built-in smart engine\n" +
                            "• Voice: Android TTS + Farsi transliteration\n" +
                            "• Languages: English & فارسی",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ===== Reusable Components =====

@Composable
fun SimpleDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun SliderCard(label: String, value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int, low: String, high: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.Medium)
                Text("${"%.1f".format(value)}x", color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(low, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(high, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ModelStatusCard(isLoaded: Boolean, isLoading: Boolean, info: String, status: String?, loadedLabel: String, notLoaded: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = when { isLoading -> MaterialTheme.colorScheme.surfaceVariant; isLoaded -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.errorContainer }
    )) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(when { isLoading -> "⏳ Loading..."; isLoaded -> "✅ $loadedLabel"; else -> "❌ $notLoaded" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(info, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isLoading && status != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(status, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun InstructionCard(title: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}

@Composable
fun SettingSwitchItem(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
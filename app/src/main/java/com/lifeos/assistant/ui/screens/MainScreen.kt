package com.lifeos.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lifeos.assistant.LifeOsApp
import com.lifeos.assistant.audio.AudioRecorder
import com.lifeos.assistant.data.ModelRepository
import com.lifeos.assistant.nlu.IntentClassifier
import com.lifeos.assistant.nlu.ResponseGenerator
import com.lifeos.assistant.ui.components.AssistantState
import com.lifeos.assistant.ui.components.StatusText
import com.lifeos.assistant.ui.components.WakeButton
import com.lifeos.assistant.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    audioRecorder: AudioRecorder,
    modelRepository: ModelRepository,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = (context.applicationContext as LifeOsApp).tts

    var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
    var recognizedText by remember { mutableStateOf<String?>(null) }
    var responseText by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showNoModelDialog by remember { mutableStateOf(false) }

    val currentModel by modelRepository.currentModel.collectAsState()

    val intentClassifier = remember { IntentClassifier(context) }
    val responseGenerator = remember { ResponseGenerator(context) }

    val hasRecordPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LifeOs",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurface
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StatusText(
                    state = assistantState,
                    recognizedText = recognizedText,
                    responseText = responseText
                )
            }

            Box(
                modifier = Modifier.weight(1.5f),
                contentAlignment = Alignment.Center
            ) {
                WakeButton(
                    state = assistantState,
                    onClick = {
                        when (assistantState) {
                            AssistantState.IDLE -> {
                                if (!hasRecordPermission) {
                                    showPermissionDialog = true
                                    return@WakeButton
                                }
                                if (currentModel == null) {
                                    showNoModelDialog = true
                                    return@WakeButton
                                }
                                startListening(
                                    scope = scope,
                                    audioRecorder = audioRecorder,
                                    onStateChange = { assistantState = it },
                                    onTextRecognized = { recognizedText = it }
                                )
                            }
                            AssistantState.LISTENING -> {
                                stopListening(
                                    scope = scope,
                                    audioRecorder = audioRecorder,
                                    onStateChange = { assistantState = it },
                                    onTextRecognized = { text ->
                                        recognizedText = text
                                        processRecognizedText(
                                            scope = scope,
                                            text = text,
                                            intentClassifier = intentClassifier,
                                            responseGenerator = responseGenerator,
                                            tts = tts,
                                            onStateChange = { assistantState = it },
                                            onResponse = { responseText = it }
                                        )
                                    }
                                )
                            }
                            else -> {
                                assistantState = AssistantState.IDLE
                                tts.stop()
                            }
                        }
                    },
                    enabled = !modelRepository.isLoading.value
                )
            }

            Column(
                modifier = Modifier.weight(0.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                currentModel?.let { model ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Success)
                        )
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            maxLines = 1
                        )
                    }
                } ?: run {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onNavigateToSettings() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Warning)
                        )
                        Text(
                            text = "No model loaded - Tap to settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "English / فارسی",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Required") },
            text = { Text("Microphone permission is required for voice recognition.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showNoModelDialog) {
        AlertDialog(
            onDismissRequest = { showNoModelDialog = false },
            title = { Text("No Model Loaded") },
            text = { Text("Please load a speech recognition model from settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoModelDialog = false
                        onNavigateToSettings()
                    }
                ) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoModelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun startListening(
    scope: CoroutineScope,
    audioRecorder: AudioRecorder,
    onStateChange: (AssistantState) -> Unit,
    onTextRecognized: (String) -> Unit
) {
    scope.launch {
        try {
            onStateChange(AssistantState.LISTENING)
            audioRecorder.startRecording()

            delay(10000)

            if (audioRecorder.isRecording()) {
                if (!audioRecorder.hasVoiceActivity()) {
                    audioRecorder.stopRecording()
                    onStateChange(AssistantState.IDLE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            onStateChange(AssistantState.IDLE)
        }
    }
}

private fun stopListening(
    scope: CoroutineScope,
    audioRecorder: AudioRecorder,
    onStateChange: (AssistantState) -> Unit,
    onTextRecognized: (String) -> Unit
) {
    scope.launch {
        try {
            onStateChange(AssistantState.PROCESSING)

            val audioFile = audioRecorder.stopRecording()

            // For Vosk integration - placeholder
            onTextRecognized("Voice recognition placeholder")
            onStateChange(AssistantState.IDLE)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            onStateChange(AssistantState.IDLE)
        }
    }
}

private fun processRecognizedText(
    scope: CoroutineScope,
    text: String,
    intentClassifier: IntentClassifier,
    responseGenerator: ResponseGenerator,
    tts: com.lifeos.assistant.tts.OfflineTTS,
    onStateChange: (AssistantState) -> Unit,
    onResponse: (String) -> Unit
) {
    scope.launch {
        try {
            onStateChange(AssistantState.PROCESSING)

            val intent = intentClassifier.classify(text)
            val response = responseGenerator.generateResponse(intent, text)
            onResponse(response)

            onStateChange(AssistantState.SPEAKING)
            tts.speak(response) {
                onStateChange(AssistantState.IDLE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing text", e)
            onStateChange(AssistantState.IDLE)
        }
    }
}
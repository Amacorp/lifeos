package com.lifeos.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lifeos.assistant.viewmodel.ConversationItem
import com.lifeos.assistant.viewmodel.MainViewModel

// Light color palette
private val PrimaryBlue = Color(0xFF4A90D9)
private val PrimaryBlueDark = Color(0xFF3A7BC8)
private val LightBlue = Color(0xFFE8F4FD)
private val AccentGreen = Color(0xFF34C759)
private val AccentOrange = Color(0xFFFF9500)
private val AccentRed = Color(0xFFFF3B30)
private val AccentPurple = Color(0xFF9B59B6)
private val BackgroundWhite = Color(0xFFF8FAFE)
private val CardWhite = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF6B7280)
private val UserBubble = Color(0xFF4A90D9)
private val AiBubble = Color(0xFFF0F4F8)
private val ShadowColor = Color(0x1A000000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Auto-scroll
    LaunchedEffect(uiState.conversationHistory.size) {
        if (uiState.conversationHistory.isNotEmpty()) {
            listState.animateScrollToItem(uiState.conversationHistory.size - 1)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) viewModel.startListening()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWhite)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ===== TOP BAR =====
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo and title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Logo circle
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(PrimaryBlue, AccentPurple)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "L",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "LifeOS",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            // Status indicator
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                uiState.isSpeaking -> AccentPurple
                                                uiState.isListening -> AccentRed
                                                uiState.isProcessing -> AccentOrange
                                                uiState.isModelLoaded -> AccentGreen
                                                else -> TextSecondary
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        uiState.isSpeaking -> "Speaking..."
                                        uiState.isListening -> "Listening..."
                                        uiState.isProcessing -> "Thinking..."
                                        uiState.isModelLoaded && uiState.isLLMLoaded -> "Ready"
                                        uiState.isModelLoaded -> "Basic mode"
                                        else -> "Setup needed"
                                    },
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Action buttons
                    Row {
                        if (uiState.isSpeaking) {
                            IconButton(onClick = { viewModel.stopSpeaking() }) {
                                Icon(
                                    Icons.Default.VolumeOff,
                                    contentDescription = "Stop",
                                    tint = AccentRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (uiState.conversationHistory.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearConversation() }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // ===== LOADING BAR =====
            if (uiState.isModelLoading || uiState.isLLMLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryBlue,
                    trackColor = LightBlue
                )
                Text(
                    text = uiState.statusMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // ===== ERROR BANNER =====
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                uiState.errorMessage?.let { error ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFFFFF3CD),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = Color(0xFF856404)
                            )
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFF856404),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ===== CONTENT AREA =====
            if (uiState.conversationHistory.isEmpty()) {
                // Empty state - Welcome screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        // Animated logo
                        val infiniteTransition = rememberInfiniteTransition(label = "logo")
                        val logoScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = EaseInOutCubic),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "logoScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(logoScale)
                                .shadow(12.dp, CircleShape, spotColor = PrimaryBlue.copy(alpha = 0.3f))
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            PrimaryBlue,
                                            PrimaryBlueDark,
                                            AccentPurple
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "Hi, I'm LifeOS",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (uiState.isModelLoaded)
                                "Tap the button below to start talking"
                            else
                                "Import a Vosk model in Settings to begin",
                            fontSize = 16.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Status chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusChip(
                                text = if (uiState.isModelLoaded) "Speech ✓" else "Speech ✗",
                                isActive = uiState.isModelLoaded
                            )
                            StatusChip(
                                text = if (uiState.isLLMLoaded) "AI Brain ✓" else "AI Brain ✗",
                                isActive = uiState.isLLMLoaded
                            )
                        }

                        if (!uiState.isModelLoaded && !uiState.isModelLoading) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onNavigateToSettings,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryBlue
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Settings", fontSize = 15.sp)
                            }
                        }

                        if (uiState.isModelLoaded) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Suggestion chips
                            Text(
                                text = "Try saying:",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip("\"Hello\"")
                                SuggestionChip("\"What time is it?\"")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip("\"Tell me a joke\"")
                                SuggestionChip("\"Fun fact\"")
                            }
                        }
                    }
                }
            } else {
                // ===== CONVERSATION =====
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(uiState.conversationHistory) { item: ConversationItem ->
                        ChatBubble(item = item)
                    }

                    // Thinking indicator
                    if (uiState.isProcessing) {
                        item {
                            ThinkingBubble()
                        }
                    }

                    // Speaking indicator
                    if (uiState.isSpeaking && !uiState.isProcessing) {
                        item {
                            SpeakingBubble()
                        }
                    }
                }
            }

            // ===== BOTTOM AREA - MIC BUTTON =====
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status text above button
                    AnimatedVisibility(
                        visible = uiState.isListening || uiState.isSpeaking || uiState.isProcessing
                    ) {
                        Text(
                            text = when {
                                uiState.isListening -> uiState.statusMessage
                                uiState.isSpeaking -> "🔊 Speaking..."
                                uiState.isProcessing -> "🧠 Thinking..."
                                else -> ""
                            },
                            fontSize = 14.sp,
                            color = when {
                                uiState.isListening -> AccentRed
                                uiState.isSpeaking -> AccentPurple
                                else -> AccentOrange
                            },
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // ===== BIG MIC BUTTON =====
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        // Outer pulse ring when listening
                        if (uiState.isListening) {
                            val pulseTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by pulseTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.4f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = EaseOut),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "outerPulse"
                            )
                            val pulseAlpha by pulseTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = EaseOut),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "outerAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(AccentRed.copy(alpha = pulseAlpha))
                            )

                            // Second pulse ring
                            val pulse2Scale by pulseTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = EaseOut, delayMillis = 300),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "innerPulse"
                            )
                            val pulse2Alpha by pulseTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = EaseOut, delayMillis = 300),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "innerAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .scale(pulse2Scale)
                                    .clip(CircleShape)
                                    .background(AccentRed.copy(alpha = pulse2Alpha))
                            )
                        }

                        // Speaking glow
                        if (uiState.isSpeaking && !uiState.isListening) {
                            val glowTransition = rememberInfiniteTransition(label = "glow")
                            val glowScale by glowTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "glowScale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .scale(glowScale)
                                    .clip(CircleShape)
                                    .background(AccentPurple.copy(alpha = 0.15f))
                            )
                        }

                        // Main button
                        val buttonColor by animateColorAsState(
                            targetValue = when {
                                uiState.isListening -> AccentRed
                                uiState.isSpeaking -> AccentPurple
                                !uiState.isModelLoaded -> Color(0xFFBDBDBD)
                                else -> PrimaryBlue
                            },
                            animationSpec = tween(300),
                            label = "buttonColor"
                        )

                        FloatingActionButton(
                            onClick = {
                                when {
                                    uiState.isListening -> viewModel.stopListening()
                                    uiState.isSpeaking -> viewModel.stopSpeaking()
                                    else -> {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (hasPermission) {
                                            viewModel.startListening()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(100.dp)
                                .shadow(
                                    elevation = if (uiState.isListening) 16.dp else 8.dp,
                                    shape = CircleShape,
                                    spotColor = buttonColor.copy(alpha = 0.4f)
                                ),
                            containerColor = buttonColor,
                            contentColor = Color.White,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 12.dp
                            )
                        ) {
                            Icon(
                                imageVector = when {
                                    uiState.isListening -> Icons.Default.Stop
                                    uiState.isSpeaking -> Icons.Default.VolumeOff
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = when {
                                    uiState.isListening -> "Stop listening"
                                    uiState.isSpeaking -> "Stop speaking"
                                    else -> "Start listening"
                                },
                                modifier = Modifier.size(42.dp),
                                tint = Color.White
                            )
                        }
                    }

                    // Bottom hint
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            !uiState.isModelLoaded -> "Import speech model to start"
                            uiState.isListening -> "Tap to stop"
                            uiState.isSpeaking -> "Tap to stop speaking"
                            else -> "Tap to speak"
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// ===== COMPONENTS =====

@Composable
fun StatusChip(text: String, isActive: Boolean) {
    Surface(
        color = if (isActive) AccentGreen.copy(alpha = 0.12f) else Color(0xFFF3F4F6),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) AccentGreen else TextSecondary
        )
    }
}

@Composable
fun SuggestionChip(text: String) {
    Surface(
        color = LightBlue,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = PrimaryBlue
        )
    }
}

@Composable
fun ChatBubble(item: ConversationItem) {
    val isUser = item.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryBlue, AccentPurple)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("L", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            color = if (isUser) UserBubble else AiBubble,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isUser) "You" else "LifeOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) Color.White.copy(alpha = 0.8f) else PrimaryBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = if (isUser) Color.White else TextPrimary
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // User avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(PrimaryBlue, AccentPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("L", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = AiBubble,
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Animated dots
                val transition = rememberInfiniteTransition(label = "dots")
                repeat(3) { index ->
                    val dotAlpha by transition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 200)
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = dotAlpha))
                    )
                }
            }
        }
    }
}

@Composable
fun SpeakingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(PrimaryBlue, AccentPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("L", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = AccentPurple.copy(alpha = 0.08f),
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated bars
                val transition = rememberInfiniteTransition(label = "bars")
                repeat(4) { index ->
                    val barHeight by transition.animateFloat(
                        initialValue = 8f,
                        targetValue = 20f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 100)
                        ),
                        label = "bar$index"
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AccentPurple.copy(alpha = 0.6f))
                    )
                    if (index < 3) Spacer(modifier = Modifier.width(3.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Speaking...",
                    fontSize = 13.sp,
                    color = AccentPurple,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
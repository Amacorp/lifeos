package com.lifeos.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lifeos.assistant.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Voice Assistant State
 */
enum class AssistantState {
    IDLE,       // Ready to listen
    LISTENING,  // Recording audio
    PROCESSING, // Transcribing/understanding
    SPEAKING    // Playing TTS response
}

/**
 * WakeButton - Main interaction button for LifeOs
 * 
 * Features:
 * - Animated ripple effect when listening
 * - Color changes based on state
 * - Scale animation on press
 * - Gradient background
 */
@Composable
fun WakeButton(
    state: AssistantState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Animation states
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Pulsing animation for listening state
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    // Ripple animation for listening
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )
    
    // Rotation animation for processing
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "pressScale"
    )
    
    // Get colors based on state
    val (backgroundColor, iconColor, shadowColor) = when (state) {
        AssistantState.IDLE -> Triple(
            Brush.radialGradient(listOf(Primary, PrimaryDark)),
            OnPrimary,
            Primary.copy(alpha = 0.3f)
        )
        AssistantState.LISTENING -> Triple(
            Brush.radialGradient(listeningGradientColors()),
            OnPrimary,
            Success.copy(alpha = 0.5f)
        )
        AssistantState.PROCESSING -> Triple(
            Brush.radialGradient(listOf(Warning, Warning.copy(alpha = 0.7f))),
            OnBackground,
            Warning.copy(alpha = 0.3f)
        )
        AssistantState.SPEAKING -> Triple(
            Brush.radialGradient(listOf(Secondary, SecondaryDark)),
            OnPrimary,
            Secondary.copy(alpha = 0.3f)
        )
    }
    
    Box(
        modifier = modifier
            .size(180.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Outer ripple rings (only when listening)
        if (state == AssistantState.LISTENING) {
            // First ripple ring
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = rippleAlpha
                    }
                    .background(Success.copy(alpha = 0.2f), CircleShape)
            )
            
            // Second ripple ring (delayed)
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .graphicsLayer {
                        scaleX = pulseScale * 0.9f
                        scaleY = pulseScale * 0.9f
                        alpha = rippleAlpha * 0.7f
                    }
                    .background(Success.copy(alpha = 0.15f), CircleShape)
            )
        }
        
        // Main button
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    shadowElevation = 20f
                    spotShadowColor = shadowColor
                }
                .background(backgroundColor, CircleShape)
                .border(
                    width = 3.dp,
                    color = when (state) {
                        AssistantState.IDLE -> PrimaryLight.copy(alpha = 0.5f)
                        AssistantState.LISTENING -> Success.copy(alpha = 0.7f)
                        AssistantState.PROCESSING -> Warning.copy(alpha = 0.7f)
                        AssistantState.SPEAKING -> SecondaryLight.copy(alpha = 0.5f)
                    },
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Icon based on state
            val icon = when (state) {
                AssistantState.IDLE -> Icons.Default.Mic
                AssistantState.LISTENING -> Icons.Default.Stop
                AssistantState.PROCESSING -> Icons.Default.PlayArrow
                AssistantState.SPEAKING -> Icons.Default.Pause
            }
            
            Icon(
                imageVector = icon,
                contentDescription = when (state) {
                    AssistantState.IDLE -> "Tap to speak"
                    AssistantState.LISTENING -> "Stop listening"
                    AssistantState.PROCESSING -> "Processing"
                    AssistantState.SPEAKING -> "Speaking"
                },
                modifier = Modifier.size(64.dp),
                tint = iconColor
            )
        }
    }
}

/**
 * Get gradient colors for listening state with animation
 */
@Composable
private fun listeningGradientColors(): List<Color> {
    val infiniteTransition = rememberInfiniteTransition(label = "colorShift")
    
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colorShift"
    )
    
    return listOf(
        Success,
        Success.copy(alpha = 0.8f + (0.2f * colorShift))
    )
}

/**
 * Status text composable
 */
@Composable
fun StatusText(
    state: AssistantState,
    recognizedText: String? = null,
    responseText: String? = null,
    modifier: Modifier = Modifier
) {
    val statusText = when (state) {
        AssistantState.IDLE -> "Tap to speak"
        AssistantState.LISTENING -> "Listening..."
        AssistantState.PROCESSING -> "Processing..."
        AssistantState.SPEAKING -> "Speaking..."
    }
    
    val statusColor = when (state) {
        AssistantState.IDLE -> OnSurfaceVariant
        AssistantState.LISTENING -> Success
        AssistantState.PROCESSING -> Warning
        AssistantState.SPEAKING -> Secondary
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main status
        androidx.compose.material3.Text(
            text = statusText,
            style = MaterialTheme.typography.headlineSmall,
            color = statusColor
        )
        
        // Recognized text (when available)
        recognizedText?.takeIf { it.isNotBlank() && state != AssistantState.IDLE }?.let { text ->
            androidx.compose.material3.Text(
                text = "\"$text\"",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface,
                maxLines = 2
            )
        }
        
        // Response text (when available)
        responseText?.takeIf { it.isNotBlank() }?.let { text ->
            androidx.compose.material3.Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                maxLines = 3
            )
        }
    }
}

/**
 * Preview helper
 */
@Composable
fun WakeButtonPreview() {
    var state by remember { mutableStateOf(AssistantState.IDLE) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WakeButton(
            state = state,
            onClick = {
                state = when (state) {
                    AssistantState.IDLE -> AssistantState.LISTENING
                    AssistantState.LISTENING -> AssistantState.PROCESSING
                    AssistantState.PROCESSING -> AssistantState.SPEAKING
                    AssistantState.SPEAKING -> AssistantState.IDLE
                }
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        StatusText(
            state = state,
            recognizedText = if (state != AssistantState.IDLE) "Hello, how are you?" else null,
            responseText = if (state == AssistantState.SPEAKING) "I'm doing great, thanks for asking!" else null
        )
    }
}
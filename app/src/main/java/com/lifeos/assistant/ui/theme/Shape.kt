package com.lifeos.assistant.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * LifeOs Shapes
 * Rounded, friendly shapes for voice assistant UI
 */
val Shapes = Shapes(
    // Small - Buttons, chips
    small = RoundedCornerShape(8.dp),
    
    // Medium - Cards, dialogs
    medium = RoundedCornerShape(16.dp),
    
    // Large - Bottom sheets, expanded cards
    large = RoundedCornerShape(24.dp),
    
    // Extra large - Full screen dialogs
    extraLarge = RoundedCornerShape(32.dp)
)

// Circle shape for wake button
val CircleButtonShape = CircleShape

// Pill shape for action buttons
val PillShape = RoundedCornerShape(50)
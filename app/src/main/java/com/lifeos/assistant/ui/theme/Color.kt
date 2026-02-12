package com.lifeos.assistant.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * LifeOs Color Palette - Dark Theme Optimized
 * Deep blue/purple aesthetic for voice assistant
 */

// Primary Colors - Indigo
val Primary = Color(0xFF6366F1)
val PrimaryDark = Color(0xFF4F46E5)
val PrimaryLight = Color(0xFF818CF8)

// Secondary Colors - Purple
val Secondary = Color(0xFFA855F7)
val SecondaryDark = Color(0xFF9333EA)
val SecondaryLight = Color(0xFFC084FC)

// Accent Colors - Cyan
val Accent = Color(0xFF22D3EE)
val AccentDark = Color(0xFF06B6D4)

// Background Colors - Dark Slate
val Background = Color(0xFF0F172A)
val Surface = Color(0xFF1E293B)
val SurfaceVariant = Color(0xFF334155)
val SurfaceLight = Color(0xFF475569)

// Text Colors
val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFFF1F5F9)
val OnSurface = Color(0xFFF1F5F9)
val OnSurfaceVariant = Color(0xFF94A3B8)
val OnSurfaceMuted = Color(0xFF64748B)

// Status Colors
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)
val Info = Color(0xFF3B82F6)

// Listening State Colors
val ListeningIdle = Primary
val ListeningActive = Success
val ListeningProcessing = Warning
val ListeningSpeaking = Secondary

// Gradient Colors
val GradientStart = Primary
val GradientEnd = Secondary
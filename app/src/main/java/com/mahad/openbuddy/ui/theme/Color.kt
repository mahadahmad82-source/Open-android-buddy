package com.mahad.openbuddy.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// Open Buddy 2026 Modern Glassmorphism Design Tokens
// ============================================================================

// Accent Colors
val AccentViolet = Color(0xFF7C5CFF)
val AccentBlue = Color(0xFF3D8BFF)
val AccentCyan = Color(0xFF22D3EE)

// Primary Gradient: #7C5CFF to #3D8BFF
val PrimaryGradient = Brush.horizontalGradient(
    colors = listOf(AccentViolet, AccentBlue)
)

val PrimaryGradientVertical = Brush.verticalGradient(
    colors = listOf(AccentViolet, AccentBlue)
)

// Legacy brand aliases
val BrandPrimary = AccentViolet
val BrandSecondary = AccentBlue
val BrandTertiary = AccentCyan
val ElectricPurple = AccentViolet
val CyanBlue = AccentCyan
val OpenBuddyGradient = PrimaryGradient

// Semantic Feedback
val SemanticSuccess = Color(0xFF34D399) // Soft Green
val SemanticWarning = Color(0xFFFBBF24) // Soft Amber
val SemanticError = Color(0xFFF87171)   // Soft Red
val EmeraldGreen = SemanticSuccess

// Dark Theme Colors
val DarkBackgroundStart = Color(0xFF0B0F1A)
val DarkBackgroundEnd = Color(0xFF1A1033)
val DarkSurface = Color(0xFF111726)
val DarkBackground = DarkBackgroundStart

val DarkTextPrimary = Color(0xFFF5F7FF)
val DarkTextSecondary = Color(0xFFA7B0C8)
val DarkTextTertiary = Color(0xFF717D96)

// Dark Glass Surface Tokens
val DarkGlassFill = Color.White.copy(alpha = 0.09f) // 8-12% alpha
val DarkGlassStrokeStart = Color.White.copy(alpha = 0.24f)
val DarkGlassStrokeEnd = Color.White.copy(alpha = 0.04f)
val DarkGlassStroke = Brush.verticalGradient(
    colors = listOf(DarkGlassStrokeStart, DarkGlassStrokeEnd)
)
val DarkBorder = Color.White.copy(alpha = 0.12f)

// Light Theme Colors
val LightBackgroundStart = Color(0xFFF6F5FD)
val LightBackgroundEnd = Color(0xFFE9E5FC)
val LightSurface = Color(0xFFFFFFFF)
val LightBackground = LightBackgroundStart

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextTertiary = Color(0xFF94A3B8)

// Light Glass Surface Tokens
val LightGlassFill = Color.White.copy(alpha = 0.65f) // 60-70% alpha
val LightGlassStrokeStart = Color.White.copy(alpha = 0.85f)
val LightGlassStrokeEnd = Color.White.copy(alpha = 0.35f)
val LightGlassStroke = Brush.verticalGradient(
    colors = listOf(LightGlassStrokeStart, LightGlassStrokeEnd)
)

// Code Syntax Highlighting Palette
val SyntaxKeyword = Color(0xFFFF7B72)
val SyntaxString = Color(0xFFA5D6FF)
val SyntaxComment = Color(0xFF8B949E)
val SyntaxFunction = Color(0xFFD2A8FF)
val SyntaxNumber = Color(0xFF79C0FF)
val SyntaxType = Color(0xFFFFA657)
val SyntaxDefault = Color(0xFFE6EDF3)
val CodeBackground = Color(0xFF0A0E17) // Darker, near-solid surface for readability
val SyntaxBackground = CodeBackground

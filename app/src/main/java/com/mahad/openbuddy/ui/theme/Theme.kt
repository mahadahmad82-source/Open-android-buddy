package com.mahad.openbuddy.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================================
// Central Design System Tokens (GlassTokens)
// ============================================================================

data class GlassTokens(
    val isDark: Boolean,
    val backgroundBrush: Brush,
    val backgroundStart: Color,
    val backgroundEnd: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassStroke: Brush,
    val glassBorderStroke: BorderStroke,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentViolet: Color = AccentViolet,
    val accentBlue: Color = AccentBlue,
    val accentCyan: Color = AccentCyan,
    val primaryGradient: Brush = PrimaryGradient,
    val success: Color = SemanticSuccess,
    val warning: Color = SemanticWarning,
    val error: Color = SemanticError,
    val codeBackground: Color = CodeBackground,
    val cardRadius: Dp = 20.dp,
    val pillRadius: Dp = 50.dp,
    val inputRadius: Dp = 20.dp
)

private val DarkGlassTokens = GlassTokens(
    isDark = true,
    backgroundBrush = Brush.verticalGradient(listOf(DarkBackgroundStart, DarkBackgroundEnd)),
    backgroundStart = DarkBackgroundStart,
    backgroundEnd = DarkBackgroundEnd,
    glassFill = DarkGlassFill,
    glassFillStrong = Color.White.copy(alpha = 0.14f),
    glassStroke = DarkGlassStroke,
    glassBorderStroke = BorderStroke(1.dp, DarkGlassStroke),
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary
)

private val LightGlassTokens = GlassTokens(
    isDark = false,
    backgroundBrush = Brush.verticalGradient(listOf(LightBackgroundStart, LightBackgroundEnd)),
    backgroundStart = LightBackgroundStart,
    backgroundEnd = LightBackgroundEnd,
    glassFill = LightGlassFill,
    glassFillStrong = Color.White.copy(alpha = 0.85f),
    glassStroke = LightGlassStroke,
    glassBorderStroke = BorderStroke(1.dp, LightGlassStroke),
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary
)

val LocalGlassTokens = staticCompositionLocalOf { DarkGlassTokens }

object GlassTheme {
    val tokens: GlassTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassTokens.current
}

// Material 3 Mappings
private val DarkM3ColorScheme = darkColorScheme(
    primary = AccentViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E2260),
    onPrimaryContainer = Color(0xFFEADBFF),
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = AccentCyan,
    onTertiary = Color.Black,
    background = DarkBackgroundStart,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF182035),
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkGlassStrokeStart,
    error = SemanticError,
    onError = Color(0xFF450A0A)
)

private val LightM3ColorScheme = lightColorScheme(
    primary = AccentViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADBFF),
    onPrimaryContainer = Color(0xFF2E2260),
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = AccentCyan,
    onTertiary = Color.Black,
    background = LightBackgroundStart,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFF1EEFB),
    onSurfaceVariant = LightTextSecondary,
    outline = LightGlassStrokeStart,
    error = SemanticError,
    onError = Color.White
)

@Composable
fun OpenBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = if (darkTheme) DarkGlassTokens else LightGlassTokens
    val m3Colors = if (darkTheme) DarkM3ColorScheme else LightM3ColorScheme

    CompositionLocalProvider(
        LocalGlassTokens provides tokens
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            typography = Typography,
            content = content
        )
    }
}

// Background layer with soft blurred gradient blobs (violet #7C5CFF, blue #3D8BFF, cyan #22D3EE)
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = GlassTheme.tokens
    val isDark = tokens.isDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.backgroundBrush)
    ) {
        // Blob 1: Violet blob top-start
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-40).dp, y = (-50).dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(64.dp)
                    } else Modifier
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentViolet.copy(alpha = if (isDark) 0.18f else 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Blob 2: Blue blob center-end
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = 180.dp, y = 260.dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(64.dp)
                    } else Modifier
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentBlue.copy(alpha = if (isDark) 0.15f else 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Blob 3: Cyan blob bottom-start
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-60).dp, y = 520.dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(64.dp)
                    } else Modifier
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AccentCyan.copy(alpha = if (isDark) 0.12f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main app content
        content()
    }
}

// Alias for template compatibility
@Composable
fun OpenBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    OpenBuddyTheme(darkTheme = darkTheme, content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    OpenBuddyTheme(darkTheme = darkTheme, content = content)
}

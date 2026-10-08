package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.theme.GlassTheme

data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val codePreview: String? = null
)

@Composable
fun OnboardingScreen(
    onStartOfflineClick: () -> Unit,
    onEnableCloudBackupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    val slides = remember {
        listOf(
            OnboardingSlide(
                title = "AI-Powered Mobile IDE",
                subtitle = "DEVELOP ANYWHERE",
                description = "Navigate projects, inspect syntax diffs, and synthesize offline intelligence directly on your Android device.",
                icon = Icons.Default.Code,
                codePreview = "fun solveProblem() = codePilot.assist()"
            ),
            OnboardingSlide(
                title = "Bring Your Own Keys",
                subtitle = "SOVEREIGN & ENCRYPTED",
                description = "Connect Gemini, Claude, or custom OpenAI-compatible models. Keys are encrypted at rest with Android Keystore.",
                icon = Icons.Default.Key,
                codePreview = "BYOK.encryptLocal(androidKeystore)"
            ),
            OnboardingSlide(
                title = "Offline-First Architecture",
                subtitle = "ZERO ACCOUNT REQUIRED",
                description = "Start coding immediately. All sessions, encrypted Room SQLite databases, and query logs live 100% locally.",
                icon = Icons.Default.OfflinePin,
                codePreview = "Room.database(offline = true)"
            )
        )
    }

    var currentSlideIndex by remember { mutableIntStateOf(0) }
    val currentSlide = slides[currentSlideIndex]

    // Slow animated gradient background motion (breathing blobs)
    val infiniteTransition = rememberInfiniteTransition(label = "bg_motion")
    val blobOffset1 by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1"
    )
    val blobOffset2 by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.backgroundBrush)
    ) {
        // Slow animated gradient blobs
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = (blobOffset1 - 50).dp, y = (blobOffset2 - 40).dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(64.dp)
                    else Modifier
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(tokens.accentViolet.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (200 + blobOffset2).dp, y = (300 + blobOffset1).dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(64.dp)
                    else Modifier
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(tokens.accentBlue.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (blobOffset1 - 40).dp, y = (580 - blobOffset2).dp)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(64.dp)
                    else Modifier
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(tokens.accentCyan.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
        )

        // Screen Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Glass Badge & Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(8.dp, RoundedCornerShape(10.dp), spotColor = tokens.accentViolet.copy(alpha = 0.4f))
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.primaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "<>",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Open Buddy",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = tokens.textPrimary
                    )
                }

                if (currentSlideIndex < slides.size - 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tokens.glassFill)
                            .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                            .clickable { currentSlideIndex = slides.size - 1 }
                            .testTag("onboarding_skip_button")
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.textSecondary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Center: Visual Glass Emblem & Slide Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Frosted Glowing Icon Orb
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .shadow(24.dp, CircleShape, spotColor = tokens.accentViolet.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    tokens.accentViolet.copy(alpha = 0.35f),
                                    tokens.accentBlue.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(tokens.glassBorderStroke, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentSlide.icon,
                        contentDescription = null,
                        tint = tokens.accentCyan,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Subtitle Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(tokens.accentViolet.copy(alpha = 0.16f))
                        .border(1.dp, tokens.accentViolet.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = currentSlide.subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = tokens.accentViolet
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Large Title
                Text(
                    text = currentSlide.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = tokens.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description with WCAG AA readable contrast
                Text(
                    text = currentSlide.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    color = tokens.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Code snippet teaser inside frosted GlassCard
                if (currentSlide.codePreview != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.codeBackground.copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = currentSlide.codePreview,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            color = tokens.accentCyan
                        )
                    }
                }
            }

            // Bottom Section: Glass Page Indicator & Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glass Page Indicator
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tokens.glassFill)
                        .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    slides.forEachIndexed { index, _ ->
                        val isSelected = index == currentSlideIndex
                        val widthAnim by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            label = "dot_width"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(width = widthAnim, height = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) tokens.accentViolet
                                    else Color.White.copy(alpha = 0.25f)
                                )
                                .clickable { currentSlideIndex = index }
                        )
                    }
                }

                if (currentSlideIndex < slides.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(tokens.glassFill)
                            .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                            .clickable { currentSlideIndex++ }
                            .testTag("onboarding_next_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.textPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Gradient "Start Offline" Button
                GradientButton(
                    text = "Start Offline",
                    onClick = onStartOfflineClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    testTag = "start_offline_button"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary Glass Button: "Enable Cloud Backup (optional)"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tokens.glassFill)
                        .border(tokens.glassBorderStroke, RoundedCornerShape(50))
                        .clickable(onClick = onEnableCloudBackupClick)
                        .testTag("enable_cloud_backup_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = tokens.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Cloud Backup (optional)",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.5.sp,
                                color = tokens.textSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

package com.mahad.openbuddy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mahad.openbuddy.ui.theme.GlassTheme
import com.mahad.openbuddy.ui.theme.SemanticSuccess

// ============================================================================
// 1. GlassCard
// ============================================================================

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    fillColor: Color = GlassTheme.tokens.glassFill,
    borderStroke: BorderStroke = GlassTheme.tokens.glassBorderStroke,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = tween(120),
        label = "glass_card_press_scale"
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(fillColor)
            .border(borderStroke, shape)
            .then(clickableModifier)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

// ============================================================================
// 2. GradientButton (with 0.97 press-scale and haptic feedback)
// ============================================================================

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    gradient: Brush = GlassTheme.tokens.primaryGradient,
    shape: Shape = RoundedCornerShape(50),
    testTag: String = "gradient_button"
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label = "btn_scale"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .scale(scale)
            .shadow(
                elevation = if (enabled) 8.dp else 0.dp,
                shape = shape,
                ambientColor = GlassTheme.tokens.accentViolet.copy(alpha = 0.35f),
                spotColor = GlassTheme.tokens.accentBlue.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                if (enabled) gradient
                else SolidColor(GlassTheme.tokens.textSecondary.copy(alpha = 0.2f))
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .testTag(testTag)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.2.sp
                ),
                color = Color.White
            )

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ============================================================================
// 2b. GlassOutlinedButton
// ============================================================================

@Composable
fun GlassOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(50),
    testTag: String = "glass_outlined_button"
) {
    val tokens = GlassTheme.tokens
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label = "btn_scale_outlined"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .scale(scale)
            .clip(shape)
            .background(tokens.glassFill)
            .border(tokens.glassBorderStroke, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .testTag(testTag)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = tokens.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.2.sp
                ),
                color = tokens.textPrimary
            )

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = tokens.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ============================================================================
// 3. GlassTextField
// ============================================================================

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = 4,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    testTag: String = "glass_text_field"
) {
    val tokens = GlassTheme.tokens
    val shape = RoundedCornerShape(tokens.inputRadius)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.glassFill)
            .border(tokens.glassBorderStroke, shape)
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        textStyle = TextStyle(
            color = tokens.textPrimary,
            fontSize = 14.5.sp,
            lineHeight = 20.sp
        ),
        cursorBrush = SolidColor(tokens.accentViolet),
        singleLine = singleLine,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = tokens.textSecondary.copy(alpha = 0.65f),
                            fontSize = 14.5.sp
                        )
                    }
                    innerTextField()
                }

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }
    )
}

// ============================================================================
// 4. PillChip (Glass Pill Chip)
// ============================================================================

@Composable
fun PillChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val tokens = GlassTheme.tokens
    val shape = RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(80),
        label = "pill_scale"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .scale(scale)
            .clip(shape)
            .background(
                if (isSelected) tokens.primaryGradient
                else SolidColor(tokens.glassFill)
            )
            .border(
                if (isSelected) BorderStroke(1.dp, tokens.accentCyan.copy(alpha = 0.5f))
                else tokens.glassBorderStroke,
                shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else tokens.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = if (isSelected) Color.White else tokens.textPrimary
            )
        }
    }
}

// ============================================================================
// 5. GlassBottomSheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = GlassTheme.tokens

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = tokens.backgroundEnd.copy(alpha = 0.94f),
        contentColor = tokens.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            content = content
        )
    }
}

// ============================================================================
// 6. GlassDialog
// ============================================================================

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    confirmButtonText: String = "Confirm",
    dismissButtonText: String = "Cancel",
    confirmButtonEnabled: Boolean = true,
    confirmGradient: Brush? = null,
    onConfirm: () -> Unit,
    isDestructive: Boolean = false,
    testTag: String = "glass_dialog",
    confirmTestTag: String = "confirm_button",
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val tokens = GlassTheme.tokens

    Dialog(onDismissRequest = onDismissRequest) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            fillColor = tokens.backgroundEnd.copy(alpha = 0.94f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (icon != null) {
                    val badgeTint = iconTint ?: if (isDestructive) tokens.error else tokens.accentViolet
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(badgeTint.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = if (isDestructive) tokens.error else tokens.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    lineHeight = 20.sp
                )
            }

            if (content != null) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(
                        text = dismissButtonText,
                        color = tokens.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val buttonGradient = confirmGradient ?: if (isDestructive) {
                    Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                } else tokens.primaryGradient

                GradientButton(
                    text = confirmButtonText,
                    onClick = onConfirm,
                    enabled = confirmButtonEnabled,
                    gradient = buttonGradient,
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                    testTag = confirmTestTag
                )
            }
        }
    }
}

// ============================================================================
// 7. ShimmerSkeleton & ShimmerListSkeleton
// ============================================================================

@Composable
fun ShimmerListSkeleton(
    itemCount: Int = 3,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(itemCount) {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ShimmerSkeleton(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerSkeleton(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(14.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerSkeleton(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(11.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.04f),
        Color.White.copy(alpha = 0.16f),
        Color.White.copy(alpha = 0.04f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
    )
}

// ============================================================================
// 8. GlassSnackbar
// ============================================================================

@Composable
fun GlassSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .padding(16.dp)
            .shadow(12.dp, shape, spotColor = tokens.accentViolet.copy(alpha = 0.4f))
            .clip(shape)
            .background(tokens.backgroundEnd.copy(alpha = 0.92f))
            .border(tokens.glassBorderStroke, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tokens.accentCyan)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = snackbarData.visuals.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f)
            )

            snackbarData.visuals.actionLabel?.let { actionLabel ->
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = tokens.accentViolet
                    ),
                    modifier = Modifier.clickable { snackbarData.performAction() }
                )
            }
        }
    }
}

// ============================================================================
// Status Badges & Setting Cards
// ============================================================================

@Composable
fun OfflineStatusBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        color = GlassTheme.tokens.glassFill,
        shape = RoundedCornerShape(16.dp),
        border = GlassTheme.tokens.glassBorderStroke,
        modifier = modifier.testTag("offline_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SemanticSuccess.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Offline Ready",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = GlassTheme.tokens.textPrimary
            )
        }
    }
}

@Composable
fun SettingSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingBadge: String? = null,
    testTag: String = "setting_section_card"
) {
    val tokens = GlassTheme.tokens

    GlassCard(
        onClick = onClick,
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.primaryGradient.apply { }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }

            if (trailingBadge != null) {
                Surface(
                    color = tokens.accentViolet.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = trailingBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = tokens.accentViolet,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = tokens.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ============================================================================
// Gradient Switch (Switch with gradient track)
// ============================================================================

@Composable
fun GradientSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val tokens = GlassTheme.tokens
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "switch_thumb"
    )

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (checked) tokens.primaryGradient
                else SolidColor(Color.White.copy(alpha = 0.12f))
            )
            .border(
                1.dp,
                if (checked) tokens.accentCyan.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.18f),
                RoundedCornerShape(50)
            )
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else Modifier
            )
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}


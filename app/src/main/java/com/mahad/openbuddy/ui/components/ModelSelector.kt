package com.mahad.openbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahad.openbuddy.domain.model.AiProviderConfig
import com.mahad.openbuddy.ui.theme.GlassTheme

@Composable
fun ModelSelector(
    activeProvider: AiProviderConfig?,
    providers: List<AiProviderConfig>,
    onSelectProvider: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    var expanded by remember { mutableStateOf(false) }

    val displayText = if (activeProvider != null) {
        "${activeProvider.name} (${activeProvider.modelName})"
    } else {
        "Select Model"
    }

    val pillShape = RoundedCornerShape(50)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(pillShape)
                .background(tokens.glassFillStrong)
                .border(tokens.glassBorderStroke, pillShape)
                .clickable { expanded = true }
                .testTag("model_selector_button")
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Key status dot
                val keyConfigured = activeProvider?.hasApiKey == true
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (keyConfigured) tokens.success else tokens.warning)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand model selector",
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(tokens.backgroundEnd.copy(alpha = 0.95f))
                .border(tokens.glassBorderStroke, RoundedCornerShape(16.dp))
                .testTag("model_selector_dropdown")
        ) {
            Text(
                text = "CONFIGURED PROVIDERS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = tokens.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            providers.forEach { provider ->
                val isSelected = provider.id == activeProvider?.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${provider.name} (${provider.modelName})",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) tokens.accentViolet else tokens.textPrimary
                            )
                            if (!provider.hasApiKey) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[No Key]",
                                    fontSize = 11.sp,
                                    color = tokens.warning,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = tokens.accentViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectProvider(provider.id)
                    }
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Configure Providers & Keys...",
                        color = tokens.textPrimary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = tokens.accentViolet,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onOpenSettings()
                }
            )
        }
    }
}

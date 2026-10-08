package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.SemanticError

@Composable
fun ErrorBanner(
    errorMessage: String,
    onNavigateToSettings: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens

    val isKeyError = errorMessage.contains("API key", ignoreCase = true) ||
            errorMessage.contains("401", ignoreCase = true) ||
            errorMessage.contains("403", ignoreCase = true) ||
            errorMessage.contains("unauthorized", ignoreCase = true)

    val isRateLimit = errorMessage.contains("429", ignoreCase = true) ||
            errorMessage.contains("rate limit", ignoreCase = true)

    val isOffline = errorMessage.contains("internet", ignoreCase = true) ||
            errorMessage.contains("offline", ignoreCase = true) ||
            errorMessage.contains("network", ignoreCase = true)

    val isTimeout = errorMessage.contains("time", ignoreCase = true) ||
            errorMessage.contains("timed out", ignoreCase = true)

    val icon = when {
        isKeyError -> Icons.Default.Key
        isOffline -> Icons.Default.WifiOff
        isTimeout -> Icons.Default.Timer
        else -> Icons.Default.ErrorOutline
    }

    val title = when {
        isKeyError -> "Authentication Required"
        isRateLimit -> "Rate Limit Exceeded"
        isOffline -> "Connection Offline"
        isTimeout -> "Request Timed Out"
        else -> "Provider Error"
    }

    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_banner")
            .clip(shape)
            .background(SemanticError.copy(alpha = 0.12f))
            .border(
                BorderStroke(1.dp, SemanticError.copy(alpha = 0.35f)),
                shape
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SemanticError.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Error icon",
                        tint = SemanticError,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = tokens.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = tokens.textSecondary,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                if (onRetry != null) {
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, tokens.textSecondary.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = tokens.textPrimary
                        ),
                        modifier = Modifier.testTag("error_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry", fontSize = 13.sp)
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToSettings,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, tokens.accentViolet.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = tokens.accentViolet
                    ),
                    modifier = Modifier.testTag("error_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isKeyError) "Manage Keys" else "Settings", fontSize = 13.sp)
                }
            }
        }
    }
}

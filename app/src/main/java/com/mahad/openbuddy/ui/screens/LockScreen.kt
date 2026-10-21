package com.mahad.openbuddy.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mahad.openbuddy.ui.components.GlassCard
import com.mahad.openbuddy.ui.components.GradientButton
import com.mahad.openbuddy.ui.theme.GlassBackground
import com.mahad.openbuddy.ui.theme.GlassTheme
import com.mahad.openbuddy.ui.theme.SemanticError

@Composable
fun LockScreen(
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = GlassTheme.tokens
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerBiometricPrompt() {
        if (activity == null) {
            errorMessage = "Unable to launch biometrics: Activity not ready."
            return
        }

        errorMessage = null
        val executor = ContextCompat.getMainExecutor(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val biometricManager = BiometricManager.from(activity)
        val canAuth = biometricManager.canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS &&
            canAuth != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        ) {
            errorMessage = "Biometrics or device credential not available ($canAuth)."
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Open Buddy")
            .setSubtitle("Confirm your biometric identity or device PIN/password to proceed")
            .setAllowedAuthenticators(authenticators)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    errorMessage = null
                    onUnlockSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        errorMessage = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    errorMessage = "Authentication failed. Please try again."
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Authentication error"
        }
    }

    // Auto-prompt on display
    LaunchedEffect(Unit) {
        triggerBiometricPrompt()
    }

    GlassBackground(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    // Lock Emblem with glow
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(20.dp, CircleShape, spotColor = tokens.accentViolet.copy(alpha = 0.5f))
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
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
                            imageVector = Icons.Default.Lock,
                            contentDescription = "App Locked",
                            tint = tokens.accentCyan,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Open Buddy is Locked",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = tokens.textPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Protected by Android Keystore. Authenticate to access encrypted sessions and API credentials.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp
                        ),
                        color = tokens.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SemanticError.copy(alpha = 0.15f))
                                .border(1.dp, SemanticError.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = SemanticError,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    GradientButton(
                        text = "Unlock Open Buddy",
                        onClick = { triggerBiometricPrompt() },
                        leadingIcon = Icons.Default.Fingerprint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        testTag = "unlock_app_button"
                    )
                }
            }
        }
    }
}

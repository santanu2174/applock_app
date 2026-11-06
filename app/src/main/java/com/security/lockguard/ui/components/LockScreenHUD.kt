package com.security.lockguard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.lockguard.ui.theme.*

@Composable
fun LockScreenHUD(
    isLockoutActive: Boolean,
    isFaceEnrolled: Boolean,
    remainingSeconds: Long,
    remainingAttempts: Int,
    feedbackMessage: String,
    targetAppName: String? = null,
    onFaceTrigger: () -> Unit,
    onEnrollFace: () -> Unit,
    onBiometricTrigger: () -> Unit,
    onPinSubmit: (String) -> Unit
) {
    var showPinOverlay by remember { mutableStateOf(false) }
    var showFaceNotEnrolledDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header & Device Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Icon(
                    imageVector = if (isLockoutActive) Icons.Default.Warning else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isLockoutActive) CrimsonAlert else CyanAccent,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isLockoutActive) "SYSTEM LOCKED"
                           else if (!targetAppName.isNullOrEmpty()) "UNLOCK ${targetAppName.uppercase()}"
                           else "DEVICE SECURED",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isLockoutActive) "Cooldown active - Biometrics suspended"
                           else "Attempts remaining: $remainingAttempts/5",
                    fontSize = 13.sp,
                    color = if (isLockoutActive) CrimsonAlert else TextSecondary
                )
            }

            // Central Biometrics Section (Dual Face ID + Fingerprint)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f)
            ) {
                if (!isLockoutActive && !showPinOverlay) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Face ID Button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clickable {
                                        if (isFaceEnrolled) {
                                            onFaceTrigger()
                                        } else {
                                            showFaceNotEnrolledDialog = true
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF141926))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = "Face ID",
                                        tint = if (isFaceEnrolled) CyanAccent else Color.Gray,
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isFaceEnrolled) "FACE ID" else "SET UP FACE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFaceEnrolled) CyanAccent else Color.Gray,
                                    letterSpacing = 1.sp
                                )
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(Color(0x33FFFFFF))
                            )

                            // Fingerprint Button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clickable { onBiometricTrigger() }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1426))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Fingerprint",
                                        tint = Color(0xFFC39BD3),
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "FINGERPRINT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC39BD3),
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Tap Face ID or Fingerprint to unlock",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                } else if (isLockoutActive) {
                    LockoutCountdownDisplay(remainingSeconds = remainingSeconds)
                }
            }

            // Real-Time Feedback Message Banner
            if (feedbackMessage.isNotEmpty()) {
                Surface(
                    color = CrimsonAlertBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = feedbackMessage,
                        color = Color(0xFFFF8595),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Fallback PIN Action
            Button(
                onClick = { showPinOverlay = true },
                colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp)
            ) {
                Text(
                    text = "Unlock with 6-digit Security PIN",
                    color = CyanAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Fullscreen PIN Keypad Overlay
        AnimatedVisibility(
            visible = showPinOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PinKeypadOverlay(
                pin = enteredPin,
                onNumberClick = { digit ->
                    if (enteredPin.length < 6) {
                        enteredPin += digit
                        if (enteredPin.length == 6) {
                            onPinSubmit(enteredPin)
                            enteredPin = ""
                        }
                    }
                },
                onBackspace = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                },
                onDismiss = {
                    showPinOverlay = false
                    enteredPin = ""
                }
            )
        }

        // Dialog: Face Not Enrolled
        if (showFaceNotEnrolledDialog) {
            AlertDialog(
                onDismissRequest = { showFaceNotEnrolledDialog = false },
                title = { Text(text = "Face ID Not Registered", color = Color.White) },
                text = {
                    Text(
                        text = "You haven't set up your Face ID yet. Would you like to scan and register your face now?",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFaceNotEnrolledDialog = false
                            onEnrollFace()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Register Face", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFaceNotEnrolledDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF161A22)
            )
        }
    }
}

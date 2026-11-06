package com.security.lockguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.lockguard.ui.theme.*

@Composable
fun PinKeypadOverlay(
    pin: String,
    onNumberClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFA0A0B0E))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter Master Security PIN",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(28.dp))

        // 6-digit visual dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (i in 0 until 6) {
                val isFilled = i < pin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) CyanAccent else CardDark)
                )
            }
        }

        Spacer(modifier = Modifier.height(44.dp))

        // 3x4 Numeric Keypad Grid
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("CANCEL", "0", "DEL")
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(KeypadButtonBg)
                            .clickable {
                                when (key) {
                                    "DEL" -> onBackspace()
                                    "CANCEL" -> onDismiss()
                                    else -> onNumberClick(key)
                                }
                            }
                    ) {
                        if (key == "DEL") {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Delete",
                                tint = TextPrimary
                            )
                        } else {
                            Text(
                                text = key,
                                fontSize = if (key.length > 1) 12.sp else 24.sp,
                                color = if (key == "CANCEL") CrimsonAlert else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


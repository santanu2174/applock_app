package com.security.lockguard.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.lockguard.ui.theme.CrimsonAlert
import com.security.lockguard.ui.theme.TextPrimary
import com.security.lockguard.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun LockoutCountdownDisplay(remainingSeconds: Long) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SECURITY LOCKOUT",
            color = CrimsonAlert,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = timeFormatted,
            fontSize = 48.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Emergency unlock available via Master PIN only",
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}


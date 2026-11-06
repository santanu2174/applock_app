package com.security.lockguard.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.security.lockguard.ui.theme.BlueAccent
import com.security.lockguard.ui.theme.CyanAccent

@Composable
fun BiometricScannerRing(onTrigger: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(140.dp)
            .clickable { onTrigger() }
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(listOf(CyanAccent, BlueAccent, CyanAccent)),
                    shape = CircleShape
                )
        )
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "Scan Fingerprint",
            tint = CyanAccent,
            modifier = Modifier.size(64.dp)
        )
    }
}


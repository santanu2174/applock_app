package com.security.lockguard.ui.components

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.security.lockguard.ml.FaceDetectorHelper
import com.security.lockguard.ml.FaceEmbeddingComparator
import com.security.lockguard.ui.theme.CyanAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FaceScannerOverlay(
    faceDetector: FaceDetectorHelper,
    embeddingComparator: FaceEmbeddingComparator,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var scanState by remember { mutableStateOf("SCANNING") } // SCANNING, OK, FAIL
    var statusText by remember { mutableStateOf("Position your face within the oval") }

    val infiniteTransition = rememberInfiniteTransition(label = "faceScanLine")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineY"
    )

    // Continuous Face Recognition Scanner Loop
    LaunchedEffect(previewViewRef) {
        val preview = previewViewRef ?: return@LaunchedEffect
        var attempts = 0

        // Give camera 1.2s to initialize
        delay(1200)

        while (scanState == "SCANNING" && attempts < 12) {
            val bitmap = preview.bitmap
            if (bitmap != null) {
                try {
                    val faces = faceDetector.detectFaces(bitmap)
                    if (faces.isNotEmpty()) {
                        val candidateVector = faceDetector.extractLandmarkVector(faces.first())
                        if (candidateVector != null) {
                            val isMatch = embeddingComparator.verifyFace(candidateVector)
                            if (isMatch) {
                                scanState = "OK"
                                statusText = "Owner Verified! Unlocking..."
                                delay(600)
                                onSuccess()
                                return@LaunchedEffect
                            } else {
                                attempts++
                                if (attempts >= 4) {
                                    scanState = "FAIL"
                                    statusText = "Stranger detected! Face not recognized."
                                    delay(1200)
                                    onFailure("Stranger detected: face does not match owner.")
                                    return@LaunchedEffect
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            delay(500)
        }

        if (scanState == "SCANNING") {
            scanState = "FAIL"
            statusText = "Face scan timeout. Please try again or use PIN."
            delay(1200)
            onFailure("Face scan timeout")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0B0E))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FACE ID SCAN",
                        color = CyanAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Camera Box with Oval Cutout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        previewViewRef = previewView
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Oval Target Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val ovalWidth = size.width * 0.70f
                    val ovalHeight = size.height * 0.55f
                    val topLeft = Offset((size.width - ovalWidth) / 2f, (size.height - ovalHeight) / 2f)

                    // Outer Vignette
                    drawRect(color = Color(0x99000000))

                    // Transparent Cutout
                    drawOval(
                        color = Color.Transparent,
                        topLeft = topLeft,
                        size = Size(ovalWidth, ovalHeight),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                    )

                    // Oval Border
                    val borderColor = when (scanState) {
                        "OK" -> Color(0xFF00E676)
                        "FAIL" -> Color(0xFFFF5252)
                        else -> CyanAccent
                    }

                    drawOval(
                        color = borderColor,
                        topLeft = topLeft,
                        size = Size(ovalWidth, ovalHeight),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Scanning line inside oval
                    if (scanState == "SCANNING") {
                        val lineY = topLeft.y + ovalHeight * scanLineY
                        drawLine(
                            color = CyanAccent.copy(alpha = 0.8f),
                            start = Offset(topLeft.x + 20f, lineY),
                            end = Offset(topLeft.x + ovalWidth - 20f, lineY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status message
            Text(
                text = statusText,
                color = when (scanState) {
                    "OK" -> Color(0xFF00E676)
                    "FAIL" -> Color(0xFFFF5252)
                    else -> Color.White
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}


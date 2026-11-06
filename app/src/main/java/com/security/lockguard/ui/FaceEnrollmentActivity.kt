package com.security.lockguard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.lifecycleScope
import com.security.lockguard.ml.FaceDetectorHelper
import com.security.lockguard.ml.FaceEmbeddingComparator
import com.security.lockguard.ui.theme.CyanAccent
import com.security.lockguard.ui.theme.LockGuardTheme
import kotlinx.coroutines.launch

class FaceEnrollmentActivity : ComponentActivity() {

    private val faceDetector = FaceDetectorHelper()
    private lateinit var embeddingComparator: FaceEmbeddingComparator

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission is required to enroll Face ID", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        embeddingComparator = FaceEmbeddingComparator(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            LockGuardTheme {
                FaceEnrollmentScreen(
                    faceDetector = faceDetector,
                    onFaceEnrolled = { embedding ->
                        embeddingComparator.enrollOwnerFace(embedding)
                        Toast.makeText(this, "Face ID successfully enrolled and secured!", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@Composable
fun FaceEnrollmentScreen(
    faceDetector: FaceDetectorHelper,
    onFaceEnrolled: (FloatArray) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var enrollmentProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Position your face within the frame") }
    var isSuccess by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E))
    ) {
        // CameraX Live Preview with front camera
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

        // Dark Vignette & Face Oval Overlay Mask
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ovalWidth = size.width * 0.72f
            val ovalHeight = size.height * 0.45f
            val topLeft = Offset((size.width - ovalWidth) / 2f, (size.height - ovalHeight) / 2f - 40.dp.toPx())

            // Outer dark mask
            drawRect(
                color = Color(0xCC0A0B0E)
            )

            // Transparent oval cutout
            drawOval(
                color = Color.Transparent,
                topLeft = topLeft,
                size = Size(ovalWidth, ovalHeight),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )

            // Oval Border Ring
            drawOval(
                color = if (isSuccess) Color(0xFF00E676) else CyanAccent,
                topLeft = topLeft,
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = 3.dp.toPx())
            )

            // Animated Scanning Line
            val lineY = topLeft.y + ovalHeight * scanLineY
            drawLine(
                color = CyanAccent.copy(alpha = 0.7f),
                start = Offset(topLeft.x + 20f, lineY),
                end = Offset(topLeft.x + ovalWidth - 20f, lineY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SET UP FACE ID",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = statusText,
                color = if (statusText.contains("No face") || statusText.contains("error")) Color(0xFFFF5252) else Color.LightGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp)
            )
        }

        // Bottom Controls & Progress
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
        ) {
            LinearProgressIndicator(
                progress = { enrollmentProgress },
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyanAccent,
                trackColor = Color(0xFF1E222D)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val preview = previewViewRef
                    val bitmap = preview?.bitmap
                    if (bitmap == null) {
                        statusText = "Camera warming up, please wait..."
                        return@Button
                    }

                    isProcessing = true
                    statusText = "Scanning facial landmarks with AI..."
                    enrollmentProgress = 0.4f

                    coroutineScope.launch {
                        try {
                            val faces = faceDetector.detectFaces(bitmap)
                            if (faces.isEmpty()) {
                                statusText = "No face detected! Look directly into the camera."
                                enrollmentProgress = 0f
                                isProcessing = false
                                return@launch
                            }

                            val face = faces.first()
                            enrollmentProgress = 0.7f
                            statusText = "Extracting biometric geometric vector..."

                            val vector = faceDetector.extractLandmarkVector(face)
                            if (vector == null) {
                                statusText = "Landmarks incomplete. Ensure good lighting and look straight."
                                enrollmentProgress = 0f
                                isProcessing = false
                                return@launch
                            }

                            enrollmentProgress = 1.0f
                            isSuccess = true
                            statusText = "Face registered successfully!"
                            onFaceEnrolled(vector)
                        } catch (e: Exception) {
                            statusText = "Error: ${e.message}"
                            isProcessing = false
                        }
                    }
                },
                enabled = !isProcessing && !isSuccess,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp)
            ) {
                Text(
                    text = if (isSuccess) "ENROLLED" else if (isProcessing) "ANALYZING..." else "CAPTURE & ENROLL FACE",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

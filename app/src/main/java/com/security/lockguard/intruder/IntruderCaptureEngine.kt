package com.security.lockguard.intruder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntruderCaptureEngine(
    private val context: Context,
    private val storageManager: EncryptedStorageManager = EncryptedStorageManager(context)
) {
    companion object {
        private const val TAG = "LockGuard_CaptureEngine"
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fun executeSilentCapture(lifecycleOwner: LifecycleOwner, onComplete: (File?) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Headless capture: No SurfaceView or Preview use-case bound
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                if (!cameraProvider.hasCamera(cameraSelector)) {
                    Log.e(TAG, "No front camera found on hardware.")
                    onComplete(null)
                    return@addListener
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

                val tempFile = File(context.cacheDir, "temp_intruder_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            fetchLocationAndEncrypt(tempFile, onComplete)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Headless photo capture failed: ${exception.message}", exception)
                            if (tempFile.exists()) tempFile.delete()
                            onComplete(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider binding exception: ${e.message}", e)
                onComplete(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndEncrypt(tempFile: File, onComplete: (File?) -> Unit) {
        try {
            fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                val location: Location? = if (task.isSuccessful) task.result else null
                val encryptedFile = persistEncryptedSnapshot(tempFile, location)
                
                // Securely remove temporary plaintext image
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                onComplete(encryptedFile)
            }
        } catch (e: Exception) {
            val encryptedFile = persistEncryptedSnapshot(tempFile, null)
            if (tempFile.exists()) tempFile.delete()
            onComplete(encryptedFile)
        }
    }

    private fun persistEncryptedSnapshot(sourceFile: File, location: Location?): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val lat = location?.latitude ?: 0.0
        val lon = location?.longitude ?: 0.0
        val filename = "INTRUDER_${timeStamp}_lat${lat}_lon${lon}.enc"

        val targetFile = storageManager.createEncryptedTargetFile(filename)
        storageManager.encryptAndSaveFile(sourceFile, targetFile)
        Log.i(TAG, "Intruder evidence securely encrypted at: ${targetFile.absolutePath}")
        return targetFile
    }
}


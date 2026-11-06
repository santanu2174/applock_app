package com.security.lockguard.ml

import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot

class FaceDetectorHelper {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.20f)
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)

    suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces ->
                continuation.resume(faces)
            }
            .addOnFailureListener {
                continuation.resume(emptyList())
            }
    }

    /**
     * Extracts scale- and translation-invariant normalized landmark geometric descriptor.
     * Normalized relative to nose base and scaled by interpupillary distance (IPD).
     */
    fun extractLandmarkVector(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return null
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position

        // Interpupillary Distance (scale factor)
        val ipd = hypot((leftEye.x - rightEye.x).toDouble(), (leftEye.y - rightEye.y).toDouble()).toFloat()
        if (ipd <= 1f) return null

        val vector = mutableListOf<Float>()

        fun addRelative(pt: PointF?) {
            if (pt != null) {
                vector.add((pt.x - nose.x) / ipd)
                vector.add((pt.y - nose.y) / ipd)
            } else {
                vector.add(0f)
                vector.add(0f)
            }
        }

        // Relative positions from center nose
        addRelative(leftEye)
        addRelative(rightEye)
        addRelative(mouthLeft)
        addRelative(mouthRight)
        addRelative(mouthBottom)
        addRelative(leftCheek)
        addRelative(rightCheek)

        // Geometric proportions (Aspect ratios)
        val mouthWidth = hypot((mouthLeft.x - mouthRight.x).toDouble(), (mouthLeft.y - mouthRight.y).toDouble()).toFloat()
        val eyeToNose = hypot((nose.x - (leftEye.x + rightEye.x) / 2f).toDouble(), (nose.y - (leftEye.y + rightEye.y) / 2f).toDouble()).toFloat()
        val noseToMouth = hypot((mouthBottom.x - nose.x).toDouble(), (mouthBottom.y - nose.y).toDouble()).toFloat()

        vector.add(mouthWidth / ipd)
        vector.add(eyeToNose / ipd)
        vector.add(noseToMouth / ipd)
        vector.add(face.boundingBox.width().toFloat() / face.boundingBox.height().toFloat())

        return vector.toFloatArray()
    }
}

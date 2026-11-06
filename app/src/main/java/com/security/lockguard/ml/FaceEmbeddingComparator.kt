package com.security.lockguard.ml

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import com.security.lockguard.security.KeystoreManager
import kotlin.math.sqrt

class FaceEmbeddingComparator(context: Context) {

    companion object {
        private const val PREFS_NAME = "secure_face_vault"
        private const val KEY_ENROLLED_EMBEDDING = "owner_face_embedding"
        private const val KEY_IS_FACE_ENROLLED = "owner_face_enrolled"
        const val MATCH_THRESHOLD = 0.85f // Match threshold for normalized landmark descriptor
    }

    private val masterKey = KeystoreManager.getOrCreateMasterKey(context)

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isFaceEnrolled(): Boolean = prefs.getBoolean(KEY_IS_FACE_ENROLLED, false)

    fun enrollOwnerFace(embedding: FloatArray) {
        val serialized = embedding.joinToString(",") { it.toString() }
        prefs.edit()
            .putBoolean(KEY_IS_FACE_ENROLLED, true)
            .putString(KEY_ENROLLED_EMBEDDING, serialized)
            .apply()
    }

    fun clearEnrolledFace() {
        prefs.edit()
            .remove(KEY_IS_FACE_ENROLLED)
            .remove(KEY_ENROLLED_EMBEDDING)
            .apply()
    }

    fun verifyFace(candidateEmbedding: FloatArray): Boolean {
        if (!isFaceEnrolled()) return false
        val storedString = prefs.getString(KEY_ENROLLED_EMBEDDING, null) ?: return false
        val enrolledEmbedding = storedString.split(",").map { it.toFloat() }.toFloatArray()

        val similarity = cosineSimilarity(candidateEmbedding, enrolledEmbedding)
        return similarity >= MATCH_THRESHOLD
    }

    fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) return 0.0f

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }

        val denominator = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
        return if (denominator > 0.0) {
            (dotProduct / denominator).toFloat()
        } else {
            0.0f
        }
    }
}

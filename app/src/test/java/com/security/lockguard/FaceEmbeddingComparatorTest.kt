package com.security.lockguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FaceEmbeddingComparatorTest {

    private fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
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
        return if (denominator > 0.0) (dotProduct / denominator).toFloat() else 0.0f
    }

    @Test
    fun testIdenticalFaceVectors_returnsMaximumSimilarity() {
        val vectorA = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val vectorB = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)

        val similarity = cosineSimilarity(vectorA, vectorB)
        assertEquals(1.0f, similarity, 0.001f)
    }

    @Test
    fun testOrthogonalVectors_returnsZeroSimilarity() {
        val vectorA = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vectorB = floatArrayOf(0.0f, 1.0f, 0.0f)

        val similarity = cosineSimilarity(vectorA, vectorB)
        assertEquals(0.0f, similarity, 0.001f)
    }

    @Test
    fun testThresholdVerification() {
        val enrolled = floatArrayOf(0.8f, 0.2f, 0.1f)
        val candidateGood = floatArrayOf(0.79f, 0.21f, 0.11f)
        val candidateBad = floatArrayOf(-0.8f, -0.2f, 0.9f)

        val goodScore = cosineSimilarity(enrolled, candidateGood)
        val badScore = cosineSimilarity(enrolled, candidateBad)

        assertTrue("Good match should exceed 0.72 threshold", goodScore >= 0.72f)
        assertTrue("Bad match should fall below 0.72 threshold", badScore < 0.72f)
    }
}


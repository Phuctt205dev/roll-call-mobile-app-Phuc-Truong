package com.example.roll_call.domain.model.omr

import java.io.Serializable

enum class OmrAnswerStatus : Serializable {
    OK,
    BLANK,
    MULTIPLE,
    UNCERTAIN
}

data class BubbleReadResult(
    val id: String,
    val fillRatio: Double,
    val isFilled: Boolean
) : Serializable

data class OmrQuestionAnswer(
    val questionNumber: Int,
    val answer: String?,
    val status: OmrAnswerStatus,
    val fillRatios: Map<String, Double> = emptyMap()
) : Serializable

data class OmrDebugInfo(
    val warpedWidth: Int,
    val warpedHeight: Int,
    val thresholdUsed: Double,
    val markerCount: Int,
    val sharpnessVariance: Double,
    val debugOverlayPath: String? = null
) : Serializable

data class OmrScanResult(
    val examCode: String,
    val studentCode: String,
    val answers: List<OmrQuestionAnswer>,
    val warnings: List<String>,
    val confidence: Double,
    val debugInfo: OmrDebugInfo
) : Serializable {
    fun answersMap(): Map<String, String> {
        return answers.associate { answer ->
            answer.questionNumber.toString() to (answer.answer ?: answer.status.name)
        }
    }

    fun statusesMap(): Map<String, String> {
        return answers.associate { answer ->
            answer.questionNumber.toString() to answer.status.name
        }
    }
}

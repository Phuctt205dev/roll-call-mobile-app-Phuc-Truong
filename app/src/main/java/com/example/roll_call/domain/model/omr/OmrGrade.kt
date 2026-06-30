package com.example.roll_call.domain.model.omr

import java.io.Serializable

data class OmrGrade(
    val id: String = "",
    val classId: String = "",
    val examId: String = "",
    val classExamInstanceId: String? = null,
    val teacherId: String = "",
    val studentId: String? = null,
    val studentCode: String = "",
    val examCode: String = "",
    val printVersionId: String? = null,
    val answers: Map<String, String> = emptyMap(),
    val answerStatuses: Map<String, String> = emptyMap(),
    val correctAnswers: Map<String, String> = emptyMap(),
    val correctCount: Int = 0,
    val totalQuestions: Int = 40,
    val score: Double = 0.0,
    val maxScore: Double = 10.0,
    val scanConfidence: Double = 0.0,
    val warnings: List<String> = emptyList(),
    val imageDebugInfo: Map<String, Any?> = emptyMap()
) : Serializable {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "classId" to classId,
            "examId" to examId,
            "classExamInstanceId" to classExamInstanceId,
            "teacherId" to teacherId,
            "studentId" to studentId,
            "studentCode" to studentCode,
            "examCode" to examCode,
            "printVersionId" to printVersionId,
            "answers" to answers,
            "answerStatuses" to answerStatuses,
            "correctAnswers" to correctAnswers,
            "correctCount" to correctCount,
            "totalQuestions" to totalQuestions,
            "score" to score,
            "maxScore" to maxScore,
            "scanConfidence" to scanConfidence,
            "warnings" to warnings,
            "imageDebugInfo" to imageDebugInfo
        )
    }

    companion object {
        fun fromFirestoreMap(id: String, data: Map<String, Any?>): OmrGrade {
            return OmrGrade(
                id = id,
                classId = data["classId"] as? String ?: "",
                examId = data["examId"] as? String ?: "",
                classExamInstanceId = data["classExamInstanceId"] as? String,
                teacherId = data["teacherId"] as? String ?: "",
                studentId = data["studentId"] as? String,
                studentCode = data["studentCode"] as? String ?: "",
                examCode = data["examCode"] as? String ?: "",
                printVersionId = data["printVersionId"] as? String,
                answers = readStringMap(data["answers"]),
                answerStatuses = readStringMap(data["answerStatuses"]),
                correctAnswers = readStringMap(data["correctAnswers"]),
                correctCount = (data["correctCount"] as? Number)?.toInt() ?: 0,
                totalQuestions = (data["totalQuestions"] as? Number)?.toInt() ?: 40,
                score = (data["score"] as? Number)?.toDouble() ?: 0.0,
                maxScore = (data["maxScore"] as? Number)?.toDouble() ?: 10.0,
                scanConfidence = (data["scanConfidence"] as? Number)?.toDouble() ?: 0.0,
                warnings = (data["warnings"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                imageDebugInfo = data["imageDebugInfo"] as? Map<String, Any?> ?: emptyMap()
            )
        }

        private fun readStringMap(value: Any?): Map<String, String> {
            return (value as? Map<*, *>)
                ?.mapNotNull { (key, mapValue) ->
                    val stringKey = key as? String ?: return@mapNotNull null
                    val stringValue = mapValue as? String ?: return@mapNotNull null
                    stringKey to stringValue
                }
                ?.toMap()
                ?: emptyMap()
        }
    }
}

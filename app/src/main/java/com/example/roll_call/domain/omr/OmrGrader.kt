package com.example.roll_call.domain.omr

import com.example.roll_call.domain.model.omr.OmrAnswerStatus
import com.example.roll_call.domain.model.omr.OmrGrade
import com.example.roll_call.domain.model.omr.OmrPrintVersion
import com.example.roll_call.domain.model.omr.OmrQuestionAnswer
import com.example.roll_call.domain.model.omr.OmrScanResult
import kotlin.math.round

object OmrGrader {
    fun selectAnswerKey(
        versions: List<OmrPrintVersion>,
        examCode: String,
        preferredVersionId: String? = null
    ): OmrPrintVersion? {
        val normalizedExamCode = normalizeExamCode(examCode)
        fun matches(version: OmrPrintVersion): Boolean {
            if (normalizedExamCode.isBlank()) return false
            return normalizeExamCode(version.examCode) == normalizedExamCode ||
                normalizeExamCode(version.id) == normalizedExamCode
        }

        val preferred = preferredVersionId
            ?.takeIf { it.isNotBlank() && it != "_" }
            ?.let { id -> versions.firstOrNull { it.id == id } }
        if (preferred != null && normalizedExamCode.isBlank()) return preferred
        if (preferred != null && matches(preferred)) return preferred

        return versions.firstOrNull { matches(it) }
            ?: if (normalizedExamCode.isBlank()) {
                preferred ?: versions.firstOrNull { it.examCode.isBlank() && it.answers.isNotEmpty() }
            } else {
                null
            }
    }

    fun gradeAnswers(
        scanResult: OmrScanResult,
        answerKey: OmrPrintVersion,
        classId: String,
        examId: String,
        classExamInstanceId: String?,
        teacherId: String,
        studentId: String?,
        studentCodeOverride: String = scanResult.studentCode,
        examCodeOverride: String = scanResult.examCode,
        answersOverride: Map<Int, String?> = emptyMap(),
        statusesOverride: Map<Int, OmrAnswerStatus> = emptyMap()
    ): OmrGrade {
        val totalQuestions = answerKey.totalQuestions.coerceAtLeast(answerKey.answers.size).coerceAtLeast(1)
        val normalizedCorrect = normalizeAnswerMap(answerKey.answers)

        val finalAnswers = (1..totalQuestions).associateWith { question ->
            val override = answersOverride[question]
            val scanned = scanResult.answers.firstOrNull { it.questionNumber == question }?.answer
            normalizeAnswer(override ?: scanned)
        }
        val finalStatuses = (1..totalQuestions).associateWith { question ->
            statusesOverride[question]
                ?: scanResult.answers.firstOrNull { it.questionNumber == question }?.status
                ?: if (finalAnswers[question].isNullOrBlank()) OmrAnswerStatus.BLANK else OmrAnswerStatus.OK
        }

        val correctCount = (1..totalQuestions).count { question ->
            isAnswerCorrect(
                answer = finalAnswers[question],
                status = finalStatuses[question],
                correctAnswer = normalizedCorrect[question.toString()]
            )
        }
        val maxScore = if (answerKey.maxScore > 0) answerKey.maxScore else 10.0
        val score = roundToTwoDecimals(correctCount.toDouble() / totalQuestions.toDouble() * maxScore)

        val warningSet = scanResult.warnings.toMutableList()
        finalStatuses.filter { it.value != OmrAnswerStatus.OK }.forEach { (question, status) ->
            val message = "C\u00e2u $question: ${status.name}"
            if (message !in warningSet) warningSet += message
        }

        return OmrGrade(
            classId = classId,
            examId = examId,
            classExamInstanceId = classExamInstanceId?.takeIf { it.isNotBlank() && it != "_" },
            teacherId = teacherId,
            studentId = studentId,
            studentCode = studentCodeOverride,
            examCode = examCodeOverride,
            printVersionId = answerKey.id.takeIf { it.isNotBlank() },
            answers = finalAnswers.mapKeys { it.key.toString() }.mapValues { (_, answer) -> answer ?: OmrAnswerStatus.BLANK.name },
            answerStatuses = finalStatuses.mapKeys { it.key.toString() }.mapValues { it.value.name },
            correctAnswers = normalizedCorrect,
            correctCount = correctCount,
            totalQuestions = totalQuestions,
            score = score,
            maxScore = maxScore,
            scanConfidence = scanResult.confidence,
            warnings = warningSet,
            imageDebugInfo = mapOf(
                "warpedWidth" to scanResult.debugInfo.warpedWidth,
                "warpedHeight" to scanResult.debugInfo.warpedHeight,
                "thresholdUsed" to scanResult.debugInfo.thresholdUsed,
                "markerCount" to scanResult.debugInfo.markerCount,
                "sharpnessVariance" to scanResult.debugInfo.sharpnessVariance,
                "debugOverlayPath" to scanResult.debugInfo.debugOverlayPath
            )
        )
    }

    fun normalizeAnswerMap(answers: Map<String, String>): Map<String, String> {
        return answers.mapNotNull { (question, answer) ->
            val normalized = normalizeAnswer(answer) ?: return@mapNotNull null
            normalizeQuestionKey(question) to normalized
        }.toMap()
    }

    fun isAnswerCorrect(answer: String?, status: OmrAnswerStatus?, correctAnswer: String?): Boolean {
        val normalizedAnswer = normalizeAnswer(answer) ?: return false
        val normalizedCorrect = normalizeAnswer(correctAnswer) ?: return false
        val normalizedStatus = status ?: OmrAnswerStatus.BLANK
        if (normalizedStatus == OmrAnswerStatus.BLANK) return false
        return normalizedAnswer == normalizedCorrect
    }

    fun normalizeQuestionAnswers(answers: List<OmrQuestionAnswer>): Map<Int, String?> {
        return answers.associate { it.questionNumber to normalizeAnswer(it.answer) }
    }

    fun normalizeAnswer(answer: String?): String? {
        val normalized = answer?.trim()?.uppercase()
        return if (normalized in setOf("A", "B", "C", "D")) normalized else null
    }

    fun normalizeQuestionKey(question: String?): String {
        val raw = question?.trim().orEmpty()
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotEmpty()) digits.trimStart('0').ifEmpty { "0" } else raw
    }

    fun normalizeExamCode(examCode: String?): String {
        val raw = examCode?.trim().orEmpty()
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotEmpty()) {
            digits.trimStart('0').ifEmpty { "0" }
        } else {
            raw.uppercase()
        }
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}

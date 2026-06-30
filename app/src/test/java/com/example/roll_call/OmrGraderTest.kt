package com.example.roll_call

import com.example.roll_call.domain.model.omr.OmrAnswerStatus
import com.example.roll_call.domain.model.omr.OmrDebugInfo
import com.example.roll_call.domain.model.omr.OmrPrintVersion
import com.example.roll_call.domain.model.omr.OmrQuestionAnswer
import com.example.roll_call.domain.model.omr.OmrScanResult
import com.example.roll_call.domain.omr.OmrGrader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OmrGraderTest {
    @Test
    fun selectAnswerKey_prefersMatchingExamCode() {
        val versions = listOf(
            OmrPrintVersion(id = "v1", examCode = "101", answers = mapOf("1" to "A")),
            OmrPrintVersion(id = "v2", examCode = "202", answers = mapOf("1" to "B"))
        )

        val selected = OmrGrader.selectAnswerKey(versions, examCode = "202", preferredVersionId = "v1")

        assertNotNull(selected)
        assertEquals("v2", selected!!.id)
    }

    @Test
    fun selectAnswerKey_matchesLeadingZeroExamCode() {
        val versions = listOf(
            OmrPrintVersion(id = "v1", examCode = "1", answers = mapOf("1" to "A")),
            OmrPrintVersion(id = "v2", examCode = "2", answers = mapOf("1" to "B"))
        )

        val selected = OmrGrader.selectAnswerKey(versions, examCode = "001")

        assertNotNull(selected)
        assertEquals("v1", selected!!.id)
    }

    @Test
    fun selectAnswerKey_returnsNullWhenExamCodeDoesNotMatchPreferredVersion() {
        val versions = listOf(
            OmrPrintVersion(id = "v1", examCode = "001", answers = mapOf("1" to "A"))
        )

        val selected = OmrGrader.selectAnswerKey(versions, examCode = "946", preferredVersionId = "v1")

        assertNull(selected)
    }

    @Test
    fun gradeAnswers_countsSelectedAnswersWhenTheyMatchCorrectKey() {
        val scan = scanResult(
            listOf(
                OmrQuestionAnswer(1, "A", OmrAnswerStatus.OK),
                OmrQuestionAnswer(2, "C", OmrAnswerStatus.MULTIPLE),
                OmrQuestionAnswer(3, null, OmrAnswerStatus.BLANK),
                OmrQuestionAnswer(4, "D", OmrAnswerStatus.OK)
            )
        )
        val key = OmrPrintVersion(
            id = "v1",
            examCode = "123",
            answers = mapOf("1" to "A", "2" to "C", "3" to "B", "4" to "D"),
            totalQuestions = 4,
            maxScore = 10.0
        )

        val grade = OmrGrader.gradeAnswers(
            scanResult = scan,
            answerKey = key,
            classId = "class1",
            examId = "exam1",
            classExamInstanceId = null,
            teacherId = "teacher1",
            studentId = "student1"
        )

        assertEquals(3, grade.correctCount)
        assertEquals(7.5, grade.score, 0.001)
        assertEquals("MULTIPLE", grade.answerStatuses["2"])
    }

    @Test
    fun gradeAnswers_appliesManualCorrection() {
        val scan = scanResult(listOf(OmrQuestionAnswer(1, null, OmrAnswerStatus.BLANK)))
        val key = OmrPrintVersion(
            id = "v1",
            examCode = "123",
            answers = mapOf("1" to "B"),
            totalQuestions = 1,
            maxScore = 10.0
        )

        val grade = OmrGrader.gradeAnswers(
            scanResult = scan,
            answerKey = key,
            classId = "class1",
            examId = "exam1",
            classExamInstanceId = "ce1",
            teacherId = "teacher1",
            studentId = null,
            answersOverride = mapOf(1 to "B"),
            statusesOverride = mapOf(1 to OmrAnswerStatus.OK)
        )

        assertEquals(1, grade.correctCount)
        assertEquals(10.0, grade.score, 0.001)
        assertEquals("B", grade.answers["1"])
    }

    @Test
    fun gradeAnswers_countsUncertainAnswerWhenItMatchesCorrectAnswer() {
        val scan = scanResult(listOf(OmrQuestionAnswer(1, "A", OmrAnswerStatus.UNCERTAIN)))
        val key = OmrPrintVersion(
            id = "v1",
            examCode = "123",
            answers = mapOf("1" to "A"),
            totalQuestions = 1,
            maxScore = 10.0
        )

        val grade = OmrGrader.gradeAnswers(
            scanResult = scan,
            answerKey = key,
            classId = "class1",
            examId = "exam1",
            classExamInstanceId = null,
            teacherId = "teacher1",
            studentId = "student1"
        )

        assertEquals(1, grade.correctCount)
        assertEquals(10.0, grade.score, 0.001)
        assertEquals("A", grade.correctAnswers["1"])
    }

    @Test
    fun gradeAnswers_matchesZeroPaddedQuestionKeys() {
        val scan = scanResult(listOf(OmrQuestionAnswer(1, "A", OmrAnswerStatus.OK)))
        val key = OmrPrintVersion(
            id = "v1",
            examCode = "123",
            answers = mapOf("01" to "A"),
            totalQuestions = 1,
            maxScore = 10.0
        )

        val grade = OmrGrader.gradeAnswers(
            scanResult = scan,
            answerKey = key,
            classId = "class1",
            examId = "exam1",
            classExamInstanceId = null,
            teacherId = "teacher1",
            studentId = "student1"
        )

        assertEquals(1, grade.correctCount)
        assertEquals("A", grade.correctAnswers["1"])
    }

    private fun scanResult(answers: List<OmrQuestionAnswer>): OmrScanResult {
        return OmrScanResult(
            examCode = "123",
            studentCode = "180560",
            answers = answers,
            warnings = emptyList(),
            confidence = 0.9,
            debugInfo = OmrDebugInfo(1200, 850, 0.35, 8, 120.0)
        )
    }
}

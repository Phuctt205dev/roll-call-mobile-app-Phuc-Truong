package com.example.roll_call

import com.example.roll_call.domain.model.omr.OmrGrade
import org.junit.Assert.assertEquals
import org.junit.Test

class OmrGradeTest {
    @Test
    fun firestoreMap_roundTripsCoreFields() {
        val grade = OmrGrade(
            classId = "class1",
            examId = "exam1",
            classExamInstanceId = "ce1",
            teacherId = "teacher1",
            studentId = "student1",
            studentCode = "180560",
            examCode = "123",
            printVersionId = "v1",
            answers = mapOf("1" to "A"),
            answerStatuses = mapOf("1" to "OK"),
            correctAnswers = mapOf("1" to "A"),
            correctCount = 1,
            totalQuestions = 1,
            score = 10.0,
            maxScore = 10.0,
            scanConfidence = 0.95,
            warnings = listOf("sample"),
            imageDebugInfo = mapOf("warpedWidth" to 1200, "warpedHeight" to 850)
        )

        val copy = OmrGrade.fromFirestoreMap("grade1", grade.toFirestoreMap())

        assertEquals("grade1", copy.id)
        assertEquals("class1", copy.classId)
        assertEquals("180560", copy.studentCode)
        assertEquals(mapOf("1" to "A"), copy.answers)
        assertEquals(10.0, copy.score, 0.001)
        assertEquals(listOf("sample"), copy.warnings)
    }
}

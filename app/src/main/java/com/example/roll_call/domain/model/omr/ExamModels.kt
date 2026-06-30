package com.example.roll_call.domain.model.omr

import java.io.Serializable

data class OmrExam(
    val id: String = "",
    val title: String = "",
    val teacherId: String = "",
    val status: String = "",
    val totalQuestions: Int = 40,
    val maxScore: Double = 10.0,
    val printVersions: List<OmrPrintVersion> = emptyList(),
    val classExamInstanceId: String? = null
) : Serializable

data class OmrPrintVersion(
    val id: String = "",
    val examId: String = "",
    val examCode: String = "",
    val answers: Map<String, String> = emptyMap(),
    val totalQuestions: Int = 40,
    val maxScore: Double = 10.0
) : Serializable

data class OmrClassExam(
    val id: String = "",
    val examId: String = "",
    val title: String = "",
    val status: String = ""
) : Serializable

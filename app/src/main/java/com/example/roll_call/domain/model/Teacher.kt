package com.example.roll_call.domain.model

data class Teacher(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val faceEmbedding: List<Float> = emptyList()  // Khuôn mặt của giáo viên
)

data class TeacherFaceProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val faceEmbedding: List<Float> = emptyList(),
    val photoUrl: String = "",
    val createdAt: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now()
)


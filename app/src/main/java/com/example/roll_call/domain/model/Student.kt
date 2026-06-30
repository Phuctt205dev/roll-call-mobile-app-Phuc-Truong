package com.example.roll_call.domain.model

data class Student(
    val id: String = "",
    val name: String = "",
    val studentCode: String = "",
    val photoUrl: String = "",
    val faceEmbedding: List<Float> = emptyList()
)


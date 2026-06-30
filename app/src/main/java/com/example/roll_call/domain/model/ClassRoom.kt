package com.example.roll_call.domain.model

data class ClassRoom(
    val id: String = "",
    val classCode: String = "",
    val name: String = "",
    val subject: String = "",
    val teacherId: String = "",
    val studentCount: Int = 0
)


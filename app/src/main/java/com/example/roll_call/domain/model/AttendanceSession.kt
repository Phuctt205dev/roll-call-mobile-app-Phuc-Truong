package com.example.roll_call.domain.model

import com.google.firebase.Timestamp

data class AttendanceSession(
    val id: String = "",
    val classId: String = "",
    val className: String = "",
    val sessionName: String = "",
    val sessionNumber: String = "",
    val date: Timestamp = Timestamp.now(),
    val sessionStartTime: Timestamp = Timestamp.now(),
    val teacherId: String = "",
    val totalStudents: Int = 0,
    val presentCount: Int = 0,
    val lateThreshold: Int = 15 // Ngưỡng trễ mặc định là 15 phút
)

data class AttendanceRecord(
    val studentId: String = "",
    val studentName: String = "",
    val studentCode: String = "",
    val status: AttendanceStatus = AttendanceStatus.ABSENT,
    val timestamp: Timestamp? = null
)

enum class AttendanceStatus {
    PRESENT, ABSENT, LATE
}

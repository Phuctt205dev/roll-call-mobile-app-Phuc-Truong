package com.example.roll_call.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ClassList : Screen("class_list")
    object AttendanceSessionList : Screen("attendance_session_list/{classId}/{className}") {
        fun createRoute(classId: String, className: String) = "attendance_session_list/$classId/$className"
    }
    object StudentList : Screen("student_list/{classId}/{className}") {
        fun createRoute(classId: String, className: String) = "student_list/$classId/$className"
    }
    object AddStudent : Screen("add_student/{classId}/{className}") {
        fun createRoute(classId: String, className: String) = "add_student/$classId/$className"
    }
    object FaceScanner : Screen("face_scanner/{sessionId}/{classId}/{className}") {
        fun createRoute(sessionId: String, classId: String, className: String) = "face_scanner/$sessionId/$classId/$className"
    }
    object AttendanceSummary : Screen("attendance_summary/{sessionId}") {
        fun createRoute(sessionId: String) = "attendance_summary/$sessionId"
    }
    object AttendanceHistory : Screen("attendance_history/{classId}/{className}") {
        fun createRoute(classId: String, className: String) = "attendance_history/$classId/$className"
    }
    object TeacherFaceRegistration : Screen("teacher_face_registration")

    object OMRExamList : Screen("omr_exam_list/{classId}/{className}") {
        fun createRoute(classId: String, className: String) =
            "omr_exam_list/${Uri.encode(classId)}/${Uri.encode(className)}"
    }

    object OMRScanner : Screen("omr_scanner/{classId}/{className}/{examId}/{classExamInstanceId}/{printVersionId}") {
        fun createRoute(
            classId: String,
            className: String,
            examId: String,
            classExamInstanceId: String?,
            printVersionId: String?
        ) = "omr_scanner/${Uri.encode(classId)}/${Uri.encode(className)}/${Uri.encode(examId)}/${Uri.encode(classExamInstanceId ?: "_")}/${Uri.encode(printVersionId ?: "_")}"
    }

    object OMRReview : Screen("omr_review/{classId}/{className}/{examId}/{classExamInstanceId}/{printVersionId}") {
        fun createRoute(
            classId: String,
            className: String,
            examId: String,
            classExamInstanceId: String?,
            printVersionId: String?
        ) = "omr_review/${Uri.encode(classId)}/${Uri.encode(className)}/${Uri.encode(examId)}/${Uri.encode(classExamInstanceId ?: "_")}/${Uri.encode(printVersionId ?: "_")}"
    }
}

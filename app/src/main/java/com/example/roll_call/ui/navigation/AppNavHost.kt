package com.example.roll_call.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.roll_call.domain.model.omr.OmrScanResult
import com.example.roll_call.ui.screens.AddStudentScreen
import com.example.roll_call.ui.screens.AttendanceHistoryScreen
import com.example.roll_call.ui.screens.AttendanceSessionListScreen
import com.example.roll_call.ui.screens.AttendanceSummaryScreen
import com.example.roll_call.ui.screens.ClassListScreen
import com.example.roll_call.ui.screens.FaceScannerScreen
import com.example.roll_call.ui.screens.LoginScreen
import com.example.roll_call.ui.screens.OMRExamListScreen
import com.example.roll_call.ui.screens.OMRReviewScreen
import com.example.roll_call.ui.screens.OMRScannerScreen
import com.example.roll_call.ui.screens.StudentListScreen
import com.example.roll_call.ui.screens.TeacherFaceRegistrationScreen

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.ClassList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ClassList.route) {
            ClassListScreen(
                onClassClick = { classId, className ->
                    navController.navigate(Screen.AttendanceSessionList.createRoute(classId, className))
                },
                onOmrClick = { classId, className ->
                    navController.navigate(Screen.OMRExamList.createRoute(classId, className))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRegisterTeacherFace = {
                    navController.navigate(Screen.TeacherFaceRegistration.route)
                }
            )
        }

        composable(
            route = Screen.OMRExamList.route,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = Uri.decode(backStackEntry.arguments?.getString("classId") ?: "")
            val className = Uri.decode(backStackEntry.arguments?.getString("className") ?: "")
            OMRExamListScreen(
                classId = classId,
                className = className,
                onBack = { navController.popBackStack() },
                onScanExam = { examId, classExamInstanceId, printVersionId ->
                    navController.navigate(
                        Screen.OMRScanner.createRoute(classId, className, examId, classExamInstanceId, printVersionId)
                    )
                }
            )
        }

        composable(
            route = Screen.OMRScanner.route,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType },
                navArgument("examId") { type = NavType.StringType },
                navArgument("classExamInstanceId") { type = NavType.StringType },
                navArgument("printVersionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = Uri.decode(backStackEntry.arguments?.getString("classId") ?: "")
            val className = Uri.decode(backStackEntry.arguments?.getString("className") ?: "")
            val examId = Uri.decode(backStackEntry.arguments?.getString("examId") ?: "")
            val classExamInstanceId = Uri.decode(backStackEntry.arguments?.getString("classExamInstanceId") ?: "_")
            val printVersionId = Uri.decode(backStackEntry.arguments?.getString("printVersionId") ?: "_")
            OMRScannerScreen(
                classId = classId,
                className = className,
                examId = examId,
                classExamInstanceId = classExamInstanceId,
                printVersionId = printVersionId,
                onBack = { navController.popBackStack() },
                onOpenReview = { result ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("omr_scan_result", result)
                    navController.navigate(
                        Screen.OMRReview.createRoute(classId, className, examId, classExamInstanceId, printVersionId)
                    )
                }
            )
        }

        composable(
            route = Screen.OMRReview.route,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType },
                navArgument("examId") { type = NavType.StringType },
                navArgument("classExamInstanceId") { type = NavType.StringType },
                navArgument("printVersionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = Uri.decode(backStackEntry.arguments?.getString("classId") ?: "")
            val className = Uri.decode(backStackEntry.arguments?.getString("className") ?: "")
            val examId = Uri.decode(backStackEntry.arguments?.getString("examId") ?: "")
            val classExamInstanceId = Uri.decode(backStackEntry.arguments?.getString("classExamInstanceId") ?: "_")
            val printVersionId = Uri.decode(backStackEntry.arguments?.getString("printVersionId") ?: "_")
            val scanResult = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<OmrScanResult>("omr_scan_result")
                ?: navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.get<OmrScanResult>("omr_scan_result")
            OMRReviewScreen(
                classId = classId,
                className = className,
                examId = examId,
                classExamInstanceId = classExamInstanceId,
                printVersionId = printVersionId,
                scanResult = scanResult,
                onBack = { navController.popBackStack() },
                onDone = {
                    val popped = navController.popBackStack(Screen.OMRExamList.route, false)
                    if (!popped) {
                        navController.navigate(Screen.OMRExamList.createRoute(classId, className))
                    }
                }
            )
        }

        composable(
            route = Screen.AttendanceSessionList.route,
            arguments = listOf<NamedNavArgument>(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            AttendanceSessionListScreen(
                classId = classId,
                className = className,
                onSessionClick = { sessionId, classIdParam, classNameParam ->
                    navController.navigate(Screen.FaceScanner.createRoute(sessionId, classIdParam, classNameParam))
                },
                onManageStudents = {
                    navController.navigate(Screen.AddStudent.createRoute(classId, className))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.StudentList.route,
            arguments = listOf<NamedNavArgument>(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            StudentListScreen(
                classId = classId,
                className = className,
                onStartAttendance = {
                    navController.navigate(Screen.FaceScanner.createRoute("", classId, className))
                },
                onManageStudents = {
                    navController.navigate(Screen.AddStudent.createRoute(classId, className))
                },
                onViewHistory = {
                    navController.navigate(Screen.AttendanceHistory.createRoute(classId, className))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddStudent.route,
            arguments = listOf<NamedNavArgument>(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            AddStudentScreen(
                classId = classId,
                className = className,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FaceScanner.route,
            arguments = listOf<NamedNavArgument>(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            FaceScannerScreen(
                sessionId = sessionId,
                classId = classId,
                className = className,
                onFinish = { finalSessionId ->
                    navController.navigate(Screen.AttendanceSummary.createRoute(finalSessionId)) {
                        popUpTo(Screen.FaceScanner.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AttendanceSummary.route,
            arguments = listOf<NamedNavArgument>(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            AttendanceSummaryScreen(
                sessionId = sessionId,
                onDone = {
                    navController.navigate(Screen.ClassList.route) {
                        popUpTo(Screen.ClassList.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.AttendanceHistory.route,
            arguments = listOf<NamedNavArgument>(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            AttendanceHistoryScreen(
                classId = classId,
                className = className,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TeacherFaceRegistration.route) {
            TeacherFaceRegistrationScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

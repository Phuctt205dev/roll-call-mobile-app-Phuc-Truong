package com.example.roll_call.data.repository

import com.example.roll_call.domain.model.Teacher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    fun getCurrentTeacherName(): String? {
        return auth.currentUser?.displayName ?: auth.currentUser?.email?.substringBefore("@")
    }

    fun getCurrentTeacherEmail(): String? {
        return auth.currentUser?.email
    }

    suspend fun login(email: String, password: String): Result<Teacher> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("Login failed"))
            val doc = db.collection("teachers").document(uid).get().await()
            val teacher = doc.toObject(Teacher::class.java)?.copy(uid = uid)
                ?: Teacher(uid = uid, email = email, name = email.substringBefore("@"))
            Result.success(teacher)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() = auth.signOut()
}

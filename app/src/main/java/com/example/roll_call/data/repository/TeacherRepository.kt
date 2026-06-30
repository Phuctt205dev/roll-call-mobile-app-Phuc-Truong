package com.example.roll_call.data.repository

import com.example.roll_call.domain.model.TeacherFaceProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TeacherRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val teacherCollection = firestore.collection("teachers")

    suspend fun getTeacherFaceProfile(uid: String): Result<TeacherFaceProfile?> {
        return try {
            val snapshot = teacherCollection.document(uid).get().await()
            if (snapshot.exists()) {
                val profile = snapshot.toObject(TeacherFaceProfile::class.java)
                Result.success(profile)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveTeacherFaceProfile(profile: TeacherFaceProfile): Result<Unit> {
        return try {
            teacherCollection.document(profile.uid).set(profile).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

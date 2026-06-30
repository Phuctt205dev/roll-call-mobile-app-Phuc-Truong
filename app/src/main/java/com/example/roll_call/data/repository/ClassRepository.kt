package com.example.roll_call.data.repository

import com.example.roll_call.domain.model.ClassRoom
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ClassRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getClassesByTeacher(teacherId: String): Result<List<ClassRoom>> {
        return try {
            val snapshot = db.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .await()
            val classes = snapshot.documents.map { doc ->
                val classCode = doc.getString("classCode")
                    ?: doc.getString("code")
                    ?: ""

                // Đếm số sinh viên từ subcollection
                val studentSnapshot = db.collection("classes")
                    .document(doc.id)
                    .collection("students")
                    .get()
                    .await()
                val studentCount = studentSnapshot.documents.size

                doc.toObject(ClassRoom::class.java)?.copy(
                    id = doc.id,
                    classCode = classCode,
                    studentCount = studentCount
                ) ?: ClassRoom()
            }
            Result.success(classes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getClassById(classId: String): Result<ClassRoom> {
        return try {
            val doc = db.collection("classes").document(classId).get().await()
            val classCode = doc.getString("classCode")
                ?: doc.getString("code")
                ?: ""

            // Đếm số sinh viên từ subcollection
            val studentSnapshot = db.collection("classes")
                .document(classId)
                .collection("students")
                .get()
                .await()
            val studentCount = studentSnapshot.documents.size

            val classRoom = doc.toObject(ClassRoom::class.java)?.copy(
                id = doc.id,
                classCode = classCode,
                studentCount = studentCount
            )
                ?: return Result.failure(Exception("Class not found"))
            Result.success(classRoom)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


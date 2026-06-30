package com.example.roll_call.data.repository

import com.example.roll_call.domain.model.Student
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class StudentRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getStudentsByClass(classId: String): Result<List<Student>> {
        return try {
            val snapshot = db.collection("classes")
                .document(classId)
                .collection("students")
                .get()
                .await()
            val students = snapshot.documents.map { doc ->
                val embeddingRaw = doc.get("faceEmbedding") as? List<*>
                val embedding = embeddingRaw?.mapNotNull {
                    when (it) {
                        is Double -> it.toFloat()
                        is Long -> it.toFloat()
                        is Float -> it
                        else -> null
                    }
                } ?: emptyList()
                Student(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    studentCode = doc.getString("studentCode") ?: "",
                    photoUrl = doc.getString("photoUrl") ?: "",
                    faceEmbedding = embedding
                )
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addStudent(classId: String, name: String, studentCode: String): Result<Student> {
        return try {
            val data = mapOf(
                "name" to name,
                "studentCode" to studentCode,
                "photoUrl" to "",
                "faceEmbedding" to emptyList<Float>()
            )
            val ref = db.collection("classes")
                .document(classId)
                .collection("students")
                .add(data)
                .await()
            Result.success(Student(id = ref.id, name = name, studentCode = studentCode))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFaceEmbedding(classId: String, studentId: String, embedding: FloatArray): Result<Unit> {
        return try {
            db.collection("classes")
                .document(classId)
                .collection("students")
                .document(studentId)
                .update("faceEmbedding", embedding.toList())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStudent(classId: String, studentId: String): Result<Unit> {
        return try {
            db.collection("classes")
                .document(classId)
                .collection("students")
                .document(studentId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStudent(classId: String, studentId: String, name: String, studentCode: String): Result<Unit> {
        return try {
            db.collection("classes")
                .document(classId)
                .collection("students")
                .document(studentId)
                .update(mapOf("name" to name, "studentCode" to studentCode))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Kiểm tra mã sinh viên đã tồn tại trong lớp chưa (bỏ qua studentId hiện tại nếu đang sửa)
    suspend fun isStudentCodeExists(classId: String, studentCode: String, excludeId: String = ""): Boolean {
        return try {
            val snapshot = db.collection("classes")
                .document(classId)
                .collection("students")
                .whereEqualTo("studentCode", studentCode)
                .get()
                .await()
            snapshot.documents.any { it.id != excludeId }
        } catch (e: Exception) {
            false
        }
    }
}


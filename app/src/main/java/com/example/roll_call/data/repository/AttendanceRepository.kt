package com.example.roll_call.data.repository

import com.example.roll_call.domain.model.AttendanceRecord
import com.example.roll_call.domain.model.AttendanceSession
import com.example.roll_call.domain.model.AttendanceStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AttendanceRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun createSession(
        classId: String,
        className: String,
        sessionName: String,
        sessionNumber: String,
        teacherId: String,
        totalStudents: Int,
        lateThreshold: Int = 15
    ): Result<String> {
        return try {
            val now = Timestamp.now()
            val session = hashMapOf(
                "classId" to classId,
                "className" to className,
                "sessionName" to sessionName,
                "sessionNumber" to sessionNumber,
                "teacherId" to teacherId,
                "date" to now,
                "sessionStartTime" to now,
                "totalStudents" to totalStudents,
                "presentCount" to 0,
                "lateThreshold" to lateThreshold
            )
            val ref = db.collection("attendanceSessions").add(session).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveAttendanceRecords(
        sessionId: String,
        records: List<AttendanceRecord>
    ): Result<Unit> {
        return try {
            val batch = db.batch()
            val sessionRef = db.collection("attendanceSessions").document(sessionId)
            val presentCount = records.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }

            records.forEach { record ->
                val recordRef = sessionRef.collection("records").document(record.studentId)
                batch.set(
                    recordRef, hashMapOf(
                        "studentId" to record.studentId,
                        "studentName" to record.studentName,
                        "studentCode" to record.studentCode,
                        "status" to record.status.name,
                        "timestamp" to record.timestamp
                    )
                )
            }
            batch.update(sessionRef, "presentCount", presentCount)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSession(sessionId: String): Result<AttendanceSession> {
        return try {
            val doc = db.collection("attendanceSessions").document(sessionId).get().await()
            val session = AttendanceSession(
                id = doc.id,
                classId = doc.getString("classId") ?: "",
                className = doc.getString("className") ?: "",
                sessionName = doc.getString("sessionName") ?: "",
                sessionNumber = doc.getString("sessionNumber") ?: "",
                date = doc.getTimestamp("date") ?: Timestamp.now(),
                sessionStartTime = doc.getTimestamp("sessionStartTime") ?: Timestamp.now(),
                teacherId = doc.getString("teacherId") ?: "",
                totalStudents = (doc.getLong("totalStudents") ?: 0).toInt(),
                presentCount = (doc.getLong("presentCount") ?: 0).toInt(),
                lateThreshold = (doc.getLong("lateThreshold") ?: 15).toInt()
            )
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecords(sessionId: String): Result<List<AttendanceRecord>> {
        return try {
            val snapshot = db.collection("attendanceSessions")
                .document(sessionId)
                .collection("records")
                .get()
                .await()
            val records = snapshot.documents.map { doc ->
                AttendanceRecord(
                    studentId = doc.getString("studentId") ?: "",
                    studentName = doc.getString("studentName") ?: "",
                    studentCode = doc.getString("studentCode") ?: "",
                    status = try {
                        AttendanceStatus.valueOf(doc.getString("status") ?: "ABSENT")
                    } catch (e: Exception) {
                        AttendanceStatus.ABSENT
                    },
                    timestamp = doc.getTimestamp("timestamp")
                )
            }
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessionsByClass(classId: String): Result<List<AttendanceSession>> {
        return try {
            val snapshot = db.collection("attendanceSessions")
                .whereEqualTo("classId", classId)
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            val sessions = snapshot.documents.map { doc ->
                AttendanceSession(
                    id = doc.id,
                    classId = doc.getString("classId") ?: "",
                    className = doc.getString("className") ?: "",
                    sessionName = doc.getString("sessionName") ?: "",
                    sessionNumber = doc.getString("sessionNumber") ?: "",
                    date = doc.getTimestamp("date") ?: com.google.firebase.Timestamp.now(),
                    sessionStartTime = doc.getTimestamp("sessionStartTime") ?: com.google.firebase.Timestamp.now(),
                    teacherId = doc.getString("teacherId") ?: "",
                    totalStudents = (doc.getLong("totalStudents") ?: 0).toInt(),
                    presentCount = (doc.getLong("presentCount") ?: 0).toInt(),
                    lateThreshold = (doc.getLong("lateThreshold") ?: 15).toInt()
                )
            }
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveRecognition(
        sessionId: String,
        record: AttendanceRecord
    ): Result<Unit> {
        return try {
            val sessionRef = db.collection("attendanceSessions").document(sessionId)
            val recordRef = sessionRef.collection("records").document(record.studentId)

            val sessionDoc = sessionRef.get().await()
            val sessionStartTime = sessionDoc.getTimestamp("sessionStartTime") ?: Timestamp.now()
            val lateThreshold = (sessionDoc.getLong("lateThreshold") ?: 15).toInt()
            val studentRecognitionTime = record.timestamp ?: Timestamp.now()

            val gracePeriodMs = lateThreshold * 60 * 1000
            val timeDiffMs = studentRecognitionTime.seconds * 1000 - sessionStartTime.seconds * 1000
            val finalStatus = if (timeDiffMs <= gracePeriodMs) {
                AttendanceStatus.PRESENT
            } else {
                AttendanceStatus.LATE
            }

            val existingRecord = recordRef.get().await()
            val wasAlreadyCounted = existingRecord.exists() &&
                (existingRecord.getString("status") == AttendanceStatus.PRESENT.name ||
                 existingRecord.getString("status") == AttendanceStatus.LATE.name)

            if (!wasAlreadyCounted) {
                val batch = db.batch()
                batch.set(
                    recordRef, hashMapOf(
                        "studentId" to record.studentId,
                        "studentName" to record.studentName,
                        "studentCode" to record.studentCode,
                        "status" to finalStatus.name,
                        "timestamp" to studentRecognitionTime
                    )
                )
                // Atomic increment of presentCount
                batch.update(sessionRef, "presentCount", com.google.firebase.firestore.FieldValue.increment(1))
                batch.commit().await()
            } else {
                // Just update timestamp if already present/late? 
                // Usually once recognized, we don't want to change status from PRESENT to LATE if they scan again later
                // So if wasAlreadyCounted, we do nothing or just log
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            val records = db.collection("attendanceSessions")
                .document(sessionId).collection("records").get().await()
            val batch = db.batch()
            records.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.collection("attendanceSessions").document(sessionId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.example.roll_call.data.repository

import com.example.roll_call.domain.model.Student
import com.example.roll_call.domain.model.omr.OmrClassExam
import com.example.roll_call.domain.model.omr.OmrExam
import com.example.roll_call.domain.model.omr.OmrGrade
import com.example.roll_call.domain.model.omr.OmrPrintVersion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class OmrRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val currentTeacherId: String? get() = auth.currentUser?.uid

    suspend fun getExamsForClass(classId: String): Result<List<OmrExam>> {
        return try {
            currentTeacherId ?: return Result.failure(Exception("Ch\u01b0a \u0111\u0103ng nh\u1eadp"))

            val classExamDocs = db.collection("classes")
                .document(classId)
                .collection("classExams")
                .get()
                .await()
                .documents

            val exams = classExamDocs.mapNotNull { classExamDoc ->
                val classExam = classExamDoc.toClassExam()
                val examDoc = runCatching {
                    db.collection("exams").document(classExam.examId).get().await()
                }.getOrNull()

                val baseExam = if (examDoc?.exists() == true) {
                    examDoc.toOmrExam(classExam.id)
                } else {
                    classExamDoc.toOmrExamFromClassExam(classExam.examId, classExam.id)
                } ?: return@mapNotNull null

                val versions = buildPrintVersions(
                    examId = classExam.examId,
                    examDoc = examDoc,
                    classExamDoc = classExamDoc,
                    fallbackTotalQuestions = baseExam.totalQuestions,
                    fallbackMaxScore = baseExam.maxScore
                )

                baseExam.copy(
                    title = classExam.title.ifBlank { baseExam.title },
                    status = classExam.status.ifBlank { baseExam.status },
                    totalQuestions = versions.firstOrNull()?.totalQuestions ?: baseExam.totalQuestions,
                    maxScore = versions.firstOrNull()?.maxScore ?: baseExam.maxScore,
                    printVersions = versions,
                    classExamInstanceId = classExam.id
                )
            }

            Result.success(exams.sortedBy { it.title.ifBlank { it.id } })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getExamById(examId: String, classExamInstanceId: String? = null): Result<OmrExam> {
        return try {
            val doc = db.collection("exams").document(examId).get().await()
            val exam = doc.toOmrExam(classExamInstanceId)
                ?: return Result.failure(Exception("Kh\u00f4ng t\u00ecm th\u1ea5y b\u00e0i ki\u1ec3m tra"))
            val versions = getPrintVersions(examId).getOrDefault(emptyList())
            Result.success(exam.copy(printVersions = versions))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPrintVersions(
        examId: String,
        classId: String? = null,
        classExamInstanceId: String? = null
    ): Result<List<OmrPrintVersion>> {
        return try {
            val examDoc = db.collection("exams").document(examId).get().await()
            val examMaxScore = readDouble(examDoc, "maxScore") ?: 10.0
            val examTotalQuestions = readInt(examDoc, "totalQuestions") ?: 40
            val classExamDoc = findClassExamDocument(classId, classExamInstanceId, examId)

            val versions = buildPrintVersions(
                examId = examId,
                examDoc = examDoc,
                classExamDoc = classExamDoc,
                fallbackTotalQuestions = classExamDoc?.let { readInt(it, "totalQuestions") } ?: examTotalQuestions,
                fallbackMaxScore = classExamDoc?.let { readDouble(it, "maxScore") } ?: examMaxScore
            )

            Result.success(versions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findStudentByCode(classId: String, studentCode: String): Result<Student?> {
        return try {
            val normalizedCode = studentCode.filter { it.isDigit() }
            if (normalizedCode.isBlank()) return Result.success(null)

            val exactSnapshot = db.collection("classes")
                .document(classId)
                .collection("students")
                .whereEqualTo("studentCode", normalizedCode)
                .limit(1)
                .get()
                .await()
            exactSnapshot.documents.firstOrNull()?.let { doc ->
                return Result.success(doc.toStudent())
            }

            val allStudents = db.collection("classes")
                .document(classId)
                .collection("students")
                .get()
                .await()
                .documents
                .mapNotNull { it.toStudent() }

            Result.success(
                allStudents.firstOrNull { student ->
                    val fullCode = student.studentCode.filter { it.isDigit() }
                    fullCode == normalizedCode || toOmrStudentNumber(fullCode) == normalizedCode
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun saveOmrGrade(classId: String, grade: OmrGrade): Result<String> {
        return try {
            val data = grade.toFirestoreMap().toMutableMap()
            data["createdAt"] = FieldValue.serverTimestamp()
            data["updatedAt"] = FieldValue.serverTimestamp()

            val classRef = db.collection("classes").document(classId)
            val ref = classRef.collection("omrGrades").document()
            val classExamDoc = findClassExamDocument(classId, grade.classExamInstanceId, grade.examId)
            val batch = db.batch()

            batch.set(ref, data)

            if (classExamDoc != null) {
                val summary = grade.toClassExamGradeSummaryMap(ref.id).toMutableMap()
                summary["createdAt"] = FieldValue.serverTimestamp()
                summary["updatedAt"] = FieldValue.serverTimestamp()
                batch.set(
                    classExamDoc.reference
                        .collection("omrGrades")
                        .document(classExamGradeId(grade, ref.id)),
                    summary,
                    SetOptions.merge()
                )
            }

            val studentId = grade.studentId?.takeIf { it.isNotBlank() }
            if (studentId != null) {
                val attemptData = grade.toExamAttemptMap(ref.id).toMutableMap()
                attemptData["submittedAt"] = FieldValue.serverTimestamp()
                attemptData["gradedAt"] = FieldValue.serverTimestamp()
                attemptData["updatedAt"] = FieldValue.serverTimestamp()
                batch.set(
                    db.collection("examAttempts").document(examAttemptId(grade, ref.id)),
                    attemptData,
                    SetOptions.merge()
                )
            }

            batch.commit().await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun OmrGrade.toClassExamGradeSummaryMap(sourceGradeId: String): Map<String, Any?> {
        val percentage = if (maxScore > 0.0) score / maxScore * 100.0 else 0.0
        return mapOf(
            "sourceOmrGradeId" to sourceGradeId,
            "classId" to classId,
            "examId" to examId,
            "classExamInstanceId" to classExamInstanceId,
            "teacherId" to teacherId,
            "studentId" to studentId,
            "studentCode" to studentCode,
            "examCode" to examCode,
            "printVersionId" to printVersionId,
            "score" to score,
            "maxScore" to maxScore,
            "percentage" to percentage,
            "correctCount" to correctCount,
            "totalQuestions" to totalQuestions,
            "answers" to answers,
            "answerStatuses" to answerStatuses,
            "correctAnswers" to correctAnswers,
            "scanConfidence" to scanConfidence,
            "warnings" to warnings,
            "gradingSource" to "omr",
            "status" to "graded"
        )
    }

    private fun OmrGrade.toExamAttemptMap(sourceGradeId: String): Map<String, Any?> {
        val answerResults = answers.mapValues { (questionId, selected) ->
            mapOf(
                "selected" to selected,
                "isCorrect" to (selected == correctAnswers[questionId])
            )
        }

        return mapOf(
            "studentId" to studentId,
            "examId" to examId,
            "classId" to classId,
            "classExamInstanceId" to classExamInstanceId,
            "answers" to answerResults,
            "mcqScore" to score,
            "mcqTotalPoints" to maxScore,
            "score" to score,
            "totalScore" to maxScore,
            "essayScore" to 0.0,
            "essayTotalPoints" to 0.0,
            "essayPending" to false,
            "status" to "graded",
            "source" to "omr",
            "omrGradeId" to sourceGradeId,
            "studentCode" to studentCode,
            "examCode" to examCode,
            "printVersionId" to printVersionId,
            "correctCount" to correctCount,
            "totalQuestions" to totalQuestions
        )
    }

    private fun examAttemptId(grade: OmrGrade, fallbackId: String): String {
        val studentPart = grade.studentId?.takeIf { it.isNotBlank() }
            ?: grade.studentCode.filter { it.isDigit() }.takeIf { it.isNotBlank() }
            ?: fallbackId
        val classPart = grade.classId.takeIf { it.isNotBlank() } ?: "class"
        val examPart = grade.classExamInstanceId?.takeIf { it.isNotBlank() } ?: grade.examId.takeIf { it.isNotBlank() } ?: "exam"
        return "omr_${safeDocumentId(classPart)}_${safeDocumentId(examPart)}_${safeDocumentId(studentPart)}"
    }

    private fun safeDocumentId(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(120).ifBlank { "_" }
    }
    private fun classExamGradeId(grade: OmrGrade, fallbackId: String): String {
        return grade.studentId?.takeIf { it.isNotBlank() }
            ?: grade.studentCode.filter { it.isDigit() }.takeIf { it.isNotBlank() }
            ?: fallbackId
    }

    private suspend fun findClassExamDocument(
        classId: String?,
        classExamInstanceId: String?,
        examId: String
    ): DocumentSnapshot? {
        val normalizedClassId = classId?.takeIf { it.isNotBlank() && it != "_" } ?: return null
        val collection = db.collection("classes").document(normalizedClassId).collection("classExams")
        val normalizedInstanceId = classExamInstanceId?.takeIf { it.isNotBlank() && it != "_" }
        if (normalizedInstanceId != null) {
            val doc = collection.document(normalizedInstanceId).get().await()
            if (doc.exists()) return doc
        }

        return collection
            .whereEqualTo("examId", examId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
    }

    private suspend fun buildPrintVersions(
        examId: String,
        examDoc: DocumentSnapshot?,
        classExamDoc: DocumentSnapshot?,
        fallbackTotalQuestions: Int,
        fallbackMaxScore: Double
    ): List<OmrPrintVersion> {
        val versions = mutableListOf<OmrPrintVersion>()

        if (examDoc?.exists() == true) {
            versions += db.collection("exams")
                .document(examId)
                .collection("printVersions")
                .get()
                .await()
                .documents
                .mapNotNull { doc -> doc.toPrintVersion(examId, fallbackTotalQuestions, fallbackMaxScore, "exam") }

            versions += readEmbeddedPrintVersions(examDoc, examId, fallbackTotalQuestions, fallbackMaxScore, "exam")
            readAnswersFromDocumentOrChildCollection(examDoc).takeIf { it.isNotEmpty() }?.let { answers ->
                versions += OmrPrintVersion(
                    id = "_default",
                    examId = examId,
                    examCode = readStringField(examDoc, listOf("examCode", "code", "versionCode")) ?: "",
                    answers = answers,
                    totalQuestions = fallbackTotalQuestions.coerceAtLeast(answers.size),
                    maxScore = fallbackMaxScore
                )
            }
        }

        if (classExamDoc != null) {
            versions += classExamDoc.reference
                .collection("printVersions")
                .get()
                .await()
                .documents
                .mapNotNull { doc -> doc.toPrintVersion(examId, fallbackTotalQuestions, fallbackMaxScore, "class") }

            versions += readEmbeddedPrintVersions(classExamDoc, examId, fallbackTotalQuestions, fallbackMaxScore, "class")
            readAnswersFromDocumentOrChildCollection(classExamDoc).takeIf { it.isNotEmpty() }?.let { answers ->
                versions += OmrPrintVersion(
                    id = "_class_${classExamDoc.id}",
                    examId = examId,
                    examCode = readStringField(classExamDoc, listOf("examCode", "code", "versionCode")) ?: "",
                    answers = answers,
                    totalQuestions = fallbackTotalQuestions.coerceAtLeast(answers.size),
                    maxScore = readDouble(classExamDoc, "maxScore") ?: fallbackMaxScore
                )
            }
        }

        return versions
            .filter { it.answers.isNotEmpty() }
            .distinctBy { "${it.id}|${it.examCode}|${it.answers.hashCode()}" }
            .sortedWith(
                compareBy<OmrPrintVersion> { version ->
                    if (version.id.startsWith("_class_")) 0 else 1
                }.thenBy { version -> version.examCode.ifBlank { version.id } }
            )
    }

    private fun DocumentSnapshot.toClassExam(): OmrClassExam {
        val examRef = get("examRef") as? DocumentReference
        val examId = readStringField(
            this,
            listOf("examId", "examID", "testId", "quizId", "sourceExamId", "originalExamId")
        ) ?: examRef?.id ?: id

        return OmrClassExam(
            id = id,
            examId = examId,
            title = readStringField(this, listOf("title", "name", "examName", "testName")) ?: "",
            status = getString("status") ?: ""
        )
    }

    private fun DocumentSnapshot.toOmrExam(classExamInstanceId: String?): OmrExam? {
        if (!exists()) return null
        val title = readStringField(this, listOf("title", "name", "examName", "testName", "subject")) ?: id
        val totalQuestions = readInt(this, "totalQuestions")
            ?: readInt(this, "questionCount")
            ?: readAnswersFromDocument(this).size.takeIf { it > 0 }
            ?: 40
        val maxScore = readDouble(this, "maxScore") ?: readDouble(this, "scoreScale") ?: 10.0
        return OmrExam(
            id = id,
            title = title,
            teacherId = getString("teacherId") ?: "",
            status = getString("status") ?: "",
            totalQuestions = totalQuestions,
            maxScore = maxScore,
            classExamInstanceId = classExamInstanceId
        )
    }

    private fun DocumentSnapshot.toOmrExamFromClassExam(examId: String, classExamInstanceId: String): OmrExam {
        val answers = readAnswersFromDocument(this)
        return OmrExam(
            id = examId,
            title = readStringField(this, listOf("title", "name", "examName", "testName")) ?: examId,
            teacherId = getString("teacherId") ?: "",
            status = getString("status") ?: "",
            totalQuestions = readInt(this, "totalQuestions")
                ?: readInt(this, "questionCount")
                ?: answers.size.takeIf { it > 0 }
                ?: 40,
            maxScore = readDouble(this, "maxScore") ?: readDouble(this, "scoreScale") ?: 10.0,
            classExamInstanceId = classExamInstanceId
        )
    }

    private suspend fun DocumentSnapshot.toPrintVersion(
        examId: String,
        fallbackTotalQuestions: Int,
        fallbackMaxScore: Double,
        sourcePrefix: String
    ): OmrPrintVersion? {
        val answers = readAnswersFromDocumentOrChildCollection(this)
        if (answers.isEmpty()) return null
        val rawId = id
        return OmrPrintVersion(
            id = sourceScopedVersionId(sourcePrefix, rawId),
            examId = getString("examId") ?: examId,
            examCode = readStringField(this, listOf("examCode", "code", "versionCode")) ?: rawId,
            answers = answers,
            totalQuestions = readInt(this, "totalQuestions") ?: fallbackTotalQuestions.coerceAtLeast(answers.size),
            maxScore = readDouble(this, "maxScore") ?: fallbackMaxScore
        )
    }

    private fun readEmbeddedPrintVersions(
        doc: DocumentSnapshot,
        examId: String,
        fallbackTotalQuestions: Int,
        fallbackMaxScore: Double,
        sourcePrefix: String
    ): List<OmrPrintVersion> {
        val fields = listOf("printVersions", "versions", "examVersions", "testVersions")
        return fields.flatMap { field ->
            readEmbeddedVersionsValue(
                value = doc.get(field),
                examId = examId,
                fallbackTotalQuestions = fallbackTotalQuestions,
                fallbackMaxScore = fallbackMaxScore,
                sourcePrefix = sourcePrefix,
                fieldName = field
            )
        }
    }

    private fun readEmbeddedVersionsValue(
        value: Any?,
        examId: String,
        fallbackTotalQuestions: Int,
        fallbackMaxScore: Double,
        sourcePrefix: String,
        fieldName: String
    ): List<OmrPrintVersion> {
        return when (value) {
            is Map<*, *> -> value.entries.mapNotNull { (key, rawVersion) ->
                val versionMap = rawVersion as? Map<*, *> ?: return@mapNotNull null
                val rawId = key?.toString() ?: "_${sourcePrefix}_$fieldName"
                versionMap.toPrintVersionFromMap(
                    id = sourceScopedVersionId(sourcePrefix, rawId),
                    examId = examId,
                    fallbackTotalQuestions = fallbackTotalQuestions,
                    fallbackMaxScore = fallbackMaxScore
                )
            }
            is List<*> -> value.mapIndexedNotNull { index, rawVersion ->
                val versionMap = rawVersion as? Map<*, *> ?: return@mapIndexedNotNull null
                val rawId = stringFromMap(versionMap, listOf("id", "versionId", "printVersionId"))
                    ?: "_${sourcePrefix}_${fieldName}_$index"
                val id = sourceScopedVersionId(sourcePrefix, rawId)
                versionMap.toPrintVersionFromMap(id, examId, fallbackTotalQuestions, fallbackMaxScore)
            }
            else -> emptyList()
        }
    }

    private fun sourceScopedVersionId(sourcePrefix: String, rawId: String): String {
        return if (sourcePrefix == "class" && !rawId.startsWith("_class_")) "_class_$rawId" else rawId
    }

    private fun Map<*, *>.toPrintVersionFromMap(
        id: String,
        examId: String,
        fallbackTotalQuestions: Int,
        fallbackMaxScore: Double
    ): OmrPrintVersion? {
        val answers = readAnswersFromRawMap(this)
        if (answers.isEmpty()) return null
        return OmrPrintVersion(
            id = id,
            examId = stringFromMap(this, listOf("examId")) ?: examId,
            examCode = stringFromMap(this, listOf("examCode", "code", "versionCode")) ?: id,
            answers = answers,
            totalQuestions = numberFromMap(this, listOf("totalQuestions", "questionCount"))?.toInt()
                ?: fallbackTotalQuestions.coerceAtLeast(answers.size),
            maxScore = numberFromMap(this, listOf("maxScore", "scoreScale"))?.toDouble() ?: fallbackMaxScore
        )
    }

    private fun readAnswersFromDocument(doc: DocumentSnapshot): Map<String, String> {
        return readAnswersFromRawMap(doc.data ?: emptyMap<String, Any>())
    }

    private suspend fun readAnswersFromDocumentOrChildCollection(doc: DocumentSnapshot): Map<String, String> {
        val embedded = readAnswersFromDocument(doc)
        if (embedded.isNotEmpty()) return embedded
        return readAnswersFromChildCollection(doc)
    }

    private suspend fun readAnswersFromChildCollection(doc: DocumentSnapshot): Map<String, String> {
        return runCatching {
            val answerDocs = doc.reference.collection("answers").get().await().documents
            readAnswersFromAnswerDocuments(answerDocs)
        }.getOrDefault(emptyMap())
    }

    private fun readAnswersFromAnswerDocuments(answerDocs: List<DocumentSnapshot>): Map<String, String> {
        val answers = linkedMapOf<String, String>()
        answerDocs.forEachIndexed { index, doc ->
            val data = doc.data ?: emptyMap<String, Any>()
            val nestedAnswers = readAnswersFromRawMap(data)
            if (nestedAnswers.isNotEmpty()) {
                answers.putAll(nestedAnswers)
            } else {
                val answer = readAnswerFromQuestionMap(data) ?: normalizeAnswerValue(doc.get("value"))
                val questionNumber = questionNumberFrom(doc.id, data, index + 1)
                if (answer != null) answers[questionNumber] = answer
            }
        }
        return answers
    }

    private fun readAnswersFromRawMap(raw: Map<*, *>): Map<String, String> {
        val directAnswerFields = listOf(
            "answers",
            "answerKey",
            "correctAnswers",
            "correctAnswerMap",
            "solutions",
            "solution",
            "omrAnswers"
        )
        directAnswerFields.forEach { field ->
            val answers = readAnswersMap(raw[field])
            if (answers.isNotEmpty()) return answers
        }

        val questionFields = listOf("questions", "questionList", "items")
        questionFields.forEach { field ->
            val answers = readAnswersMap(raw[field])
            if (answers.isNotEmpty()) return answers
        }

        return emptyMap()
    }

    private fun readAnswersMap(value: Any?): Map<String, String> {
        return when (value) {
            is Map<*, *> -> value.entries.mapIndexedNotNull { index, entry ->
                val key = entry.key
                val rawAnswer = entry.value
                val questionMap = rawAnswer as? Map<*, *>
                val questionNumber = questionNumberFrom(key, questionMap, index + 1)
                val answer = if (questionMap != null) {
                    readAnswerFromQuestionMap(questionMap)
                } else {
                    normalizeAnswerValue(rawAnswer)
                }
                if (answer == null) null else questionNumber to answer
            }.toMap()
            is List<*> -> value.mapIndexedNotNull { index, rawItem ->
                val questionMap = rawItem as? Map<*, *>
                val questionNumber = questionNumberFrom(null, questionMap, index + 1)
                val answer = if (questionMap != null) {
                    readAnswerFromQuestionMap(questionMap)
                } else {
                    normalizeAnswerValue(rawItem)
                }
                if (answer == null) null else questionNumber to answer
            }.toMap()
            else -> emptyMap()
        }
    }

    private fun readAnswerFromQuestionMap(question: Map<*, *>): String? {
        val indexAnswer = numberFromMap(
            question,
            listOf("correctOptionIndex", "correctAnswerIndex", "answerIndex", "correctIndex")
        )?.toInt()?.let { indexToLetter(it) }
        if (indexAnswer != null) return indexAnswer

        val directFields = listOf(
            "correctAnswer",
            "correctOption",
            "correctOptionId",
            "correctAnswerId",
            "answer",
            "rightAnswer",
            "correct",
            "correctChoice",
            "correctChoiceId",
            "selectedOption",
            "selectedOptionId",
            "option",
            "optionId",
            "value"
        )
        directFields.forEach { field ->
            normalizeAnswerValue(question[field])?.let { return it }
        }

        val options = question["options"] as? List<*> ?: question["choices"] as? List<*>
        if (options != null) {
            val correctId = stringFromMap(question, listOf("correctAnswerId", "correctOptionId", "answerId"))
            if (!correctId.isNullOrBlank()) {
                options.forEachIndexed { index, option ->
                    val optionMap = option as? Map<*, *> ?: return@forEachIndexed
                    val optionId = stringFromMap(optionMap, listOf("id", "key", "value", "label"))
                    if (optionId == correctId) return indexToLetter(index)
                }
            }

            options.forEachIndexed { index, option ->
                val optionMap = option as? Map<*, *> ?: return@forEachIndexed
                val isCorrect = (optionMap["isCorrect"] as? Boolean)
                    ?: (optionMap["correct"] as? Boolean)
                    ?: (optionMap["isAnswer"] as? Boolean)
                    ?: false
                if (isCorrect) return indexToLetter(index)
            }
        }

        return null
    }

    private fun questionNumberFrom(key: Any?, question: Map<*, *>?, fallback: Int): String {
        val fromQuestion = numberFromMap(
            question ?: emptyMap<Any, Any>(),
            listOf("questionNumber", "number", "order", "index", "position")
        )?.toInt()

        if (fromQuestion != null) {
            return if (fromQuestion <= 0) (fromQuestion + 1).toString() else fromQuestion.toString()
        }

        val keyString = key?.toString()?.trim()
        val numericKey = keyString?.toIntOrNull()
        return numericKey?.toString() ?: fallback.toString()
    }

    private fun normalizeAnswerValue(value: Any?): String? {
        return when (value) {
            is String -> {
                val trimmed = value.trim().uppercase()
                if (trimmed in setOf("A", "B", "C", "D")) return trimmed
                trimmed.toIntOrNull()?.let { return indexToLetter(it) }
                Regex("[ABCD]").find(trimmed)?.value
            }
            is Number -> indexToLetter(value.toInt())
            else -> null
        }
    }

    private fun indexToLetter(index: Int): String? {
        return when (index) {
            0 -> "A"
            1 -> "B"
            2 -> "C"
            3 -> "D"
            4 -> "D"
            else -> null
        }
    }

    private fun readStringField(doc: DocumentSnapshot, fields: List<String>): String? {
        return fields.firstNotNullOfOrNull { field -> doc.getString(field)?.takeIf { it.isNotBlank() } }
    }

    private fun stringFromMap(map: Map<*, *>, fields: List<String>): String? {
        return fields.firstNotNullOfOrNull { field -> map[field]?.toString()?.takeIf { it.isNotBlank() } }
    }

    private fun numberFromMap(map: Map<*, *>, fields: List<String>): Number? {
        return fields.firstNotNullOfOrNull { field -> map[field] as? Number }
    }

    private fun DocumentSnapshot.toStudent(): Student? {
        val code = getString("studentCode") ?: return null
        return Student(
            id = id,
            name = getString("name") ?: "",
            studentCode = code
        )
    }

    private fun toOmrStudentNumber(studentCode: String): String {
        val digits = studentCode.filter { it.isDigit() }
        return when {
            digits.length <= 6 -> digits
            else -> digits.take(2) + digits.takeLast(4)
        }
    }
    private fun readInt(doc: DocumentSnapshot, field: String): Int? {
        return (doc.get(field) as? Number)?.toInt()
    }

    private fun readDouble(doc: DocumentSnapshot, field: String): Double? {
        return (doc.get(field) as? Number)?.toDouble()
    }
}

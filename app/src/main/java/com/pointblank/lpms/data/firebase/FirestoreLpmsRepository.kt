package com.pointblank.lpms.data.firebase

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.pointblank.lpms.core.model.*
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.Messaging
import com.pointblank.lpms.core.util.SessionRules
import com.pointblank.lpms.core.util.Viewer
import com.pointblank.lpms.data.repository.LpmsRepository
import com.pointblank.lpms.data.seed.SeedData
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "FirestoreLpms"

/** How long a write waits for the server before it is treated as "saved on this device, syncing later". */
private const val WRITE_WAIT_MS = 4_000L

/**
 * LPMS data in Cloud Firestore.
 *  - Reads never throw: an offline or denied read gives an empty list (or cached data) and a log line.
 *  - Reads of learners, attendance, progress and feedback are scoped to the signed-in user with filtered queries,
 *    mirroring firestore.rules and [com.pointblank.lpms.core.util.Scoping].
 *  - Writes work offline: Firestore keeps them on the device and uploads them when the network returns, so a write that
 *    the server has not confirmed within a few seconds returns false instead of leaving the screen waiting.
 */
class FirestoreLpmsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val currentUser: () -> User?
) : LpmsRepository {

    // ---- reading

    private suspend fun <T> read(query: Query, label: String, map: (DocumentSnapshot) -> T?): List<T> =
        try {
            query.get().await().documents.mapNotNull(map)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $label", e)
            emptyList()
        }

    private fun col(name: String) = db.collection(name)

    private suspend fun viewer(): Viewer? {
        val u = currentUser() ?: return null
        return Viewer(u.id, u.role, schoolIdFor(u))
    }

    override suspend fun schoolIdFor(user: User): String =
        user.schoolId.ifBlank { schools().firstOrNull { it.name == user.school }?.id ?: "" }

    override suspend fun schools() = read(col(Col.SCHOOLS), "schools") { it.toSchool() }.sortedBy { it.name }
    override suspend fun programmes() = read(col(Col.PROGRAMMES), "programmes") { it.toProgramme() }.sortedBy { it.name }
    override suspend fun enrollments() = read(col(Col.ENROLLMENTS), "enrollments") { it.toEnrollment() }
    override suspend fun sessions() = read(col(Col.SESSIONS), "sessions") { it.toSession() }.sortedBy { it.date }
    override suspend fun reports() = read(col(Col.REPORTS), "reports") { it.toReport() }.sortedByDescending { it.createdAt }
    override suspend fun resources() = read(col(Col.RESOURCES), "resources") { it.toResource() }.sortedByDescending { it.createdAt }

    override suspend fun learners(): List<Learner> {
        val v = viewer() ?: return emptyList()
        val query: Query = when {
            v.role.isStaff -> col(Col.LEARNERS)
            v.role == Role.TEACHER ->
                if (v.schoolId.isBlank()) return emptyList() else col(Col.LEARNERS).whereEqualTo("schoolId", v.schoolId)
            v.role == Role.PARENT -> col(Col.LEARNERS).whereArrayContains("guardianIds", v.id)
            else -> col(Col.LEARNERS).whereEqualTo("userId", v.id)
        }
        return read(query, "learners") { it.toLearner() }.sortedBy { it.name }
    }

    /** Attendance and progress share one scoping rule: staff all, teacher their school, parent/learner via viewerIds. */
    private suspend fun scopedRecords(collection: String): Query? {
        val v = viewer() ?: return null
        return when {
            v.role.isStaff -> col(collection)
            v.role == Role.TEACHER ->
                if (v.schoolId.isBlank()) null else col(collection).whereEqualTo("schoolId", v.schoolId)
            else -> col(collection).whereArrayContains("viewerIds", v.id)
        }
    }

    override suspend fun attendance(): List<AttendanceRecord> {
        val q = scopedRecords(Col.ATTENDANCE) ?: return emptyList()
        return read(q, "attendance") { it.toAttendance() }
    }

    override suspend fun progress(): List<ProgressRecord> {
        val q = scopedRecords(Col.PROGRESS) ?: return emptyList()
        return read(q, "progress") { it.toProgress() }.sortedBy { it.date }
    }

    override suspend fun feedback(): List<FeedbackEntry> {
        val v = viewer() ?: return emptyList()
        val q = if (v.role.isStaff) col(Col.FEEDBACK) else col(Col.FEEDBACK).whereEqualTo("authorId", v.id)
        return read(q, "feedback") { it.toFeedback() }.sortedByDescending { it.createdAt }
    }

    override suspend fun messages(): List<Message> {
        val v = viewer() ?: return emptyList()
        return read(col(Col.MESSAGES).whereArrayContains("participants", v.id), "messages") { it.toMessage() }
            .sortedBy { it.createdAt }
    }

    override suspend fun contacts(): List<Contact> {
        val v = viewer() ?: return emptyList()
        if (v.role == Role.LEARNER) return emptyList()
        val people = mutableListOf<Contact>()
        if (v.role.isStaff) {
            // Staff can read every profile.
            people += read(col(Col.USERS), "users") { doc ->
                val u = doc.toUser()
                if (u.approved && u.active) {
                    Contact(u.id, u.displayName, u.role, u.schoolId,
                        u.grade, if (u.role == Role.PARENT && u.childName.isNotBlank()) "Parent of ${u.childName}" else "")
                } else null
            }
        } else {
            // Everyone else reads the staff directory, which holds teachers, coordinators and admins only.
            people += read(col(Col.DIRECTORY), "directory") { it.toContact() }
            if (v.role == Role.TEACHER) {
                // A teacher can message the parents of their learners (names are kept on the learner record).
                learners().forEach { l ->
                    l.guardianNames.forEach { (id, name) ->
                        people += Contact(id, name, Role.PARENT, l.schoolId, "", "Parent of ${l.name}")
                    }
                }
            }
        }
        return people.distinctBy { it.userId }
    }

    override suspend fun visits(): List<SchoolVisit> {
        if (viewer()?.role?.isStaff != true) return emptyList()
        return read(col(Col.VISITS), "visits") { it.toVisit() }.sortedByDescending { it.date }
    }

    override suspend fun audit(): List<AuditEntry> {
        if (viewer()?.role != Role.ADMIN) return emptyList()
        return read(col(Col.AUDIT), "audit") { it.toAudit() }.sortedByDescending { it.at }.take(200)
    }

    override suspend fun classRoster(schoolId: String, grade: String): List<Learner> {
        val gradeNumber = LearnerInsights.parseGrade(grade) ?: return emptyList()
        if (schoolId.isBlank()) return emptyList()
        return learners().filter { it.schoolId == schoolId && it.grade == gradeNumber }
    }

    // ---- writing

    /** True when the server confirmed the write; false when it is saved on this device and queued. Failures throw. */
    private suspend fun Task<Void>.synced(): Boolean =
        withTimeoutOrNull(WRITE_WAIT_MS) { this@synced.await(); true } ?: false

    private suspend fun commitAll(batches: List<com.google.firebase.firestore.WriteBatch>): Boolean {
        var allSynced = true
        batches.forEach { if (!it.commit().synced()) allSynced = false }
        return allSynced
    }

    override suspend fun saveAttendance(sessionId: String, marks: Map<String, Boolean>, by: User): Boolean {
        val sessionDoc = col(Col.SESSIONS).document(sessionId).get().await()
        val session = if (sessionDoc.exists()) sessionDoc.toSession() else throw IllegalStateException("Session not found")
        if (!SessionRules.canMarkAttendance(by.role, session)) {
            throw IllegalStateException("This session is ${SessionStatus.label(session.status).lowercase()}, so attendance can't be changed")
        }
        val visible = learners().associateBy { it.id }
        val now = System.currentTimeMillis()
        val batch = db.batch()
        marks.forEach { (learnerId, present) ->
            val learner = visible[learnerId] ?: return@forEach // outside this user's scope: ignore
            val ref = col(Col.ATTENDANCE).document("${sessionId}_$learnerId")
            batch.set(
                ref,
                AttendanceRecord(ref.id, sessionId, learnerId, present, learner.schoolId, learner.viewerIds(), by.displayName, now).toMap()
            )
        }
        return batch.commit().synced()
    }

    override suspend fun addProgress(learnerId: String, programme: String, level: Int, by: User): Boolean {
        val learner = learners().firstOrNull { it.id == learnerId }
            ?: throw IllegalStateException("You can only record progress for learners at your own school")
        val record = ProgressRecord(
            UUID.randomUUID().toString(), learnerId, programme, level.coerceIn(1, 5),
            java.time.LocalDate.now().toString(), learner.schoolId, learner.viewerIds(), by.displayName
        )
        return col(Col.PROGRESS).document(record.id).set(record.toMap()).synced()
    }

    override suspend fun addSession(session: Session) = col(Col.SESSIONS).document(session.id).set(session.toMap()).synced()

    override suspend fun setSessionStatus(sessionId: String, status: String) =
        col(Col.SESSIONS).document(sessionId).update("status", status).synced()

    override suspend fun sendMessage(message: Message): Boolean {
        Messaging.validate(message.text)?.let { throw IllegalArgumentException(it) }
        return col(Col.MESSAGES).document(message.id).set(message.copy(text = message.text.trim()).toMap()).synced()
    }

    override suspend fun markMessagesRead(ids: List<String>): Boolean {
        if (ids.isEmpty()) return true
        val batches = ids.chunked(400).map { chunk ->
            db.batch().also { b -> chunk.forEach { b.update(col(Col.MESSAGES).document(it), "read", true) } }
        }
        return commitAll(batches)
    }

    override suspend fun addSchool(school: School) = col(Col.SCHOOLS).document(school.id).set(school.toMap()).synced()

    override suspend fun upgradeLibrary(schoolId: String, today: String): Boolean {
        val ref = col(Col.SCHOOLS).document(schoolId)
        val level = ref.get().await().toSchool()?.libraryLevel ?: return true
        if (level >= 3) return true
        return ref.update(mapOf("libraryLevel" to (level + 1).toLong(), "lastUpgrade" to today)).synced()
    }

    override suspend fun addLearner(name: String, schoolId: String, grade: Int): Boolean {
        val id = "l-" + UUID.randomUUID().toString().take(8)
        return col(Col.LEARNERS).document(id).set(Learner(id, null, name.trim(), grade, schoolId).toMap()).synced()
    }

    override suspend fun linkAccount(learnerId: String, user: User): Boolean {
        val learnerRef = col(Col.LEARNERS).document(learnerId)
        val learnerBatch = db.batch()
        if (user.role == Role.PARENT) {
            learnerBatch.update(learnerRef, "guardianIds", FieldValue.arrayUnion(user.id))
            learnerBatch.update(learnerRef, "guardianNames.${user.id}", user.displayName)
        } else learnerBatch.update(learnerRef, "userId", user.id)

        // Give the new viewer the learner's existing records too (they carry their own viewerIds).
        val recordRefs = mutableListOf<DocumentReference>()
        listOf(Col.ATTENDANCE, Col.PROGRESS).forEach { name ->
            try {
                col(name).whereEqualTo("learnerId", learnerId).get().await().documents.forEach { recordRefs += it.reference }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not list $name for linking", e)
            }
        }
        val batches = mutableListOf(learnerBatch)
        recordRefs.chunked(400).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { b.update(it, "viewerIds", FieldValue.arrayUnion(user.id)) }
            batches += b
        }
        return commitAll(batches)
    }

    override suspend fun addVisit(visit: SchoolVisit) = col(Col.VISITS).document(visit.id).set(visit.toMap()).synced()
    override suspend fun addFeedback(entry: FeedbackEntry) = col(Col.FEEDBACK).document(entry.id).set(entry.toMap()).synced()
    override suspend fun addResource(resource: Resource) = col(Col.RESOURCES).document(resource.id).set(resource.toMap()).synced()
    override suspend fun setResourceShared(id: String, shared: Boolean) = col(Col.RESOURCES).document(id).update("shared", shared).synced()
    override suspend fun addReport(report: Report) = col(Col.REPORTS).document(report.id).set(report.toMap()).synced()
    override suspend fun logAudit(entry: AuditEntry) = col(Col.AUDIT).document(entry.id).set(entry.toMap()).synced()

    override suspend fun seedDemoData(): Int {
        val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()
        fun add(collection: String, id: String, data: Map<String, Any?>) {
            writes += col(collection).document(id) to data
        }
        SeedData.schools.forEach { add(Col.SCHOOLS, it.id, it.toMap()) }
        SeedData.programmes.forEach { add(Col.PROGRAMMES, it.id, it.toMap()) }
        // Account links (userId, guardianIds, viewerIds) are left out so links made earlier survive a re-run;
        // DemoDataSeeder re-applies them after the accounts exist.
        SeedData.learners.forEach { add(Col.LEARNERS, it.id, it.toMap(includeLinks = false)) }
        SeedData.enrollments.forEach { add(Col.ENROLLMENTS, it.id, it.toMap()) }
        SeedData.sessions.forEach { add(Col.SESSIONS, it.id, it.toMap()) }
        SeedData.progress.forEach { add(Col.PROGRESS, it.id, it.toMap(includeViewers = false)) }
        SeedData.attendance.forEach {
            add(Col.ATTENDANCE, "${it.sessionId}_${it.learnerId}", it.copy(markedAt = System.currentTimeMillis()).toMap(includeViewers = false))
        }
        SeedData.reports.forEach { add(Col.REPORTS, it.id, it.toMap()) }
        SeedData.feedback.forEach { add(Col.FEEDBACK, it.id, it.toMap()) }
        SeedData.resources.forEach { add(Col.RESOURCES, it.id, it.toMap()) }
        SeedData.visits.forEach { add(Col.VISITS, it.id, it.toMap()) }

        writes.chunked(400).forEach { chunk -> // a batch holds at most 500 writes
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data, SetOptions.merge()) }
            batch.commit().await() // the demo loader needs a real server answer, so it waits
        }
        return writes.size
    }
}

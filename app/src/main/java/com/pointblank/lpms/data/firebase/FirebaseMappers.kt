package com.pointblank.lpms.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.pointblank.lpms.core.model.*

/** Firestore collection names. */
internal object Col {
    const val USERS = "users"
    const val SCHOOLS = "schools"
    const val PROGRAMMES = "programmes"
    const val LEARNERS = "learners"
    const val ENROLLMENTS = "enrollments"
    const val SESSIONS = "sessions"
    const val PROGRESS = "progress"
    const val ATTENDANCE = "attendance"
    const val REPORTS = "reports"
    const val FEEDBACK = "feedback"
    const val RESOURCES = "resources"
    const val VISITS = "visits"
    const val AUDIT = "audit"
    const val MESSAGES = "messages"
    /** Public staff and teacher directory (names only) so parents can pick who to message. */
    const val DIRECTORY = "directory"
}

private fun DocumentSnapshot.int(field: String): Int = getLong(field)?.toInt() ?: 0

private fun DocumentSnapshot.strings(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

// ---- users (passwords are held by Firebase Auth, never stored here)
internal fun DocumentSnapshot.toUser(): User = User(
    id = id,
    email = getString("email") ?: "",
    displayName = getString("displayName") ?: (getString("email") ?: "User"),
    role = runCatching { Role.valueOf(getString("role") ?: "PARENT") }.getOrDefault(Role.PARENT),
    passwordHash = "",
    active = getBoolean("active") ?: true,
    school = getString("school") ?: "",
    grade = getString("grade") ?: "",
    approved = getBoolean("approved") ?: true, // accounts made before approval existed stay approved
    schoolId = getString("schoolId") ?: "",
    childName = getString("childName") ?: ""
)

internal fun User.toMap(): Map<String, Any?> = mapOf(
    "email" to email, "displayName" to displayName, "role" to role.name,
    "active" to active, "school" to school, "grade" to grade, "approved" to approved,
    "schoolId" to schoolId, "childName" to childName
)

// ---- schools
internal fun DocumentSnapshot.toSchool(): School? {
    val name = getString("name") ?: return null
    return School(
        id, name, getString("district") ?: "", getString("coordinatorId") ?: "",
        startDate = getString("startDate") ?: "",
        libraryLevel = getLong("libraryLevel")?.toInt() ?: 1,
        lastUpgrade = getString("lastUpgrade") ?: ""
    )
}
internal fun School.toMap(): Map<String, Any?> = mapOf(
    "name" to name, "district" to district, "coordinatorId" to coordinatorId,
    "startDate" to startDate, "libraryLevel" to libraryLevel.toLong(), "lastUpgrade" to lastUpgrade
)

// ---- programmes
internal fun DocumentSnapshot.toProgramme(): Programme? {
    val name = getString("name") ?: return null
    return Programme(id, name, getString("schoolId") ?: "", getString("description") ?: "", getBoolean("active") ?: true)
}
internal fun Programme.toMap(): Map<String, Any?> =
    mapOf("name" to name, "schoolId" to schoolId, "description" to description, "active" to active)

// ---- learners. Seed writes leave out userId and guardianIds so existing account links are kept.
internal fun DocumentSnapshot.toLearner(): Learner? {
    val name = getString("name") ?: return null
    val names = (get("guardianNames") as? Map<*, *>)?.entries
        ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }?.toMap() ?: emptyMap()
    return Learner(id, getString("userId"), name, int("grade"), getString("schoolId") ?: "", strings("guardianIds"), names)
}
internal fun Learner.toMap(includeLinks: Boolean = true): Map<String, Any?> {
    val base = mapOf("name" to name, "grade" to grade.toLong(), "schoolId" to schoolId)
    return if (includeLinks) base + mapOf("userId" to userId, "guardianIds" to guardianIds, "guardianNames" to guardianNames) else base
}

// ---- enrollments
internal fun DocumentSnapshot.toEnrollment() =
    Enrollment(id, getString("learnerId") ?: "", getString("programmeId") ?: "")
internal fun Enrollment.toMap(): Map<String, Any?> = mapOf("learnerId" to learnerId, "programmeId" to programmeId)

// ---- sessions
internal fun DocumentSnapshot.toSession() = Session(
    id, getString("programme") ?: "", getString("schoolId") ?: "",
    getString("date") ?: "", getString("facilitator") ?: "",
    status = getString("status") ?: SessionStatus.SCHEDULED,
    createdByName = getString("createdByName") ?: ""
)
internal fun Session.toMap(): Map<String, Any?> = mapOf(
    "programme" to programme, "schoolId" to schoolId, "date" to date, "facilitator" to facilitator,
    "status" to status, "createdByName" to createdByName
)

// ---- attendance (document id is "<sessionId>_<learnerId>", so saving again overwrites).
// schoolId and viewerIds are copied from the learner so security rules can limit who may read each record.
internal fun DocumentSnapshot.toAttendance() = AttendanceRecord(
    id, getString("sessionId") ?: "", getString("learnerId") ?: "", getBoolean("present") ?: false,
    schoolId = getString("schoolId") ?: "", viewerIds = strings("viewerIds"),
    markedByName = getString("markedByName") ?: "", markedAt = getLong("markedAt") ?: 0L
)
internal fun AttendanceRecord.toMap(includeViewers: Boolean = true): Map<String, Any?> {
    val base = mapOf(
        "sessionId" to sessionId, "learnerId" to learnerId, "present" to present, "schoolId" to schoolId,
        "markedByName" to markedByName, "markedAt" to markedAt
    )
    return if (includeViewers) base + ("viewerIds" to viewerIds) else base
}

// ---- progress
internal fun DocumentSnapshot.toProgress() = ProgressRecord(
    id, getString("learnerId") ?: "", getString("programme") ?: "", int("level"), getString("date") ?: "",
    schoolId = getString("schoolId") ?: "", viewerIds = strings("viewerIds"),
    recordedByName = getString("recordedByName") ?: ""
)
internal fun ProgressRecord.toMap(includeViewers: Boolean = true): Map<String, Any?> {
    val base = mapOf(
        "learnerId" to learnerId, "programme" to programme, "level" to level.toLong(), "date" to date,
        "schoolId" to schoolId, "recordedByName" to recordedByName
    )
    return if (includeViewers) base + ("viewerIds" to viewerIds) else base
}

// ---- reports
internal fun DocumentSnapshot.toReport() =
    Report(id, getString("title") ?: "", getString("programmeId") ?: "", getString("createdAt") ?: "")
internal fun Report.toMap(): Map<String, Any?> =
    mapOf("title" to title, "programmeId" to programmeId, "createdAt" to createdAt)

// ---- feedback
internal fun DocumentSnapshot.toFeedback() = FeedbackEntry(
    id, getString("authorId") ?: "", getString("author") ?: "", getString("subject") ?: "",
    getString("text") ?: "", int("stars"), getLong("createdAt") ?: 0L
)
internal fun FeedbackEntry.toMap(): Map<String, Any?> = mapOf(
    "authorId" to authorId, "author" to author, "subject" to subject,
    "text" to text, "stars" to stars.toLong(), "createdAt" to createdAt
)

// ---- resources
internal fun DocumentSnapshot.toResource() = Resource(
    id, getString("title") ?: "", getString("type") ?: "", getString("uploadedBy") ?: "",
    getBoolean("shared") ?: false, getLong("createdAt") ?: 0L
)
internal fun Resource.toMap(): Map<String, Any?> = mapOf(
    "title" to title, "type" to type, "uploadedBy" to uploadedBy, "shared" to shared, "createdAt" to createdAt
)

// ---- school visits
internal fun DocumentSnapshot.toVisit() = SchoolVisit(
    id, getString("schoolId") ?: "", getString("schoolName") ?: "", getString("visitorId") ?: "",
    getString("visitorName") ?: "", getString("date") ?: "", getString("teacherObserved") ?: "",
    getString("notes") ?: "", getString("followUp") ?: "", getLong("createdAt") ?: 0L
)
internal fun SchoolVisit.toMap(): Map<String, Any?> = mapOf(
    "schoolId" to schoolId, "schoolName" to schoolName, "visitorId" to visitorId, "visitorName" to visitorName,
    "date" to date, "teacherObserved" to teacherObserved, "notes" to notes, "followUp" to followUp,
    "createdAt" to createdAt
)

// ---- audit log
internal fun DocumentSnapshot.toAudit() = AuditEntry(
    id, getString("actorId") ?: "", getString("actorName") ?: "", getString("action") ?: "",
    getString("target") ?: "", getLong("at") ?: 0L
)
internal fun AuditEntry.toMap(): Map<String, Any?> = mapOf(
    "actorId" to actorId, "actorName" to actorName, "action" to action, "target" to target, "at" to at
)

// ---- messages (participants lets each side query only their own messages)
internal fun DocumentSnapshot.toMessage() = Message(
    id, getString("fromId") ?: "", getString("fromName") ?: "", getString("toId") ?: "", getString("toName") ?: "",
    getString("text") ?: "", getLong("createdAt") ?: 0L, getBoolean("read") ?: false
)
internal fun Message.toMap(): Map<String, Any?> = mapOf(
    "fromId" to fromId, "fromName" to fromName, "toId" to toId, "toName" to toName, "text" to text,
    "createdAt" to createdAt, "read" to read, "participants" to participants
)

// ---- staff directory: names only, never emails
internal fun DocumentSnapshot.toContact(): Contact? {
    val name = getString("displayName") ?: return null
    val role = runCatching { Role.valueOf(getString("role") ?: "") }.getOrNull() ?: return null
    return Contact(id, name, role, getString("schoolId") ?: "", getString("grade") ?: "")
}
internal fun User.toDirectoryMap(): Map<String, Any?> =
    mapOf("displayName" to displayName, "role" to role.name, "schoolId" to schoolId, "grade" to grade)

package com.pointblank.lpms.core.model

/** Mirrors the LPMS web portal roles, plus Parent (guardians have the logins; young learners are records). */
enum class Role(val label: String) {
    ADMIN("Administrator"),
    COORDINATOR("Field Coordinator"),
    TEACHER("Teacher"),
    PARENT("Parent / Guardian"),
    LEARNER("Learner")
}

val Role.isStaff: Boolean get() = this == Role.ADMIN || this == Role.COORDINATOR

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val role: Role,
    val passwordHash: String,
    val active: Boolean = true,
    val school: String = "",
    val grade: String = "",
    /** Self-registered accounts start unapproved; staff approve them before they can sign in. */
    val approved: Boolean = true,
    /** Id of the school (the name in [school] is only for display, so renaming a school cannot break links). */
    val schoolId: String = "",
    /** Parents: the name of the child they are registering for, so staff can link the right learner. */
    val childName: String = ""
)

data class School(
    val id: String,
    val name: String,
    val district: String,
    val coordinatorId: String,
    /** First day of the three-year READ programme at this school (ISO yyyy-MM-dd), or empty if unknown. */
    val startDate: String = "",
    /** Box-library level delivered so far (1 to 3). */
    val libraryLevel: Int = 1,
    val lastUpgrade: String = ""
)

data class Programme(
    val id: String,
    val name: String,
    val schoolId: String,
    val description: String,
    val active: Boolean = true
)

/** [grade] 0 means Grade R. */
data class Learner(
    val id: String,
    val userId: String?,
    val name: String,
    val grade: Int,
    val schoolId: String,
    val guardianIds: List<String> = emptyList(),
    /** Parent account id to display name, so teachers can message a learner's parents without reading their profiles. */
    val guardianNames: Map<String, String> = emptyMap()
)

/** Everyone with a login who may see this learner's records: their parents and the learner. */
fun Learner.viewerIds(): List<String> = (guardianIds + listOfNotNull(userId)).distinct()

data class Enrollment(
    val id: String,
    val learnerId: String,
    val programmeId: String
)

/** A session's life: scheduled (open for attendance), then completed (closed) or cancelled. */
object SessionStatus {
    const val SCHEDULED = "SCHEDULED"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"

    fun label(status: String): String = when (status) {
        COMPLETED -> "Completed"
        CANCELLED -> "Cancelled"
        else -> "Scheduled"
    }
}

data class Session(
    val id: String,
    val programme: String,
    val schoolId: String,
    val date: String, // ISO yyyy-MM-dd
    val facilitator: String,
    val status: String = SessionStatus.SCHEDULED,
    val createdByName: String = ""
)

/** One row per learner per session. [markedByName] and [markedAt] record who saved it and when. */
data class AttendanceRecord(
    val id: String,
    val sessionId: String,
    val learnerId: String,
    val present: Boolean,
    val schoolId: String = "",
    val viewerIds: List<String> = emptyList(),
    val markedByName: String = "",
    val markedAt: Long = 0L
)

data class ProgressRecord(
    val id: String,
    val learnerId: String,
    val programme: String,
    val level: Int,
    val date: String, // ISO yyyy-MM-dd
    val schoolId: String = "",
    val viewerIds: List<String> = emptyList(),
    val recordedByName: String = ""
)

data class Report(
    val id: String,
    val title: String,
    val programmeId: String,
    val createdAt: String // ISO yyyy-MM-dd
)

data class FeedbackEntry(
    val id: String,
    val authorId: String,
    val author: String,
    val subject: String,
    val text: String,
    val stars: Int,
    val createdAt: Long // epoch millis
)

data class Resource(
    val id: String,
    val title: String,
    val type: String,
    val uploadedBy: String,
    val shared: Boolean,
    val createdAt: Long // epoch millis
)

/** A field coordinator's visit to a school to monitor and mentor, which is how READ supports teachers. */
data class SchoolVisit(
    val id: String,
    val schoolId: String,
    val schoolName: String,
    val visitorId: String,
    val visitorName: String,
    val date: String, // ISO yyyy-MM-dd
    val teacherObserved: String,
    val notes: String,
    val followUp: String,
    val createdAt: Long
)

/** Who did what, and when, for administrative actions. */
data class AuditEntry(
    val id: String,
    val actorId: String,
    val actorName: String,
    val action: String,
    val target: String,
    val at: Long
)

/** A message between two people. [participants] is [fromId] and [toId], so each side can query their own messages. */
data class Message(
    val id: String,
    val fromId: String,
    val fromName: String,
    val toId: String,
    val toName: String,
    val text: String,
    val createdAt: Long, // epoch millis
    val read: Boolean = false
) {
    val participants: List<String> get() = listOf(fromId, toId)
}

/** Someone the signed-in user may start a conversation with. [note] explains who they are, e.g. "Parent of Thabo". */
data class Contact(
    val userId: String,
    val name: String,
    val role: Role,
    val schoolId: String = "",
    val grade: String = "",
    val note: String = ""
)

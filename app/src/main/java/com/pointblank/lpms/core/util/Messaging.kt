package com.pointblank.lpms.core.util

import com.pointblank.lpms.core.model.*

/** One conversation as shown in the inbox: everything with a single other person. */
data class Conversation(
    val otherId: String,
    val otherName: String,
    val lastText: String,
    val lastAt: Long,
    val unread: Int
)

/**
 * Who may message whom, and how messages are grouped.
 *  - A parent can message the teachers at their child's school and the coordinators and admins.
 *  - A teacher can message coordinators, admins, other teachers at their school, and the parents of their learners.
 *  - Staff can message anyone with an approved, active account.
 *  - Learners do not message.
 * firestore.rules enforces the role pairs on the server.
 */
object Messaging {
    const val MAX_LENGTH = 1000

    fun allowedContacts(me: User, candidates: List<Contact>, mySchoolIds: Set<String>): List<Contact> =
        candidates.filter { c ->
            c.userId != me.id && when (me.role) {
                Role.LEARNER -> false
                Role.PARENT -> c.role == Role.COORDINATOR || c.role == Role.ADMIN ||
                    (c.role == Role.TEACHER && c.schoolId in mySchoolIds)
                Role.TEACHER -> c.role == Role.COORDINATOR || c.role == Role.ADMIN || c.role == Role.PARENT ||
                    (c.role == Role.TEACHER && c.schoolId.isNotBlank() && c.schoolId in mySchoolIds)
                Role.COORDINATOR, Role.ADMIN -> true
            }
        }.sortedWith(compareBy({ it.role.ordinal }, { it.name.lowercase() }))

    fun validate(text: String): String? = when {
        text.isBlank() -> "Write a message first"
        text.trim().length > MAX_LENGTH -> "Messages can be up to $MAX_LENGTH characters"
        else -> null
    }

    /** Roles a sender may start a conversation with (the same pairs the security rules allow). */
    fun roleMayMessage(sender: Role, recipient: Role): Boolean = when (sender) {
        Role.LEARNER -> false
        Role.PARENT -> recipient == Role.TEACHER || recipient == Role.COORDINATOR || recipient == Role.ADMIN
        else -> true
    }

    fun conversations(meId: String, messages: List<Message>): List<Conversation> =
        messages.filter { meId in it.participants }
            .groupBy { if (it.fromId == meId) it.toId else it.fromId }
            .map { (otherId, list) ->
                val last = list.maxByOrNull { it.createdAt }!!
                Conversation(
                    otherId = otherId,
                    otherName = if (last.fromId == meId) last.toName else last.fromName,
                    lastText = last.text,
                    lastAt = last.createdAt,
                    unread = list.count { it.toId == meId && !it.read }
                )
            }
            .sortedByDescending { it.lastAt }

    fun thread(meId: String, otherId: String, messages: List<Message>): List<Message> =
        messages.filter { (it.fromId == meId && it.toId == otherId) || (it.fromId == otherId && it.toId == meId) }
            .sortedBy { it.createdAt }

    fun unreadCount(meId: String, messages: List<Message>): Int = messages.count { it.toId == meId && !it.read }
}

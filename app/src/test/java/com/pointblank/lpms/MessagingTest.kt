package com.pointblank.lpms

import com.pointblank.lpms.core.model.*
import com.pointblank.lpms.core.util.Messaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagingTest {
    private fun user(id: String, role: Role, school: String = "") = User(id, "$id@x.com", id, role, "", schoolId = school)
    private val all = listOf(
        Contact("t1", "Teacher One", Role.TEACHER, "s1"), Contact("t2", "Teacher Two", Role.TEACHER, "s2"),
        Contact("c1", "Coordinator", Role.COORDINATOR), Contact("a1", "Admin", Role.ADMIN),
        Contact("p1", "Parent One", Role.PARENT, "s1"), Contact("p2", "Parent Two", Role.PARENT, "s2"),
        Contact("l1", "Learner", Role.LEARNER, "s1")
    )

    @Test fun parentsSeeTeachersAtTheirChildsSchoolAndStaffOnly() {
        val ids = Messaging.allowedContacts(user("p1", Role.PARENT), all, setOf("s1")).map { it.userId }
        assertEquals(setOf("t1", "c1", "a1"), ids.toSet())
    }

    @Test fun aParentWithChildrenAtTwoSchoolsSeesBothTeachers() {
        val ids = Messaging.allowedContacts(user("p1", Role.PARENT), all, setOf("s1", "s2")).map { it.userId }
        assertTrue("t1" in ids && "t2" in ids)
    }

    @Test fun teachersSeeStaffParentsAndColleaguesAtTheirSchool() {
        val ids = Messaging.allowedContacts(user("t1", Role.TEACHER, "s1"), all, setOf("s1")).map { it.userId }.toSet()
        assertEquals(setOf("c1", "a1", "p1", "p2"), ids) // no other-school teacher, no learner, not themselves
    }

    @Test fun staffSeeEveryoneExceptThemselves() {
        val ids = Messaging.allowedContacts(user("c1", Role.COORDINATOR), all, emptySet()).map { it.userId }
        assertEquals(6, ids.size)
        assertFalse("c1" in ids)
    }

    @Test fun learnersSeeNobody() {
        assertTrue(Messaging.allowedContacts(user("l1", Role.LEARNER), all, setOf("s1")).isEmpty())
    }

    @Test fun contactsAreOrderedByRoleThenName() {
        val names = Messaging.allowedContacts(user("c1", Role.COORDINATOR), all, emptySet()).map { it.name }
        assertEquals(listOf("Admin", "Coordinator", "Teacher One", "Teacher Two", "Parent One", "Parent Two").sorted().size, names.size)
        assertEquals("Admin", names.first())
    }

    @Test fun roleRulesMatchTheServerRules() {
        assertTrue(Messaging.roleMayMessage(Role.PARENT, Role.TEACHER))
        assertTrue(Messaging.roleMayMessage(Role.PARENT, Role.ADMIN))
        assertFalse(Messaging.roleMayMessage(Role.PARENT, Role.PARENT))
        assertFalse(Messaging.roleMayMessage(Role.PARENT, Role.LEARNER))
        assertFalse(Messaging.roleMayMessage(Role.LEARNER, Role.TEACHER))
        assertTrue(Messaging.roleMayMessage(Role.TEACHER, Role.PARENT))
        assertTrue(Messaging.roleMayMessage(Role.COORDINATOR, Role.PARENT))
    }

    @Test fun validationRules() {
        assertNull(Messaging.validate("Hello"))
        assertNotNull(Messaging.validate(""))
        assertNotNull(Messaging.validate("  \n "))
        assertNull(Messaging.validate("x".repeat(1000)))
        assertNotNull(Messaging.validate("x".repeat(1001)))
    }

    private fun m(id: String, from: String, to: String, at: Long, read: Boolean = false, text: String = id) =
        Message(id, from, "name-$from", to, "name-$to", text, at, read)

    @Test fun inboxGroupsByPersonNewestFirstWithUnreadCounts() {
        val msgs = listOf(
            m("1", "p1", "t1", 100, read = true), m("2", "t1", "p1", 200, read = true),
            m("3", "p2", "t1", 300), m("4", "p2", "t1", 400),
            m("5", "c1", "t1", 250, read = true)
        )
        val inbox = Messaging.conversations("t1", msgs)
        assertEquals(listOf("p2", "c1", "p1"), inbox.map { it.otherId })
        assertEquals(2, inbox.first().unread)
        assertEquals("4", inbox.first().lastText)
        assertEquals("name-p2", inbox.first().otherName)
        assertEquals(0, inbox.last().unread)
    }

    @Test fun myOwnSentMessagesNeverCountAsUnread() {
        val msgs = listOf(m("1", "t1", "p1", 100), m("2", "t1", "p1", 200))
        assertEquals(0, Messaging.unreadCount("t1", msgs))
        assertEquals(2, Messaging.unreadCount("p1", msgs))
    }

    @Test fun conversationNameIsTheOtherPersonEvenWhenIWroteLast() {
        val msgs = listOf(m("1", "p1", "t1", 100), m("2", "t1", "p1", 200))
        assertEquals("name-p1", Messaging.conversations("t1", msgs).single().otherName)
    }

    @Test fun threadContainsOnlyTheTwoPeopleInOrder() {
        val msgs = listOf(m("2", "t1", "p1", 200), m("1", "p1", "t1", 100), m("9", "p2", "t1", 150), m("8", "p1", "c1", 120))
        assertEquals(listOf("1", "2"), Messaging.thread("t1", "p1", msgs).map { it.id })
    }

    @Test fun someoneElsesMessagesAreIgnored() {
        val msgs = listOf(m("1", "p1", "t1", 100))
        assertTrue(Messaging.conversations("c1", msgs).isEmpty())
        assertEquals(0, Messaging.unreadCount("c1", msgs))
    }
}

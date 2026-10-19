package com.pointblank.lpms

import com.pointblank.lpms.core.model.*
import com.pointblank.lpms.data.repository.InMemoryLpmsRepository
import com.pointblank.lpms.data.seed.SeedData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryLpmsRepositoryTest {

    private val teacher = SeedData.users().first { it.id == "u-teacher" }

    @Test fun teacherRosterIsSchoolAndGrade() = runBlocking {
        val repo = InMemoryLpmsRepository()
        val roster = repo.classRoster("s1", "Grade 3")
        assertEquals(listOf("l1", "l2", "l3", "l4", "l5", "l6"), roster.map { it.id })
    }

    @Test fun rosterAcceptsDifferentGradeFormats() = runBlocking {
        val repo = InMemoryLpmsRepository()
        assertEquals(6, repo.classRoster("s1", "3").size)
        assertEquals(6, repo.classRoster("s1", " Grade 3 ").size)
    }

    @Test fun gradeRClassWorks() = runBlocking {
        val repo = InMemoryLpmsRepository()
        assertEquals(listOf("l11"), repo.classRoster("s5", "R").map { it.id })
        assertEquals(listOf("l11"), repo.classRoster("s5", "Grade R").map { it.id })
    }

    @Test fun unknownSchoolOrGradeGivesEmptyRoster() = runBlocking {
        val repo = InMemoryLpmsRepository()
        assertTrue(repo.classRoster("nowhere", "3").isEmpty())
        assertTrue(repo.classRoster("s1", "").isEmpty())
        assertTrue(repo.classRoster("", "3").isEmpty())
    }

    @Test fun schoolIdFallsBackToSchoolNameForOldAccounts() = runBlocking {
        val repo = InMemoryLpmsRepository()
        val old = teacher.copy(schoolId = "")
        assertEquals("s1", repo.schoolIdFor(old))
        assertEquals("s1", repo.schoolIdFor(teacher))
    }

    @Test fun savingReplacesOnlyThoseLearnersMarksForThatSession() = runBlocking {
        val repo = InMemoryLpmsRepository()
        repo.saveAttendance("se6", mapOf("l1" to true, "l7" to true), teacher) // se6 is still open
        repo.saveAttendance("se6", mapOf("l1" to false), teacher)
        val rows = repo.attendance().filter { it.sessionId == "se6" }
        assertEquals(2, rows.size)                                       // l1 replaced, l7 untouched
        assertEquals(false, rows.first { it.learnerId == "l1" }.present)
        assertEquals(true, rows.first { it.learnerId == "l7" }.present)
        repo.saveAttendance("se6", mapOf("l1" to true), teacher)
        assertEquals(1, repo.attendance().count { it.sessionId == "se6" && it.learnerId == "l1" })
    }

    @Test fun attendanceRecordsWhoMarkedItAndWhen() = runBlocking {
        val repo = InMemoryLpmsRepository()
        repo.saveAttendance("se6", mapOf("l1" to true), teacher)
        val row = repo.attendance().first { it.sessionId == "se6" && it.learnerId == "l1" }
        assertEquals("Lindiwe Nkosi", row.markedByName)
        assertTrue(row.markedAt > 0)
        assertEquals("s1", row.schoolId)
    }

    @Test fun seedHasAtLeastTenRowsPerTable() = runBlocking {
        val repo = InMemoryLpmsRepository()
        listOf(
            repo.schools().size, repo.programmes().size, repo.learners().size, repo.enrollments().size,
            repo.sessions().size, repo.progress().size, repo.attendance().size, repo.reports().size,
            repo.feedback().size, repo.resources().size, repo.visits().size, SeedData.users().size
        ).forEach { assertTrue("table has $it rows", it >= 10) }
    }

    @Test fun savedFeedbackAndResourcesAppear() = runBlocking {
        val repo = InMemoryLpmsRepository()
        val f = repo.feedback().size
        repo.addFeedback(FeedbackEntry("f-new", "u", "Tester", "General", "Nice", 5, 1L))
        assertEquals(f + 1, repo.feedback().size)
        repo.setResourceShared("rs4", true)
        assertTrue(repo.resources().first { it.id == "rs4" }.shared)
    }

    @Test fun seededLearnerLoginsAndGuardiansAreLinked() = runBlocking {
        val repo = InMemoryLpmsRepository()
        assertEquals("l1", repo.learners().first { it.userId == "u-learner" }.id)
        assertEquals(listOf("u-parent3"), repo.learners().first { it.id == "l4" }.guardianIds)
    }

    @Test fun staffCanEnrolALearnerAndLinkAParent() = runBlocking {
        val repo = InMemoryLpmsRepository()
        repo.addLearner("New Child", "s1", 0)
        val child = repo.learners().first { it.name == "New Child" }
        assertEquals(0, child.grade)
        val parent = SeedData.users().first { it.id == "u-parent1" }
        repo.linkAccount(child.id, parent)
        assertEquals(listOf("u-parent1"), repo.learners().first { it.id == child.id }.guardianIds)
    }

    @Test fun linkingAParentGivesThemTheLearnersExistingRecords() = runBlocking {
        val repo = InMemoryLpmsRepository()
        val parent = SeedData.users().first { it.id == "u-parent1" }
        repo.linkAccount("l5", parent) // l5 had no parent, but has attendance and progress
        val asParent = InMemoryLpmsRepository { parent }
        // a separate repository instance starts from seed data, so check the linking on the original instead
        assertTrue(repo.attendance().filter { it.learnerId == "l5" }.all { "u-parent1" in it.viewerIds })
        assertTrue(repo.progress().filter { it.learnerId == "l5" }.all { "u-parent1" in it.viewerIds })
        assertTrue(asParent.learners().none { it.id == "l5" }) // fresh seed: not linked there
    }

    @Test fun libraryUpgradeStopsAtLevelThree() = runBlocking {
        val repo = InMemoryLpmsRepository()
        repeat(5) { repo.upgradeLibrary("s1", "2026-10-12") }
        assertEquals(3, repo.schools().first { it.id == "s1" }.libraryLevel)
        assertEquals("2026-10-12", repo.schools().first { it.id == "s1" }.lastUpgrade)
    }

    @Test fun progressRecordsKeepWhoRecordedAndAreVisibleToGuardians() = runBlocking {
        val repo = InMemoryLpmsRepository()
        repo.addProgress("l1", "Reading Boost", 5, teacher)
        val rec = repo.progress().last { it.learnerId == "l1" }
        assertEquals(5, rec.level)
        assertEquals("Lindiwe Nkosi", rec.recordedByName)
        assertTrue("u-parent1" in rec.viewerIds)
    }

    // ---- privacy scoping (POPIA): the same rules the Firestore queries and security rules enforce

    private fun repoAs(id: String) = InMemoryLpmsRepository { SeedData.users().first { it.id == id } }

    @Test fun teacherOnlySeesLearnersAtTheirOwnSchool() = runBlocking {
        val seen = repoAs("u-teacher").learners()
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it.schoolId == "s1" })
    }

    @Test fun teacherOnlySeesRecordsAtTheirOwnSchool() = runBlocking {
        val repo = repoAs("u-teacher2") // Mamelodi East
        assertTrue(repo.attendance().all { it.schoolId == "s2" })
        assertTrue(repo.progress().all { it.schoolId == "s2" })
    }

    @Test fun parentOnlySeesTheirOwnChildren() = runBlocking {
        val repo = repoAs("u-parent3")
        assertEquals(listOf("l3", "l4"), repo.learners().map { it.id })
        assertTrue(repo.attendance().all { it.learnerId in setOf("l3", "l4") })
        assertTrue(repo.progress().all { it.learnerId in setOf("l3", "l4") })
    }

    @Test fun parentWithNoLinkedChildSeesNothing() = runBlocking {
        val lonely = SeedData.users().first { it.id == "u-parent1" }.copy(id = "u-new-parent")
        val repo = InMemoryLpmsRepository { lonely }
        assertTrue(repo.learners().isEmpty())
        assertTrue(repo.attendance().isEmpty())
    }

    @Test fun learnerLoginSeesOnlyTheirOwnRecord() = runBlocking {
        val repo = repoAs("u-learner")
        assertEquals(listOf("l1"), repo.learners().map { it.id })
        assertTrue(repo.attendance().all { it.learnerId == "l1" })
    }

    @Test fun staffSeeEverything() = runBlocking {
        val coordinator = repoAs("u-coordinator")
        assertEquals(SeedData.learners.size, coordinator.learners().size)
        assertEquals(SeedData.attendance.size, coordinator.attendance().size)
        assertEquals(SeedData.visits.size, coordinator.visits().size)
    }

    @Test fun signedOutSeesNothing() = runBlocking {
        val repo = InMemoryLpmsRepository { null }
        assertTrue(repo.learners().isEmpty())
        assertTrue(repo.attendance().isEmpty())
        assertTrue(repo.feedback().isEmpty())
    }

    @Test fun feedbackIsPrivateToItsAuthorAndStaff() = runBlocking {
        val teacherRepo = repoAs("u-teacher")
        teacherRepo.addFeedback(FeedbackEntry("f-t", "u-teacher", "Lindiwe Nkosi", "General", "Hi", 5, 1L))
        assertEquals(listOf("f-t"), teacherRepo.feedback().map { it.id }) // seed feedback belongs to "seed"
        val coordinator = InMemoryLpmsRepository { SeedData.users().first { it.id == "u-coordinator" } }
        assertEquals(SeedData.feedback.size, coordinator.feedback().size)
    }

    @Test fun visitsAreStaffOnlyAndAuditIsAdminOnly() = runBlocking {
        assertTrue(repoAs("u-teacher").visits().isEmpty())
        assertTrue(repoAs("u-parent1").visits().isEmpty())
        val admin = repoAs("u-admin")
        admin.logAudit(AuditEntry("au1", "u-admin", "Sipho", "Approved account", "x", 1L))
        assertEquals(1, admin.audit().size)
        assertTrue(repoAs("u-coordinator").audit().isEmpty())
    }

    @Test fun aTeacherCannotMarkLearnersFromAnotherSchool() = runBlocking {
        val repo = repoAs("u-teacher") // Soshanguve; l7 is at Mamelodi East
        val before = repo.attendance().size
        repo.saveAttendance("se5", mapOf("l7" to true), teacher)
        assertEquals(before, repo.attendance().size)
        assertFalse(repo.attendance().any { it.learnerId == "l7" })
    }

    // ---- session lifecycle

    private fun newSession(id: String = "se-new", school: String = "s1", date: String = java.time.LocalDate.now().plusDays(3).toString()) =
        Session(id, "Reading Boost", school, date, "Lindiwe Nkosi", createdByName = "Naledi Mthembu")

    @Test fun aScheduledSessionCanBeCreatedAndMarked() = runBlocking {
        val repo = InMemoryLpmsRepository()
        repo.addSession(newSession())
        assertEquals(SessionStatus.SCHEDULED, repo.sessions().first { it.id == "se-new" }.status)
        repo.saveAttendance("se-new", mapOf("l1" to true), teacher)
        assertEquals(1, repo.attendance().count { it.sessionId == "se-new" })
    }

    @Test fun aClosedSessionLocksTheRegisterForTeachers() = runBlocking {
        val repo = InMemoryLpmsRepository()
        repo.addSession(newSession())
        repo.saveAttendance("se-new", mapOf("l1" to true), teacher)
        repo.setSessionStatus("se-new", SessionStatus.COMPLETED)
        var refused = false
        try { repo.saveAttendance("se-new", mapOf("l1" to false), teacher) } catch (e: IllegalStateException) { refused = true }
        assertTrue(refused)
        assertEquals(true, repo.attendance().first { it.sessionId == "se-new" }.present) // unchanged
    }

    @Test fun staffCanCorrectACompletedSessionAndReopenIt() = runBlocking {
        val repo = InMemoryLpmsRepository()
        val coordinator = SeedData.users().first { it.id == "u-coordinator" }
        repo.saveAttendance("se2", mapOf("l7" to false), coordinator) // se2 is completed in the seed
        assertEquals(false, repo.attendance().first { it.sessionId == "se2" && it.learnerId == "l7" }.present)
        repo.setSessionStatus("se2", SessionStatus.SCHEDULED)
        repo.saveAttendance("se2", mapOf("l7" to true), teacher) // reopened, so a teacher can mark again
        assertEquals(true, repo.attendance().first { it.sessionId == "se2" && it.learnerId == "l7" }.present)
    }

    @Test fun aCancelledSessionCannotBeMarkedByAnyone() = runBlocking {
        val repo = InMemoryLpmsRepository()
        repo.addSession(newSession())
        repo.setSessionStatus("se-new", SessionStatus.CANCELLED)
        val coordinator = SeedData.users().first { it.id == "u-coordinator" }
        listOf(teacher, coordinator).forEach { who ->
            var refused = false
            try { repo.saveAttendance("se-new", mapOf("l1" to true), who) } catch (e: IllegalStateException) { refused = true }
            assertTrue(refused)
        }
    }

    @Test fun markingAnUnknownSessionFails() = runBlocking {
        val repo = InMemoryLpmsRepository()
        var refused = false
        try { repo.saveAttendance("nope", mapOf("l1" to true), teacher) } catch (e: IllegalStateException) { refused = true }
        assertTrue(refused)
    }

    // ---- messaging

    private fun msg(id: String, from: String, fromName: String, to: String, toName: String, text: String, read: Boolean = false) =
        Message(id, from, fromName, to, toName, text, System.currentTimeMillis(), read)

    @Test fun everyoneOnlySeesTheirOwnMessages() = runBlocking {
        assertTrue(repoAs("u-parent1").messages().all { "u-parent1" in it.participants })
        assertTrue(repoAs("u-teacher").messages().all { "u-teacher" in it.participants })
        assertTrue(repoAs("u-teacher2").messages().isEmpty()) // Mpho is in nobody's seeded conversation
        assertTrue(repoAs("u-learner").messages().isEmpty())
    }

    @Test fun aParentCanWriteToATeacherAndTheTeacherSeesItUnread() = runBlocking {
        val repo = InMemoryLpmsRepository { SeedData.users().first { it.id == "u-parent2" } }
        repo.sendMessage(msg("m-new", "u-parent2", "Sibusiso Phiri", "u-teacher2", "Mpho Mthembu", "  Hello  "))
        val sent = repo.messages().first { it.id == "m-new" }
        assertEquals("Hello", sent.text)          // trimmed
        assertFalse(sent.read)
    }

    @Test fun emptyOrOverlongMessagesAreRefused() = runBlocking {
        val repo = InMemoryLpmsRepository()
        listOf("", "   ", "x".repeat(1001)).forEach {
            var refused = false
            try { repo.sendMessage(msg("m-bad", "a", "A", "b", "B", it)) } catch (e: IllegalArgumentException) { refused = true }
            assertTrue("'${it.take(10)}'", refused)
        }
    }

    @Test fun onlyTheRecipientCanMarkAMessageRead() = runBlocking {
        val parent = repoAs("u-parent2")
        val unread = parent.messages().first { it.id == "m-seed-3" } // parent2 -> teacher, unread
        parent.markMessagesRead(listOf(unread.id))               // the sender is not the recipient
        assertFalse(parent.messages().first { it.id == "m-seed-3" }.read)
        val teacherRepo = repoAs("u-teacher")
        teacherRepo.markMessagesRead(listOf("m-seed-3"))
        assertTrue(teacherRepo.messages().first { it.id == "m-seed-3" }.read)
    }

    @Test fun parentsSeeStaffAndTeachersButNeverOtherParents() = runBlocking {
        val contacts = repoAs("u-parent1").contacts()
        assertTrue(contacts.none { it.role == Role.PARENT || it.role == Role.LEARNER })
        assertTrue(contacts.any { it.role == Role.TEACHER } && contacts.any { it.role == Role.COORDINATOR })
    }

    @Test fun teachersSeeTheParentsOfTheirOwnLearnersOnly() = runBlocking {
        val parents = repoAs("u-teacher").contacts().filter { it.role == Role.PARENT }
        assertEquals(setOf("u-parent1", "u-parent2", "u-parent3"), parents.map { it.userId }.toSet())
        assertTrue(parents.first { it.userId == "u-parent1" }.note.startsWith("Parent of"))
        // a teacher at another school sees no parents (no learners there have linked parents)
        assertTrue(repoAs("u-teacher2").contacts().none { it.role == Role.PARENT })
    }

    @Test fun learnersGetNoContacts() = runBlocking {
        assertTrue(repoAs("u-learner").contacts().isEmpty())
    }
}

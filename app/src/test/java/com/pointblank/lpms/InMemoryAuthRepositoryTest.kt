package com.pointblank.lpms

import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.data.repository.AuthResult
import com.pointblank.lpms.data.repository.InMemoryAuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryAuthRepositoryTest {

    private fun repo() = InMemoryAuthRepository()

    @Test fun seededCoordinatorCanLogIn() = runBlocking {
        val repo = repo()
        val result = repo.login("coordinator@read.org.za", "password123")
        assertTrue(result is AuthResult.Success)
        assertEquals(Role.COORDINATOR, repo.currentUser.value?.role)
    }

    @Test fun wrongPasswordIsRejected() = runBlocking {
        val repo = repo()
        val result = repo.login("admin@read.org.za", "wrong-password")
        assertTrue(result is AuthResult.Failure)
        assertNull(repo.currentUser.value)
    }

    @Test fun registrationWaitsForApprovalThenDefaultsToParent() = runBlocking {
        val repo = repo()
        val result = repo.register("new.user@example.com", "New User", "abcdefg1")
        assertTrue(result is AuthResult.Pending)
        assertNull(repo.currentUser.value) // not signed in until approved
        assertTrue(repo.login("new.user@example.com", "abcdefg1") is AuthResult.Failure)

        repo.login("admin@read.org.za", "password123")
        val id = repo.allUsers().first { it.email == "new.user@example.com" }.id
        assertTrue(repo.approveUser(id) is AuthResult.Success)
        repo.logout()
        assertTrue(repo.login("new.user@example.com", "abcdefg1") is AuthResult.Success)
        assertEquals(Role.PARENT, repo.currentUser.value?.role)
    }

    @Test fun teacherCanSelfRegisterButNeedsApproval() = runBlocking {
        val repo = repo()
        val result = repo.register("t@example.com", "Teach Er", "abcdefg1", Role.TEACHER, "Mamelodi East", "4")
        assertTrue(result is AuthResult.Pending)
        val pending = repo.allUsers().first { it.email == "t@example.com" }
        assertEquals(Role.TEACHER, pending.role)
        assertTrue(!pending.approved)
    }

    @Test fun nonStaffCannotApproveAccounts() = runBlocking {
        val repo = repo()
        repo.register("t@example.com", "Teach Er", "abcdefg1", Role.TEACHER, "Mamelodi East", "4")
        val id = repo.allUsers().first { it.email == "t@example.com" }.id
        repo.login("learner@email.com", "password123")
        assertTrue(repo.approveUser(id) is AuthResult.Failure)
    }

    @Test fun coordinatorCanApproveAccounts() = runBlocking {
        val repo = repo()
        repo.register("t2@example.com", "Teach Two", "abcdefg1", Role.TEACHER, "Mamelodi East", "4")
        val id = repo.allUsers().first { it.email == "t2@example.com" }.id
        repo.login("coordinator@read.org.za", "password123")
        assertTrue(repo.approveUser(id) is AuthResult.Success)
    }

    @Test fun adminCannotSelfRegister() = runBlocking {
        val repo = repo()
        val result = repo.register("a@example.com", "Sneaky", "abcdefg1", Role.ADMIN, "", "")
        assertTrue(result is AuthResult.Failure)
        assertNull(repo.currentUser.value)
    }

    @Test fun duplicateEmailIsRejected() = runBlocking {
        val repo = repo()
        val result = repo.register("admin@read.org.za", "Someone", "abcdefg1")
        assertTrue(result is AuthResult.Failure)
    }

    @Test fun weakPasswordIsRejectedOnRegister() = runBlocking {
        val repo = repo()
        val result = repo.register("weak@example.com", "Weak", "short")
        assertTrue(result is AuthResult.Failure)
    }

    @Test fun logoutClearsSession() = runBlocking {
        val repo = repo()
        repo.login("teacher@school.org", "password123")
        assertNotNull(repo.currentUser.value)
        repo.logout()
        assertNull(repo.currentUser.value)
    }

    @Test fun changePasswordRequiresCorrectCurrentPassword() = runBlocking {
        val repo = repo()
        repo.login("learner@email.com", "password123")
        val bad = repo.changePassword("nope", "newpass123")
        assertTrue(bad is AuthResult.Failure)
        val good = repo.changePassword("password123", "newpass123")
        assertTrue(good is AuthResult.Success)
    }

    @Test fun newlyRegisteredUserAppearsInUserList() = runBlocking {
        val repo = repo()
        val before = repo.allUsers().size
        repo.register("fresh@example.com", "Fresh User", "abcdefg1")
        assertEquals(before + 1, repo.allUsers().size)
    }

    @Test fun adminCanSuspendUserAndSuspendedUserCannotLogIn() = runBlocking {
        val repo = repo()
        repo.login("admin@read.org.za", "password123")
        val r = repo.setActive("u-teacher", false)
        assertTrue(r is AuthResult.Success)
        repo.logout()
        assertTrue(repo.login("teacher@school.org", "password123") is AuthResult.Failure)
    }

    @Test fun nonAdminCannotChangeAccountStatus() = runBlocking {
        val repo = repo()
        repo.login("teacher@school.org", "password123")
        assertTrue(repo.setActive("u-learner", false) is AuthResult.Failure)
    }

    @Test fun adminCannotSuspendThemselves() = runBlocking {
        val repo = repo()
        repo.login("admin@read.org.za", "password123")
        assertTrue(repo.setActive("u-admin", false) is AuthResult.Failure)
    }

    @Test fun adminCanCreateAccountAndItCanLogIn() = runBlocking {
        val repo = repo()
        repo.login("admin@read.org.za", "password123")
        val r = repo.adminCreateUser("new.coord@read.org.za", "New Coord", "abcdefg1", Role.COORDINATOR)
        assertTrue(r is AuthResult.Success)
        repo.logout()
        assertTrue(repo.login("new.coord@read.org.za", "abcdefg1") is AuthResult.Success)
        assertEquals(Role.COORDINATOR, repo.currentUser.value?.role)
    }

    @Test fun nonAdminCannotCreateAccounts() = runBlocking {
        val repo = repo()
        repo.login("teacher@school.org", "password123")
        val r = repo.adminCreateUser("x@example.com", "Sneaky", "abcdefg1", Role.ADMIN)
        assertTrue(r is AuthResult.Failure)
    }

    @Test fun adminCanChangeRoleButNotTheirOwn() = runBlocking {
        val repo = repo()
        repo.login("admin@read.org.za", "password123")
        assertTrue(repo.setRole("u-learner", Role.TEACHER) is AuthResult.Success)
        assertEquals(Role.TEACHER, repo.allUsers().first { it.id == "u-learner" }.role)
        assertTrue(repo.setRole("u-admin", Role.LEARNER) is AuthResult.Failure)
    }

    @Test fun googleSignInFailsCleanlyWithoutBackend() = runBlocking {
        assertTrue(repo().signInWithGoogle("token") is AuthResult.Failure)
    }

    @Test fun learnersCannotSelfRegister() = runBlocking {
        val repo = repo()
        val r = repo.register("kid@example.com", "Kid", "abcdefg1", Role.LEARNER)
        assertTrue(r is AuthResult.Failure)
        assertTrue(repo.allUsers().none { it.email == "kid@example.com" })
    }

    @Test fun parentRegistrationKeepsTheChildNameAndSchoolForStaffToCheck() = runBlocking {
        val repo = repo()
        val r = repo.register("mum@example.com", "Mum", "abcdefg1", Role.PARENT, "Soshanguve Primary", "", "s1", "  Thabo M ")
        assertTrue(r is AuthResult.Pending)
        val u = repo.allUsers().first { it.email == "mum@example.com" }
        assertEquals("Thabo M", u.childName)
        assertEquals("s1", u.schoolId)
        assertTrue(!u.approved)
    }

    @Test fun adminCanBackfillSchoolIdsForOlderAccounts() = runBlocking {
        val old = com.pointblank.lpms.data.seed.SeedData.users().map {
            if (it.id == "u-teacher") it.copy(schoolId = "") else it
        }
        val repo = com.pointblank.lpms.data.repository.InMemoryAuthRepository(old)
        repo.login("admin@read.org.za", "password123")
        val fixed = repo.repairAccounts(com.pointblank.lpms.data.seed.SeedData.schools)
        assertEquals(1, fixed)
        assertEquals("s1", repo.allUsers().first { it.id == "u-teacher" }.schoolId)
    }

    @Test fun backfillDoesNothingForNonAdmins() = runBlocking {
        val repo = repo()
        repo.login("teacher@school.org", "password123")
        assertEquals(0, repo.repairAccounts(com.pointblank.lpms.data.seed.SeedData.schools))
    }

    @Test fun adminCanSetATeachersSchoolAndGradeAfterGoogleSignUp() = runBlocking {
        val repo = repo()
        repo.register("g@example.com", "Google Teacher", "abcdefg1", Role.TEACHER) // no school, as after a bare Google sign-in
        repo.login("admin@read.org.za", "password123")
        val id = repo.allUsers().first { it.email == "g@example.com" }.id
        val school = com.pointblank.lpms.data.seed.SeedData.schools.first { it.id == "s2" }
        val r = repo.updateAccount(id, Role.TEACHER, school, "4")
        assertTrue(r is AuthResult.Success)
        val u = repo.allUsers().first { it.id == id }
        assertEquals("s2", u.schoolId); assertEquals("Mamelodi East", u.school); assertEquals("4", u.grade)
    }

    @Test fun onlyAdminsCanEditAccountsAndNotTheirOwnRole() = runBlocking {
        val repo = repo()
        repo.login("coordinator@read.org.za", "password123")
        assertTrue(repo.updateAccount("u-teacher", Role.TEACHER, null, "3") is AuthResult.Failure)
        repo.logout()
        repo.login("admin@read.org.za", "password123")
        assertTrue(repo.updateAccount("u-admin", Role.TEACHER, null, "") is AuthResult.Failure)
    }
}

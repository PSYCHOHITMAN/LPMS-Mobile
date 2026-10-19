package com.pointblank.lpms

import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.data.repository.AuthResult
import com.pointblank.lpms.data.repository.InMemoryAuthRepository
import com.pointblank.lpms.data.repository.InMemoryLpmsRepository
import com.pointblank.lpms.data.seed.DemoDataSeeder
import com.pointblank.lpms.data.seed.SeedData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataSeederTest {
    private val admin = SeedData.users().first { it.role == Role.ADMIN }

    private fun freshSystem(): Triple<InMemoryAuthRepository, InMemoryLpmsRepository, DemoDataSeeder> {
        val auth = InMemoryAuthRepository(listOf(admin)) // only the admin you set up yourself exists
        val repo = InMemoryLpmsRepository()
        return Triple(auth, repo, DemoDataSeeder(auth, repo))
    }

    @Test fun createsTheNineDemoAccountsAndTheyCanSignIn() = runBlocking {
        val (auth, _, seeder) = freshSystem()
        auth.login(admin.email, SeedData.DEMO_PASSWORD)
        val message = seeder.run()
        assertTrue(message, message.contains("9 demo accounts created"))
        assertEquals(10, auth.allUsers().size)
        auth.logout()
        assertTrue(auth.login("teacher@school.org", SeedData.DEMO_PASSWORD) is AuthResult.Success)
    }

    @Test fun parentsAreLinkedToTheirChildrenWithTheirRealAccountIds() = runBlocking {
        val (auth, repo, seeder) = freshSystem()
        auth.login(admin.email, SeedData.DEMO_PASSWORD)
        seeder.run()
        val parent3 = auth.allUsers().first { it.email == "parent3@email.com" }
        val children = repo.learners().filter { parent3.id in it.guardianIds }.map { it.id }.sorted()
        assertEquals(listOf("l3", "l4"), children)
        // and the learners' existing records became visible to that parent
        assertTrue(repo.attendance().filter { it.learnerId == "l3" }.all { parent3.id in it.viewerIds })
    }

    @Test fun runningTwiceCreatesNothingNewAndFailsNothing() = runBlocking {
        val (auth, _, seeder) = freshSystem()
        auth.login(admin.email, SeedData.DEMO_PASSWORD)
        seeder.run()
        val second = seeder.run()
        assertTrue(second, second.contains("0 demo accounts created"))
        assertTrue(second, second.contains("9 already existed"))
        assertFalse(second, second.contains("failed"))
        assertEquals(10, auth.allUsers().size)
    }

    @Test fun onlyAnAdminCanSeed() = runBlocking {
        val (auth, _, seeder) = freshSystem()
        // not signed in: every account creation is refused, and the message says so
        val message = seeder.run()
        assertTrue(message, message.contains("failed"))
        assertEquals(1, auth.allUsers().size)
    }

    @Test fun demoConversationsUseTheRealAccountIds() = runBlocking {
        val (auth, repo, seeder) = freshSystem()
        auth.login(admin.email, SeedData.DEMO_PASSWORD)
        val message = seeder.run()
        assertTrue(message, message.contains("10 demo messages"))
        val parent1 = auth.allUsers().first { it.email == "parent1@email.com" }
        val teacher = auth.allUsers().first { it.email == "teacher@school.org" }
        val sent = repo.messages().filter { parent1.id in it.participants && teacher.id in it.participants }
        assertTrue(sent.isNotEmpty())
    }
}

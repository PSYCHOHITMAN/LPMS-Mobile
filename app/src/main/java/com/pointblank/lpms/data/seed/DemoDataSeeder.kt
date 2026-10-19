package com.pointblank.lpms.data.seed

import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.AuthResult
import com.pointblank.lpms.data.repository.LpmsRepository

/**
 * One-tap demo setup for an administrator: writes the demo records to the database, creates the demo accounts
 * (password [SeedData.DEMO_PASSWORD]) and links parents and the demo learner login to their children.
 * Safe to run again: existing accounts are left alone and links are only ever added.
 */
class DemoDataSeeder(
    private val auth: AuthRepository,
    private val repo: LpmsRepository
) {
    suspend fun run(): String {
        val records = repo.seedDemoData()
        val schools = repo.schools()
        val schoolIdByName = schools.associate { it.name to it.id }
        val backfilled = auth.repairAccounts(schools)

        var created = 0
        var existing = 0
        var failed = 0
        val demoUsers = SeedData.users()
        for (u in demoUsers) {
            if (u.role == Role.ADMIN) continue // the signed-in admin is the one you set up yourself
            val result = auth.adminCreateUser(
                email = u.email, displayName = u.displayName, password = SeedData.DEMO_PASSWORD,
                role = u.role, school = u.school, grade = u.grade, schoolId = schoolIdByName[u.school] ?: u.schoolId
            )
            when {
                result is AuthResult.Success -> created++
                result is AuthResult.Failure && result.message.contains("already exists") -> existing++
                else -> failed++
            }
        }

        // Link each demo parent / learner login to the seeded learner records they belong to.
        val seedUserById = demoUsers.associateBy { it.id }
        val accountsByEmail = auth.allUsers().associateBy { it.email.lowercase() }
        var linked = 0
        for (learner in SeedData.learners) {
            for (seedUserId in learner.guardianIds + listOfNotNull(learner.userId)) {
                val account = seedUserById[seedUserId]?.let { accountsByEmail[it.email.lowercase()] } ?: continue
                repo.linkAccount(learner.id, account)
                linked++
            }
        }

        // Demo conversations between the demo parents, teachers and coordinator, using the real account ids.
        val realIdBySeedId = demoUsers.mapNotNull { u -> accountsByEmail[u.email.lowercase()]?.let { u.id to it.id } }.toMap()
        var messagesSent = 0
        for (m in SeedData.messages) {
            val from = realIdBySeedId[m.fromId] ?: continue
            val to = realIdBySeedId[m.toId] ?: continue
            repo.sendMessage(m.copy(fromId = from, toId = to))
            messagesSent++
        }

        val accounts = "$created demo accounts created" + if (existing > 0) ", $existing already existed" else ""
        return "Loaded $records records. $accounts. $linked parent/learner links set, $messagesSent demo messages." +
            (if (backfilled > 0) " $backfilled accounts repaired (school id)." else "") +
            (if (failed > 0) " $failed accounts failed." else "")
    }
}

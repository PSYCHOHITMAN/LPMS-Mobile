package com.pointblank.lpms.data.seed

import com.pointblank.lpms.core.model.*
import com.pointblank.lpms.data.security.PasswordHasher
import java.time.LocalDate

/**
 * Demo data set, at least ten rows per table, to satisfy the brief.
 * [DemoDataSeeder] copies it into Firestore and Firebase Auth when an administrator taps "Load demo data".
 * Unit tests use it directly through the in-memory repositories.
 */
object SeedData {
    const val DEMO_PASSWORD = "password123"

    fun users(): List<User> {
        val h = PasswordHasher.hash(DEMO_PASSWORD) // in-memory demo accounts share one hash
        return listOf(
            User("u-admin", "admin@read.org.za", "Sipho Dlamini", Role.ADMIN, h),
            User("u-coordinator", "coordinator@read.org.za", "Naledi Mthembu", Role.COORDINATOR, h),
            User("u-teacher", "teacher@school.org", "Lindiwe Nkosi", Role.TEACHER, h,
                school = "Soshanguve Primary", grade = "3", schoolId = "s1"),
            User("u-teacher2", "mpho@school.org", "Mpho Mthembu", Role.TEACHER, h,
                school = "Mamelodi East", grade = "4", schoolId = "s2"),
            User("u-teacher3", "thandi@school.org", "Thandi Khumalo", Role.TEACHER, h,
                school = "Midrand Primary", grade = "3", schoolId = "s4"),
            User("u-teacher4", "sizwe@school.org", "Sizwe Dube", Role.TEACHER, h,
                school = "Centurion Academy", grade = "2", schoolId = "s5"),
            User("u-parent1", "parent1@email.com", "Nomvula Mokoena", Role.PARENT, h,
                school = "Soshanguve Primary", schoolId = "s1", childName = "Thabo Mokoena"),
            User("u-parent2", "parent2@email.com", "Sibusiso Phiri", Role.PARENT, h,
                school = "Soshanguve Primary", schoolId = "s1", childName = "Kagiso P."),
            User("u-parent3", "parent3@email.com", "Zodwa Mabaso", Role.PARENT, h,
                school = "Soshanguve Primary", schoolId = "s1", childName = "Zanele M. and Ayanda N."),
            User("u-learner", "learner@email.com", "Thabo Mokoena", Role.LEARNER, h,
                school = "Soshanguve Primary", grade = "3", schoolId = "s1")
        ) // 10 users
    }

    private val today: LocalDate = LocalDate.now()

    /** Programme start dates are spread so every stage of READ's three-year cycle is represented. */
    private val schoolSetup: List<Triple<String, String, Pair<Long, Int>>> = listOf(
        Triple("Soshanguve Primary", "Tshwane", 400L to 1),       // year 2, library still level 1: upgrade due
        Triple("Mamelodi East", "Tshwane", 20L to 1),             // year 1
        Triple("Pretoria Central", "Tshwane", 800L to 2),         // year 3, library level 2: upgrade due
        Triple("Midrand Primary", "Midrand", 1300L to 3),         // programme completed
        Triple("Centurion Academy", "Centurion", 100L to 1),      // year 1
        Triple("Sandton Primary", "Sandton", 500L to 2),          // year 2
        Triple("Randburg Elementary", "Randburg", 60L to 1),      // year 1
        Triple("Boksburg Primary", "Boksburg", 1000L to 3),       // year 3
        Triple("Kempton Park", "Kempton Park", 250L to 1),        // year 1
        Triple("Benoni Primary", "Benoni", 700L to 2)             // year 2
    )

    val schools: List<School> = schoolSetup.mapIndexed { i, (name, district, cycle) ->
        val (daysAgo, level) = cycle
        val start = today.minusDays(daysAgo)
        School(
            id = "s${i + 1}", name = name, district = district, coordinatorId = "",
            startDate = start.toString(), libraryLevel = level,
            lastUpgrade = if (level > 1) start.plusYears((level - 1).toLong()).toString() else ""
        )
    }

    val programmes: List<Programme> = listOf(
        "Reading Boost", "Literacy Circle", "Reading Buddies", "Library Hour",
        "Comprehension Club", "Letter Sounds Lab", "Shared Reading", "Vocabulary Builders",
        "Storytelling Workshop", "Homework Reading Support"
    ).mapIndexed { i, n ->
        Programme("p${i + 1}", n, "s${i + 1}", "$n literacy programme")
    }

    // Soshanguve Primary Grade 3 (l1-l6) is Lindiwe Nkosi's class; Mamelodi East Grade 4 (l7-l9) is Mpho Mthembu's.
    // l1-l4 are linked to demo logins: l1 has a learner login and a parent, l2 and l3 have a parent, l4 shares l3's parent.
    // l11 is in Grade R (0), the reception year READ starts with.
    val learners: List<Learner> = listOf(
        Learner("l1", "u-learner", "Thabo Mokoena", 3, "s1", listOf("u-parent1"), mapOf("u-parent1" to "Nomvula Mokoena")),
        Learner("l2", null, "Kagiso P.", 3, "s1", listOf("u-parent2"), mapOf("u-parent2" to "Sibusiso Phiri")),
        Learner("l3", null, "Zanele M.", 3, "s1", listOf("u-parent3"), mapOf("u-parent3" to "Zodwa Mabaso")),
        Learner("l4", null, "Ayanda N.", 3, "s1", listOf("u-parent3"), mapOf("u-parent3" to "Zodwa Mabaso")),
        Learner("l5", null, "Sipho D.", 3, "s1"),
        Learner("l6", null, "Naledi K.", 3, "s1"),
        Learner("l7", null, "Lerato S.", 4, "s2"),
        Learner("l8", null, "Tumelo R.", 4, "s2"),
        Learner("l9", null, "Nomsa L.", 4, "s2"),
        Learner("l10", null, "Bheki T.", 3, "s4"),
        Learner("l11", null, "Palesa G.", 0, "s5"),
        Learner("l12", null, "Musa Z.", 2, "s5")
    )

    val enrollments: List<Enrollment> = learners.mapIndexed { i, l ->
        Enrollment("e${i + 1}", l.id, "p${i % 10 + 1}")
    }

    /** Dates are relative to today so there are always past and upcoming sessions: se1-se3 past, se4 today, se5+ ahead. */
    val sessions: List<Session> = programmes.mapIndexed { i, p ->
        Session(
            id = "se${i + 1}",
            programme = p.name,
            schoolId = p.schoolId,
            date = today.plusDays((i - 3) * 2L).toString(),
            facilitator = when (i % 4) {
                0 -> "Lindiwe Nkosi"
                1 -> "Mpho Mthembu"
                2 -> "Thandi Khumalo"
                else -> "Sizwe Dube"
            },
            // se1-se4 already have attendance marked, so they are closed; the rest are still to run.
            status = if (i < 4) SessionStatus.COMPLETED else SessionStatus.SCHEDULED,
            createdByName = "Naledi Mthembu"
        )
    }

    private fun learner(id: String) = learners.first { it.id == id }

    val progress: List<ProgressRecord> = listOf(
        Triple("l1", "Reading Boost", 2), Triple("l1", "Reading Boost", 3),
        Triple("l2", "Reading Boost", 3), Triple("l2", "Reading Boost", 4),
        Triple("l3", "Reading Boost", 2), Triple("l5", "Reading Boost", 3),
        Triple("l7", "Literacy Circle", 2), Triple("l8", "Literacy Circle", 3),
        Triple("l9", "Literacy Circle", 4), Triple("l10", "Reading Boost", 3),
        Triple("l11", "Reading Boost", 1), Triple("l12", "Reading Boost", 4)
    ).mapIndexed { i, (learnerId, programme, level) ->
        val l = learner(learnerId)
        ProgressRecord("pr${i + 1}", learnerId, programme, level, "2026-06-%02d".format(1 + i),
            schoolId = l.schoolId, viewerIds = l.viewerIds(), recordedByName = "Demo data")
    }

    // Lindiwe's class (l1-l6) was marked for se1, se3 and se4; Mpho's class (l7-l9) for se2.
    val attendance: List<AttendanceRecord> = run {
        val rows = mutableListOf<AttendanceRecord>()
        var n = 1
        fun add(session: String, learnerId: String, present: Boolean) {
            val l = learner(learnerId)
            rows += AttendanceRecord("a${n++}", session, learnerId, present, l.schoolId, l.viewerIds(), "Demo data", 0L)
        }
        listOf("se1", "se3", "se4").forEachIndexed { si, session ->
            (1..6).forEach { li -> add(session, "l$li", (li + si) % 4 != 0) }
        }
        (7..9).forEach { li -> add("se2", "l$li", li != 8) }
        rows
    }

    val reports: List<Report> = programmes.mapIndexed { i, p ->
        Report("r${i + 1}", "${p.name} monthly summary", p.id, "2026-09-%02d".format(i + 1))
    }

    private const val DAY = 86_400_000L

    val feedback: List<FeedbackEntry> = listOf(
        Triple("Thabo Mokoena", "Reading Boost", "I like the new storybooks. The group reading is fun!") to 5,
        Triple("Kagiso P.", "Reading Boost", "Can we have more time for the letter sounds game?") to 4,
        Triple("Lindiwe Nkosi", "Literacy Circle", "Learners are responding well to the comprehension prompts.") to 5,
        Triple("Zanele M.", "Reading Buddies", "My reading buddy helps me with hard words.") to 5,
        Triple("Mpho Mthembu", "Library Hour", "We need more Sepedi storybooks for Grade 4.") to 4,
        Triple("Ayanda N.", "Letter Sounds Lab", "The sounds games are my favourite part.") to 5,
        Triple("Thandi Khumalo", "Shared Reading", "Sessions run on time and learners are engaged.") to 4,
        Triple("Lerato S.", "Comprehension Club", "Some questions were too hard for me.") to 3,
        Triple("Sizwe Dube", "Vocabulary Builders", "Great word lists. Please share more printable worksheets.") to 4,
        Triple("Nomsa L.", "Storytelling Workshop", "I loved telling my own story!") to 5
    ).mapIndexed { i, (meta, stars) ->
        FeedbackEntry("f${i + 1}", "seed", meta.first, meta.second, meta.third, stars, System.currentTimeMillis() - (i + 1) * DAY)
    }

    val resources: List<Resource> = listOf(
        Triple("Phonics lesson plan: Term 3", "Lesson plan", true),
        Triple("Letter sounds worksheet pack", "Worksheet", true),
        Triple("Grade 3 storybook: The Brave Hare", "Storybook", true),
        Triple("Reading level assessment: Level 2", "Assessment", false),
        Triple("Reading level assessment: Level 3", "Assessment", false),
        Triple("Comprehension prompts (Grade 3)", "Worksheet", true),
        Triple("Vocabulary word list: Term 3", "Worksheet", false),
        Triple("Shared reading guide for facilitators", "Lesson plan", true),
        Triple("Storybook: Thuli and the River (isiZulu)", "Storybook", false),
        Triple("Library hour planning template", "Lesson plan", false)
    ).mapIndexed { i, (title, type, shared) ->
        Resource("rs${i + 1}", title, type, listOf("Lindiwe Nkosi", "Mpho Mthembu", "Thandi Khumalo")[i % 3], shared,
            System.currentTimeMillis() - (i + 2) * DAY)
    }

    private val observed = mapOf("s1" to "Lindiwe Nkosi", "s2" to "Mpho Mthembu", "s4" to "Thandi Khumalo", "s5" to "Sizwe Dube")

    /** Field visits by the coordinator, newest first: monitoring and mentoring is how READ supports teachers. */
    val visits: List<SchoolVisit> = listOf(
        Triple("s1", "Observed a Reading Boost session; learners read in pairs.", "Share the phonics lesson plan."),
        Triple("s2", "Checked the box library; books are in good condition.", "Replace two damaged storybooks."),
        Triple("s1", "Mentoring on guided reading questions.", "Re-observe in two weeks."),
        Triple("s4", "Reviewed attendance records with the teacher.", "Mark attendance on the day of each session."),
        Triple("s5", "Classroom library corner set up for Grade R.", "Add picture-book labels."),
        Triple("s3", "Year 3 review meeting with the principal.", "Schedule the library upgrade."),
        Triple("s6", "Observed a Literacy Circle; strong learner participation.", "None."),
        Triple("s2", "Followed up on damaged storybooks.", "Confirm replacements arrived."),
        Triple("s7", "Introduced the programme to a new teacher.", "Training session next week."),
        Triple("s8", "Checked reading levels against the assessment.", "Record progress for all learners.")
    ).mapIndexed { i, (schoolId, notes, followUp) ->
        val school = schools.first { it.id == schoolId }
        SchoolVisit("v${i + 1}", schoolId, school.name, "seed", "Naledi Mthembu",
            today.minusDays((i * 3 + 1).toLong()).toString(), observed[schoolId] ?: "", notes, followUp,
            System.currentTimeMillis() - (i * 3 + 1) * DAY)
    }

    private val HOUR = 3_600_000L

    /** Parents, teachers and the coordinator talking to each other (seed account ids; the seeder maps them to real accounts). */
    val messages: List<Message> = listOf(
        listOf("u-parent1", "Nomvula Mokoena", "u-teacher", "Lindiwe Nkosi", "Good morning Ms Nkosi, Thabo will miss Friday's reading session because of a clinic appointment.", true),
        listOf("u-teacher", "Lindiwe Nkosi", "u-parent1", "Nomvula Mokoena", "Thank you for letting me know. I will send his reading pack home on Monday.", true),
        listOf("u-parent2", "Sibusiso Phiri", "u-teacher", "Lindiwe Nkosi", "Can Kagiso borrow a storybook for the weekend?", false),
        listOf("u-teacher", "Lindiwe Nkosi", "u-parent3", "Zodwa Mabaso", "Zanele and Ayanda both did very well in Reading Boost this week.", false),
        listOf("u-parent3", "Zodwa Mabaso", "u-coordinator", "Naledi Mthembu", "Hi, I registered for Zanele and Ayanda. Are both children linked to my account?", true),
        listOf("u-coordinator", "Naledi Mthembu", "u-parent3", "Zodwa Mabaso", "Yes, both are linked. Welcome to LPMS!", false),
        listOf("u-teacher", "Lindiwe Nkosi", "u-coordinator", "Naledi Mthembu", "The Reading Boost books are in good condition but we need two replacements.", true),
        listOf("u-coordinator", "Naledi Mthembu", "u-teacher", "Lindiwe Nkosi", "Noted, I will bring them on my next visit.", false),
        listOf("u-parent1", "Nomvula Mokoena", "u-coordinator", "Naledi Mthembu", "Where can I see Thabo's reading level?", true),
        listOf("u-coordinator", "Naledi Mthembu", "u-parent1", "Nomvula Mokoena", "Open My Children in the menu.", true)
    ).mapIndexed { i, m ->
        Message("m-seed-${i + 1}", m[0] as String, m[1] as String, m[2] as String, m[3] as String, m[4] as String,
            System.currentTimeMillis() - (10 - i) * HOUR, m[5] as Boolean)
    }
}

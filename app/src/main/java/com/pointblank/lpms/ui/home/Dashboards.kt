package com.pointblank.lpms.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.ui.components.*
import com.pointblank.lpms.ui.theme.*

private const val DASH = "\u2014"

@Composable
private fun MetricGrid(vararg m: Triple<String, String, ImageVector>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        m.toList().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (n, l, i) -> MetricCard(n, l, i, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MessagesCard(d: HomeData, onNavigate: (String) -> Unit) {
    if (d.unreadMessages > 0) {
        LpmsCard {
            SectionTitle("Messages", Icons.Filled.Chat)
            Text("You have ${d.unreadMessages} unread message(s).", fontSize = 14.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate("messages") }) { Text("Open messages") }
        }
    }
}

/** Buttons that jump to a screen; [targets] pairs each label with the menu key it opens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(vararg targets: Pair<String, String>, onNavigate: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        targets.forEachIndexed { i, (label, key) ->
            if (i == 0) Button(onClick = { onNavigate(key) }) { Text(label) }
            else OutlinedButton(onClick = { onNavigate(key) }) { Text(label, color = Ink) }
        }
    }
}

@Composable
private fun UpcomingCard(title: String, d: HomeData) = LpmsCard {
    SectionTitle(title, Icons.Filled.CalendarMonth)
    if (d.upcoming.isEmpty()) Text("No upcoming sessions scheduled.", color = Muted, fontSize = 14.sp)
    d.upcoming.forEach { FeedItem(Icons.Filled.MenuBook, it.title, it.subtitle) }
}

@Composable
fun LearnerDashboard(d: HomeData) = Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    if (d.loaded && !d.hasLearnerProfile) {
        LpmsCard { Text("No learner profile is linked to your account yet. Ask your coordinator to enrol you.", color = Muted, fontSize = 14.sp) }
    }
    MetricGrid(
        Triple(d.programmes.toString(), "Active programmes", Icons.Filled.MenuBook),
        Triple(d.sessionsAttended.toString(), "Sessions attended", Icons.Filled.CalendarMonth),
        Triple(d.readingLevel?.toString() ?: DASH, "Reading level", Icons.Filled.Star),
        Triple(d.attendance?.let { "$it%" } ?: DASH, "Attendance rate", Icons.Filled.CheckCircle)
    )
    UpcomingCard("Upcoming sessions at your school", d)
    LpmsCard {
        SectionTitle("My progress", Icons.Filled.ShowChart)
        if (d.progressRows.isEmpty()) Text("No progress recorded yet.", color = Muted, fontSize = 14.sp)
        d.progressRows.forEach { (programme, level) ->
            ProgressRow("$programme \u2014 level $level of 5", (level / 5f).coerceAtMost(1f))
        }
    }
}

@Composable
fun ParentDashboard(d: HomeData, onNavigate: (String) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    MessagesCard(d, onNavigate)
    if (d.loaded && d.children.isEmpty()) {
        LpmsCard {
            Text(
                "No children are linked to your account yet. Your coordinator links you to your child once your account is approved. " +
                    "If it has been a while, ask them to check the name you registered.",
                color = Muted, fontSize = 14.sp
            )
        }
    }
    MetricGrid(
        Triple(d.children.size.toString(), "Children", Icons.Filled.ChildCare),
        Triple(d.attendance?.let { "$it%" } ?: DASH, "Attendance", Icons.Filled.CheckCircle)
    )
    d.children.forEach { c ->
        LpmsCard {
            Text(c.name, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
            Text("${c.gradeText} \u00B7 ${c.school}", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Reading level", fontSize = 11.sp, color = Muted)
                    Text(c.level?.toString() ?: DASH, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Brand)
                }
                Column {
                    Text("Attendance", fontSize = 11.sp, color = Muted)
                    Text(c.attendance?.let { "$it%" } ?: DASH, fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TealDark)
                }
            }
        }
    }
    Actions("My children" to "children", "Feedback" to "feedback", onNavigate = onNavigate)
    UpcomingCard("Upcoming sessions at your child's school", d)
}

@Composable
fun TeacherDashboard(d: HomeData, onNavigate: (String) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    MessagesCard(d, onNavigate)
    MetricGrid(
        Triple(d.myLearners.toString(), "My learners", Icons.Filled.ChildCare),
        Triple(d.sessionsFacilitated.toString(), "Sessions facilitated", Icons.Filled.CalendarMonth),
        Triple(d.attendance?.let { "$it%" } ?: DASH, "Class attendance", Icons.Filled.CheckCircle),
        Triple(d.atReadingLevel.toString(), "At reading level", Icons.Filled.Flag)
    )
    Actions("Mark attendance" to "attendance", "Sessions" to "sessions", "Record progress" to "progress",
        "Messages" to "messages", onNavigate = onNavigate)
    UpcomingCard("Upcoming sessions", d)
}

@Composable
fun CoordinatorDashboard(d: HomeData, onNavigate: (String) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    MessagesCard(d, onNavigate)
    MetricGrid(
        Triple(d.schools.toString(), "Active schools", Icons.Filled.School),
        Triple(d.learners.toString(), "Learners enrolled", Icons.Filled.ChildCare),
        Triple(d.sessions.toString(), "Sessions scheduled", Icons.Filled.CalendarMonth),
        Triple(d.averageAttendance?.let { "$it%" } ?: DASH, "Avg. attendance", Icons.Filled.CheckCircle),
        Triple(d.visitsThisMonth.toString(), "School visits (30 days)", Icons.Filled.DirectionsWalk),
        Triple(d.libraryUpgradesDue.toString(), "Library upgrades due", Icons.Filled.MenuBook)
    )
    if (d.pending > 0) {
        LpmsCard {
            SectionTitle("Waiting for approval", Icons.Filled.HowToReg)
            Text("${d.pending} new account(s) are waiting to be approved.", fontSize = 14.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate("users") }) { Text("Review accounts") }
        }
    }
    LpmsCard {
        SectionTitle("Reading progress", Icons.Filled.ShowChart)
        Text(
            if (d.learnersAssessed == 0) "No learners have been assessed twice yet."
            else "${d.learnersImproved} of ${d.learnersAssessed} assessed learners improved, by ${d.averageGain ?: 0.0} levels on average.",
            fontSize = 14.sp, color = Ink
        )
    }
    Actions("Visits" to "visits", "Sessions" to "sessions", "Schools" to "schools", "Learners" to "learners", "Reports" to "reports",
        onNavigate = onNavigate)
    UpcomingCard("Upcoming sessions", d)
}

@Composable
fun AdminDashboard(
    d: HomeData,
    seeding: Boolean,
    seedMessage: String?,
    onSeed: () -> Unit,
    onNavigate: (String) -> Unit
) = Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    MessagesCard(d, onNavigate)
    MetricGrid(
        Triple(d.users.toString(), "Total users", Icons.Filled.Groups),
        Triple(d.schools.toString(), "Schools", Icons.Filled.School),
        Triple(d.teachers.toString(), "Teachers", Icons.Filled.Person),
        Triple(d.learners.toString(), "Learners", Icons.Filled.ChildCare)
    )
    if (d.pending > 0) {
        LpmsCard {
            SectionTitle("Waiting for approval", Icons.Filled.HowToReg)
            Text("${d.pending} new account(s) are waiting for you to approve them.", fontSize = 14.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate("users") }) { Text("Review accounts") }
        }
    }
    Actions("Manage users" to "users", "Reports" to "reports", "Analytics" to "analytics", onNavigate = onNavigate)
    LpmsCard {
        SectionTitle("Users by role", Icons.Filled.PieChart)
        val max = (d.usersByRole.values.maxOrNull() ?: 0).coerceAtLeast(1)
        Role.entries.forEach { role ->
            val n = d.usersByRole[role] ?: 0
            ProgressRow("${role.label}: $n", n / max.toFloat(), if (role == Role.ADMIN) Teal else Brand)
        }
    }
    LpmsCard {
        SectionTitle("Demo data", Icons.Filled.Storage)
        Text(
            "Loads the sample schools, programmes, learners, sessions and demo accounts into the database. " +
                "Safe to run again, but it resets the demo attendance.",
            fontSize = 13.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSeed, enabled = !seeding) { Text(if (seeding) "Loading..." else "Load demo data") }
        seedMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp, color = TealDark)
        }
    }
}

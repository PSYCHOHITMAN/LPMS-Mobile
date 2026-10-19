package com.pointblank.lpms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.School
import com.pointblank.lpms.core.model.User
import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.util.formatDisplayDate
import com.pointblank.lpms.core.util.timeAgo
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.*
import com.pointblank.lpms.ui.theme.*

/* ---------- School visits: READ's field staff monitor and mentor teachers by visiting schools ---------- */

@Composable
fun VisitsScreen(
    user: User,
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var logging by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTop(s.visits.size, "school visits", action = "Log visit") { logging = true }
        s.message?.let { NoteCard(it) }
        s.visits.forEach { v ->
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.DirectionsWalk, null, tint = Brand, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.schoolName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                        Text("${v.visitorName} \u00B7 ${formatDisplayDate(v.date)}", fontSize = 13.sp, color = Muted)
                    }
                }
                if (v.teacherObserved.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Chip("Teacher: ${v.teacherObserved}", Purple, PurpleTint)
                }
                Spacer(Modifier.height(8.dp))
                Text(v.notes, fontSize = 14.sp, color = Ink)
                if (v.followUp.isNotBlank() && v.followUp != "None.") {
                    Spacer(Modifier.height(4.dp))
                    Text("Follow-up: ${v.followUp}", fontSize = 13.sp, color = BrandDark)
                }
            }
        }
        if (s.visits.isEmpty()) NoteCard("No visits logged yet. Log a visit after each school you monitor or mentor at.")
    }

    if (logging) {
        var school by remember { mutableStateOf<School?>(null) }
        var teacher by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var followUp by remember { mutableStateOf("") }
        val teachersHere = s.users.filter { it.role == Role.TEACHER && (school == null || it.schoolId == school?.id || it.school == school?.name) }
        AlertDialog(
            onDismissRequest = { logging = false },
            title = { Text("Log school visit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SchoolPicker(s.schools, school, { school = it; teacher = "" }, "Choose school")
                    OptionPicker(teachersHere.map { it.displayName }, teacher.ifBlank { null }, { teacher = it }, "Teacher observed (optional)")
                    OutlinedTextField(notes, { notes = it }, label = { Text("What you observed") }, minLines = 2)
                    OutlinedTextField(followUp, { followUp = it }, label = { Text("Follow-up (optional)") }, minLines = 1)
                }
            },
            confirmButton = { TextButton(onClick = { vm.addVisit(user, school, teacher, notes, followUp); logging = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { logging = false }) { Text("Cancel") } }
        )
    }
}

/* ---------- Activity log (admin): who did what, and when ---------- */

@Composable
fun AuditScreen(
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTop(s.audit.size, "recent actions")
        LpmsCard {
            SectionTitle("Activity log", Icons.Filled.History)
            if (s.audit.isEmpty()) Text("Nothing has been logged yet.", color = Muted, fontSize = 14.sp)
            s.audit.forEach { e ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(e.action, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
                    Text("${e.target} \u00B7 by ${e.actorName} \u00B7 ${timeAgo(e.at)}", fontSize = 12.sp, color = Muted)
                }
                HorizontalDivider(color = Line)
            }
        }
    }
}

/* ---------- My children (parents): attendance, reading level and progress for each linked child ---------- */

@Composable
fun ChildrenScreen(
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sessionsById = s.sessions.associateBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (s.learners.isEmpty() && !s.loading) {
            NoteCard("No children are linked to your account yet. Your coordinator links you to your child once your account is approved.")
        }
        s.learners.forEach { c ->
            val attendance = s.attendance.filter { it.learnerId == c.id }
                .sortedByDescending { sessionsById[it.sessionId]?.date ?: "" }
            val levels = s.progress.filter { it.learnerId == c.id }.groupBy { it.programme }
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.ChildCare, null, tint = Teal, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                        Text("${Grades.display(c.grade)} \u00B7 ${s.schoolName(c.schoolId)}", fontSize = 13.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.attendanceRate(c.id)?.let { "$it%" } ?: "\u2014", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TealDark)
                        Text("Attendance", fontSize = 12.sp, color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.latestLevel(c.id)?.toString() ?: "\u2014", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand)
                        Text("Reading level", fontSize = 12.sp, color = Muted)
                    }
                }
            }
            if (levels.isNotEmpty()) {
                LpmsCard {
                    SectionTitle("${c.name}'s reading progress", Icons.Filled.ShowChart)
                    levels.forEach { (programme, rows) ->
                        val latest = rows.sortedBy { it.date }.last().level
                        ProgressRow("$programme \u2014 level $latest of 5", (latest / 5f).coerceAtMost(1f))
                    }
                }
            }
            if (attendance.isNotEmpty()) {
                LpmsCard {
                    SectionTitle("${c.name}'s recent sessions", Icons.Filled.Schedule)
                    attendance.take(5).forEach { rec ->
                        val se = sessionsById[rec.sessionId]
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(se?.programme ?: "Session", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
                                Text(se?.date?.let { formatDisplayDate(it) } ?: "", fontSize = 12.sp, color = Muted)
                            }
                            PresentChip(rec.present)
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
        }
    }
}

package com.pointblank.lpms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.School
import com.pointblank.lpms.core.model.isStaff
import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.model.User
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.formatDisplayDate
import com.pointblank.lpms.core.util.timeAgo
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.*
import com.pointblank.lpms.ui.theme.*

/* ---------- Feedback ---------- */

@Composable
fun FeedbackScreen(
    user: User,
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(5) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!user.role.isStaff) {
            LpmsCard {
                SectionTitle("Submit feedback", Icons.Filled.Star)
                OutlinedTextField(value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Share your feedback...") }, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rating:", fontSize = 13.sp, color = Muted)
                    (1..5).forEach { i ->
                        Icon(Icons.Filled.Star, null, tint = if (i <= stars) Amber else Track,
                            modifier = Modifier.size(26.dp).clickable { stars = i })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    vm.addFeedback(user, text, stars)
                    if (text.isNotBlank()) text = ""
                }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Submit") }
            }
        }
        s.message?.let { Text(it, color = TealDark, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().background(TealTint, RoundedCornerShape(12.dp)).padding(12.dp)) }
        LpmsCard {
            SectionTitle(if (user.role.isStaff) "Recent feedback" else "Your feedback", Icons.Filled.Forum)
            if (s.feedback.isEmpty()) Text("No feedback yet.", color = Muted, fontSize = 14.sp)
            s.feedback.forEach { e ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.author, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
                        Row { repeat(e.stars) { Icon(Icons.Filled.Star, null, tint = Amber, modifier = Modifier.size(13.dp)) } }
                    }
                    Text(e.subject, fontSize = 12.sp, color = Brand)
                    Spacer(Modifier.height(2.dp))
                    Text(e.text, fontSize = 14.sp, color = Ink)
                    Text(timeAgo(e.createdAt), fontSize = 11.sp, color = Muted)
                }
                HorizontalDivider(color = Line)
            }
        }
    }
}

/* ---------- Parent Portal (staff view of any learner's family summary) ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentPortalScreen(
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var childId by remember { mutableStateOf<String?>(null) }
    val child = s.learners.firstOrNull { it.id == childId } ?: s.learners.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LpmsCard {
            SectionTitle("Choose a learner", Icons.Filled.FamilyRestroom)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.learners.forEach { c ->
                    FilterChip(
                        selected = c.id == child?.id,
                        onClick = { childId = c.id },
                        label = { Text(c.name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandTint, selectedLabelColor = BrandDark)
                    )
                }
            }
        }
        if (child == null) {
            LpmsCard { Text("No learners yet.", color = Muted, fontSize = 14.sp) }
        } else {
            val rate = s.attendanceRate(child.id)
            val level = s.latestLevel(child.id)
            // The class teacher is whoever teaches that school and grade.
            val teacher = s.users.firstOrNull {
                it.role == Role.TEACHER && (it.schoolId == child.schoolId || it.school == s.schoolName(child.schoolId)) &&
                    LearnerInsights.parseGrade(it.grade) == child.grade
            }
            LpmsCard {
                SectionTitle("${child.name} \u2014 ${Grades.display(child.grade)}", Icons.Filled.ChildCare)
                Text(s.schoolName(child.schoolId), fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(rate?.let { "$it%" } ?: "\u2014", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TealDark)
                        Text("Attendance", fontSize = 12.sp, color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(level?.toString() ?: "\u2014", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand)
                        Text("Reading level", fontSize = 12.sp, color = Muted)
                    }
                }
            }
            LpmsCard {
                SectionTitle("Progress", Icons.Filled.ShowChart)
                ProgressRow("Reading level ${level ?: 0} of 5", ((level ?: 0) / 5f).coerceAtMost(1f))
            }
            LpmsCard {
                SectionTitle("Class teacher", Icons.Filled.Email)
                if (teacher != null) {
                    Text(teacher.displayName, fontSize = 14.sp, color = Ink)
                    Text(teacher.email, fontSize = 13.sp, color = Muted)
                } else Text("No teacher is registered for this class yet.", fontSize = 14.sp, color = Muted)
            }
        }
    }
}

/* ---------- User Management: admins manage everything; coordinators can only approve new accounts ---------- */

@Composable
fun UserManagementScreen(
    user: User,
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<User?>(null) }
    val isAdmin = user.role == Role.ADMIN
    val users = s.users.filter { it.displayName.contains(query, true) || it.email.contains(query, true) }
        .sortedBy { it.approved } // accounts waiting for approval first
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${s.users.size} users", fontSize = 14.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            if (isAdmin) {
                OutlinedButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp), tint = Brand)
                    Spacer(Modifier.width(6.dp))
                    Text("Add user", color = Ink)
                }
            }
        }
        ScreenSearch(query, { query = it }, "Search users...")
        s.message?.let { Text(it, color = BrandDark, fontSize = 13.sp) }
        users.forEach { u -> key(u.id) {
            var roleMenu by remember { mutableStateOf(false) }
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(u.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
                        Text(u.email, fontSize = 13.sp, color = Muted)
                        if (u.school.isNotEmpty()) {
                            Text(u.school + if (u.grade.isNotEmpty()) " \u00B7 ${Grades.display(u.grade)}" else "", fontSize = 12.sp, color = Muted)
                        }
                        if (u.role == Role.PARENT && u.childName.isNotBlank()) {
                            Text("Parent of ${u.childName}", fontSize = 12.sp, color = Muted)
                        }
                        if (u.role == Role.TEACHER && u.school.isBlank()) {
                            Text("No school set: this teacher cannot see any learners or sessions yet.", fontSize = 12.sp, color = BrandDark)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!u.approved) {
                                Button(onClick = { vm.approve(u.id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text("Approve", fontSize = 13.sp)
                                }
                            }
                            if (isAdmin) {
                                OutlinedButton(onClick = { editing = u }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text("Edit", fontSize = 13.sp, color = Ink)
                                }
                            }
                        }
                    }
                    Box {
                        Box(Modifier.clickable(enabled = isAdmin) { roleMenu = true }) { RoleBadge(u.role) }
                        DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                            Role.entries.forEach { r ->
                                DropdownMenuItem(text = { Text(r.label) }, onClick = { roleMenu = false; vm.setRole(u.id, r) })
                            }
                        }
                    }
                    if (isAdmin) {
                        Switch(checked = u.active, onCheckedChange = { vm.setActive(u.id, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Teal))
                    }
                }
            }
        } }
        if (users.isEmpty()) LpmsCard { Text("No users found.", color = Muted, fontSize = 14.sp) }
        Text(
            if (isAdmin) "Check the school and grade (or the child's name for parents), then tap Approve. Tap a role to change it. The switch suspends or restores an account."
            else "Check the school and grade (or the child's name for parents), then tap Approve.",
            fontSize = 12.sp, color = Muted
        )
    }

    editing?.let { u ->
        // Set who this person is and where they work. A teacher's school and grade decide which learners and sessions they see.
        var role by remember(u.id) { mutableStateOf(u.role) }
        var school by remember(u.id) { mutableStateOf(s.schools.firstOrNull { it.id == u.schoolId || it.name == u.school }) }
        var grade by remember(u.id) { mutableStateOf(Grades.parse(u.grade)?.let { Grades.label(it) } ?: "") }
        var roleMenu by remember(u.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit ${u.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(u.email, fontSize = 13.sp, color = Muted)
                    Box {
                        OutlinedButton({ roleMenu = true }, Modifier.fillMaxWidth(), enabled = u.id != user.id) {
                            Text("Role: ${role.label}", color = Ink)
                        }
                        DropdownMenu(roleMenu, { roleMenu = false }) {
                            Role.entries.forEach { r -> DropdownMenuItem({ Text(r.label) }, { role = r; roleMenu = false }) }
                        }
                    }
                    SchoolPicker(s.schools, school, { school = it }, "No school set")
                    if (role == Role.TEACHER || role == Role.LEARNER) {
                        Text("Grade", fontSize = 13.sp, color = Muted)
                        GradeChips(grade) { grade = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.updateAccount(u.id, role, school, grade); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(Role.COORDINATOR) }
        var school by remember { mutableStateOf<School?>(null) }
        var grade by remember { mutableStateOf("") }
        var roleMenu by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add user") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(password, { password = it }, label = { Text("Temporary password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Box {
                        OutlinedButton({ roleMenu = true }, Modifier.fillMaxWidth()) { Text("Role: ${role.label}", color = Ink) }
                        DropdownMenu(roleMenu, { roleMenu = false }) {
                            Role.entries.forEach { r -> DropdownMenuItem({ Text(r.label) }, { role = r; roleMenu = false }) }
                        }
                    }
                    if (role == Role.TEACHER || role == Role.LEARNER || role == Role.PARENT) {
                        SchoolPicker(s.schools, school, { school = it })
                    }
                    if (role == Role.TEACHER || role == Role.LEARNER) {
                        Text("Grade", fontSize = 13.sp, color = Muted)
                        GradeChips(grade) { grade = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.createUser(email, name, password, role, school, grade); adding = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } }
        )
    }
}

/* ---------- Notifications: built from what is actually in the database ---------- */

@Composable
fun NotificationsScreen(
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sessionsById = s.sessions.associateBy { it.id }
    val marked = s.attendance.groupBy { it.sessionId }
        .mapNotNull { (id, rows) -> sessionsById[id]?.let { it to rows } }
        .sortedByDescending { it.first.date }.take(3)
    val reports = s.reports.take(2)
    val feedback = s.feedback.take(2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LpmsCard {
            SectionTitle("Notifications", Icons.Filled.Notifications)
            if (marked.isEmpty() && reports.isEmpty() && feedback.isEmpty()) {
                Text("You're all caught up.", color = Muted, fontSize = 14.sp)
            }
            marked.forEach { (se, rows) ->
                FeedItem(Icons.Filled.CheckCircle,
                    "Attendance recorded for ${se.programme}: ${rows.count { it.present }}/${rows.size} present",
                    formatDisplayDate(se.date), Teal)
            }
            reports.forEach { FeedItem(Icons.Filled.Description, "Report ready: ${it.title}", formatDisplayDate(it.createdAt), Teal) }
            feedback.forEach { FeedItem(Icons.Filled.Star, "New feedback from ${it.author}", timeAgo(it.createdAt), Amber) }
        }
    }
}

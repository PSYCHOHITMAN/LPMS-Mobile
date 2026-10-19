package com.pointblank.lpms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.core.model.*
import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.ProgrammeCycle
import com.pointblank.lpms.core.util.ReportMetrics
import com.pointblank.lpms.core.util.SessionRules
import com.pointblank.lpms.core.util.formatDisplayDate
import com.pointblank.lpms.core.util.timeAgo
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.*
import com.pointblank.lpms.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/* ---------- shared bits, in the website's visual language ---------- */

@Composable
internal fun Chip(text: String, tint: Color, bg: Color) {
    Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
internal fun PresentChip(present: Boolean) =
    if (present) Chip("Present", TealDark, TealTint) else Chip("Absent", BrandDark, BrandTint)

@Composable
internal fun RateBar(label: String, fraction: Float, color: Color = Brand) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = Muted)
            Text("${(fraction * 100).roundToInt()}%", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color, trackColor = Track, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
internal fun ScreenSearch(query: String, onQuery: (String) -> Unit, placeholder: String = "Search...") {
    OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = Brand) },
        singleLine = true)
}

@Composable
internal fun ScreenTop(count: Int, noun: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$count $noun", fontSize = 14.sp, color = Muted)
        if (action != null) {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onAction) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = Brand)
                Spacer(Modifier.width(6.dp))
                Text(action, color = Ink)
            }
        }
    }
}

@Composable
internal fun NoteCard(text: String) = LpmsCard { Text(text, color = Muted, fontSize = 14.sp) }

/** A dropdown of schools; shows [placeholder] until one is chosen. */
@Composable
internal fun SchoolPicker(schools: List<School>, selected: School?, onSelect: (School) -> Unit, placeholder: String = "Choose school") {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) {
            Text(selected?.name ?: placeholder, color = Ink, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, tint = Ink)
        }
        DropdownMenu(open, { open = false }) {
            schools.forEach { sc -> DropdownMenuItem({ Text(sc.name) }, { onSelect(sc); open = false }) }
        }
    }
}

/** A generic dropdown of text options. */
@Composable
internal fun OptionPicker(options: List<String>, selected: String?, onSelect: (String) -> Unit, placeholder: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth(), enabled = options.isNotEmpty()) {
            Text(selected ?: placeholder, color = Ink, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, tint = Ink)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(o) }, { onSelect(o); open = false }) }
        }
    }
}

/* ---------- Schools: READ supports a school for three years and upgrades its box library each year ---------- */

@Composable
fun SchoolsScreen(
    user: User,
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val canEdit = user.role.isStaff
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTop(s.schools.size, "schools", action = if (canEdit) "Add school" else null) { adding = true }
        s.schools.forEach { school ->
            val cycle = ProgrammeCycle.status(school)
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.School, null, tint = Brand, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(school.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                        Text(school.district, fontSize = 13.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(cycle.label, if (cycle.completed) Muted else TealDark, if (cycle.completed) Track else TealTint)
                    if (cycle.upgradeDue) Chip("Library upgrade due", BrandDark, BrandTint)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Box library level ${school.libraryLevel} of ${ProgrammeCycle.MAX_LIBRARY_LEVEL}" +
                        if (school.lastUpgrade.isNotBlank()) " \u00B7 upgraded ${formatDisplayDate(school.lastUpgrade)}" else "",
                    fontSize = 13.sp, color = Muted
                )
                if (school.startDate.isNotBlank()) {
                    Text("Programme started ${formatDisplayDate(school.startDate)}", fontSize = 13.sp, color = Muted)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.programmes.count { it.schoolId == school.id }} programmes", fontSize = 13.sp, color = Muted)
                    Text("${s.learners.count { it.schoolId == school.id }} learners", fontSize = 13.sp, color = Muted)
                }
                if (canEdit && ProgrammeCycle.canUpgrade(school) && !cycle.completed) {
                    Spacer(Modifier.height(8.dp))
                    if (cycle.upgradeDue) Button(onClick = { vm.upgradeLibrary(school) }) { Text("Upgrade library") }
                    else OutlinedButton(onClick = { vm.upgradeLibrary(school) }) { Text("Upgrade library", color = Ink) }
                }
            }
        }
        s.message?.let { NoteCard(it) }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var district by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add school") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("School name") }, singleLine = true)
                    OutlinedTextField(district, { district = it }, label = { Text("District") }, singleLine = true)
                    Text("The three-year programme starts today at level 1 of the box library.", fontSize = 12.sp, color = Muted)
                }
            },
            confirmButton = { TextButton(onClick = { vm.addSchool(name, district); adding = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } }
        )
    }
}

/* ---------- Teachers ---------- */

@Composable
fun TeachersScreen(
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val teachers = s.users.filter { it.role == Role.TEACHER && it.displayName.contains(query, true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenSearch(query, { query = it }, "Search teachers...")
        teachers.forEach { t ->
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(38.dp).background(PurpleTint, CircleShape), Alignment.Center) {
                        Text(t.displayName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                            color = Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(t.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                        Text(t.email, fontSize = 13.sp, color = Muted)
                    }
                    Box(Modifier.size(10.dp).background(if (t.active) Teal else Color(0xFF9C8F7E), CircleShape))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.school.isNotEmpty()) Chip(t.school, Blue, BlueTint)
                    if (t.grade.isNotEmpty()) Chip(Grades.display(t.grade), Purple, PurpleTint)
                }
            }
        }
        if (teachers.isEmpty()) NoteCard("No teachers found.")
    }
}

/* ---------- Learners: enrolled by staff; parents (and older learners) are linked to them ---------- */

@Composable
fun LearnersScreen(
    user: User,
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var enrolling by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<Learner?>(null) }
    val canEdit = user.role.isStaff
    val learners = s.learners.filter { it.name.contains(query, true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTop(s.learners.size, "learners", action = if (canEdit) "Enrol learner" else null) { enrolling = true }
        ScreenSearch(query, { query = it }, "Search learners...")
        s.message?.let { NoteCard(it) }
        learners.forEach { l ->
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.ChildCare, null, tint = Teal, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                        Text("${Grades.display(l.grade)} \u00B7 ${s.schoolName(l.schoolId)}", fontSize = 13.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column { Text("Reading level", fontSize = 11.sp, color = Muted)
                        Text(s.latestLevel(l.id)?.toString() ?: "\u2014", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
                    Column { Text("Attendance", fontSize = 11.sp, color = Muted)
                        Text(s.attendanceRate(l.id)?.let { "$it%" } ?: "\u2014", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (l.guardianIds.isEmpty()) "No parent linked" else "${l.guardianIds.size} parent(s) linked",
                        if (l.guardianIds.isEmpty()) BrandDark else TealDark, if (l.guardianIds.isEmpty()) BrandTint else TealTint)
                    if (l.userId != null) Chip("Learner login", Blue, BlueTint)
                    if (canEdit) {
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { linking = l }) { Text("Link account", color = Ink, fontSize = 13.sp) }
                    }
                }
            }
        }
        if (learners.isEmpty()) NoteCard("No learners found.")
    }

    if (enrolling) {
        var name by remember { mutableStateOf("") }
        var school by remember { mutableStateOf<School?>(null) }
        var grade by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { enrolling = false },
            title = { Text("Enrol learner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Learner's name") }, singleLine = true)
                    SchoolPicker(s.schools, school, { school = it })
                    Text("Grade", fontSize = 13.sp, color = Muted)
                    GradeChips(grade) { grade = it }
                }
            },
            confirmButton = { TextButton(onClick = { vm.addLearner(name, school, grade); enrolling = false }) { Text("Enrol") } },
            dismissButton = { TextButton(onClick = { enrolling = false }) { Text("Cancel") } }
        )
    }

    linking?.let { learner ->
        // Parents become guardians; a learner login becomes the learner's own sign-in.
        val candidates = s.users.filter { (it.role == Role.PARENT || it.role == Role.LEARNER) && it.approved && it.active }
        AlertDialog(
            onDismissRequest = { linking = null },
            title = { Text("Link an account to ${learner.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (candidates.isEmpty()) Text("No approved parent or learner accounts yet.", color = Muted, fontSize = 14.sp)
                    candidates.forEach { c ->
                        TextButton(onClick = { vm.linkAccount(learner, c); linking = null }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(c.displayName, color = Ink)
                                Text(
                                    c.role.label + if (c.childName.isNotBlank()) " of ${c.childName}" else "",
                                    fontSize = 12.sp, color = Muted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linking = null }) { Text("Close") } }
        )
    }
}

/* ---------- Sessions: schedule, mark, close, cancel ---------- */

/** A date button that opens the Material date picker; [dateIso] is yyyy-MM-dd. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(placeholder: String, dateIso: String, onDate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton({ open = true }, Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp), tint = Brand)
        Spacer(Modifier.width(8.dp))
        Text(if (dateIso.isBlank()) placeholder else formatDisplayDate(dateIso), color = Ink)
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(dateIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun StatusChip(status: String) = when (status) {
    SessionStatus.COMPLETED -> Chip("Completed", TealDark, TealTint)
    SessionStatus.CANCELLED -> Chip("Cancelled", Muted, Track)
    else -> Chip("Scheduled", BrandDark, BrandTint)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionsScreen(
    user: User,
    onMarkAttendance: (String) -> Unit,
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var scheduling by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf<Session?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    val mySchool = user.schoolId.ifBlank { s.schools.firstOrNull { it.name == user.school }?.id ?: "" }
    // A teacher works with their own school's sessions; staff see them all.
    val mine = if (user.role == Role.TEACHER) s.sessions.filter { it.schoolId == mySchool } else s.sessions
    val shown = mine.filter { filter == null || it.status == filter }
    val open = shown.filter { it.status == SessionStatus.SCHEDULED }.sortedBy { it.date }
    val closed = shown.filter { it.status != SessionStatus.SCHEDULED }.sortedByDescending { it.date }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTop(mine.size, "sessions", action = if (SessionRules.canSchedule(user.role)) "Schedule session" else null) { scheduling = true }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<String?>(null, SessionStatus.SCHEDULED, SessionStatus.COMPLETED, SessionStatus.CANCELLED).forEach { f ->
                FilterChip(
                    selected = filter == f, onClick = { filter = f },
                    label = { Text(f?.let { SessionStatus.label(it) } ?: "All") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandTint, selectedLabelColor = BrandDark)
                )
            }
        }
        s.message?.let { NoteCard(it) }
        (open + closed).forEach { se ->
            val rows = s.attendance.filter { it.sessionId == se.id }
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = Brand, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(se.programme, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                        Text("${s.schoolName(se.schoolId)} \u00B7 Facilitator: ${se.facilitator}", fontSize = 13.sp, color = Muted)
                    }
                    Chip(formatDisplayDate(se.date), BrandDark, BrandTint)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(se.status)
                    Text(
                        if (rows.isEmpty()) "Attendance not marked" else "${rows.count { it.present }}/${rows.size} present",
                        fontSize = 12.sp, color = Muted
                    )
                }
                val canMarkHere = user.role == Role.TEACHER && SessionRules.canMarkAttendance(user.role, se)
                val canClose = SessionRules.canComplete(user.role, se)
                val canCancel = SessionRules.canCancel(user.role, se)
                val canReopen = SessionRules.canReopen(user.role, se)
                if (canMarkHere || canClose || canCancel || canReopen) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (canMarkHere) Button(onClick = { onMarkAttendance(se.id) }) { Text("Mark attendance") }
                        if (canClose) OutlinedButton(onClick = { vm.changeSessionStatus(user, se, SessionStatus.COMPLETED) }) { Text("Close session", color = Ink) }
                        if (canCancel) TextButton(onClick = { cancelling = se }) { Text("Cancel session", color = BrandDark) }
                        if (canReopen) OutlinedButton(onClick = { vm.changeSessionStatus(user, se, SessionStatus.SCHEDULED) }) { Text("Reopen", color = Ink) }
                    }
                }
            }
        }
        if (shown.isEmpty()) NoteCard(
            if (mine.isEmpty()) "No sessions yet. Tap Schedule session to plan the first one." else "No sessions match this filter."
        )
    }

    cancelling?.let { se ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel this session?") },
            text = { Text("${se.programme} on ${formatDisplayDate(se.date)} will be marked as cancelled and no attendance can be recorded. A coordinator can reopen it.") },
            confirmButton = {
                TextButton(onClick = { vm.changeSessionStatus(user, se, SessionStatus.CANCELLED); cancelling = null }) { Text("Cancel session") }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep it") } }
        )
    }

    if (scheduling) {
        val teacherSchool = s.schools.firstOrNull { it.id == mySchool }
        var school by remember { mutableStateOf(if (user.role == Role.TEACHER) teacherSchool else null) }
        var programme by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        var facilitator by remember { mutableStateOf(if (user.role == Role.TEACHER) user.displayName else "") }
        val suggestions = (s.programmes.filter { it.schoolId == school?.id }.map { it.name } + s.programmes.map { it.name }).distinct().take(6)
        val teachersHere = s.users.filter { it.role == Role.TEACHER && school != null && (it.schoolId == school?.id || it.school == school?.name) }
        AlertDialog(
            onDismissRequest = { scheduling = false },
            title = { Text("Schedule session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (user.role == Role.TEACHER) {
                        Text(teacherSchool?.name ?: "Your account has no school yet. Ask an administrator to set it.",
                            fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
                    } else {
                        SchoolPicker(s.schools, school, { school = it; facilitator = "" })
                    }
                    OutlinedTextField(programme, { programme = it }, label = { Text("Programme") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { AssistChip(onClick = { programme = it }, label = { Text(it, fontSize = 12.sp) }) }
                    }
                    DatePickerField("Pick a date", date) { date = it }
                    if (user.role.isStaff) {
                        OptionPicker(teachersHere.map { it.displayName }, facilitator.ifBlank { null }, { facilitator = it },
                            "Facilitator (teacher)")
                    } else {
                        Text("Facilitator: ${user.displayName}", fontSize = 13.sp, color = Muted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.scheduleSession(user, school, programme, date, facilitator); scheduling = false }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = { scheduling = false }) { Text("Cancel") } }
        )
    }
}

/* ---------- Attendance overview (learners see their own; staff see every session) ---------- */

@Composable
fun AttendanceOverviewScreen(
    user: User,
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    if (user.role == Role.LEARNER) {
        val me = s.learnerFor(user)
        if (me == null) {
            NoteCard("No learner profile is linked to your account yet. Ask your coordinator to link it.")
            return
        }
        val mine = s.attendance.filter { it.learnerId == me.id }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LpmsCard {
                SectionTitle("My attendance", Icons.Filled.CheckCircle)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${mine.count { it.present }}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TealDark)
                        Text("Sessions attended", fontSize = 12.sp, color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${s.attendanceRate(me.id) ?: 0}%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand)
                        Text("Attendance rate", fontSize = 12.sp, color = Muted)
                    }
                }
            }
            LpmsCard {
                SectionTitle("Session history", Icons.Filled.Schedule)
                val sessionsById = s.sessions.associateBy { it.id }
                mine.sortedByDescending { sessionsById[it.sessionId]?.date }.forEach { rec ->
                    val se = sessionsById[rec.sessionId]
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
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
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LpmsCard {
                SectionTitle("Attendance by session", Icons.Filled.CheckCircle)
                s.sessions.sortedBy { it.date }.forEach { se ->
                    val rows = s.attendance.filter { it.sessionId == se.id }
                    if (rows.isEmpty()) {
                        Text("${se.programme} \u00B7 ${formatDisplayDate(se.date)} \u2014 not marked yet", fontSize = 13.sp, color = Muted,
                            modifier = Modifier.padding(vertical = 6.dp))
                    } else {
                        RateBar("${se.programme} \u00B7 ${formatDisplayDate(se.date)} (${rows.count { it.present }}/${rows.size})",
                            rows.count { it.present } / rows.size.toFloat(), Teal)
                        rows.maxByOrNull { it.markedAt }?.takeIf { it.markedAt > 0 }?.let {
                            Text("Marked by ${it.markedByName.ifBlank { "a teacher" }} \u00B7 ${timeAgo(it.markedAt)}", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Progress (reading levels); teachers can record a new level ---------- */

@Composable
fun ProgressScreen(
    user: User,
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var recording by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (user.role == Role.LEARNER) {
            val me = s.learnerFor(user)
            LpmsCard {
                SectionTitle("My progress", Icons.Filled.ShowChart)
                val mine = s.progress.filter { it.learnerId == me?.id }
                if (me == null) Text("No learner profile is linked to your account yet.", color = Muted, fontSize = 14.sp)
                else if (mine.isEmpty()) Text("No progress recorded yet.", color = Muted, fontSize = 14.sp)
                mine.groupBy { it.programme }.forEach { (prog, rows) ->
                    val latest = rows.sortedBy { it.date }.lastOrNull() // same rule as LearnerInsights.latestLevel
                    ProgressRow("$prog \u2014 level ${latest?.level ?: 0} of 5", ((latest?.level ?: 0) / 5f).coerceAtMost(1f))
                }
            }
        } else {
            val gradeNumber = LearnerInsights.parseGrade(user.grade)
            // The repository already limits a teacher to their own school; here they see their own grade.
            val shown = if (user.role == Role.TEACHER) s.learners.filter { it.grade == gradeNumber } else s.learners
            if (user.role == Role.TEACHER) ScreenTop(shown.size, "learners in your class", action = "Record progress") { recording = true }
            s.message?.let { NoteCard(it) }
            LpmsCard {
                SectionTitle("Learner progress", Icons.Filled.ShowChart)
                if (shown.isEmpty()) Text("No learners to show yet.", color = Muted, fontSize = 14.sp)
                shown.forEach { l ->
                    val level = s.latestLevel(l.id)
                    val gain = ReportMetrics.gain(l.id, s.progress)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name, Modifier.weight(1f), fontSize = 14.sp, color = Ink)
                        if (gain != null && gain != 0) {
                            Text(if (gain > 0) "+$gain" else "$gain", fontSize = 12.sp, color = if (gain > 0) TealDark else BrandDark,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Level ${level ?: "\u2014"} of 5", fontSize = 13.sp, color = Muted)
                    }
                    LinearProgressIndicator(progress = { ((level ?: 0) / 5f).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp), color = Teal, trackColor = Track,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (recording) {
        val gradeNumber = LearnerInsights.parseGrade(user.grade)
        val classLearners = s.learners.filter { it.grade == gradeNumber }
        var learner by remember { mutableStateOf<Learner?>(null) }
        var programme by remember { mutableStateOf(s.programmes.firstOrNull()?.name) }
        var level by remember { mutableIntStateOf(3) }
        AlertDialog(
            onDismissRequest = { recording = false },
            title = { Text("Record reading level") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionPicker(classLearners.map { it.name }, learner?.name, { n -> learner = classLearners.firstOrNull { it.name == n } },
                        "Choose learner")
                    OptionPicker(s.programmes.map { it.name }, programme, { programme = it }, "Choose programme")
                    Text("Reading level (1 to 5)", fontSize = 13.sp, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { lv ->
                            FilterChip(selected = level == lv, onClick = { level = lv }, label = { Text("$lv") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandTint, selectedLabelColor = BrandDark))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    learner?.let { vm.addProgress(user, it.id, programme ?: "", level) }
                    recording = false
                }, enabled = learner != null) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { recording = false }) { Text("Cancel") } }
        )
    }
}

/* ---------- Resources & Shared Resources ---------- */

private val RESOURCE_TYPES = listOf("Lesson plan", "Worksheet", "Storybook", "Assessment")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResourceList(user: User, sharedOnly: Boolean, vm: ScreensViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val canEdit = user.role == Role.TEACHER || user.role.isStaff
    val shown = s.resources.filter { !sharedOnly || it.shared }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTop(shown.size, if (sharedOnly) "shared resources" else "resources",
            action = if (canEdit) "Add resource" else null) { adding = true }
        shown.forEach { r ->
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (r.shared) Icons.Filled.Share else Icons.Filled.Folder,
                        null, tint = if (r.shared) Purple else Brand, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
                        Text("Added by ${r.uploadedBy} \u00B7 ${timeAgo(r.createdAt)}", fontSize = 13.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(r.type, Blue, BlueTint)
                    if (r.shared) Chip("Shared", TealDark, TealTint)
                    if (canEdit) {
                        Spacer(Modifier.weight(1f))
                        Text("Share", fontSize = 13.sp, color = Muted)
                        Switch(checked = r.shared, onCheckedChange = { vm.setResourceShared(r.id, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Teal))
                    }
                }
            }
        }
        if (shown.isEmpty()) NoteCard("Nothing here yet.")
        s.message?.let { NoteCard(it) }
    }
    if (adding) {
        var title by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(RESOURCE_TYPES.first()) }
        var share by remember { mutableStateOf(sharedOnly) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add resource") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RESOURCE_TYPES.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Share with colleagues", Modifier.weight(1f))
                        Switch(checked = share, onCheckedChange = { share = it })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.addResource(user, title, type, share); adding = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ResourcesScreen(user: User, vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)) =
    ResourceList(user, sharedOnly = false, vm)

@Composable
fun SharedResourcesScreen(user: User, vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)) =
    ResourceList(user, sharedOnly = true, vm)

/* ---------- Reports: what a funder wants to know (reach, attendance, reading-level gain), exportable ---------- */

@Composable
fun ReportsScreen(
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = LocalDate.now().toString()
    val teachers = s.users.count { it.role == Role.TEACHER }
    val headline = ReportMetrics.headline(s.schools, teachers, s.learners, s.attendance, s.progress)
    val rows = s.programmes.map { ReportMetrics.programme(it, s.schools, s.enrollments, s.sessions, s.attendance, s.progress) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(headline.schools.toString(), "Schools", Icons.Filled.School, Modifier.weight(1f))
            MetricCard(headline.teachers.toString(), "Teachers", Icons.Filled.Person, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(headline.learners.toString(), "Learners", Icons.Filled.ChildCare, Modifier.weight(1f))
            MetricCard(headline.attendance?.let { "$it%" } ?: "\u2014", "Attendance", Icons.Filled.CheckCircle, Modifier.weight(1f))
        }
        LpmsCard {
            SectionTitle("Reading progress", Icons.Filled.ShowChart)
            Text(
                if (headline.learnersAssessed == 0) "No learners have been assessed twice yet, so there is no gain to report."
                else "${headline.learnersImproved} of ${headline.learnersAssessed} assessed learners improved, " +
                    "by ${headline.averageGain ?: 0.0} reading levels on average.",
                fontSize = 14.sp, color = Ink
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { shareCsv(context, "lpms-impact-report-$today.csv", ReportMetrics.toCsv(headline, rows, today)) }) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Export CSV")
            }
            OutlinedButton(onClick = { vm.generateReports() }) { Text("Save report records", color = Ink) }
        }
        s.message?.let { NoteCard(it) }
        rows.forEach { m ->
            LpmsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Description, null, tint = Teal, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.programmeName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
                        Text(m.schoolName, fontSize = 13.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("${m.learnersReached} learners", Blue, BlueTint)
                    Chip(m.attendance?.let { "$it% attendance" } ?: "No attendance yet", TealDark, TealTint)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${m.sessionsHeld} sessions held \u00B7 " +
                        if (m.learnersAssessed == 0) "no reading gain yet"
                        else "${m.learnersImproved}/${m.learnersAssessed} improved, ${m.averageGain} levels on average",
                    fontSize = 12.sp, color = Muted
                )
            }
        }
        if (s.reports.isNotEmpty()) {
            LpmsCard {
                SectionTitle("Saved report records", Icons.Filled.Description)
                s.reports.take(5).forEach { FeedItem(Icons.Filled.Description, it.title, formatDisplayDate(it.createdAt), Teal) }
            }
        }
    }
}

/* ---------- Analytics ---------- */

@Composable
fun AnalyticsScreen(
    vm: ScreensViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val overall = s.overallAttendance() ?: 0
    val avgLevel = s.progress.map { it.level }.average().let { if (it.isNaN()) 0.0 else it }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(s.learners.size.toString(), "Learners", Icons.Filled.ChildCare, Modifier.weight(1f))
            MetricCard("$overall%", "Attendance", Icons.Filled.CheckCircle, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("%.1f".format(avgLevel), "Avg reading level", Icons.Filled.Star, Modifier.weight(1f))
            MetricCard(s.programmes.size.toString(), "Programmes", Icons.Filled.MenuBook, Modifier.weight(1f))
        }
        LpmsCard {
            SectionTitle("Reading level distribution", Icons.Filled.BarChart)
            val max = (1..5).maxOf { lv -> s.progress.count { it.level == lv } }.coerceAtLeast(1)
            (1..5).forEach { lv ->
                ProgressRow("Level $lv", s.progress.count { it.level == lv } / max.toFloat())
            }
        }
        LpmsCard {
            SectionTitle("Learners per school", Icons.Filled.School)
            val max = (s.schools.maxOfOrNull { sc -> s.learners.count { it.schoolId == sc.id } } ?: 0).coerceAtLeast(1)
            s.schools.forEach { sc ->
                ProgressRow("${sc.name}: ${s.learners.count { it.schoolId == sc.id }}", s.learners.count { it.schoolId == sc.id } / max.toFloat(), Teal)
            }
        }
    }
}

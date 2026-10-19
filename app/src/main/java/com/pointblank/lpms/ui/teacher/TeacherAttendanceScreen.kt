package com.pointblank.lpms.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.core.model.Session
import com.pointblank.lpms.core.model.SessionStatus
import com.pointblank.lpms.core.util.label
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.ErrorBanner
import com.pointblank.lpms.ui.components.LpmsCard
import com.pointblank.lpms.ui.theme.*

@Composable
private fun TogglePill(
    label: String, icon: ImageVector, selected: Boolean,
    bg: Color, border: Color, fg: Color, enabled: Boolean = true, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) bg else Track)
            .border(if (selected) 2.dp else 1.dp, if (selected) border else Color(0xFFD9CDC0), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = if (selected) fg else Muted, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 13.sp, color = if (selected) fg else Muted)
    }
}

private fun sessionLabel(s: Session) = "${s.label} \u00B7 ${SessionStatus.label(s.status)}"


/** Website "Mark Attendance": pick a session, mark each learner present or absent, save. */
@Composable
fun TeacherAttendanceScreen(
    initialSessionId: String? = null,
    viewModel: TeacherAttendanceViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() } // fresh roster and marks every time the screen opens
    // Opened from the Sessions screen: jump to that session once the list has loaded.
    LaunchedEffect(initialSessionId, state.sessions) {
        if (initialSessionId != null && state.sessions.any { it.id == initialSessionId }) viewModel.selectSession(initialSessionId)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val selectedLabel = state.selected?.let { sessionLabel(it) }
        ?: if (state.hasSchool) "No sessions scheduled at your school yet" else "Your account has no school yet"

    LpmsCard {
        Box {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth(), enabled = state.sessions.isNotEmpty()) {
                Text(selectedLabel, color = Ink, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, null, tint = Ink)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                state.sessions.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(sessionLabel(s)) },
                        onClick = { viewModel.selectSession(s.id); menuOpen = false }
                    )
                }
            }
        }
        if (!state.hasSchool) {
            Spacer(Modifier.height(8.dp))
            Text("Ask an administrator to set your school and grade. They decide which learners and sessions you see.",
                fontSize = 13.sp, color = Muted)
        }
        state.selected?.let { session ->
            if (!state.canMark) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "This session is ${SessionStatus.label(session.status).lowercase()}, so the register is locked. " +
                        "A coordinator can reopen it if something needs correcting.",
                    fontSize = 13.sp, color = BrandDark,
                    modifier = Modifier.fillMaxWidth().background(BrandTint, RoundedCornerShape(12.dp)).padding(10.dp)
                )
            }
        }
        state.lastSaved?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = viewModel::reload) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp), tint = Brand)
            Spacer(Modifier.width(6.dp))
            Text("Load", color = Ink)
        }
        Spacer(Modifier.height(16.dp))

        if (state.roster.isEmpty() && !state.isLoading) {
            Text(
                "No learners found for your class yet - enrol learners at your school and grade first.",
                color = Muted, fontSize = 14.sp
            )
        }
        state.roster.forEach { learner ->
            val present = state.marks[learner.id] ?: true
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Person, null, tint = Brand, modifier = Modifier.size(20.dp))
                    Text(learner.name, fontWeight = FontWeight.Medium, color = Ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TogglePill("Present", Icons.Filled.Check, present, TealTint, Teal, TealDark, state.canMark) { viewModel.setMark(learner.id, true) }
                    TogglePill("Absent", Icons.Filled.Close, !present, BrandTint, Brand, BrandDark, state.canMark) { viewModel.setMark(learner.id, false) }
                }
            }
            HorizontalDivider(color = Line)
        }

        state.message?.let { msg ->
            Spacer(Modifier.height(12.dp))
            if (state.isError) ErrorBanner(msg)
            else Text(
                msg, color = TealDark, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().background(TealTint, RoundedCornerShape(12.dp)).padding(12.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            if (state.canClose) {
                OutlinedButton(onClick = { confirmClose = true }) { Text("Close session", color = Ink) }
            }
            Button(
                onClick = viewModel::save,
                enabled = state.canMark,
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) {
                Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save attendance")
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close this session?") },
            text = { Text("Save the register first. Closing the session locks it: only a coordinator can reopen it to make corrections.") },
            confirmButton = { TextButton(onClick = { viewModel.closeSession(); confirmClose = false }) { Text("Close session") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Not yet") } }
        )
    }
}

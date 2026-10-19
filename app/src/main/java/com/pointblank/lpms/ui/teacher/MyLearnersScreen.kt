package com.pointblank.lpms.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.util.LearnerStatus
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.LpmsCard
import com.pointblank.lpms.ui.theme.*

private fun statusColor(status: LearnerStatus): Color = when (status) {
    LearnerStatus.EXCELLING, LearnerStatus.ON_TRACK -> Teal
    LearnerStatus.NEEDS_SUPPORT -> Brand
    LearnerStatus.NOT_ASSESSED -> Color(0xFF9C8F7E)
}

@Composable
private fun StatusBadge(status: LearnerStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(statusColor(status), CircleShape))
        Text(status.label, fontSize = 13.sp, color = Ink)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Muted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

/** Website "My Learners": search plus the class roster with level, attendance and status. */
@Composable
fun MyLearnersScreen(
    viewModel: MyLearnersViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LearnerRow?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search learners...") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Brand) },
            singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Info, null, tint = Muted, modifier = Modifier.size(16.dp))
            Text("Learners are enrolled by your Programme Coordinator.", fontSize = 13.sp, color = Muted)
        }

        val rows = state.visible
        if (rows.isEmpty() && !state.isLoading) {
            LpmsCard { Text("No learners found for your class.", color = Muted) }
        }
        rows.forEach { row ->
            LpmsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.learner.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                        Text(Grades.display(row.learner.grade), fontSize = 13.sp, color = Muted)
                    }
                    OutlinedButton(onClick = { selected = row }) { Text("View", color = Ink) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    LabelValue("Reading level", row.level?.toString() ?: "\u2014")
                    LabelValue("Attendance", row.attendance?.let { "$it%" } ?: "\u2014")
                    StatusBadge(row.status)
                }
            }
        }
    }

    selected?.let { row ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            title = { Text(row.learner.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelValue("Grade", Grades.display(row.learner.grade))
                    LabelValue("Reading level", row.level?.toString() ?: "Not yet assessed")
                    LabelValue("Attendance", row.attendance?.let { "$it%" } ?: "No sessions marked yet")
                    StatusBadge(row.status)
                    // TODO: replace with a jump to Record Progress once that screen exists
                }
            }
        )
    }
}

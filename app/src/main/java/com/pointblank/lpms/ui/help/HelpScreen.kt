package com.pointblank.lpms.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val faq = listOf(
    "How do I log in?" to "Use the email and password you registered with, or tap Continue with Google.",
    "How do I register?" to "Tap Register here and choose Teacher or Parent / Guardian. Pick your school; teachers also pick their grade, and parents enter their child's name. An administrator or coordinator must approve your account before you can sign in.",
    "How do learners get on the system?" to "Learners are enrolled by their coordinator, not by themselves. Parents are linked to their child once approved.",
    "I am a parent but I see no children." to "Your coordinator links your account to your child's record after approving you. Ask them to check the child's name you registered.",
    "How do teachers mark attendance?" to "Open Mark Attendance, choose a session, set each learner to Present or Absent and tap Save attendance. Teachers can also record reading levels under Progress.",
    "What do the reading levels mean?" to "Levels run from 1 to 5. Level 3 is on track, 4 or 5 is excelling, and 1 or 2 means the learner needs support.",
    "How do I change my password or name?" to "Open Settings. Changing your password needs your current password. Google accounts have no LPMS password.",
    "What if I have no signal?" to "Keep working. What you save is kept on the phone and uploads when you are back online. A banner tells you when you are offline.",
    "Nothing loads. What now?" to "Check your internet connection and open the screen again."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            faq.forEach { (q, a) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(q, style = MaterialTheme.typography.titleSmall)
                    Text(a, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

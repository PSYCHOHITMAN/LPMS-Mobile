package com.pointblank.lpms.ui.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.core.util.Messaging
import com.pointblank.lpms.core.util.timeAgo
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.ErrorBanner
import com.pointblank.lpms.ui.components.InfoBanner
import com.pointblank.lpms.ui.components.LpmsCard
import com.pointblank.lpms.ui.theme.*

/** Parents write to teachers and coordinators; teachers and staff reply and can start conversations too. */
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var composing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }
    // Back from a conversation returns to the inbox (HomeScreen's handler then returns to the dashboard).
    BackHandler(enabled = s.openWith != null) { viewModel.close() }

    if (s.openWith == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.conversations.size} conversations", fontSize = 14.sp, color = Muted)
                Spacer(Modifier.weight(1f))
                if (s.contacts.isNotEmpty()) {
                    OutlinedButton(onClick = { composing = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = Brand)
                        Spacer(Modifier.width(6.dp))
                        Text("New message", color = Ink)
                    }
                }
            }
            s.note?.let { if (s.isError) ErrorBanner(it) else InfoBanner(it) }
            s.conversations.forEach { c ->
                LpmsCard(Modifier.clickable { viewModel.open(c.otherId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(c.otherName, fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 16.sp, color = Ink)
                            Text(c.lastText, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(timeAgo(c.lastAt), fontSize = 11.sp, color = Muted)
                            if (c.unread > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text("${c.unread}", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.background(Brand, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
            if (s.conversations.isEmpty() && !s.loading) {
                LpmsCard {
                    Text(
                        if (s.contacts.isEmpty()) "There is nobody you can message yet. Your coordinator links your account to your child, and then your child's teachers appear here."
                        else "No messages yet. Tap New message to write to someone.",
                        color = Muted, fontSize = 14.sp
                    )
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.close() }) { Text("Back") }
                Text(s.openName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Ink)
            }
            s.note?.let { if (s.isError) ErrorBanner(it) else InfoBanner(it) }
            if (s.thread.isEmpty()) {
                Text("Say hello. Messages are private between you and ${s.openName}.", fontSize = 13.sp, color = Muted)
            }
            s.thread.forEach { m ->
                val mine = m.fromId == s.me?.id
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 280.dp)
                            .background(if (mine) BrandTint else androidx.compose.ui.graphics.Color.White, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(m.text, fontSize = 14.sp, color = Ink)
                        Text(timeAgo(m.createdAt), fontSize = 10.sp, color = Muted)
                    }
                }
            }
            var text by remember { mutableStateOf("") }
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(Messaging.MAX_LENGTH + 1) },
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Write a message...") }, maxLines = 4,
                supportingText = { Text("${text.length}/${Messaging.MAX_LENGTH}") }
            )
            Button(
                onClick = { viewModel.send(text); if (Messaging.validate(text) == null) text = "" },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send")
            }
        }
    }

    if (composing) {
        AlertDialog(
            onDismissRequest = { composing = false },
            title = { Text("Message who?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    s.contacts.forEach { c ->
                        TextButton(onClick = { composing = false; viewModel.open(c.userId) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(c.name, color = Ink)
                                Text(
                                    c.role.label + if (c.note.isNotBlank()) " \u00B7 ${c.note}" else "",
                                    fontSize = 12.sp, color = Muted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { composing = false }) { Text("Close") } }
        )
    }
}

package com.pointblank.lpms.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointblank.lpms.core.model.Contact
import com.pointblank.lpms.core.model.Message
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.User
import com.pointblank.lpms.core.util.Conversation
import com.pointblank.lpms.core.util.Messaging
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.LpmsRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessagesUiState(
    val loading: Boolean = true,
    val me: User? = null,
    /** People this user may start a conversation with. */
    val contacts: List<Contact> = emptyList(),
    val messages: List<Message> = emptyList(),
    /** The other person's id while a conversation is open; null shows the inbox. */
    val openWith: String? = null,
    val note: String? = null,
    val isError: Boolean = false
) {
    val conversations: List<Conversation> get() = me?.let { Messaging.conversations(it.id, messages) } ?: emptyList()
    val thread: List<Message> get() = me?.let { u -> openWith?.let { Messaging.thread(u.id, it, messages) } } ?: emptyList()
    val openName: String
        get() = contacts.firstOrNull { it.userId == openWith }?.name
            ?: conversations.firstOrNull { it.otherId == openWith }?.otherName ?: "Conversation"
}

class MessagesViewModel(
    private val repo: LpmsRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    private var loadedForUserId: String? = null

    fun load() {
        viewModelScope.launch {
            try {
                val me = auth.currentUser.value
                if (me?.id != loadedForUserId) { // new person signed in: start from a clean inbox
                    loadedForUserId = me?.id
                    _state.value = MessagesUiState()
                }
                if (me == null) return@launch
                // Which schools count as "mine": a parent's children's schools, or a teacher's own school.
                val schoolIds = when (me.role) {
                    Role.PARENT -> repo.learners().map { it.schoolId }.toSet()
                    Role.TEACHER -> setOf(repo.schoolIdFor(me)).filter { it.isNotBlank() }.toSet()
                    else -> emptySet()
                }
                val contacts = Messaging.allowedContacts(me, repo.contacts(), schoolIds)
                val messages = repo.messages()
                _state.update { it.copy(loading = false, me = me, contacts = contacts, messages = messages) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, note = "Could not load messages. Check your connection.", isError = true) }
            }
        }
    }

    fun open(otherId: String) {
        _state.update { it.copy(openWith = otherId, note = null) }
        markRead(otherId)
    }

    fun close() {
        _state.update { it.copy(openWith = null, note = null) }
        load() // refresh the inbox
    }

    private fun markRead(otherId: String) {
        val s = _state.value
        val me = s.me ?: return
        val ids = s.messages.filter { it.fromId == otherId && it.toId == me.id && !it.read }.map { it.id }
        if (ids.isEmpty()) return
        // Show them as read straight away, then tell the server.
        _state.update { st -> st.copy(messages = st.messages.map { if (it.id in ids) it.copy(read = true) else it }) }
        viewModelScope.launch {
            try { repo.markMessagesRead(ids) } catch (e: CancellationException) { throw e } catch (e: Exception) { /* will retry on next open */ }
        }
    }

    fun send(text: String) {
        val s = _state.value
        val me = s.me ?: return
        val otherId = s.openWith ?: return
        Messaging.validate(text)?.let { err -> _state.update { it.copy(note = err, isError = true) }; return }
        val message = Message(UUID.randomUUID().toString(), me.id, me.displayName, otherId, s.openName, text.trim(), System.currentTimeMillis())
        // Show it straight away; it uploads in the background (and later if the phone is offline).
        _state.update { it.copy(messages = it.messages + message, note = null, isError = false) }
        viewModelScope.launch {
            try {
                val synced = repo.sendMessage(message)
                if (!synced) _state.update { it.copy(note = "Sent from this phone. It will deliver when you're back online.", isError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(messages = it.messages.filterNot { m -> m.id == message.id },
                        note = e.localizedMessage ?: "Could not send the message.", isError = true)
                }
            }
        }
    }
}

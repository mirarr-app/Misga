package com.miss.ga.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import com.miss.ga.data.model.RuleCategory
import com.miss.ga.data.model.SenderPreference
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.data.repository.SmsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val isLoading: Boolean = true,
    val threadId: Long = 0,
    val address: String = "",
    val contactName: String? = null,
    val messages: List<SmsMessage> = emptyList(),
    val senderPreference: SenderPreference? = null,
    val senderRules: List<FilterRule> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val hasMoreOlder: Boolean = true,
    val isLoadingOlder: Boolean = false
)

class ChatViewModel(
    application: Application,
    private val initialThreadId: Long,
    private val initialAddress: String,
    private val initialContactName: String?,
    private val initialMessageId: Long? = null
) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private val dbHelper = MisgaDatabaseHelper.getInstance(application)
    private var messagesJob: Job? = null
    private var olderJob: Job? = null

    private val _uiState = MutableStateFlow(
        ChatUiState(
            threadId = initialThreadId,
            address = initialAddress,
            contactName = initialContactName
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
        loadSenderSettings()
    }

    fun loadMessages() {
        if (messagesJob?.isActive == true) return
        messagesJob = viewModelScope.launch {
            val hadMessages = _uiState.value.messages.isNotEmpty()
            if (!hadMessages) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            try {
                val page = repository.getMessagesForThread(
                    threadId = initialThreadId,
                    address = initialAddress,
                    beforeDate = null,
                    limit = MESSAGE_PAGE_SIZE
                )
                if (!hadMessages) {
                    repository.markThreadRead(initialThreadId)
                    _uiState.value = _uiState.value.copy(
                        messages = page,
                        hasMoreOlder = page.size == MESSAGE_PAGE_SIZE,
                        error = null
                    )
                    if (initialMessageId != null) {
                        loadUntilMessageVisible(initialMessageId)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messages = mergeLatestPage(_uiState.value.messages, page),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load chat messages"
                )
            }
        }
    }

    fun loadOlderMessages() {
        val state = _uiState.value
        if (!state.hasMoreOlder || state.isLoadingOlder || state.isLoading) return
        if (olderJob?.isActive == true) return
        olderJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOlder = true)
            try {
                val beforeDate = _uiState.value.messages.firstOrNull()?.date
                if (beforeDate == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingOlder = false,
                        hasMoreOlder = false
                    )
                    return@launch
                }
                val page = repository.getMessagesForThread(
                    threadId = initialThreadId,
                    address = initialAddress,
                    beforeDate = beforeDate,
                    limit = MESSAGE_PAGE_SIZE
                )
                val current = _uiState.value.messages
                val existingIds = current.mapTo(HashSet()) { it.id }
                val older = page.filter { it.id !in existingIds }
                _uiState.value = _uiState.value.copy(
                    isLoadingOlder = false,
                    messages = older + current,
                    hasMoreOlder = page.size == MESSAGE_PAGE_SIZE && older.isNotEmpty()
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingOlder = false)
            }
        }
    }

    private suspend fun loadUntilMessageVisible(messageId: Long) {
        var pagesLoaded = 0
        while (pagesLoaded < MAX_TARGET_PAGES) {
            val state = _uiState.value
            if (state.messages.any { it.id == messageId }) return
            if (!state.hasMoreOlder) return
            val beforeDate = state.messages.firstOrNull()?.date ?: return
            val page = repository.getMessagesForThread(
                threadId = initialThreadId,
                address = initialAddress,
                beforeDate = beforeDate,
                limit = MESSAGE_PAGE_SIZE
            )
            val current = _uiState.value.messages
            val existingIds = current.mapTo(HashSet()) { it.id }
            val older = page.filter { it.id !in existingIds }
            _uiState.value = _uiState.value.copy(
                messages = older + current,
                hasMoreOlder = page.size == MESSAGE_PAGE_SIZE && older.isNotEmpty()
            )
            pagesLoaded++
            if (older.isEmpty()) return
        }
    }

    private fun mergeLatestPage(
        existing: List<SmsMessage>,
        latestPage: List<SmsMessage>
    ): List<SmsMessage> {
        if (latestPage.isEmpty()) return existing
        val latestIds = HashSet<Long>(latestPage.size)
        var minDate = Long.MAX_VALUE
        for (msg in latestPage) {
            latestIds.add(msg.id)
            if (msg.date < minDate) minDate = msg.date
        }
        val olderKept = existing.filter { it.id !in latestIds && it.date < minDate }
        if (olderKept.isEmpty()) return latestPage
        return olderKept + latestPage
    }

    fun loadSenderSettings() {
        viewModelScope.launch {
            try {
                val pref = dbHelper.getSenderPreference(initialAddress)
                val senderRules = dbHelper.getSenderRules(initialAddress)
                _uiState.value = _uiState.value.copy(
                    senderPreference = pref,
                    senderRules = senderRules
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun sendMessage(text: String, onComplete: (Boolean) -> Unit = {}) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val success = repository.sendSms(initialAddress, text)
            _uiState.value = _uiState.value.copy(isSending = false)
            if (success) {
                loadMessages()
            }
            onComplete(success)
        }
    }

    fun toggleSpamReveal(messageId: Long, isRevealed: Boolean) {
        viewModelScope.launch {
            repository.revealSpamMessage(messageId, isRevealed)
            // Update local state in-place for instant UI responsiveness
            val updated = _uiState.value.messages.map {
                if (it.id == messageId) it.copy(isRevealed = isRevealed) else it
            }
            _uiState.value = _uiState.value.copy(messages = updated)
        }
    }

    fun markMessageNotSpam(messageId: Long) {
        viewModelScope.launch {
            repository.markMessageNotSpam(messageId)
            val updated = _uiState.value.messages.map {
                if (it.id == messageId) it.copy(isSpam = false, isRevealed = true) else it
            }
            _uiState.value = _uiState.value.copy(messages = updated)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            val success = repository.deleteMessage(messageId)
            if (success) {
                val updated = _uiState.value.messages.filter { it.id != messageId }
                _uiState.value = _uiState.value.copy(messages = updated)
            }
        }
    }

    fun updateSenderDefaultAction(action: FilterAction) {
        viewModelScope.launch {
            val currentPref = _uiState.value.senderPreference ?: SenderPreference(
                address = initialAddress,
                displayName = initialContactName
            )
            val updated = currentPref.copy(defaultAction = action)
            dbHelper.saveSenderPreference(updated)
            _uiState.value = _uiState.value.copy(senderPreference = updated)
        }
    }

    fun addSenderRule(pattern: String, isRegex: Boolean, action: FilterAction, name: String) {
        viewModelScope.launch {
            val rule = FilterRule(
                name = name.ifBlank { "Custom Rule for $initialAddress" },
                pattern = pattern,
                isRegex = isRegex,
                action = action,
                isEnabled = true,
                isPredefined = false,
                category = RuleCategory.CUSTOM,
                senderTarget = initialAddress,
                description = "Sender-specific rule"
            )
            dbHelper.insertCustomRule(rule)
            loadSenderSettings()
        }
    }

    companion object {
        private const val MESSAGE_PAGE_SIZE = 100
        private const val MAX_TARGET_PAGES = 20
    }
}

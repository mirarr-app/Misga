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
    val error: String? = null
)

class ChatViewModel(
    application: Application,
    private val initialThreadId: Long,
    private val initialAddress: String,
    private val initialContactName: String?
) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private val dbHelper = MisgaDatabaseHelper.getInstance(application)

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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val msgs = repository.getMessagesForThread(initialThreadId, initialAddress)
                repository.markThreadRead(initialThreadId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    messages = msgs,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load chat messages"
                )
            }
        }
    }

    fun loadSenderSettings() {
        viewModelScope.launch {
            try {
                val pref = dbHelper.getSenderPreference(initialAddress)
                val allRules = dbHelper.getAllRules()
                val senderRules = allRules.filter {
                    !it.senderTarget.isNullOrBlank() &&
                            dbHelper.normalizeAddress(it.senderTarget) == dbHelper.normalizeAddress(initialAddress)
                }
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
}

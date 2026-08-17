package com.miss.ga.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.repository.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val isLoading: Boolean = true,
    val threads: List<ConversationThread> = emptyList(),
    val filteredThreads: List<ConversationThread> = emptyList(),
    val searchQuery: String = "",
    val isDefaultSmsApp: Boolean = false,
    val hasSmsPermission: Boolean = true,
    val error: String? = null
)

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init {
        loadThreads()
    }

    fun loadThreads(silent: Boolean = false) {
        viewModelScope.launch {
            val showLoading = !silent && _uiState.value.threads.isEmpty()
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            _uiState.value = _uiState.value.copy(
                isDefaultSmsApp = repository.isDefaultSmsApp()
            )
            try {
                val threads = repository.getThreads()
                val query = _uiState.value.searchQuery
                val filtered = if (query.isBlank()) {
                    threads
                } else {
                    threads.filter {
                        (it.contactName?.contains(query, ignoreCase = true) == true) ||
                                it.address.contains(query, ignoreCase = true) ||
                                it.snippet.contains(query, ignoreCase = true)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    threads = threads,
                    filteredThreads = filtered,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load messages"
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        val threads = _uiState.value.threads
        val filtered = if (query.isBlank()) {
            threads
        } else {
            threads.filter {
                (it.contactName?.contains(query, ignoreCase = true) == true) ||
                        it.address.contains(query, ignoreCase = true) ||
                        it.snippet.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredThreads = filtered
        )
    }

    fun checkDefaultSmsStatus() {
        _uiState.value = _uiState.value.copy(isDefaultSmsApp = repository.isDefaultSmsApp())
    }

    fun deleteConversation(threadId: Long) {
        viewModelScope.launch {
            val success = repository.deleteThread(threadId)
            if (success) {
                val updatedThreads = _uiState.value.threads.filter { it.threadId != threadId }
                val updatedFiltered = _uiState.value.filteredThreads.filter { it.threadId != threadId }
                _uiState.value = _uiState.value.copy(
                    threads = updatedThreads,
                    filteredThreads = updatedFiltered
                )
            }
        }
    }

    fun markConversationRead(threadId: Long) {
        viewModelScope.launch {
            repository.markThreadRead(threadId)
            loadThreads(silent = true)
        }
    }
}

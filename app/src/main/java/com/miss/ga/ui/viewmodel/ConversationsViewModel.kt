package com.miss.ga.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.SearchMessageResult
import com.miss.ga.data.repository.SmsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val threads: List<ConversationThread> = emptyList(),
    val filteredThreads: List<ConversationThread> = emptyList(),
    val matchingMessages: List<SearchMessageResult> = emptyList(),
    val searchQuery: String = "",
    val showContactsOnly: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val hasSmsPermission: Boolean = true,
    val error: String? = null,
    val selectedThreadIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false
)

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var defaultSmsCheckJob: Job? = null

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init {
        loadThreads()
    }

    fun loadThreads(silent: Boolean = false) {
        if (loadJob?.isActive == true) {
            if (silent) return
            loadJob?.cancel()
        }
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDefaultSmsApp = repository.isDefaultSmsApp()
            )

            val cached = try {
                repository.getCachedThreads()
            } catch (e: Exception) {
                emptyList()
            }
            val query = _uiState.value.searchQuery.trim()
            if (cached.isNotEmpty() && _uiState.value.threads.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    threads = cached,
                    filteredThreads = filterThreads(cached, query)
                )
            } else {
                val showLoading = !silent && _uiState.value.threads.isEmpty()
                if (showLoading) {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }

            try {
                val threads = repository.getThreads()
                val currentQuery = _uiState.value.searchQuery.trim()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    threads = threads,
                    filteredThreads = filterThreads(threads, currentQuery),
                    error = null
                )
                if (currentQuery.isNotBlank()) {
                    val messages = repository.searchAllMessages(currentQuery)
                    _uiState.value = _uiState.value.copy(
                        matchingMessages = messages,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load messages"
                )
            }
        }
    }

    private fun filterThreads(threads: List<ConversationThread>, query: String): List<ConversationThread> {
        if (query.isBlank()) return threads
        return threads.filter {
            (it.contactName?.contains(query, ignoreCase = true) == true) ||
                    it.address.contains(query, ignoreCase = true) ||
                    it.snippet.contains(query, ignoreCase = true)
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                filteredThreads = _uiState.value.threads,
                matchingMessages = emptyList(),
                isSearching = false
            )
            return
        }

        val threads = _uiState.value.threads
        val filtered = threads.filter {
            (it.contactName?.contains(trimmed, ignoreCase = true) == true) ||
                    it.address.contains(trimmed, ignoreCase = true) ||
                    it.snippet.contains(trimmed, ignoreCase = true)
        }

        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredThreads = filtered,
            isSearching = true
        )

        searchJob = viewModelScope.launch {
            delay(200)
            val messageResults = repository.searchAllMessages(trimmed)
            _uiState.value = _uiState.value.copy(
                matchingMessages = messageResults,
                isSearching = false
            )
        }
    }

    fun toggleContactsOnly() {
        _uiState.value = _uiState.value.copy(
            showContactsOnly = !_uiState.value.showContactsOnly
        )
    }

    fun checkDefaultSmsStatus() {
        _uiState.value = _uiState.value.copy(isDefaultSmsApp = repository.isDefaultSmsApp())
    }

    fun refreshDefaultSmsStatus() {
        defaultSmsCheckJob?.cancel()
        checkDefaultSmsStatus()
        if (_uiState.value.isDefaultSmsApp) return
        defaultSmsCheckJob = viewModelScope.launch {
            repeat(10) {
                delay(250)
                checkDefaultSmsStatus()
                if (_uiState.value.isDefaultSmsApp) return@launch
            }
        }
    }

    fun enterSelectionMode(threadId: Long) {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = true,
            selectedThreadIds = setOf(threadId)
        )
    }

    fun toggleSelectThread(threadId: Long) {
        val currentSelected = _uiState.value.selectedThreadIds.toMutableSet()
        if (currentSelected.contains(threadId)) {
            currentSelected.remove(threadId)
        } else {
            currentSelected.add(threadId)
        }
        val isStillSelecting = currentSelected.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            selectedThreadIds = currentSelected,
            isSelectionMode = isStillSelecting
        )
    }

    fun selectAllThreads() {
        val visibleThreads = if (_uiState.value.showContactsOnly) {
            _uiState.value.filteredThreads.filter { it.isContact }
        } else {
            _uiState.value.filteredThreads
        }
        val allFilteredIds = visibleThreads.map { it.threadId }.toSet()
        val allSelected = _uiState.value.selectedThreadIds.containsAll(allFilteredIds) && allFilteredIds.isNotEmpty()
        if (allSelected) {
            _uiState.value = _uiState.value.copy(
                selectedThreadIds = emptySet(),
                isSelectionMode = false
            )
        } else {
            _uiState.value = _uiState.value.copy(
                selectedThreadIds = allFilteredIds,
                isSelectionMode = true
            )
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedThreadIds = emptySet(),
            isSelectionMode = false
        )
    }

    fun deleteConversation(threadId: Long) {
        viewModelScope.launch {
            val success = repository.deleteThread(threadId)
            if (success) {
                val updatedThreads = _uiState.value.threads.filter { it.threadId != threadId }
                val updatedFiltered = _uiState.value.filteredThreads.filter { it.threadId != threadId }
                val updatedSelected = _uiState.value.selectedThreadIds - threadId
                _uiState.value = _uiState.value.copy(
                    threads = updatedThreads,
                    filteredThreads = updatedFiltered,
                    selectedThreadIds = updatedSelected,
                    isSelectionMode = updatedSelected.isNotEmpty()
                )
                repository.saveCachedThreads(updatedThreads)
            }
        }
    }

    fun deleteSelectedConversations(onComplete: (Int) -> Unit = {}) {
        val targetIds = _uiState.value.selectedThreadIds
        if (targetIds.isEmpty()) return

        viewModelScope.launch {
            val count = targetIds.size
            repository.deleteThreads(targetIds)
            val updatedThreads = _uiState.value.threads.filter { it.threadId !in targetIds }
            val updatedFiltered = _uiState.value.filteredThreads.filter { it.threadId !in targetIds }
            _uiState.value = _uiState.value.copy(
                threads = updatedThreads,
                filteredThreads = updatedFiltered,
                selectedThreadIds = emptySet(),
                isSelectionMode = false
            )
            repository.saveCachedThreads(updatedThreads)
            onComplete(count)
        }
    }

    fun markConversationRead(threadId: Long) {
        viewModelScope.launch {
            repository.markThreadRead(threadId)
            loadThreads(silent = true)
        }
    }

    fun markSelectedConversationsRead(onComplete: (Int) -> Unit = {}) {
        val targetIds = _uiState.value.selectedThreadIds
        if (targetIds.isEmpty()) return

        viewModelScope.launch {
            val count = targetIds.size
            repository.markThreadsRead(targetIds)
            clearSelection()
            loadThreads(silent = true)
            onComplete(count)
        }
    }

    fun markAllConversationsRead(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.markAllMessagesRead()
            loadThreads(silent = true)
            onComplete()
        }
    }
}

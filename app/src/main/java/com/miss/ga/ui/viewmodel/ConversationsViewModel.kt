package com.miss.ga.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.SearchMessageResult
import com.miss.ga.data.repository.SmsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
    val error: String? = null
)

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private val dbHelper = MisgaDatabaseHelper.getInstance(application)
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var defaultSmsCheckJob: Job? = null
    private var observerDebounceJob: Job? = null
    private var lastLoadFinishedAt = 0L
    private var smsContentObserver: ContentObserver? = null

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    val selectedThreadIds = mutableStateSetOf<Long>()
    var isSelectionMode by mutableStateOf(false)
        private set

    init {
        registerSmsContentObserver()
        loadThreads()
        viewModelScope.launch {
            dbHelper.rulesChanged.drop(1).collect { loadThreads(silent = true, force = true) }
        }
        viewModelScope.launch {
            dbHelper.prefsChanged.drop(1).collect { loadThreads(silent = true, force = true) }
        }
        viewModelScope.launch {
            dbHelper.spamMetaChanged.drop(1).collect { loadThreads(silent = true, force = true) }
        }
    }

    fun onInboxResumed() {
        repository.invalidateLookupCaches()
        checkDefaultSmsStatus()
        loadThreads(silent = true)
    }

    fun loadThreads(silent: Boolean = false, force: Boolean = false) {
        val hasPermission = ContextCompat.checkSelfPermission(
            getApplication<Application>(),
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            _uiState.value = _uiState.value.copy(
                hasSmsPermission = false,
                isLoading = false,
                error = SMS_PERMISSION_ERROR
            )
            return
        }
        val permissionError = _uiState.value.error == SMS_PERMISSION_ERROR
        if (!_uiState.value.hasSmsPermission || permissionError) {
            _uiState.value = _uiState.value.copy(
                hasSmsPermission = true,
                error = if (permissionError) null else _uiState.value.error
            )
        }

        if (!force && silent) {
            val elapsed = System.currentTimeMillis() - lastLoadFinishedAt
            if (lastLoadFinishedAt > 0L && elapsed < SILENT_LOAD_MIN_INTERVAL_MS) {
                return
            }
        }
        if (loadJob?.isActive == true) {
            if (silent && !force) return
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
                lastLoadFinishedAt = System.currentTimeMillis()
                if (currentQuery.length >= SEARCH_MIN_QUERY_LENGTH) {
                    val messages = repository.searchAllMessages(currentQuery)
                    _uiState.value = _uiState.value.copy(
                        matchingMessages = messages,
                        isSearching = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        matchingMessages = emptyList(),
                        isSearching = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load messages"
                )
            }
        }
    }

    private fun scheduleSilentReload() {
        observerDebounceJob?.cancel()
        observerDebounceJob = viewModelScope.launch {
            delay(SMS_OBSERVER_DEBOUNCE_MS)
            loadThreads(silent = true)
        }
    }

    private fun registerSmsContentObserver() {
        val resolver = getApplication<Application>().contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleSilentReload()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleSilentReload()
            }
        }
        try {
            resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            smsContentObserver = observer
        } catch (e: Exception) {
            Log.e("ConversationsViewModel", "Failed to register SMS observer", e)
            return
        }
        try {
            resolver.registerContentObserver(Telephony.Threads.CONTENT_URI, true, observer)
        } catch (e: Exception) {
            Log.e("ConversationsViewModel", "Failed to register threads observer", e)
        }
    }

    override fun onCleared() {
        observerDebounceJob?.cancel()
        val observer = smsContentObserver
        if (observer != null) {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                Log.e("ConversationsViewModel", "Failed to unregister SMS observer", e)
            }
            smsContentObserver = null
        }
        super.onCleared()
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

        val filtered = filterThreads(_uiState.value.threads, trimmed)
        if (trimmed.length < SEARCH_MIN_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                filteredThreads = filtered,
                matchingMessages = emptyList(),
                isSearching = false
            )
            return
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
        selectedThreadIds.clear()
        selectedThreadIds.add(threadId)
        isSelectionMode = true
    }

    fun toggleSelectThread(threadId: Long) {
        if (threadId in selectedThreadIds) {
            selectedThreadIds.remove(threadId)
        } else {
            selectedThreadIds.add(threadId)
        }
        isSelectionMode = selectedThreadIds.isNotEmpty()
    }

    fun selectAllThreads() {
        val visibleThreads = if (_uiState.value.showContactsOnly) {
            _uiState.value.filteredThreads.filter { it.isContact }
        } else {
            _uiState.value.filteredThreads
        }
        val allFilteredIds = visibleThreads.map { it.threadId }
        val allSelected = selectedThreadIds.containsAll(allFilteredIds) && allFilteredIds.isNotEmpty()
        if (allSelected) {
            selectedThreadIds.clear()
            isSelectionMode = false
        } else {
            selectedThreadIds.clear()
            selectedThreadIds.addAll(allFilteredIds)
            isSelectionMode = true
        }
    }

    fun clearSelection() {
        selectedThreadIds.clear()
        isSelectionMode = false
    }

    fun deleteSelectedConversations(onComplete: (Int) -> Unit = {}) {
        val targetIds = selectedThreadIds.toSet()
        if (targetIds.isEmpty()) return

        viewModelScope.launch {
            val count = targetIds.size
            val success = repository.deleteThreads(targetIds)
            if (!success) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete conversations"
                )
                onComplete(0)
                return@launch
            }
            val updatedThreads = _uiState.value.threads.filter { it.threadId !in targetIds }
            val updatedFiltered = _uiState.value.filteredThreads.filter { it.threadId !in targetIds }
            selectedThreadIds.clear()
            isSelectionMode = false
            _uiState.value = _uiState.value.copy(
                threads = updatedThreads,
                filteredThreads = updatedFiltered,
                error = null
            )
            repository.saveCachedThreads(updatedThreads)
            onComplete(count)
        }
    }

    fun markSelectedConversationsRead(onComplete: (Int) -> Unit = {}) {
        val targetIds = selectedThreadIds.toSet()
        if (targetIds.isEmpty()) return

        viewModelScope.launch {
            val count = targetIds.size
            repository.markThreadsRead(targetIds)
            clearSelection()
            loadThreads(silent = true, force = true)
            onComplete(count)
        }
    }

    fun markAllConversationsRead(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.markAllMessagesRead()
            loadThreads(silent = true, force = true)
            onComplete()
        }
    }
}

private const val SMS_OBSERVER_DEBOUNCE_MS = 400L
private const val SILENT_LOAD_MIN_INTERVAL_MS = 3_000L
private const val SEARCH_MIN_QUERY_LENGTH = 2
private const val SMS_PERMISSION_ERROR = "SMS permission required"

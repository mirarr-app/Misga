package com.miss.ga.ui.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.telephony.SubscriptionManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import com.miss.ga.data.model.PreferredSimMode
import com.miss.ga.data.model.RuleCategory
import com.miss.ga.data.model.SenderPreference
import com.miss.ga.data.model.SenderTab
import com.miss.ga.data.model.SimInfo
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.data.repository.SendSmsResult
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.data.util.AppPreferences
import com.miss.ga.data.util.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val isLoading: Boolean = true,
    val threadId: Long = 0,
    val address: String = "",
    val contactName: String? = null,
    val photoUri: String? = null,
    val contactLookupUri: String? = null,
    val messages: List<SmsMessage> = emptyList(),
    val senderPreference: SenderPreference? = null,
    val senderRules: List<FilterRule> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val hasMoreOlder: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val availableTabs: List<SenderTab> = emptyList(),
    val senderTabIds: Set<Long> = emptySet(),
    val availableSims: List<SimInfo> = emptyList(),
    val selectedSim: SimInfo? = null,
    val showShamsiDate: Boolean = false,
    val enableDateTapShamsiToggle: Boolean = true
)

class ChatViewModel(
    application: Application,
    private val initialThreadId: Long,
    private val initialAddress: String,
    private val initialContactName: String?,
    private val initialMessageId: Long? = null,
    private val userPreferences: UserPreferences = AppPreferences(application)
) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private val dbHelper = MisgaDatabaseHelper.getInstance(application)
    val selectedMessageIds = mutableStateSetOf<Long>()
    var isSelectionMode by mutableStateOf(false)
        private set
    var isDeletingSelection by mutableStateOf(false)
        private set
    private var messagesJob: Job? = null
    private var olderJob: Job? = null
    private var observerDebounceJob: Job? = null
    private var smsContentObserver: ContentObserver? = null
    @Volatile
    private var isScreenResumed = false

    private val _uiState = MutableStateFlow(
        ChatUiState(
            threadId = initialThreadId,
            address = initialAddress,
            contactName = initialContactName,
            showShamsiDate = userPreferences.showShamsiDate,
            enableDateTapShamsiToggle = userPreferences.enableDateTapShamsiToggle
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        registerSmsContentObserver()
        loadContactDetails()
        loadMessages()
        loadSenderSettings()
        loadTabs()
        viewModelScope.launch {
            dbHelper.rulesChanged.drop(1).collect {
                loadSenderSettings()
                loadMessages()
            }
        }
        viewModelScope.launch {
            dbHelper.prefsChanged.drop(1).collect {
                loadSenderSettings()
                loadMessages()
            }
        }
        viewModelScope.launch {
            dbHelper.spamMetaChanged.drop(1).collect {
                loadMessages()
            }
        }
        viewModelScope.launch {
            dbHelper.tabsChanged.collect {
                loadTabs()
            }
        }
        viewModelScope.launch {
            repository.simRepository.observeActiveSims().collect { sims ->
                val current = _uiState.value
                val resolved = resolveSelectedSim(
                    activeSims = sims,
                    prefSetting = current.senderPreference?.preferredSubId ?: PreferredSimMode.AUTO,
                    messages = current.messages,
                    currentSelectedSim = current.selectedSim
                )
                _uiState.value = current.copy(
                    availableSims = sims,
                    selectedSim = resolved
                )
            }
        }
    }

    fun loadMessages(markAsRead: Boolean = isScreenResumed) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            var currentThreadId = _uiState.value.threadId
            if (currentThreadId <= 0L && initialAddress.isNotBlank()) {
                val resolvedId = repository.getOrCreateThreadId(initialAddress)
                if (resolvedId > 0L) {
                    currentThreadId = resolvedId
                    _uiState.value = _uiState.value.copy(threadId = resolvedId)
                }
            }
            if (markAsRead && currentThreadId > 0L) {
                repository.markThreadRead(currentThreadId)
            }
            val hadMessages = _uiState.value.messages.isNotEmpty()
            if (!hadMessages) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            try {
                val page = repository.getMessagesForThread(
                    threadId = currentThreadId,
                    address = initialAddress,
                    beforeDate = null,
                    limit = MESSAGE_PAGE_SIZE
                )
                if (!hadMessages) {
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
            } catch (e: CancellationException) {
                throw e
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
                val currentThreadId = _uiState.value.threadId.takeIf { it > 0L } ?: initialThreadId
                val page = repository.getMessagesForThread(
                    threadId = currentThreadId,
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
            val currentThreadId = _uiState.value.threadId.takeIf { it > 0L } ?: initialThreadId
            val page = repository.getMessagesForThread(
                threadId = currentThreadId,
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
                val resolvedSim = resolveSelectedSim(
                    activeSims = _uiState.value.availableSims,
                    prefSetting = pref?.preferredSubId ?: PreferredSimMode.AUTO,
                    messages = _uiState.value.messages,
                    currentSelectedSim = _uiState.value.selectedSim
                )
                _uiState.value = _uiState.value.copy(selectedSim = resolvedSim)
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Failed to load sender settings", e)
            }
        }
    }

    fun toggleSim() {
        val current = _uiState.value
        val sims = current.availableSims
        if (sims.size <= 1) return
        val currentIndex = sims.indexOfFirst { it.subscriptionId == current.selectedSim?.subscriptionId }
        val nextIndex = if (currentIndex in sims.indices) (currentIndex + 1) % sims.size else 0
        _uiState.value = current.copy(selectedSim = sims[nextIndex])
    }

    fun selectSim(sim: SimInfo) {
        _uiState.value = _uiState.value.copy(selectedSim = sim)
    }

    fun setPreferredSim(mode: Int) {
        viewModelScope.launch {
            repository.updateSenderPreferredSubId(initialAddress, mode)
            val pref = repository.getSenderPreference(initialAddress)
            val resolved = resolveSelectedSim(
                activeSims = _uiState.value.availableSims,
                prefSetting = mode,
                messages = _uiState.value.messages,
                currentSelectedSim = null
            )
            _uiState.value = _uiState.value.copy(
                senderPreference = pref,
                selectedSim = resolved
            )
        }
    }

    private fun resolveSelectedSim(
        activeSims: List<SimInfo>,
        prefSetting: Int,
        messages: List<SmsMessage>,
        currentSelectedSim: SimInfo?
    ): SimInfo? {
        if (activeSims.isEmpty()) return null
        if (activeSims.size == 1) return activeSims.first()

        // If user already manually toggled a SIM in this conversation session, retain it
        if (currentSelectedSim != null && activeSims.any { it.subscriptionId == currentSelectedSim.subscriptionId }) {
            return currentSelectedSim
        }

        // 1. Explicit SIM preference
        if (prefSetting >= 0) {
            val matched = activeSims.find { it.subscriptionId == prefSetting }
            if (matched != null) return matched
        }

        // 2. System default SIM preference
        val defaultSubId = repository.simRepository.getDefaultSmsSubscriptionId()
        if (prefSetting == PreferredSimMode.SYSTEM_DEFAULT) {
            val matched = activeSims.find { it.subscriptionId == defaultSubId }
            if (matched != null) return matched
        }

        // 3. AUTO: check latest message with valid SIM in thread
        val latestMessageWithSim = messages.lastOrNull {
            it.subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        if (latestMessageWithSim != null) {
            val matched = activeSims.find { it.subscriptionId == latestMessageWithSim.subId }
            if (matched != null) return matched
        }

        return activeSims.find { it.subscriptionId == defaultSubId } ?: activeSims.first()
    }

    fun sendMessage(text: String, onComplete: (SendSmsResult) -> Unit = {}) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val subIdToSend = _uiState.value.selectedSim?.subscriptionId
            val result = repository.sendSms(initialAddress, text, subIdToSend)
            _uiState.value = _uiState.value.copy(isSending = false)
            if (result.sent) {
                loadMessages()
            }
            onComplete(result)
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

    fun enterSelectionMode(messageId: Long) {
        selectedMessageIds.clear()
        selectedMessageIds.add(messageId)
        isSelectionMode = true
    }

    fun toggleSelectMessage(messageId: Long) {
        if (messageId in selectedMessageIds) {
            selectedMessageIds.remove(messageId)
        } else {
            selectedMessageIds.add(messageId)
        }
        isSelectionMode = selectedMessageIds.isNotEmpty()
    }

    fun clearSelection() {
        selectedMessageIds.clear()
        isSelectionMode = false
    }

    fun deleteSelectedMessages(onComplete: (Int) -> Unit = {}) {
        if (isDeletingSelection) return
        val targetIds = selectedMessageIds.toSet()
        if (targetIds.isEmpty()) return
        isDeletingSelection = true
        viewModelScope.launch {
            try {
                val successfullyDeletedIds = mutableSetOf<Long>()
                for (id in targetIds) {
                    if (repository.deleteMessage(id)) {
                        successfullyDeletedIds.add(id)
                    }
                }
                if (successfullyDeletedIds.isNotEmpty()) {
                    val updated = _uiState.value.messages.filter { it.id !in successfullyDeletedIds }
                    _uiState.value = _uiState.value.copy(messages = updated)
                }
                selectedMessageIds.removeAll(successfullyDeletedIds)
                if (selectedMessageIds.isEmpty()) {
                    isSelectionMode = false
                }
                onComplete(successfullyDeletedIds.size)
            } finally {
                isDeletingSelection = false
            }
        }
    }

    fun updateSenderDefaultAction(action: FilterAction) {
        viewModelScope.launch {
            val currentPref = _uiState.value.senderPreference ?: SenderPreference(
                address = initialAddress,
                displayName = initialContactName
            )
            val updated = currentPref.copy(
                defaultAction = action,
                isBlocked = action == FilterAction.SPAM
            )
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

    fun loadContactDetails() {
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                repository.resolveContactDetail(initialAddress)
            }
            if (detail != null) {
                _uiState.value = _uiState.value.copy(
                    contactName = detail.name,
                    photoUri = detail.photoUri,
                    contactLookupUri = detail.lookupUri
                )
            }
        }
    }

    fun setScreenResumed(resumed: Boolean) {
        isScreenResumed = resumed
        if (resumed) {
            _uiState.value = _uiState.value.copy(
                showShamsiDate = userPreferences.showShamsiDate,
                enableDateTapShamsiToggle = userPreferences.enableDateTapShamsiToggle
            )
            loadContactDetails()
        }
    }

    fun toggleShamsiDate() {
        if (!_uiState.value.enableDateTapShamsiToggle) return
        val nextValue = !_uiState.value.showShamsiDate
        userPreferences.showShamsiDate = nextValue
        _uiState.value = _uiState.value.copy(
            showShamsiDate = nextValue
        )
    }

    fun loadTabs() {
        viewModelScope.launch {
            val allTabs = repository.getAllTabs()
            val senderTabs = repository.getTabsForSender(initialAddress)
            _uiState.value = _uiState.value.copy(
                availableTabs = allTabs,
                senderTabIds = senderTabs.map { it.id }.toSet()
            )
        }
    }

    fun toggleSenderTab(tabId: Long, enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                repository.addSendersToTab(tabId, listOf(initialAddress))
            } else {
                repository.removeSendersFromTab(tabId, listOf(initialAddress))
            }
            loadTabs()
        }
    }

    fun createTabWithCurrentSender(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createTab(name, listOf(initialAddress))
            if (id != -1L) {
                loadTabs()
                onCreated(id)
            }
        }
    }

    private fun registerSmsContentObserver() {
        val resolver = getApplication<Application>().contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleReload()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleReload()
            }
        }
        try {
            resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            smsContentObserver = observer
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to register SMS observer", e)
        }
    }

    private fun scheduleReload() {
        observerDebounceJob?.cancel()
        observerDebounceJob = viewModelScope.launch {
            delay(SMS_OBSERVER_DEBOUNCE_MS)
            loadMessages()
        }
    }

    override fun onCleared() {
        observerDebounceJob?.cancel()
        val observer = smsContentObserver
        if (observer != null) {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to unregister SMS observer", e)
            }
            smsContentObserver = null
        }
        super.onCleared()
    }

    companion object {
        private const val MESSAGE_PAGE_SIZE = 100
        private const val MAX_TARGET_PAGES = 20
        private const val SMS_OBSERVER_DEBOUNCE_MS = 400L
    }
}

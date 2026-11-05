package com.miss.ga.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.SenderPreference
import com.miss.ga.data.repository.ContactDetail
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.data.util.AppPreferences
import com.miss.ga.data.util.PhoneNumberKeys
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SenderBlockType {
    BLOCKED,
    MUTED
}

enum class BlockedFilterTab {
    ALL,
    BLOCKED,
    MUTED
}

data class BlockedSenderItem(
    val address: String,
    val contactName: String?,
    val photoUri: String?,
    val type: SenderBlockType,
    val preference: SenderPreference
)

data class BlockedSendersUiState(
    val allItems: List<BlockedSenderItem> = emptyList(),
    val filteredItems: List<BlockedSenderItem> = emptyList(),
    val filterTab: BlockedFilterTab = BlockedFilterTab.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val totalBlockedCount: Int = 0,
    val totalMutedCount: Int = 0,
    val snackbarMessage: String? = null,
    val lastActionForUndo: SenderPreference? = null
)

class BlockedSendersViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = MisgaDatabaseHelper.getInstance(application)
    private val repository = SmsRepository(application, AppPreferences(application))

    private val _uiState = MutableStateFlow(BlockedSendersUiState())
    val uiState: StateFlow<BlockedSendersUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private val contactCache: MutableMap<String, ContactDetail?> = Collections.synchronizedMap(HashMap())

    init {
        loadSenders()
        viewModelScope.launch {
            dbHelper.prefsChanged.drop(1).collect {
                loadSenders()
            }
        }
    }

    fun loadSenders() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val prefs = dbHelper.getBlockedAndMutedSenderPreferences()
            val items = withContext(Dispatchers.IO) {
                prefs.map { pref ->
                    val contactDetail = if (contactCache.containsKey(pref.address)) {
                        contactCache[pref.address]
                    } else {
                        repository.resolveContactDetail(pref.address).also { contactCache[pref.address] = it }
                    }
                    val type = if (pref.isBlocked || pref.defaultAction == FilterAction.SPAM) {
                        SenderBlockType.BLOCKED
                    } else {
                        SenderBlockType.MUTED
                    }
                    BlockedSenderItem(
                        address = pref.address,
                        contactName = contactDetail?.name ?: pref.displayName,
                        photoUri = contactDetail?.photoUri,
                        type = type,
                        preference = pref
                    )
                }
            }
            val blockedCount = items.count { it.type == SenderBlockType.BLOCKED }
            val mutedCount = items.count { it.type == SenderBlockType.MUTED }
            val currentTab = _uiState.value.filterTab
            val currentQuery = _uiState.value.searchQuery

            _uiState.value = _uiState.value.copy(
                allItems = items,
                filteredItems = computeFilteredItems(items, currentTab, currentQuery),
                totalBlockedCount = blockedCount,
                totalMutedCount = mutedCount,
                isLoading = false
            )
        }
    }

    fun setFilterTab(tab: BlockedFilterTab) {
        _uiState.value = _uiState.value.copy(
            filterTab = tab,
            filteredItems = computeFilteredItems(_uiState.value.allItems, tab, _uiState.value.searchQuery)
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredItems = computeFilteredItems(_uiState.value.allItems, _uiState.value.filterTab, query)
        )
    }

    fun unblockSender(item: BlockedSenderItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                lastActionForUndo = item.preference,
                snackbarMessage = "Unblocked ${item.contactName ?: item.address}"
            )
            dbHelper.unblockSender(item.address)
        }
    }

    fun unmuteSender(item: BlockedSenderItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                lastActionForUndo = item.preference,
                snackbarMessage = "Unmuted ${item.contactName ?: item.address}"
            )
            dbHelper.unmuteSender(item.address)
        }
    }

    fun undoLastAction() {
        val previous = _uiState.value.lastActionForUndo ?: return
        viewModelScope.launch {
            dbHelper.saveSenderPreference(previous)
            _uiState.value = _uiState.value.copy(
                lastActionForUndo = null,
                snackbarMessage = null
            )
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun addSender(address: String, type: SenderBlockType, displayName: String? = null) {
        if (address.isBlank()) return
        viewModelScope.launch {
            val trimmed = address.trim()
            if (type == SenderBlockType.BLOCKED) {
                dbHelper.blockSender(trimmed, displayName?.takeIf { it.isNotBlank() })
            } else {
                dbHelper.muteSender(trimmed, displayName?.takeIf { it.isNotBlank() })
            }
        }
    }

    fun toggleType(item: BlockedSenderItem) {
        viewModelScope.launch {
            if (item.type == SenderBlockType.BLOCKED) {
                dbHelper.muteSender(item.address, item.contactName)
            } else {
                dbHelper.blockSender(item.address, item.contactName)
            }
        }
    }

    fun deletePreference(item: BlockedSenderItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                lastActionForUndo = item.preference,
                snackbarMessage = "Removed settings for ${item.contactName ?: item.address}"
            )
            dbHelper.deleteSenderPreference(item.address)
        }
    }

    suspend fun getOrCreateThreadId(address: String): Long {
        return repository.getOrCreateThreadId(address)
    }

    companion object {
        fun computeFilteredItems(
            items: List<BlockedSenderItem>,
            tab: BlockedFilterTab,
            query: String
        ): List<BlockedSenderItem> {
            val tabFiltered = when (tab) {
                BlockedFilterTab.ALL -> items
                BlockedFilterTab.BLOCKED -> items.filter { it.type == SenderBlockType.BLOCKED }
                BlockedFilterTab.MUTED -> items.filter { it.type == SenderBlockType.MUTED }
            }
            if (query.isBlank()) return tabFiltered
            val cleanQuery = query.trim().lowercase()
            val canonicalQuery = PhoneNumberKeys.canonical(cleanQuery)
            return tabFiltered.filter { item ->
                item.address.lowercase().contains(cleanQuery) ||
                    item.contactName?.lowercase()?.contains(cleanQuery) == true ||
                    PhoneNumberKeys.canonical(item.address).contains(cleanQuery) ||
                    (canonicalQuery.length >= 3 && PhoneNumberKeys.canonical(item.address).contains(canonicalQuery)) ||
                    PhoneNumberKeys.keys(item.address).any { it.contains(cleanQuery) }
            }
        }
    }
}

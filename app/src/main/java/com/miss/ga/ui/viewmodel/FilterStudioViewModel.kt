package com.miss.ga.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import com.miss.ga.data.model.RuleCategory
import com.miss.ga.data.model.RuleListType
import com.miss.ga.engine.RegexTestResult
import com.miss.ga.engine.SmsFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class FilterStudioUiState(
    val rules: List<FilterRule> = emptyList(),
    val allowlistRules: List<FilterRule> = emptyList(),
    val blocklistRules: List<FilterRule> = emptyList(),
    val predefinedRules: List<FilterRule> = emptyList(),
    val customRules: List<FilterRule> = emptyList(),
    // Playground State
    val testPattern: String = "(لغو\\s*(11|۱۱)|تخفیف|وام\\s*فوری)",
    val isRegex: Boolean = true,
    val testSampleText: String = "مشترک گرامی، ۵۰ درصد تخفیف ویژه خرید اینترنت برای شما فعال شد. جهت انصراف لغو ۱۱ را ارسال فرمایید.",
    val testResult: RegexTestResult? = null,
    val simulatedAction: FilterAction = FilterAction.SPAM,
    val simulatedListType: RuleListType = RuleListType.BLOCKLIST,
    val toastMessage: String? = null
)

class FilterStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = MisgaDatabaseHelper.getInstance(application)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var playgroundTestJob: Job? = null

    private val _uiState = MutableStateFlow(FilterStudioUiState())
    val uiState: StateFlow<FilterStudioUiState> = _uiState.asStateFlow()

    init {
        loadRules()
        runPlaygroundTest()
    }

    fun loadRules() {
        viewModelScope.launch {
            val all = dbHelper.getAllRules()
            val allowlist = all.filter { it.listType == RuleListType.ALLOWLIST }
            val blocklist = all.filter { it.listType == RuleListType.BLOCKLIST }
            val predefined = all.filter { it.isPredefined }
            val custom = all.filter { !it.isPredefined }
            _uiState.value = _uiState.value.copy(
                rules = all,
                allowlistRules = allowlist,
                blocklistRules = blocklist,
                predefinedRules = predefined,
                customRules = custom
            )
        }
    }

    fun onPlaygroundPatternChanged(pattern: String) {
        _uiState.value = _uiState.value.copy(testPattern = pattern)
        runPlaygroundTest()
    }

    fun onPlaygroundSampleTextChanged(sample: String) {
        _uiState.value = _uiState.value.copy(testSampleText = sample)
        runPlaygroundTest()
    }

    fun onPlaygroundIsRegexChanged(isRegex: Boolean) {
        _uiState.value = _uiState.value.copy(isRegex = isRegex)
        runPlaygroundTest()
    }

    fun onPlaygroundActionChanged(action: FilterAction) {
        val listType = if (action == FilterAction.NORMAL) RuleListType.ALLOWLIST else RuleListType.BLOCKLIST
        _uiState.value = _uiState.value.copy(simulatedAction = action, simulatedListType = listType)
    }

    fun onPlaygroundListTypeChanged(listType: RuleListType) {
        val action = if (listType == RuleListType.ALLOWLIST) FilterAction.NORMAL else _uiState.value.simulatedAction
        _uiState.value = _uiState.value.copy(simulatedListType = listType, simulatedAction = action)
    }

    fun setSamplePreset(text: String, pattern: String, listType: RuleListType = RuleListType.BLOCKLIST) {
        val action = if (listType == RuleListType.ALLOWLIST) FilterAction.NORMAL else FilterAction.SPAM
        _uiState.value = _uiState.value.copy(
            testSampleText = text,
            testPattern = pattern,
            simulatedListType = listType,
            simulatedAction = action
        )
        runPlaygroundTest()
    }

    private fun runPlaygroundTest() {
        playgroundTestJob?.cancel()
        playgroundTestJob = viewModelScope.launch {
            delay(200)
            val pattern = _uiState.value.testPattern
            val sample = _uiState.value.testSampleText
            val isRegex = _uiState.value.isRegex
            val result = withContext(Dispatchers.Default) {
                SmsFilterEngine.testPattern(pattern, isRegex, sample)
            }
            if (pattern == _uiState.value.testPattern &&
                sample == _uiState.value.testSampleText &&
                isRegex == _uiState.value.isRegex
            ) {
                _uiState.value = _uiState.value.copy(testResult = result)
            }
        }
    }

    fun savePlaygroundRuleAsCustom(
        name: String,
        description: String = "",
        listType: RuleListType = _uiState.value.simulatedListType,
        action: FilterAction = _uiState.value.simulatedAction
    ) {
        viewModelScope.launch {
            val rule = FilterRule(
                name = name.ifBlank { if (listType == RuleListType.ALLOWLIST) "Custom Allowlist Filter" else "Custom Blocklist Filter" },
                pattern = _uiState.value.testPattern,
                isRegex = _uiState.value.isRegex,
                action = if (listType == RuleListType.ALLOWLIST) FilterAction.NORMAL else action,
                listType = listType,
                isEnabled = true,
                isPredefined = false,
                category = if (listType == RuleListType.ALLOWLIST) RuleCategory.CUSTOM_ALLOWLIST else RuleCategory.CUSTOM,
                description = description
            )
            dbHelper.insertCustomRule(rule)
            loadRules()
            _uiState.value = _uiState.value.copy(
                toastMessage = "${if (listType == RuleListType.ALLOWLIST) "Allowlist" else "Filter"} rule saved successfully!"
            )
        }
    }

    fun toggleRule(rule: FilterRule, isEnabled: Boolean) {
        viewModelScope.launch {
            dbHelper.setRuleEnabled(rule.id, isEnabled, rule.isPredefined)
            loadRules()
        }
    }

    fun updateRule(rule: FilterRule) {
        viewModelScope.launch {
            dbHelper.updateRule(rule)
            loadRules()
            _uiState.value = _uiState.value.copy(toastMessage = "Rule '${rule.name}' updated successfully!")
        }
    }

    fun updateRuleAction(rule: FilterRule, action: FilterAction) {
        viewModelScope.launch {
            val listType = if (action == FilterAction.NORMAL) RuleListType.ALLOWLIST else RuleListType.BLOCKLIST
            dbHelper.updateRule(rule.copy(action = action, listType = listType))
            loadRules()
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            dbHelper.deleteRule(ruleId)
            loadRules()
            _uiState.value = _uiState.value.copy(toastMessage = "Rule deleted successfully")
        }
    }

    fun exportRulesJson(): String {
        return json.encodeToString(_uiState.value.rules)
    }

    fun importRulesJson(jsonString: String, onComplete: (Boolean, Int, Int) -> Unit) {
        viewModelScope.launch {
            try {
                val imported = json.decodeFromString<List<FilterRule>>(jsonString)
                val existingRules = dbHelper.getAllRules()
                val seenInBatch = mutableSetOf<String>()
                var addedCount = 0
                var skippedCount = 0

                for (rule in imported) {
                    val batchKey = "${rule.listType}_${rule.pattern.trim()}_${rule.action}_${rule.isRegex}_${rule.senderTarget}"
                    val isBatchDuplicate = !seenInBatch.add(batchKey)

                    val isExistingDuplicate = existingRules.any { existing ->
                        (existing.id == rule.id && rule.id < 0) || // Predefined rule with negative ID
                        (
                            existing.pattern.trim().equals(rule.pattern.trim(), ignoreCase = true) &&
                            existing.listType == rule.listType &&
                            existing.action == rule.action &&
                            existing.isRegex == rule.isRegex &&
                            existing.senderTarget == rule.senderTarget
                        )
                    }

                    if (isBatchDuplicate || isExistingDuplicate) {
                        skippedCount++
                    } else {
                        dbHelper.insertCustomRule(
                            rule.copy(id = 0, isPredefined = false, createdAt = System.currentTimeMillis())
                        )
                        addedCount++
                    }
                }
                loadRules()
                onComplete(true, addedCount, skippedCount)
            } catch (e: Exception) {
                onComplete(false, 0, 0)
            }
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}


package com.miss.ga.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import com.miss.ga.data.model.RuleCategory
import com.miss.ga.engine.RegexTestResult
import com.miss.ga.engine.SmsFilterEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class FilterStudioUiState(
    val rules: List<FilterRule> = emptyList(),
    val predefinedRules: List<FilterRule> = emptyList(),
    val customRules: List<FilterRule> = emptyList(),
    // Playground State
    val testPattern: String = "(لغو\\s*(11|۱۱)|تخفیف|وام\\s*فوری)",
    val isRegex: Boolean = true,
    val testSampleText: String = "مشترک گرامی، ۵۰ درصد تخفیف ویژه خرید اینترنت برای شما فعال شد. جهت انصراف لغو ۱۱ را ارسال فرمایید.",
    val testResult: RegexTestResult? = null,
    val simulatedAction: FilterAction = FilterAction.SPAM,
    val toastMessage: String? = null
)

class FilterStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = MisgaDatabaseHelper.getInstance(application)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(FilterStudioUiState())
    val uiState: StateFlow<FilterStudioUiState> = _uiState.asStateFlow()

    init {
        loadRules()
        runPlaygroundTest()
    }

    fun loadRules() {
        viewModelScope.launch {
            val all = dbHelper.getAllRules()
            val predefined = all.filter { it.isPredefined }
            val custom = all.filter { !it.isPredefined }
            _uiState.value = _uiState.value.copy(
                rules = all,
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
        _uiState.value = _uiState.value.copy(simulatedAction = action)
    }

    fun setSamplePreset(text: String, pattern: String) {
        _uiState.value = _uiState.value.copy(
            testSampleText = text,
            testPattern = pattern
        )
        runPlaygroundTest()
    }

    private fun runPlaygroundTest() {
        val pattern = _uiState.value.testPattern
        val sample = _uiState.value.testSampleText
        val isRegex = _uiState.value.isRegex

        val result = SmsFilterEngine.testPattern(pattern, isRegex, sample)
        _uiState.value = _uiState.value.copy(testResult = result)
    }

    fun savePlaygroundRuleAsCustom(name: String, description: String = "") {
        viewModelScope.launch {
            val rule = FilterRule(
                name = name.ifBlank { "Custom Regex Filter" },
                pattern = _uiState.value.testPattern,
                isRegex = _uiState.value.isRegex,
                action = _uiState.value.simulatedAction,
                isEnabled = true,
                isPredefined = false,
                category = RuleCategory.CUSTOM,
                description = description
            )
            dbHelper.insertCustomRule(rule)
            loadRules()
            _uiState.value = _uiState.value.copy(toastMessage = "Filter rule saved successfully!")
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
            dbHelper.updateRule(rule.copy(action = action))
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

    fun importRulesJson(jsonString: String, onComplete: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            try {
                val imported = json.decodeFromString<List<FilterRule>>(jsonString)
                var count = 0
                for (rule in imported) {
                    dbHelper.insertCustomRule(
                        rule.copy(id = 0, isPredefined = false, createdAt = System.currentTimeMillis())
                    )
                    count++
                }
                loadRules()
                onComplete(true, count)
            } catch (e: Exception) {
                onComplete(false, 0)
            }
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}

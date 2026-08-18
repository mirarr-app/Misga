package com.miss.ga.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import com.miss.ga.data.model.RuleListType
import com.miss.ga.data.model.displayLabel
import com.miss.ga.theme.PillShape
import com.miss.ga.theme.SquircleCardShape
import com.miss.ga.theme.SquircleMediumShape
import com.miss.ga.theme.TagShape
import com.miss.ga.ui.viewmodel.FilterStudioUiState
import com.miss.ga.ui.viewmodel.FilterStudioViewModel

private enum class FilterStudioTab { Playground, Allowlist, Blocklist, Presets }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterStudioScreen(
    onBackClick: () -> Unit,
    viewModel: FilterStudioViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(FilterStudioTab.Playground) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var initialAddListType by remember { mutableStateOf(RuleListType.ALLOWLIST) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    var editingRule by remember { mutableStateOf<FilterRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<FilterRule?>(null) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Filter Studio",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "Regex",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(
                            text = "Allowlist & Iranian Spam Rules Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Export JSON (Allowlist + Blocklist rules)
                    IconButton(onClick = {
                        val json = viewModel.exportRulesJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Misga Rules", json))
                        Toast.makeText(context, "Exported ${state.rules.size} rules with listType to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export Rules JSON")
                    }

                    // Import JSON
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Upload, contentDescription = "Import Rules JSON")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (selectedTab == FilterStudioTab.Allowlist || selectedTab == FilterStudioTab.Blocklist) {
                FloatingActionButton(
                    onClick = {
                        initialAddListType = if (selectedTab == FilterStudioTab.Allowlist) {
                            RuleListType.ALLOWLIST
                        } else {
                            RuleListType.BLOCKLIST
                        }
                        showAddDialog = true
                    },
                    containerColor = if (selectedTab == FilterStudioTab.Allowlist) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (selectedTab == FilterStudioTab.Allowlist) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    shape = PillShape
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (selectedTab == FilterStudioTab.Allowlist) {
                            "Add Allowlist Rule"
                        } else {
                            "Add Blocklist Rule"
                        }
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = selectedTab == FilterStudioTab.Playground,
                    onClick = { selectedTab = FilterStudioTab.Playground },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Playground", fontWeight = if (selectedTab == FilterStudioTab.Playground) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == FilterStudioTab.Allowlist,
                    onClick = { selectedTab = FilterStudioTab.Allowlist },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GppGood, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Allowlist (${state.allowlistRules.size})",
                                fontWeight = if (selectedTab == FilterStudioTab.Allowlist) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == FilterStudioTab.Blocklist,
                    onClick = { selectedTab = FilterStudioTab.Blocklist },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Blocklist (${state.blocklistRules.size})",
                                fontWeight = if (selectedTab == FilterStudioTab.Blocklist) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == FilterStudioTab.Presets,
                    onClick = { selectedTab = FilterStudioTab.Presets },
                    text = {
                        Text(
                            "All Presets (${state.predefinedRules.size})",
                            fontWeight = if (selectedTab == FilterStudioTab.Presets) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }

            when (selectedTab) {
                FilterStudioTab.Playground -> PlaygroundTab(
                    state = state,
                    onPatternChange = viewModel::onPlaygroundPatternChanged,
                    onSampleChange = viewModel::onPlaygroundSampleTextChanged,
                    onActionChange = viewModel::onPlaygroundActionChanged,
                    onListTypeChange = viewModel::onPlaygroundListTypeChanged,
                    onPresetSelect = viewModel::setSamplePreset,
                    onSaveClick = { showSaveDialog = true }
                )
                FilterStudioTab.Allowlist -> AllowlistTab(
                    rules = state.allowlistRules,
                    onToggle = viewModel::toggleRule,
                    onEdit = { editingRule = it },
                    onDelete = { ruleToDelete = it }
                )
                FilterStudioTab.Blocklist -> BlocklistTab(
                    rules = state.blocklistRules,
                    onToggle = viewModel::toggleRule,
                    onEdit = { editingRule = it },
                    onDelete = { ruleToDelete = it }
                )
                FilterStudioTab.Presets -> PredefinedRulesTab(
                    rules = state.predefinedRules,
                    onToggle = viewModel::toggleRule,
                    onEdit = { editingRule = it },
                    onDelete = { ruleToDelete = it }
                )
            }
        }
    }

    // Edit Rule Dialog
    editingRule?.let { rule ->
        EditRuleDialog(
            rule = rule,
            onDismiss = { editingRule = null },
            onSave = { updatedRule ->
                viewModel.updateRule(updatedRule)
                editingRule = null
            }
        )
    }

    // Delete Confirmation Dialog
    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            shape = SquircleCardShape,
            title = { Text("Delete Rule?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${rule.name}'? This rule will no longer filter incoming messages.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRule(rule.id)
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = PillShape
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Save Playground Dialog
    if (showSaveDialog) {
        var ruleName by remember { mutableStateOf("") }
        var ruleDesc by remember { mutableStateOf("") }
        var selectedListType by remember { mutableStateOf(state.simulatedListType) }
        var selectedAction by remember { mutableStateOf(state.simulatedAction) }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            shape = SquircleCardShape,
            title = { Text("Save Regex as Custom Rule", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("Rule Name") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ruleDesc,
                        onValueChange = { ruleDesc = it },
                        label = { Text("Description (Optional)") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "List Classification:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                            RadioButton(
                                selected = selectedListType == RuleListType.ALLOWLIST,
                                onClick = {
                                    selectedListType = RuleListType.ALLOWLIST
                                    selectedAction = FilterAction.NORMAL
                                }
                            )
                            Text("Allowlist", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedListType == RuleListType.BLOCKLIST,
                                onClick = {
                                    selectedListType = RuleListType.BLOCKLIST
                                    selectedAction = FilterAction.SPAM
                                }
                            )
                            Text("Blocklist", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (selectedListType == RuleListType.BLOCKLIST) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Trigger Action:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(FilterAction.SPAM, FilterAction.SILENT).forEach { act ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                                    RadioButton(
                                        selected = selectedAction == act,
                                        onClick = { selectedAction = act }
                                    )
                                    Text(act.displayLabel, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = TagShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Pattern: ${state.testPattern}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (ruleName.isNotBlank()) {
                            viewModel.savePlaygroundRuleAsCustom(
                                name = ruleName,
                                description = ruleDesc,
                                listType = selectedListType,
                                action = selectedAction
                            )
                            showSaveDialog = false
                        }
                    },
                    shape = PillShape
                ) {
                    Text("Save Rule", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Custom Rule Dialog
    if (showAddDialog) {
        AddGlobalRuleDialog(
            targetAddress = null,
            initialListType = initialAddListType,
            onDismiss = { showAddDialog = false },
            onSave = { pattern, isRegex, action, name, listType ->
                viewModel.onPlaygroundPatternChanged(pattern)
                viewModel.onPlaygroundIsRegexChanged(isRegex)
                viewModel.onPlaygroundActionChanged(action)
                viewModel.onPlaygroundListTypeChanged(listType)
                viewModel.savePlaygroundRuleAsCustom(
                    name = name,
                    description = "",
                    listType = listType,
                    action = action
                )
                showAddDialog = false
            }
        )
    }


    // Import JSON Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            shape = SquircleCardShape,
            title = { Text("Import Filter Rules JSON", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Paste exported JSON rules array below. Rules include listType (ALLOWLIST / BLOCKLIST):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        placeholder = { Text("[{\"name\": \"Sample OTP\", \"pattern\": \"...\", \"listType\": \"ALLOWLIST\"}]") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (importJsonText.isNotBlank()) {
                            viewModel.importRulesJson(importJsonText) { success, addedCount, skippedCount ->
                                if (success) {
                                    val msg = if (skippedCount > 0) {
                                        "Imported $addedCount rule${if (addedCount != 1) "s" else ""}, skipped $skippedCount duplicate${if (skippedCount != 1) "s" else ""}."
                                    } else {
                                        "Successfully imported $addedCount rule${if (addedCount != 1) "s" else ""}!"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    showImportDialog = false
                                    importJsonText = ""
                                } else {
                                    Toast.makeText(context, "Invalid JSON format", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = PillShape
                ) {
                    Text("Import", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaygroundTab(
    state: FilterStudioUiState,
    onPatternChange: (String) -> Unit,
    onSampleChange: (String) -> Unit,
    onActionChange: (FilterAction) -> Unit,
    onListTypeChange: (RuleListType) -> Unit,
    onPresetSelect: (String, String, RuleListType) -> Unit,
    onSaveClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            // OTP Allowlist Quick-Fills
            Text(
                text = "OTP & Allowlist Presets (Highest Priority)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        onPresetSelect(
                            "کد تایید شما برای ورود به اسنپ: 481923",
                            "(?i)(کد\\s*(ت[اأآ]یید|ورود|فعالسازی)|(verification|auth|login)\\s*code|passcode)",
                            RuleListType.ALLOWLIST
                        )
                    },
                    label = { Text("OTP Code (کد ورود / تایید)") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = PillShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                AssistChip(
                    onClick = {
                        onPresetSelect(
                            "بانک سامان: رمز یکبار مصرف پویا برای کارت ۶۲۱۹ شما: 918234",
                            "(رمز\\s*(پویا|دوم|یکبار\\s*مصرف)|(one[ -]?time\\s*password|otp\\s*code))",
                            RuleListType.ALLOWLIST
                        )
                    },
                    label = { Text("Bank OTP (رمز پویا / دوم)") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = PillShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                AssistChip(
                    onClick = {
                        onPresetSelect(
                            "دیجی کالا: کد: 582103 برای ورود استفاده شود.",
                            "(?i)(کد\\s*[:=]\\s*[0-9۰-۹]{4,8}|رمز\\s*[:=]\\s*[0-9۰-۹]{4,8}|otp\\s*[:=]?\\s*[0-9]{4,8})",
                            RuleListType.ALLOWLIST
                        )
                    },
                    label = { Text("Direct Code (کد: / رمز:)") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = PillShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Spam Blocklist Quick-Fills
            Text(
                text = "Spam & Blocklist Presets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        onPresetSelect(
                            "مشترک گرامی، ۵۰ درصد تخفیف ویژه خرید اینترنت برای شما فعال شد. جهت انصراف لغو ۱۱ را ارسال فرمایید.",
                            "(لغو\\s*(11|۱۱)|تخفیف|اینترنت)",
                            RuleListType.BLOCKLIST
                        )
                    },
                    label = { Text("Promo Opt-out (لغو ۱۱)") },
                    shape = PillShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
                AssistChip(
                    onClick = {
                        onPresetSelect(
                            "وام فوری بدون ضامن تا سقف ۵۰ میلیون تومان، پرداخت ۲۴ ساعته. ثبت نام سریع:",
                            "وام\\s*(فوری|بدون\\s*ضامن)|پرداخت\\s*۲۴\\s*ساعته",
                            RuleListType.BLOCKLIST
                        )
                    },
                    label = { Text("Loan Scam (وام فوری)") },
                    shape = PillShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
                AssistChip(
                    onClick = {
                        onPresetSelect(
                            "سامانه ثنا: ابلاغیه جدید علیه شما صادر گردید. مشاهده در: http://sana-adlieh.top/apk",
                            "(ابلاغیه|سامانه\\s*ثنا).*http",
                            RuleListType.BLOCKLIST
                        )
                    },
                    label = { Text("Phishing Link (ثنا/عدالت)") },
                    shape = PillShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
                AssistChip(
                    onClick = {
                        onPresetSelect(
                            "بسته تخفیف ماهیانه اینترنت همراه ۵۰ گیگابایت با قیمت استثنایی فعال شد.",
                            "(تخفیف\\s*(های\\s*)?ماه[ی]?انه|بسته\\s*(های\\s*)?تخفیف\\s*ماه[ی]?انه|پیشنهاد\\s*ماه[ی]?انه)",
                            RuleListType.BLOCKLIST
                        )
                    },
                    label = { Text("Monthly Discount (تخفیف ماهیانه)") },
                    shape = PillShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Regex Pattern Input
            OutlinedTextField(
                value = state.testPattern,
                onValueChange = onPatternChange,
                label = { Text("Regex Pattern") },
                supportingText = {
                    val res = state.testResult
                    if (res != null && !res.isValid) {
                        Text("Error: ${res.errorMessage}", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Supports Unicode Persian regular expressions", color = MaterialTheme.colorScheme.outline)
                    }
                },
                isError = state.testResult?.isValid == false,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sample SMS Text Input
            OutlinedTextField(
                value = state.testSampleText,
                onValueChange = onSampleChange,
                label = { Text("Test Sample SMS Text") },
                minLines = 3,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Evaluation Result Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SquircleCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (state.simulatedListType == RuleListType.ALLOWLIST)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isMatch = state.testResult?.isMatch == true
                            Icon(
                                imageVector = if (isMatch) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isMatch) "Match Detected!" else "No Match",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Simulated Classification Badge
                        Surface(
                            shape = PillShape,
                            color = if (state.simulatedListType == RuleListType.ALLOWLIST) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                when (state.simulatedAction) {
                                    FilterAction.SPAM -> MaterialTheme.colorScheme.errorContainer
                                    FilterAction.SILENT -> MaterialTheme.colorScheme.secondaryContainer
                                    FilterAction.NORMAL -> MaterialTheme.colorScheme.primaryContainer
                                }
                            }
                        ) {
                            Text(
                                text = if (state.simulatedListType == RuleListType.ALLOWLIST) "ALLOWLIST (TOP PRIORITY)" else "BLOCKLIST: ${state.simulatedAction.displayLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = if (state.simulatedListType == RuleListType.ALLOWLIST) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    when (state.simulatedAction) {
                                        FilterAction.SPAM -> MaterialTheme.colorScheme.onErrorContainer
                                        FilterAction.SILENT -> MaterialTheme.colorScheme.onSecondaryContainer
                                        FilterAction.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                }
                            )
                        }
                    }

                    if (state.testResult?.isMatch == true) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Matched Substring: ${state.testResult.matchedSubstrings.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // List Classification Selector
                    Text(
                        text = "Rule List Target:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                            RadioButton(
                                selected = state.simulatedListType == RuleListType.ALLOWLIST,
                                onClick = { onListTypeChange(RuleListType.ALLOWLIST) }
                            )
                            Text(
                                text = "Allowlist (Top Priority)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (state.simulatedListType == RuleListType.ALLOWLIST) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.simulatedListType == RuleListType.BLOCKLIST,
                                onClick = { onListTypeChange(RuleListType.BLOCKLIST) }
                            )
                            Text(
                                text = "Blocklist",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (state.simulatedListType == RuleListType.BLOCKLIST) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    if (state.simulatedListType == RuleListType.BLOCKLIST) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Blocklist Action:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                listOf(FilterAction.SPAM, FilterAction.SILENT).forEach { act ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 6.dp)
                                    ) {
                                        RadioButton(
                                            selected = state.simulatedAction == act,
                                            onClick = { onActionChange(act) }
                                        )
                                        Text(
                                            text = act.displayLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (state.simulatedAction == act) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.testResult?.isValid == true && state.testPattern.isNotBlank(),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.simulatedListType == RuleListType.ALLOWLIST) "Save as Allowlist Rule" else "Save as Blocklist Rule",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllowlistTab(
    rules: List<FilterRule>,
    onToggle: (FilterRule, Boolean) -> Unit,
    onEdit: (FilterRule) -> Unit,
    onDelete: (FilterRule) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = SquircleMediumShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GppGood,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Allowlist Priority System",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Allowlist rules have higher priority than blocklists. If an incoming message matches an allowlist rule (e.g. OTP verification codes, banking passwords), it is immediately treated as safe and delivered with alert sound, overriding all blocklists.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No allowlist rules active. Tap '+' to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { onToggle(rule, it) },
                    onEdit = { onEdit(rule) },
                    onDelete = { onDelete(rule) }
                )
            }
        }
    }
}

@Composable
private fun BlocklistTab(
    rules: List<FilterRule>,
    onToggle: (FilterRule, Boolean) -> Unit,
    onEdit: (FilterRule) -> Unit,
    onDelete: (FilterRule) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (rules.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No blocklist rules active. Tap '+' to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { onToggle(rule, it) },
                    onEdit = { onEdit(rule) },
                    onDelete = { onDelete(rule) }
                )
            }
        }
    }
}

@Composable
private fun PredefinedRulesTab(
    rules: List<FilterRule>,
    onToggle: (FilterRule, Boolean) -> Unit,
    onEdit: (FilterRule) -> Unit,
    onDelete: (FilterRule) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = SquircleMediumShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Built-in rules include Iranian OTP presets (Allowlist) and telecom spam filters (Blocklist). You can toggle, customize, or delete any preset below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            RuleCard(
                rule = rule,
                onToggle = { onToggle(rule, it) },
                onEdit = { onEdit(rule) },
                onDelete = { onDelete(rule) }
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: FilterRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isAllowlist = rule.listType == RuleListType.ALLOWLIST

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleMediumShape,
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(
            if (isAllowlist && rule.isEnabled) 1.dp else 0.5.dp,
            if (isAllowlist && rule.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (rule.description.isNotBlank()) {
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Switch(checked = rule.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = TagShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAllowlist) {
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GppGood,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ALLOWLIST • TOP PRIORITY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Action: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Surface(
                            shape = PillShape,
                            color = when (rule.action) {
                                FilterAction.SPAM -> MaterialTheme.colorScheme.errorContainer
                                FilterAction.SILENT -> MaterialTheme.colorScheme.secondaryContainer
                                FilterAction.NORMAL -> MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = rule.action.displayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = when (rule.action) {
                                    FilterAction.SPAM -> MaterialTheme.colorScheme.onErrorContainer
                                    FilterAction.SILENT -> MaterialTheme.colorScheme.onSecondaryContainer
                                    FilterAction.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Edit Rule
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Rule",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Delete Rule
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Rule",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditRuleDialog(
    rule: FilterRule,
    onDismiss: () -> Unit,
    onSave: (FilterRule) -> Unit
) {
    var name by remember { mutableStateOf(rule.name) }
    var pattern by remember { mutableStateOf(rule.pattern) }
    var description by remember { mutableStateOf(rule.description) }
    var selectedListType by remember { mutableStateOf(rule.listType) }
    var selectedAction by remember { mutableStateOf(rule.action) }
    var isRegex by remember { mutableStateOf(rule.isRegex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SquircleCardShape,
        title = {
            Text(
                text = if (rule.isPredefined) "Edit Preset Rule" else "Edit Custom Rule",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(if (isRegex) "Regex Pattern" else "Pattern") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                FilterChip(
                    selected = isRegex,
                    onClick = { isRegex = !isRegex },
                    label = { Text("Regex") },
                    shape = PillShape
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "List Classification:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                        RadioButton(
                            selected = selectedListType == RuleListType.ALLOWLIST,
                            onClick = {
                                selectedListType = RuleListType.ALLOWLIST
                                selectedAction = FilterAction.NORMAL
                            }
                        )
                        Text("Allowlist", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedListType == RuleListType.BLOCKLIST,
                            onClick = {
                                selectedListType = RuleListType.BLOCKLIST
                                if (selectedAction == FilterAction.NORMAL) selectedAction = FilterAction.SPAM
                            }
                        )
                        Text("Blocklist", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                if (selectedListType == RuleListType.BLOCKLIST) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Action Tier:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(FilterAction.SPAM, FilterAction.SILENT).forEach { act ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedAction == act,
                                    onClick = { selectedAction = act }
                                )
                                Text(
                                    text = act.displayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedAction == act) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    if (name.isNotBlank() && pattern.isNotBlank()) {
                        onSave(
                            rule.copy(
                                name = name,
                                pattern = pattern,
                                description = description,
                                listType = selectedListType,
                                action = if (selectedListType == RuleListType.ALLOWLIST) FilterAction.NORMAL else selectedAction,
                                isRegex = isRegex
                            )
                        )
                    }
                },
                shape = PillShape
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddGlobalRuleDialog(
    targetAddress: String?,
    initialListType: RuleListType = RuleListType.ALLOWLIST,
    onDismiss: () -> Unit,
    onSave: (pattern: String, isRegex: Boolean, action: FilterAction, name: String, listType: RuleListType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(true) }
    var listType by remember { mutableStateOf(initialListType) }
    var action by remember { mutableStateOf(if (initialListType == RuleListType.ALLOWLIST) FilterAction.NORMAL else FilterAction.SPAM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SquircleCardShape,
        title = {
            Text(
                text = when {
                    !targetAddress.isNullOrBlank() -> "Rule for $targetAddress"
                    listType == RuleListType.ALLOWLIST -> "New Allowlist Rule"
                    else -> "New Blocklist Rule"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (listType == RuleListType.ALLOWLIST) "Rule Name (e.g. Bank OTP)" else "Rule Name (e.g. Discount codes)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Regex Pattern") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "List Classification:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                        RadioButton(
                            selected = listType == RuleListType.ALLOWLIST,
                            onClick = {
                                listType = RuleListType.ALLOWLIST
                                action = FilterAction.NORMAL
                            }
                        )
                        Text("Allowlist", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = listType == RuleListType.BLOCKLIST,
                            onClick = {
                                listType = RuleListType.BLOCKLIST
                                action = FilterAction.SPAM
                            }
                        )
                        Text("Blocklist", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                if (listType == RuleListType.BLOCKLIST) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Trigger Action:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(FilterAction.SPAM, FilterAction.SILENT).forEach { act ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = action == act, onClick = { action = act })
                                Text(
                                    text = act.displayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (action == act) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onSave(pattern, isRegex, action, name, listType)
                    }
                },
                shape = PillShape
            ) {
                Text("Save Rule", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


package com.example.misga.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.misga.data.model.FilterAction
import com.example.misga.data.model.FilterRule
import com.example.misga.data.model.RuleCategory
import com.example.misga.ui.viewmodel.FilterStudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterStudioScreen(
    onBackClick: () -> Unit,
    viewModel: FilterStudioViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

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
                        Text(
                            text = "Filter Studio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Iranian Regex & Spam Filters",
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
                    // Export JSON
                    IconButton(onClick = {
                        val json = viewModel.exportRulesJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MISGA Rules", json))
                        Toast.makeText(context, "Rules JSON copied to clipboard", Toast.LENGTH_SHORT).show()
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
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showAddCustomDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule")
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
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Regex Playground", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Custom Rules (${state.customRules.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Built-in Presets", fontWeight = FontWeight.SemiBold) }
                )
            }

            when (selectedTab) {
                0 -> PlaygroundTab(
                    state = state,
                    onPatternChange = viewModel::onPlaygroundPatternChanged,
                    onSampleChange = viewModel::onPlaygroundSampleTextChanged,
                    onActionChange = viewModel::onPlaygroundActionChanged,
                    onPresetSelect = viewModel::setSamplePreset,
                    onSaveClick = { showSaveDialog = true }
                )
                1 -> CustomRulesTab(
                    rules = state.customRules,
                    onToggle = viewModel::toggleRule,
                    onUpdateAction = viewModel::updateRuleAction,
                    onDelete = viewModel::deleteRule
                )
                2 -> PredefinedRulesTab(
                    rules = state.predefinedRules,
                    onToggle = viewModel::toggleRule,
                    onUpdateAction = viewModel::updateRuleAction
                )
            }
        }
    }

    // Save Playground Dialog
    if (showSaveDialog) {
        var ruleName by remember { mutableStateOf("") }
        var ruleDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Regex as Custom Rule", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("Rule Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ruleDesc,
                        onValueChange = { ruleDesc = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pattern: ${state.testPattern}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (ruleName.isNotBlank()) {
                            viewModel.savePlaygroundRuleAsCustom(ruleName, ruleDesc)
                            showSaveDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Custom Rule Dialog
    if (showAddCustomDialog) {
        AddRuleDialog(
            targetAddress = null,
            onDismiss = { showAddCustomDialog = false },
            onSave = { pattern, isRegex, action, name ->
                viewModel.onPlaygroundPatternChanged(pattern)
                viewModel.onPlaygroundIsRegexChanged(isRegex)
                viewModel.onPlaygroundActionChanged(action)
                viewModel.savePlaygroundRuleAsCustom(name)
                showAddCustomDialog = false
            }
        )
    }

    // Import JSON Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Filter Rules JSON", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Paste the exported JSON content below:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        placeholder = { Text("[{\"name\": ...}]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        viewModel.importRulesJson(importJsonText) { success, count ->
                            if (success) {
                                Toast.makeText(context, "Imported $count rules!", Toast.LENGTH_SHORT).show()
                                showImportDialog = false
                                importJsonText = ""
                            } else {
                                Toast.makeText(context, "Failed to parse JSON", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Import")
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
    state: com.example.misga.ui.viewmodel.FilterStudioUiState,
    onPatternChange: (String) -> Unit,
    onSampleChange: (String) -> Unit,
    onActionChange: (FilterAction) -> Unit,
    onPresetSelect: (sample: String, pattern: String) -> Unit,
    onSaveClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = "Preset Real-World Iranian SMS Samples",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = {
                            onPresetSelect(
                                "مشترک گرامی، ۵۰ درصد تخفیف ویژه خرید اینترنت برای شما فعال شد. جهت انصراف لغو ۱۱ را ارسال فرمایید.",
                                "(لغو\\s*(11|۱۱)|تخفیف|اینترنت)"
                            )
                        },
                        label = { Text("Promo Opt-out (لغو ۱۱)") }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            onPresetSelect(
                                "وام فوری بدون ضامن تا سقف ۵۰ میلیون تومان، پرداخت ۲۴ ساعته. ثبت نام سریع:",
                                "وام\\s*(فوری|بدون\\s*ضامن)|پرداخت\\s*۲۴\\s*ساعته"
                            )
                        },
                        label = { Text("Loan Scam (وام فوری)") }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            onPresetSelect(
                                "سامانه ثنا: ابلاغیه جدید علیه شما صادر گردید. مشاهده در: http://sana-adlieh.top/apk",
                                "(ابلاغیه|سامانه\\s*ثنا).*http"
                            )
                        },
                        label = { Text("Phishing Link (ثنا/عدالت)") }
                    )
                }
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
                        Text("Supports Unicode Persian regular expressions")
                    }
                },
                isError = state.testResult?.isValid == false,
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Evaluation Result Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                tint = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isMatch) "Match Detected!" else "No Match",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Simulated Action Badge
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = when (state.simulatedAction) {
                                FilterAction.SPAM -> MaterialTheme.colorScheme.errorContainer
                                FilterAction.SILENT -> MaterialTheme.colorScheme.secondaryContainer
                                FilterAction.NORMAL -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Text(
                                text = "Action: ${state.simulatedAction.name}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = when (state.simulatedAction) {
                                    FilterAction.SPAM -> MaterialTheme.colorScheme.onErrorContainer
                                    FilterAction.SILENT -> MaterialTheme.colorScheme.onSecondaryContainer
                                    FilterAction.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }

                    if (state.testResult?.isMatch == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Matched Segments: ${state.testResult.matchedSubstrings.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Set Action:", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterAction.values().forEach { act ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = state.simulatedAction == act,
                                        onClick = { onActionChange(act) }
                                    )
                                    Text(act.name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.testResult?.isValid == true && state.testPattern.isNotBlank(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save as Permanent Rule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomRulesTab(
    rules: List<FilterRule>,
    onToggle: (FilterRule, Boolean) -> Unit,
    onUpdateAction: (FilterRule, FilterAction) -> Unit,
    onDelete: (Long) -> Unit
) {
    if (rules.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No custom rules yet. Tap '+' to create one or save from the Playground.", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { onToggle(rule, it) },
                    onUpdateAction = { onUpdateAction(rule, it) },
                    onDelete = { onDelete(rule.id) }
                )
            }
        }
    }
}

@Composable
private fun PredefinedRulesTab(
    rules: List<FilterRule>,
    onToggle: (FilterRule, Boolean) -> Unit,
    onUpdateAction: (FilterRule, FilterAction) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Built-in rules are tailored for Iranian telecom operators, shortcodes, and common Persian spam campaigns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            RuleCard(
                rule = rule,
                onToggle = { onToggle(rule, it) },
                onUpdateAction = { onUpdateAction(rule, it) },
                onDelete = null
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: FilterRule,
    onToggle: (Boolean) -> Unit,
    onUpdateAction: (FilterAction) -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
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
                shape = MaterialTheme.shapes.extraSmall,
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
                    Text("Action: ", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = rule.action.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (rule.action) {
                            FilterAction.SPAM -> MaterialTheme.colorScheme.error
                            FilterAction.SILENT -> MaterialTheme.colorScheme.secondary
                            FilterAction.NORMAL -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

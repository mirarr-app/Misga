package com.miss.ga.testing

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miss.ga.data.model.FilterAction
import com.miss.ga.theme.PillShape
import com.miss.ga.theme.SquircleCardShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestLabScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var isBatchRunning by remember { mutableStateOf(false) }
    var batchProgress by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<SimulationResult?>(null) }

    // Custom SMS input fields
    var customSender by remember { mutableStateOf("10008899") }
    var customBody by remember { mutableStateOf("۵۰ درصد تخفیف ویژه خرید اینترنت ماهانه. جهت لغو ۱۱ را ارسال فرمایید.") }
    var isCustomSimulating by remember { mutableStateOf(false) }

    val scenarios = remember { FakeSmsSimulator.getPredefinedScenarios() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Test Lab & Simulator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val count = FakeSmsSimulator.clearSimulatedTestData(context)
                                Toast.makeText(context, "Cleared $count test messages", Toast.LENGTH_SHORT).show()
                                lastResult = null
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Test Data",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
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
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Preset Scenarios", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Custom SMS Injector", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }
                )
            }

            // Real-time Result Banner
            AnimatedVisibility(
                visible = lastResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                lastResult?.let { result ->
                    Card(
                        shape = SquircleCardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = when (result.action) {
                                FilterAction.SPAM -> MaterialTheme.colorScheme.errorContainer
                                FilterAction.SILENT -> MaterialTheme.colorScheme.tertiaryContainer
                                FilterAction.NORMAL -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (result.action) {
                                            FilterAction.SPAM -> Icons.Default.Shield
                                            FilterAction.SILENT -> Icons.Default.VolumeMute
                                            FilterAction.NORMAL -> Icons.Default.Notifications
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Evaluated Action: ${result.action.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    shape = PillShape,
                                    color = if (result.notificationSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                ) {
                                    Text(
                                        text = if (result.notificationSent) "🔔 Notification Sent" else "🔇 Silenced (Zero Alert)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sender: ${result.sender} • Rule: ${result.matchedRule ?: "Default Pass-through"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                // Preset Scenarios Tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Batch Runner Header Card
                    item {
                        Card(
                            shape = SquircleCardShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Automated Live Test Suite",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Injects 8 real Iranian spam vs normal messages with a 1.2s delay so you can observe live system notifications and chat list categorization.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                FilledTonalButton(
                                    onClick = {
                                        if (!isBatchRunning) {
                                            isBatchRunning = true
                                            coroutineScope.launch {
                                                for ((idx, scenario) in scenarios.withIndex()) {
                                                    batchProgress = "Firing (${idx + 1}/${scenarios.size}): ${scenario.title}..."
                                                    lastResult = FakeSmsSimulator.simulateIncomingSms(
                                                        context = context,
                                                        sender = scenario.sender,
                                                        body = scenario.body
                                                    )
                                                    delay(1200)
                                                }
                                                isBatchRunning = false
                                                batchProgress = "All 8 scenarios injected successfully!"
                                                Toast.makeText(context, "Test Suite Complete!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = !isBatchRunning,
                                    shape = PillShape,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isBatchRunning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(batchProgress, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Fire All 8 Scenarios (Timed Batch)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Individual Scenario Cards
                    items(scenarios) { scenario ->
                        ScenarioCard(
                            scenario = scenario,
                            onSimulate = {
                                coroutineScope.launch {
                                    lastResult = FakeSmsSimulator.simulateIncomingSms(
                                        context = context,
                                        sender = scenario.sender,
                                        body = scenario.body
                                    )
                                    Toast.makeText(context, "Injected: ${scenario.title}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            } else {
                // Custom SMS Injector Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Custom SMS Simulator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Specify any sender phone number and message body to test against your active filter rules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = customSender,
                        onValueChange = { customSender = it },
                        label = { Text("Sender Number / Name") },
                        placeholder = { Text("e.g. 10002000, 09121234567, BankMellat") },
                        singleLine = true,
                        shape = SquircleCardShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customBody,
                        onValueChange = { customBody = it },
                        label = { Text("SMS Message Body") },
                        placeholder = { Text("Enter SMS content in Persian or English...") },
                        minLines = 4,
                        maxLines = 6,
                        shape = SquircleCardShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    FilledTonalButton(
                        onClick = {
                            if (customSender.isNotBlank() && customBody.isNotBlank() && !isCustomSimulating) {
                                isCustomSimulating = true
                                coroutineScope.launch {
                                    lastResult = FakeSmsSimulator.simulateIncomingSms(
                                        context = context,
                                        sender = customSender,
                                        body = customBody
                                    )
                                    isCustomSimulating = false
                                    Toast.makeText(context, "Injected custom SMS from $customSender", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = customSender.isNotBlank() && customBody.isNotBlank() && !isCustomSimulating,
                        shape = PillShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCustomSimulating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulate Incoming SMS Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioCard(
    scenario: FakeSmsScenario,
    onSimulate: () -> Unit
) {
    Card(
        shape = SquircleCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = scenario.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = PillShape,
                    color = when (scenario.expectedAction) {
                        FilterAction.SPAM -> MaterialTheme.colorScheme.errorContainer
                        FilterAction.SILENT -> MaterialTheme.colorScheme.tertiaryContainer
                        FilterAction.NORMAL -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = "Expected: ${scenario.expectedAction.name}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (scenario.expectedAction) {
                            FilterAction.SPAM -> MaterialTheme.colorScheme.onErrorContainer
                            FilterAction.SILENT -> MaterialTheme.colorScheme.onTertiaryContainer
                            FilterAction.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sender: ${scenario.sender}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = scenario.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = scenario.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            FilledTonalButton(
                onClick = onSimulate,
                shape = PillShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simulate Single Incoming SMS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

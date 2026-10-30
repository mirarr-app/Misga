package com.miss.ga.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miss.ga.ui.util.SmsDateFormats
import java.util.Calendar

data class SchedulePreset(
    val title: String,
    val subtitle: String,
    val timeMillis: Long
)

@Composable
fun ScheduleMessageDialog(
    recipientName: String?,
    address: String,
    showShamsiDate: Boolean,
    onDismiss: () -> Unit,
    onSchedule: (Long) -> Unit
) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }

    // Generate smart presets based on current hour
    val presets = remember(now) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val list = mutableListOf<SchedulePreset>()

        // 1. Later today (if current hour < 17: set to 18:00, if < 20: set to 21:00, else in 2 hours)
        val laterToday = Calendar.getInstance().apply {
            timeInMillis = now
            if (currentHour < 17) {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            } else if (currentHour < 20) {
                set(Calendar.HOUR_OF_DAY, 21)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            } else {
                add(Calendar.HOUR_OF_DAY, 2)
            }
        }
        if (laterToday.timeInMillis > now) {
            val formattedTime = SmsDateFormats.clock(laterToday.timeInMillis)
            list.add(
                SchedulePreset(
                    title = "Later today",
                    subtitle = formattedTime,
                    timeMillis = laterToday.timeInMillis
                )
            )
        }

        // 2. Tomorrow morning (08:00 AM)
        val tomorrowMorning = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        list.add(
            SchedulePreset(
                title = "Tomorrow morning",
                subtitle = "08:00",
                timeMillis = tomorrowMorning.timeInMillis
            )
        )

        // 3. Tomorrow afternoon (1:00 PM)
        val tomorrowAfternoon = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        list.add(
            SchedulePreset(
                title = "Tomorrow afternoon",
                subtitle = "13:00",
                timeMillis = tomorrowAfternoon.timeInMillis
            )
        )

        list
    }

    var selectedTimeMillis by remember {
        mutableLongStateOf(presets.firstOrNull()?.timeMillis ?: (now + 3600_000L))
    }
    var isCustomSelected by remember { mutableStateOf(false) }

    fun showCustomPickers() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimeMillis }
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosenCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Next show TimePickerDialog
                val timePicker = TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        chosenCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        chosenCal.set(Calendar.MINUTE, minute)
                        if (chosenCal.timeInMillis > System.currentTimeMillis()) {
                            selectedTimeMillis = chosenCal.timeInMillis
                            isCustomSelected = true
                        }
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context)
                )
                timePicker.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.minDate = System.currentTimeMillis()
        datePicker.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Schedule message",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                val targetText = if (!recipientName.isNullOrBlank()) recipientName else address
                Text(
                    text = "Send to $targetText at a specific time:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Presets list
                presets.forEach { preset ->
                    val isSelected = !isCustomSelected && selectedTimeMillis == preset.timeMillis
                    PresetRow(
                        title = preset.title,
                        subtitle = preset.subtitle,
                        isSelected = isSelected,
                        onClick = {
                            selectedTimeMillis = preset.timeMillis
                            isCustomSelected = false
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Custom date & time option
                PresetRow(
                    title = "Pick date & time",
                    subtitle = if (isCustomSelected) {
                        SmsDateFormats.formatDateTimeWithYear(selectedTimeMillis, showShamsiDate)
                    } else {
                        "Choose custom..."
                    },
                    isSelected = isCustomSelected,
                    onClick = {
                        showCustomPickers()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Schedule confirmation preview card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Scheduled time",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = SmsDateFormats.formatDateTimeWithYear(selectedTimeMillis, showShamsiDate),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTimeMillis > System.currentTimeMillis()) {
                        onSchedule(selectedTimeMillis)
                    }
                },
                enabled = selectedTimeMillis > System.currentTimeMillis()
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PresetRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

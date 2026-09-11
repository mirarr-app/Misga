package com.miss.ga.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.SimInfo
import com.miss.ga.theme.PillShape

@Composable
fun SimFilterRow(
    availableSims: List<SimInfo>,
    selectedSubId: Int?,
    threads: List<ConversationThread>,
    onSelectSubId: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableSims.size <= 1) return

    val simCounts = remember(threads) {
        val counts = mutableMapOf<Int, Int>()
        for (thread in threads) {
            if (thread.subId >= 0) {
                counts[thread.subId] = (counts[thread.subId] ?: 0) + 1
            }
        }
        counts
    }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All SIMs" chip
        val isAllSelected = selectedSubId == null
        FilterChip(
            selected = isAllSelected,
            onClick = { onSelectSubId(null) },
            label = {
                Text(
                    text = "All SIMs",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                )
            },
            shape = PillShape,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Chip for each active SIM
        availableSims.forEach { sim ->
            val isSelected = selectedSubId == sim.subscriptionId
            val count = simCounts[sim.subscriptionId] ?: 0
            val chipLabel = when {
                sim.carrierName.isNotBlank() -> "SIM ${sim.slotNumber} (${sim.carrierName})"
                sim.displayName.isNotBlank() && !sim.displayName.startsWith("SIM", ignoreCase = true) ->
                    "SIM ${sim.slotNumber} (${sim.displayName})"
                else -> "SIM ${sim.slotNumber}"
            }

            FilterChip(
                selected = isSelected,
                onClick = { onSelectSubId(if (isSelected) null else sim.subscriptionId) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                },
                label = {
                    Text(
                        text = if (count > 0) "$chipLabel ($count)" else chipLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = PillShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

package com.miss.ga.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.SenderTab
import com.miss.ga.data.util.PhoneNumberKeys
import com.miss.ga.theme.PillShape

fun isThreadInTab(thread: ConversationThread, tab: SenderTab): Boolean {
    if (tab.senderAddresses.isEmpty()) return false
    val canonical = PhoneNumberKeys.canonical(thread.address)
    if (canonical in tab.senderAddresses) return true
    val keys = PhoneNumberKeys.keys(thread.address)
    return keys.any { it in tab.senderAddresses }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SenderTabRow(
    tabs: List<SenderTab>,
    selectedTabId: Long?,
    threads: List<ConversationThread>,
    onSelectTab: (Long?) -> Unit,
    onCreateTabClick: () -> Unit,
    onTabOptionsClick: (SenderTab) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All" tab
        item(key = "tab_all") {
            val isSelected = selectedTabId == null
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "allTabBg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "allTabText"
            )

            Surface(
                shape = PillShape,
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .clip(PillShape)
                    .combinedClickable(
                        onClick = { onSelectTab(null) }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (threads.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = PillShape,
                            color = if (isSelected) contentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = threads.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Custom tabs
        items(tabs, key = { "tab_${it.id}" }) { tab ->
            val isSelected = selectedTabId == tab.id
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "tabBg_${tab.id}"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabText_${tab.id}"
            )
            val matchingCount = threads.count { isThreadInTab(it, tab) }

            Surface(
                shape = PillShape,
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .clip(PillShape)
                    .combinedClickable(
                        onClick = { onSelectTab(tab.id) },
                        onLongClick = { onTabOptionsClick(tab) }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = if (isSelected) 6.dp else 14.dp,
                        top = 5.dp,
                        bottom = 5.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = PillShape,
                        color = if (isSelected) contentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = matchingCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 11.sp
                        )
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = { onTabOptionsClick(tab) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tab options",
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // "+ New Tab" button
        item(key = "tab_add_button") {
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(PillShape)
                    .combinedClickable(onClick = onCreateTabClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tab",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New Tab",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

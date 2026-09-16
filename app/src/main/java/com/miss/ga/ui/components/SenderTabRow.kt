package com.miss.ga.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.SenderTab
import com.miss.ga.data.util.PhoneNumberKeys
import com.miss.ga.theme.PillShape
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    onReorderTabs: (List<SenderTab>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop

    val currentTabs = remember { mutableStateListOf<SenderTab>() }
    var draggedTabId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var hasReordered by remember { mutableStateOf(false) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    // Sync currentTabs with tabs when not actively dragging
    if (draggedTabId == null && (currentTabs.size != tabs.size || currentTabs.map { it.id } != tabs.map { it.id })) {
        currentTabs.clear()
        currentTabs.addAll(tabs)
    }

    LazyRow(
        state = lazyListState,
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
        items(currentTabs, key = { "tab_${it.id}" }) { tab ->
            val isSelected = selectedTabId == tab.id
            val isDraggingThis = draggedTabId == tab.id
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
                shadowElevation = if (isDraggingThis) 6.dp else 0.dp,
                modifier = Modifier
                    .zIndex(if (isDraggingThis) 2f else 0f)
                    .graphicsLayer {
                        if (isDraggingThis) {
                            translationX = dragOffset
                            scaleX = 1.05f
                            scaleY = 1.05f
                        }
                    }
                    .then(if (isDraggingThis) Modifier else Modifier.animateItem())
                    .clip(PillShape)
                    .combinedClickable(
                        onClick = {
                            if (draggedTabId == null) {
                                onSelectTab(tab.id)
                            }
                        }
                    )
                    .pointerInput(tab.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedTabId = tab.id
                                dragOffset = 0f
                                hasReordered = false
                                totalDragDistance = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragDistance += abs(dragAmount.x)
                                dragOffset += dragAmount.x

                                // Auto scroll near edges
                                val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                val draggedInfo = visibleItems.find { it.key == "tab_${tab.id}" }
                                if (draggedInfo != null) {
                                    val viewportStart = lazyListState.layoutInfo.viewportStartOffset
                                    val viewportEnd = lazyListState.layoutInfo.viewportEndOffset
                                    val edgeThreshold = with(density) { 48.dp.toPx() }
                                    val currentPos = draggedInfo.offset + dragOffset
                                    if (currentPos < viewportStart + edgeThreshold) {
                                        coroutineScope.launch {
                                            lazyListState.scrollBy(-15f)
                                        }
                                    } else if (currentPos + draggedInfo.size > viewportEnd - edgeThreshold) {
                                        coroutineScope.launch {
                                            lazyListState.scrollBy(15f)
                                        }
                                    }
                                }

                                // Check swaps (one swap per drag event to allow layout to settle)
                                val draggedIndex = currentTabs.indexOfFirst { it.id == tab.id }
                                if (draggedIndex != -1) {
                                    val items = lazyListState.layoutInfo.visibleItemsInfo
                                    val dInfo = items.find { it.key == "tab_${tab.id}" }
                                    if (dInfo != null) {
                                        val draggedCenter = dInfo.offset + dInfo.size / 2f + dragOffset

                                        if (draggedIndex < currentTabs.size - 1) {
                                            val nextTab = currentTabs[draggedIndex + 1]
                                            val nextInfo = items.find { it.key == "tab_${nextTab.id}" }
                                            if (nextInfo != null && draggedCenter > nextInfo.offset + nextInfo.size / 2f) {
                                                val spacing = nextInfo.offset - (dInfo.offset + dInfo.size)
                                                val slotShift = nextInfo.size + spacing
                                                val temp = currentTabs[draggedIndex]
                                                currentTabs[draggedIndex] = currentTabs[draggedIndex + 1]
                                                currentTabs[draggedIndex + 1] = temp
                                                dragOffset -= slotShift
                                                hasReordered = true
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        } else if (draggedIndex > 0) {
                                            val prevTab = currentTabs[draggedIndex - 1]
                                            val prevInfo = items.find { it.key == "tab_${prevTab.id}" }
                                            if (prevInfo != null && draggedCenter < prevInfo.offset + prevInfo.size / 2f) {
                                                val spacing = dInfo.offset - (prevInfo.offset + prevInfo.size)
                                                val slotShift = prevInfo.size + spacing
                                                val temp = currentTabs[draggedIndex]
                                                currentTabs[draggedIndex] = currentTabs[draggedIndex - 1]
                                                currentTabs[draggedIndex - 1] = temp
                                                dragOffset += slotShift
                                                hasReordered = true
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                val finalReordered = hasReordered
                                val finalTabs = currentTabs.toList()
                                if (totalDragDistance < touchSlop && !finalReordered) {
                                    onTabOptionsClick(tab)
                                } else if (finalReordered) {
                                    onReorderTabs(finalTabs)
                                }
                                draggedTabId = null
                                dragOffset = 0f
                                hasReordered = false
                                totalDragDistance = 0f
                            },
                            onDragCancel = {
                                draggedTabId = null
                                dragOffset = 0f
                                hasReordered = false
                                totalDragDistance = 0f
                                currentTabs.clear()
                                currentTabs.addAll(tabs)
                            }
                        )
                    }
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
                            enabled = draggedTabId == null,
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

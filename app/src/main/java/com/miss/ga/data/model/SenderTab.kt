package com.miss.ga.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SenderTab(
    val id: Long,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val senderAddresses: Set<String> = emptySet()
)

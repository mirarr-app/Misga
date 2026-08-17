package com.miss.ga.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FilterRule(
    val id: Long = 0,
    val name: String,
    val pattern: String,
    val isRegex: Boolean = true,
    val action: FilterAction = FilterAction.SPAM,
    val listType: RuleListType = RuleListType.BLOCKLIST,
    val isEnabled: Boolean = true,
    val isPredefined: Boolean = false,
    val category: RuleCategory = RuleCategory.CUSTOM,
    val senderTarget: String? = null, // Null for global rule, or specific sender address
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)


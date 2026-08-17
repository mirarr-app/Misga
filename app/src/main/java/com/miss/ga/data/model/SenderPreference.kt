package com.miss.ga.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SenderPreference(
    val address: String,
    val displayName: String? = null,
    val defaultAction: FilterAction = FilterAction.NORMAL,
    val customSoundUri: String? = null,
    val isBlocked: Boolean = false,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

package com.miss.ga

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ConversationsNav : NavKey

@Serializable
data class ChatNav(
    val threadId: Long,
    val address: String,
    val contactName: String? = null
) : NavKey

@Serializable
data object FilterStudioNav : NavKey

@Serializable
data object ComposeNav : NavKey

@Serializable
data object TestLabNav : NavKey


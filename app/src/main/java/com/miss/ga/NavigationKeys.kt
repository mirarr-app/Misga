package com.miss.ga

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ConversationsNav : NavKey

@Serializable
data class ChatNav(
    val threadId: Long,
    val address: String,
    val contactName: String? = null,
    val initialMessageId: Long? = null
) : NavKey

@Serializable
data object FilterStudioNav : NavKey

@Serializable
data class ComposeNav(
    val address: String = "",
    val body: String = ""
) : NavKey

@Serializable
data object TestLabNav : NavKey


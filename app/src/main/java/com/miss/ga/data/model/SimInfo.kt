package com.miss.ga.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String = "",
    val carrierName: String = "",
    val number: String = "",
    val iconTint: Int = 0
) {
    val slotNumber: Int get() = if (slotIndex >= 0) slotIndex + 1 else 1

    val displayLabel: String
        get() {
            val name = displayName.trim()
            val carrier = carrierName.trim()
            val extra = when {
                name.isNotBlank() && !name.startsWith("SIM", ignoreCase = true) -> name
                carrier.isNotBlank() -> carrier
                else -> null
            }
            return if (extra != null) "SIM $slotNumber ($extra)" else "SIM $slotNumber"
        }

    val badgeLabel: String get() = "SIM $slotNumber"
}

object PreferredSimMode {
    const val AUTO = -1
    const val SYSTEM_DEFAULT = -2
}

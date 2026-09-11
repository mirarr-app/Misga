package com.miss.ga.data.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.miss.ga.data.model.PreferredSimMode
import com.miss.ga.data.model.SimInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

private const val TAG = "SimRepository"

class SimRepository(private val context: Context) {

    private val subscriptionManager: SubscriptionManager? by lazy {
        try {
            context.getSystemService(SubscriptionManager::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get SubscriptionManager", e)
            null
        }
    }

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns list of currently active SIMs on the device, sorted by slot index (SIM 1, SIM 2, etc.).
     */
    fun getActiveSims(): List<SimInfo> {
        val sm = subscriptionManager ?: return emptyList()
        if (!hasPhoneStatePermission()) {
            return fallbackSingleSimList()
        }

        return try {
            val list: List<SubscriptionInfo>? = sm.activeSubscriptionInfoList
            if (list.isNullOrEmpty()) {
                fallbackSingleSimList()
            } else {
                list.sortedBy { it.simSlotIndex }.map { sub ->
                    val displayName = sub.displayName?.toString().orEmpty()
                    val carrierName = sub.carrierName?.toString().orEmpty()
                    val number = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            sm.getPhoneNumber(sub.subscriptionId)
                        } else {
                            @Suppress("DEPRECATION")
                            sub.number.orEmpty()
                        }
                    } catch (_: Exception) {
                        ""
                    }
                    SimInfo(
                        subscriptionId = sub.subscriptionId,
                        slotIndex = sub.simSlotIndex,
                        displayName = displayName,
                        carrierName = carrierName,
                        number = number,
                        iconTint = sub.iconTint
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading active subscriptions: ${e.message}")
            fallbackSingleSimList()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying subscriptions", e)
            fallbackSingleSimList()
        }
    }

    /**
     * Fallback for single SIM devices or when permission is not yet granted.
     */
    private fun fallbackSingleSimList(): List<SimInfo> {
        val defaultSubId = getDefaultSmsSubscriptionId()
        return if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            listOf(
                SimInfo(
                    subscriptionId = defaultSubId,
                    slotIndex = 0,
                    displayName = "SIM 1",
                    carrierName = ""
                )
            )
        } else {
            emptyList()
        }
    }

    fun getDefaultSmsSubscriptionId(): Int {
        return try {
            val defaultSubId = SmsManager.getDefaultSmsSubscriptionId()
            if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                defaultSubId
            } else {
                SubscriptionManager.getDefaultSmsSubscriptionId()
            }
        } catch (_: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    fun getSimInfo(subId: Int): SimInfo? {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null
        val active = getActiveSims().find { it.subscriptionId == subId }
        if (active != null) return active

        // Fallback for stored messages from an inactive or swapped SIM
        return SimInfo(
            subscriptionId = subId,
            slotIndex = -1,
            displayName = "SIM",
            carrierName = ""
        )
    }

    fun isDualSim(): Boolean {
        return getActiveSims().size >= 2
    }

    /**
     * Resolves the effective subscription ID to use for an outgoing message in a conversation.
     * Hierarchy:
     * 1. If preferredSetting is a specific subId (>= 0) and that SIM is active, use it.
     * 2. If preferredSetting is SYSTEM_DEFAULT, use default SMS subId.
     * 3. If preferredSetting is AUTO (default):
     *    - Use latestMessageSubId if valid and active.
     *    - Otherwise, fallback to default SMS subId or first active SIM.
     */
    fun resolveOutgoingSubId(
        preferredSetting: Int,
        latestMessageSubId: Int?
    ): Int {
        val activeSims = getActiveSims()
        val defaultSubId = getDefaultSmsSubscriptionId()

        if (activeSims.isEmpty()) {
            return defaultSubId
        }

        when {
            preferredSetting >= 0 -> {
                val matched = activeSims.find { it.subscriptionId == preferredSetting }
                if (matched != null) return matched.subscriptionId
            }
            preferredSetting == PreferredSimMode.SYSTEM_DEFAULT -> {
                val matched = activeSims.find { it.subscriptionId == defaultSubId }
                if (matched != null) return matched.subscriptionId
            }
            else -> { // PreferredSimMode.AUTO
                if (latestMessageSubId != null && latestMessageSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    val matched = activeSims.find { it.subscriptionId == latestMessageSubId }
                    if (matched != null) return matched.subscriptionId
                }
            }
        }

        // Fallback to default SMS subId or first active SIM
        return activeSims.find { it.subscriptionId == defaultSubId }?.subscriptionId
            ?: activeSims.first().subscriptionId
    }

    /**
     * Cold flow observing active subscriptions updates.
     */
    fun observeActiveSims(): Flow<List<SimInfo>> = callbackFlow {
        trySend(getActiveSims())

        val sm = subscriptionManager
        if (sm == null || !hasPhoneStatePermission()) {
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                trySend(getActiveSims())
            }
        }

        try {
            sm.addOnSubscriptionsChangedListener(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register OnSubscriptionsChangedListener", e)
        }

        awaitClose {
            try {
                sm.removeOnSubscriptionsChangedListener(listener)
            } catch (_: Exception) {}
        }
    }.conflate()
}

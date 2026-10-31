package com.miss.ga.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.ScheduledMessageStatus
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.engine.NotificationHelper
import com.miss.ga.engine.ScheduledSmsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_SEND_SCHEDULED_SMS &&
            action != ACTION_RETRY_SCHEDULED_SMS &&
            action != ACTION_CANCEL_SCHEDULED_SMS
        ) {
            return
        }

        val scheduledId = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        if (scheduledId <= 0L) {
            Log.w(TAG, "Received scheduled SMS broadcast with invalid id: $scheduledId")
            return
        }

        if (action == ACTION_CANCEL_SCHEDULED_SMS) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dbHelper = MisgaDatabaseHelper.getInstance(context)
                    ScheduledSmsManager.cancel(context, scheduledId)
                    NotificationHelper.getInstance(context).cancelScheduledSmsFailureNotification(scheduledId)
                    dbHelper.deleteScheduledMessage(scheduledId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error cancelling scheduled SMS $scheduledId", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "misga:ScheduledSmsWakeLock"
        )
        try {
            wakeLock?.acquire(30_000L)
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dbHelper = MisgaDatabaseHelper.getInstance(context)
                val scheduledMessage = dbHelper.getScheduledMessage(scheduledId)
                if (scheduledMessage == null) {
                    Log.w(TAG, "Scheduled message $scheduledId not found in database")
                    return@launch
                }

                if (scheduledMessage.status == ScheduledMessageStatus.SENT ||
                    scheduledMessage.status == ScheduledMessageStatus.CANCELLED
                ) {
                    Log.d(TAG, "Scheduled message $scheduledId is already ${scheduledMessage.status}, skipping")
                    return@launch
                }

                Log.d(TAG, "Dispatching scheduled SMS $scheduledId to ${scheduledMessage.address} (action: $action)")
                val smsRepository = SmsRepository(context)
                val subId = if (scheduledMessage.subId >= 0) scheduledMessage.subId else null
                val result = smsRepository.sendSms(
                    address = scheduledMessage.address,
                    body = scheduledMessage.body,
                    subscriptionId = subId
                )

                if (result.sent) {
                    Log.d(TAG, "Scheduled SMS $scheduledId dispatched successfully")
                    NotificationHelper.getInstance(context).cancelScheduledSmsFailureNotification(scheduledId)
                    dbHelper.deleteScheduledMessage(scheduledId)
                } else {
                    Log.e(TAG, "Failed to send scheduled SMS $scheduledId (no cellular signal, airplane mode, or network failure)")
                    dbHelper.updateScheduledMessageStatus(scheduledId, ScheduledMessageStatus.FAILED)

                    val contactName = smsRepository.resolveContactDetail(scheduledMessage.address)?.name
                    NotificationHelper.getInstance(context).showScheduledSmsFailureNotification(
                        threadId = scheduledMessage.threadId,
                        address = scheduledMessage.address,
                        body = scheduledMessage.body,
                        scheduledId = scheduledId,
                        contactName = contactName
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing scheduled SMS dispatch", e)
                try {
                    val dbHelper = MisgaDatabaseHelper.getInstance(context)
                    dbHelper.updateScheduledMessageStatus(scheduledId, ScheduledMessageStatus.FAILED)
                    val scheduledMessage = dbHelper.getScheduledMessage(scheduledId)
                    if (scheduledMessage != null) {
                        NotificationHelper.getInstance(context).showScheduledSmsFailureNotification(
                            threadId = scheduledMessage.threadId,
                            address = scheduledMessage.address,
                            body = scheduledMessage.body,
                            scheduledId = scheduledId,
                            contactName = null
                        )
                    }
                } catch (inner: Exception) {
                    Log.e(TAG, "Error recording failure status", inner)
                }
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing WakeLock", e)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledSmsReceiver"
        const val ACTION_SEND_SCHEDULED_SMS = "com.miss.ga.action.SEND_SCHEDULED_SMS"
        const val ACTION_RETRY_SCHEDULED_SMS = "com.miss.ga.action.RETRY_SCHEDULED_SMS"
        const val ACTION_CANCEL_SCHEDULED_SMS = "com.miss.ga.action.CANCEL_SCHEDULED_SMS"
        const val EXTRA_SCHEDULED_ID = "com.miss.ga.extra.SCHEDULED_ID"
    }
}

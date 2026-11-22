package com.miss.ga.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.miss.ga.data.db.MisgaDatabaseHelper
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

        val appContext = context.applicationContext ?: context
        if (action == ACTION_CANCEL_SCHEDULED_SMS) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dbHelper = MisgaDatabaseHelper.getInstance(appContext)
                    ScheduledSmsManager.cancel(appContext, scheduledId)
                    NotificationHelper.getInstance(appContext).cancelScheduledSmsFailureNotification(scheduledId)
                    dbHelper.deleteScheduledMessage(scheduledId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error cancelling scheduled SMS $scheduledId", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val isRetry = (action == ACTION_RETRY_SCHEDULED_SMS)
        val pendingResult = goAsync()
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
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
                ScheduledSmsManager.dispatchScheduledMessage(appContext, scheduledId, isRetry = isRetry)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing scheduled SMS dispatch in receiver", e)
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

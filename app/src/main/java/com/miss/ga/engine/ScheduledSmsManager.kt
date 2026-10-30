package com.miss.ga.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.ScheduledMessage
import com.miss.ga.data.model.ScheduledMessageStatus
import com.miss.ga.receiver.ScheduledSmsReceiver

object ScheduledSmsManager {
    private const val TAG = "ScheduledSmsManager"

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            return alarmManager?.canScheduleExactAlarms() ?: false
        }
        return true
    }

    fun schedule(context: Context, scheduledMessage: ScheduledMessage) {
        if (scheduledMessage.id <= 0L) {
            Log.w(TAG, "Cannot schedule message with invalid id: ${scheduledMessage.id}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: run {
            Log.e(TAG, "AlarmManager service not available")
            return
        }

        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_SEND_SCHEDULED_SMS
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULED_ID, scheduledMessage.id)
        }

        val requestCode = (scheduledMessage.id % 100000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = if (scheduledMessage.scheduledTime <= System.currentTimeMillis()) {
            System.currentTimeMillis() + 500L
        } else {
            scheduledMessage.scheduledTime
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alarm for message ${scheduledMessage.id} at triggerTime=$triggerTime")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm for message ${scheduledMessage.id}, trying fallback", e)
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback alarm failed for message ${scheduledMessage.id}", fallbackError)
            }
        }
    }

    fun cancel(context: Context, scheduledId: Long) {
        if (scheduledId <= 0L) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_SEND_SCHEDULED_SMS
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULED_ID, scheduledId)
        }
        val requestCode = (scheduledId % 100000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for scheduled message $scheduledId")
        }
    }

    suspend fun rescheduleAllPending(context: Context) {
        val dbHelper = MisgaDatabaseHelper.getInstance(context)
        val pending = dbHelper.getPendingScheduledMessages()
        Log.d(TAG, "Rescheduling ${pending.size} pending messages")
        val now = System.currentTimeMillis()
        for (msg in pending) {
            if (msg.status == ScheduledMessageStatus.PENDING) {
                val targetTime = if (msg.scheduledTime <= now) now + 1000L else msg.scheduledTime
                schedule(context, msg.copy(scheduledTime = targetTime))
            }
        }
    }
}

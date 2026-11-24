package com.miss.ga.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.ScheduledMessage
import com.miss.ga.data.model.ScheduledMessageStatus
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.receiver.ScheduledSmsReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object ScheduledSmsManager {
    private const val TAG = "ScheduledSmsManager"

    private val activeDispatches = ConcurrentHashMap.newKeySet<Long>()

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            return alarmManager?.canScheduleExactAlarms() ?: false
        }
        return true
    }

    fun isDispatching(scheduledId: Long): Boolean = activeDispatches.contains(scheduledId)

    fun schedule(context: Context, scheduledMessage: ScheduledMessage) {
        if (scheduledMessage.id <= 0L) {
            Log.w(TAG, "Cannot schedule message with invalid id: ${scheduledMessage.id}")
            return
        }

        val appContext = context.applicationContext ?: context
        val triggerTime = scheduledMessage.scheduledTime
        if (triggerTime <= System.currentTimeMillis()) {
            Log.d(TAG, "Scheduled time $triggerTime is already due or in the past, dispatching immediately for message ${scheduledMessage.id}")
            CoroutineScope(Dispatchers.IO).launch {
                dispatchScheduledMessage(appContext, scheduledMessage.id)
            }
            return
        }

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: run {
            Log.e(TAG, "AlarmManager service not available")
            return
        }

        val intent = Intent(appContext, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_SEND_SCHEDULED_SMS
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULED_ID, scheduledMessage.id)
            data = Uri.parse("misga://scheduled_sms/${scheduledMessage.id}")
        }

        val requestCode = (scheduledMessage.id and 0x7FFFFFFF).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    } else {
                        Log.w(TAG, "Exact alarm permission not granted, falling back to inexact setAndAllowWhileIdle for message ${scheduledMessage.id}")
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled exact alarm for message ${scheduledMessage.id} at triggerTime=$triggerTime")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm for message ${scheduledMessage.id}, trying fallback", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (fallbackError: Exception) {
                try {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } catch (lastError: Exception) {
                    Log.e(TAG, "All alarm scheduling attempts failed for message ${scheduledMessage.id}", lastError)
                }
            }
        }
    }

    fun cancel(context: Context, scheduledId: Long) {
        if (scheduledId <= 0L) return
        val appContext = context.applicationContext ?: context
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_SEND_SCHEDULED_SMS
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULED_ID, scheduledId)
            data = Uri.parse("misga://scheduled_sms/$scheduledId")
        }
        val requestCode = (scheduledId and 0x7FFFFFFF).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled alarm for scheduled message $scheduledId")
    }

    suspend fun dispatchScheduledMessage(
        context: Context,
        scheduledId: Long,
        isRetry: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (scheduledId <= 0L) return@withContext false
        if (!activeDispatches.add(scheduledId)) {
            Log.d(TAG, "Message $scheduledId is already being dispatched, skipping duplicate trigger")
            return@withContext false
        }
        val appContext = context.applicationContext ?: context
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "misga:dispatchScheduledMessage_$scheduledId"
            )?.apply {
                setReferenceCounted(false)
                acquire(30_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock for dispatching message $scheduledId", e)
            null
        }

        try {
            val dbHelper = MisgaDatabaseHelper.getInstance(appContext)
            val scheduledMessage = dbHelper.getScheduledMessage(scheduledId)
            if (scheduledMessage == null) {
                Log.w(TAG, "Scheduled message $scheduledId not found in database")
                return@withContext false
            }

            if (scheduledMessage.status == ScheduledMessageStatus.SENT ||
                scheduledMessage.status == ScheduledMessageStatus.CANCELLED
            ) {
                Log.d(TAG, "Scheduled message $scheduledId is already ${scheduledMessage.status}, skipping")
                return@withContext false
            }

            if (scheduledMessage.status == ScheduledMessageStatus.FAILED && !isRetry) {
                Log.d(TAG, "Scheduled message $scheduledId is FAILED and this is not a retry, skipping")
                return@withContext false
            }

            if (isRetry && scheduledMessage.status == ScheduledMessageStatus.FAILED) {
                dbHelper.updateScheduledMessageStatus(scheduledId, ScheduledMessageStatus.PENDING)
            }

            Log.d(
                TAG,
                "Dispatching scheduled SMS $scheduledId to ${com.miss.ga.data.util.PhoneNumberKeys.redact(scheduledMessage.address)} (retry=$isRetry)"
            )
            val smsRepository = SmsRepository(appContext)
            val subId = if (scheduledMessage.subId >= 0) scheduledMessage.subId else null
            val result = smsRepository.sendSms(
                address = scheduledMessage.address,
                body = scheduledMessage.body,
                subscriptionId = subId,
                threadId = scheduledMessage.threadId
            )

            if (result.sent) {
                Log.d(TAG, "Scheduled SMS $scheduledId dispatched successfully")
                NotificationHelper.getInstance(appContext).cancelScheduledSmsFailureNotification(scheduledId)
                dbHelper.deleteScheduledMessage(scheduledId)
                cancel(appContext, scheduledId)
                true
            } else {
                Log.e(TAG, "Failed to send scheduled SMS $scheduledId (no cellular signal, airplane mode, or network failure)")
                dbHelper.updateScheduledMessageStatus(scheduledId, ScheduledMessageStatus.FAILED)

                val contactName = smsRepository.resolveContactDetail(scheduledMessage.address)?.name
                NotificationHelper.getInstance(appContext).showScheduledSmsFailureNotification(
                    threadId = scheduledMessage.threadId,
                    address = scheduledMessage.address,
                    body = scheduledMessage.body,
                    scheduledId = scheduledId,
                    contactName = contactName
                )
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing scheduled SMS dispatch for $scheduledId", e)
            try {
                val dbHelper = MisgaDatabaseHelper.getInstance(appContext)
                dbHelper.updateScheduledMessageStatus(scheduledId, ScheduledMessageStatus.FAILED)
                val scheduledMessage = dbHelper.getScheduledMessage(scheduledId)
                if (scheduledMessage != null) {
                    NotificationHelper.getInstance(appContext).showScheduledSmsFailureNotification(
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
            false
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing WakeLock for message $scheduledId", e)
            }
            activeDispatches.remove(scheduledId)
        }
    }

    suspend fun processOverdueMessages(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context
        val dbHelper = MisgaDatabaseHelper.getInstance(appContext)
        val pending = dbHelper.getPendingScheduledMessages()
        val now = System.currentTimeMillis()
        val overdue = pending.filter { it.scheduledTime <= now }
        if (overdue.isNotEmpty()) {
            Log.d(TAG, "Processing ${overdue.size} overdue scheduled messages")
            for (msg in overdue) {
                dispatchScheduledMessage(appContext, msg.id)
            }
        }
    }

    fun processOverdueMessagesAsync(context: Context) {
        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processOverdueMessages(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing overdue messages asynchronously", e)
            }
        }
    }

    suspend fun rescheduleAllPending(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context
        val dbHelper = MisgaDatabaseHelper.getInstance(appContext)
        val pending = dbHelper.getPendingScheduledMessages()
        Log.d(TAG, "Rescheduling ${pending.size} pending messages")
        val now = System.currentTimeMillis()
        var hasOverdue = false
        for (msg in pending) {
            if (msg.scheduledTime <= now) {
                hasOverdue = true
            } else {
                schedule(appContext, msg)
            }
        }
        if (hasOverdue) {
            processOverdueMessages(appContext)
        }
    }
}

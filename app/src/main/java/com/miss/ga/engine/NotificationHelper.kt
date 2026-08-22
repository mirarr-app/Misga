package com.miss.ga.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.miss.ga.MainActivity
import com.miss.ga.R
import com.miss.ga.data.model.FilterAction
import com.miss.ga.receiver.NotificationActionReceiver
import com.miss.ga.receiver.NotificationActions

class NotificationHelper private constructor(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 1. Alert Channel (Normal)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "SMS Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Standard notifications with sound and vibration for normal SMS"
                enableVibration(true)
                setShowBadge(true)
            }

            // 2. Silent Channel
            val silentChannel = NotificationChannel(
                CHANNEL_SILENT_ID,
                "Silent SMS Messages",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent incoming messages filtered without sound or vibration"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(silentChannel)
        }
    }

    fun showSmsNotification(
        threadId: Long,
        sender: String,
        contactName: String?,
        body: String,
        action: FilterAction,
        messageId: Long
    ) {
        // If action is SPAM, do NOT show any notification at all
        if (action == FilterAction.SPAM) {
            return
        }

        val channelId = if (action == FilterAction.NORMAL) CHANNEL_ALERT_ID else CHANNEL_SILENT_ID
        val unknownLabel = context.getString(R.string.unknown_sender)
        val displayName = IncomingSmsPolicy.displayName(contactName, sender, unknownLabel)
        val notificationId = notificationIdFor(threadId)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            setAction(Intent.ACTION_VIEW)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_ADDRESS, sender)
            putExtra(EXTRA_CONTACT_NAME, contactName)
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(displayName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(if (action == FilterAction.NORMAL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        buildActions(threadId, sender).forEach { builder.addAction(it) }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied", e)
        }
    }

    private fun buildActions(threadId: Long, sender: String): List<NotificationCompat.Action> {
        fun actionIntent(action: String): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(NotificationActions.EXTRA_THREAD_ID, threadId)
                putExtra(NotificationActions.EXTRA_ADDRESS, sender)
            }

        // RemoteInput results are injected into the intent, so it must stay mutable.
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            replyRequestCode(threadId),
            actionIntent(NotificationActions.ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(NotificationActions.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_action_reply))
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            context.getString(R.string.notification_action_reply),
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            markReadRequestCode(threadId),
            actionIntent(NotificationActions.ACTION_MARK_READ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_agenda,
            context.getString(R.string.notification_action_mark_read),
            markReadPendingIntent
        ).build()

        return listOf(replyAction, markReadAction)
    }

    private fun replyRequestCode(threadId: Long): Int = (threadId xor (threadId ushr 32)).toInt()
    private fun markReadRequestCode(threadId: Long): Int = (threadId xor (threadId ushr 32)).toInt() xor 0x4D524400.toInt()

    fun cancelNotification(threadId: Long) {
        notificationManager.cancel(notificationIdFor(threadId))
    }

    companion object {
        const val CHANNEL_ALERT_ID = "misga_sms_alert_channel"
        const val CHANNEL_SILENT_ID = "misga_sms_silent_channel"
        const val EXTRA_THREAD_ID = "EXTRA_THREAD_ID"
        const val EXTRA_ADDRESS = "EXTRA_ADDRESS"
        const val EXTRA_CONTACT_NAME = "EXTRA_CONTACT_NAME"
        const val EXTRA_MESSAGE_ID = "EXTRA_MESSAGE_ID"

        private const val TAG = "NotificationHelper"

        @Volatile
        private var INSTANCE: NotificationHelper? = null

        fun getInstance(context: Context): NotificationHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationHelper(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun notificationIdFor(threadId: Long): Int = (threadId xor (threadId ushr 32)).toInt()
    }
}

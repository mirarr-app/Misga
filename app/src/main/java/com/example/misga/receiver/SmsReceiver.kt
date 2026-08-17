package com.example.misga.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSmsMessage
import android.util.Log
import com.example.misga.data.db.MisgaDatabaseHelper
import com.example.misga.data.model.FilterAction
import com.example.misga.data.repository.SmsRepository
import com.example.misga.engine.NotificationHelper
import com.example.misga.engine.SmsFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processIncomingMessages(context, messages, action == Telephony.Sms.Intents.SMS_DELIVER_ACTION)
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processIncomingMessages(
        context: Context,
        messages: Array<AndroidSmsMessage>,
        isDefaultAppDeliver: Boolean
    ) {
        val dbHelper = MisgaDatabaseHelper.getInstance(context)
        val filterEngine = SmsFilterEngine(dbHelper)
        val notificationHelper = NotificationHelper(context)
        val smsRepository = SmsRepository(context)

        // Combine multipart messages by sender
        val messagesBySender = messages.groupBy { it.displayOriginatingAddress ?: "" }

        for ((sender, parts) in messagesBySender) {
            if (sender.isBlank()) continue

            val fullBody = parts.joinToString(separator = "") { it.displayMessageBody ?: "" }
            val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            // Run through the MISGA hierarchical filter engine
            val filterResult = filterEngine.evaluateMessage(sender, fullBody)

            var messageId: Long = System.currentTimeMillis()
            var threadId: Long = 0

            // If we are the default SMS app and received SMS_DELIVER, we must insert into Telephony provider
            if (isDefaultAppDeliver) {
                val cv = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, fullBody)
                    put(Telephony.Sms.DATE, timestamp)
                    put(Telephony.Sms.READ, if (filterResult.action == FilterAction.SPAM) 1 else 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                }

                try {
                    val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, cv)
                    uri?.lastPathSegment?.toLongOrNull()?.let { insertedId ->
                        messageId = insertedId
                    }

                    // Query thread ID
                    threadId = Telephony.Threads.getOrCreateThreadId(context, sender)
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error writing to Telephony provider", e)
                }
            }

            // Save filter metadata in MISGA's Room database
            dbHelper.markMessageSpam(
                messageId = messageId,
                address = sender,
                matchedRuleName = filterResult.matchedRuleName,
                action = filterResult.action
            )

            // Resolve contact name for notification display
            val contactName = smsRepository.resolveContactName(sender)

            // Dispatch notification (or suppress if SPAM)
            notificationHelper.showSmsNotification(
                threadId = threadId,
                sender = sender,
                contactName = contactName,
                body = fullBody,
                action = filterResult.action,
                messageId = messageId
            )
        }
    }
}

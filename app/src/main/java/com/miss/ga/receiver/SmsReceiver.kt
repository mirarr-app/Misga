package com.miss.ga.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSmsMessage
import android.util.Log
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.db.SpamMetaWrite
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.data.util.PhoneNumberKeys
import com.miss.ga.engine.NotificationHelper
import com.miss.ga.engine.SmsFilterEngine
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SmsReceiver"

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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Default SMS apps receive SMS_DELIVER. SMS_RECEIVED is only a fallback for
                // when we are not default; handling both would duplicate notifications.
                if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
                    SmsRepository(context).isDefaultSmsApp()
                ) {
                    return@launch
                }
                processIncomingMessages(context, messages, action == Telephony.Sms.Intents.SMS_DELIVER_ACTION)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                pendingResult.finish()
                scope.cancel()
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
        val notificationHelper = NotificationHelper.getInstance(context)
        val smsRepository = SmsRepository(context)

        // Combine multipart messages by sender
        val messagesBySender = messages.groupBy { it.displayOriginatingAddress ?: "" }
        val pendingMetaWrites = mutableListOf<SpamMetaWrite>()

        for ((sender, parts) in messagesBySender) {
            if (sender.isBlank()) continue

            val fullBody = parts.joinToString(separator = "") { it.displayMessageBody ?: "" }
            val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            // Run through the MISGA hierarchical filter engine
            val filterResult = filterEngine.evaluateMessage(sender, fullBody)

            var messageId: Long = System.currentTimeMillis()
            var threadId: Long = 0
            var shouldWriteMeta = false

            // If we are the default SMS app and received SMS_DELIVER, we must insert into Telephony provider
            if (isDefaultAppDeliver) {
                val cv = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, fullBody)
                    put(Telephony.Sms.DATE, timestamp)
                    put(Telephony.Sms.READ, if (filterResult.action == FilterAction.SPAM) 1 else 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    smsRepository.putDefaultSmsSubscription(this)
                }

                try {
                    val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, cv)
                    uri?.lastPathSegment?.toLongOrNull()?.let { insertedId ->
                        messageId = insertedId
                    }

                    // Query thread ID
                    threadId = Telephony.Threads.getOrCreateThreadId(context, sender)
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing to Telephony provider", e)
                }
                shouldWriteMeta = true
            } else {
                resolveInboxMessageId(context, sender, fullBody, timestamp)?.let { realId ->
                    messageId = realId
                    shouldWriteMeta = true
                }
            }

            // Ensure threadId is resolved
            if (threadId <= 0) {
                threadId = smsRepository.getOrCreateThreadId(sender)
            }

            if (shouldWriteMeta) {
                pendingMetaWrites.add(
                    SpamMetaWrite(
                        messageId = messageId,
                        address = sender,
                        matchedRuleName = filterResult.matchedRuleName,
                        action = filterResult.action
                    )
                )
            }

            // Only notify on SMS_DELIVER (we are default). SMS_RECEIVED is handled by the
            // default SMS app's own notification; we still resolve id and write filter meta.
            if (isDefaultAppDeliver) {
                val contactName = smsRepository.resolveContactName(sender)
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

        if (pendingMetaWrites.isNotEmpty()) {
            dbHelper.saveMessagesFilterMeta(pendingMetaWrites)
        }
    }

    private suspend fun resolveInboxMessageId(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ): Long? {
        repeat(INBOX_LOOKUP_ATTEMPTS) { attempt ->
            queryInboxMessageId(context, sender, body, timestamp)?.let { return it }
            if (attempt < INBOX_LOOKUP_ATTEMPTS - 1) {
                delay(INBOX_LOOKUP_DELAY_MS)
            }
        }
        return null
    }

    private fun queryInboxMessageId(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ): Long? {
        val minDate = timestamp - INBOX_LOOKUP_WINDOW_MS
        val maxDate = timestamp + INBOX_LOOKUP_WINDOW_MS
        return try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
                "${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf(body, minDate.toString(), maxDate.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                var bestId: Long? = null
                var bestDelta = Long.MAX_VALUE
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addrIdx) ?: continue
                    if (!addressesMatch(sender, address)) continue
                    val delta = abs(cursor.getLong(dateIdx) - timestamp)
                    if (delta < bestDelta) {
                        bestDelta = delta
                        bestId = cursor.getLong(idIdx)
                    }
                }
                bestId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve inbox message id", e)
            null
        }
    }

    private fun addressesMatch(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val keysA = PhoneNumberKeys.keys(a)
        val keysB = PhoneNumberKeys.keys(b)
        if (keysA.isNotEmpty() && keysB.isNotEmpty()) {
            return keysA.any { it in keysB }
        }
        return SmsFilterEngine.normalizeAddress(a).equals(
            SmsFilterEngine.normalizeAddress(b),
            ignoreCase = true
        )
    }

    companion object {
        private const val INBOX_LOOKUP_ATTEMPTS = 4
        private const val INBOX_LOOKUP_DELAY_MS = 400L
        private const val INBOX_LOOKUP_WINDOW_MS = 15_000L
    }
}

package com.miss.ga.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.miss.ga.data.util.PhoneNumberKeys
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

private const val TAG = "SmsSentReceiver"

/**
 * Receives per-part sent/delivery results for SMS dispatched by [com.miss.ga.data.repository.SmsRepository].
 *
 * Sent results complete the wait in the sending coroutine so the UI can report
 * genuine radio-level failures. Delivery reports update the provider STATUS column.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_SMS_SENT && action != ACTION_SMS_DELIVERED) return

        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val token = intent.getLongExtra(EXTRA_SEND_TOKEN, -1L)
        val partCount = intent.getIntExtra(EXTRA_PART_COUNT, 1)
        val ok = resultCode == Activity.RESULT_OK

        if (!ok && action == ACTION_SMS_SENT) {
            val error = when (resultCode) {
                android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure"
                android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off"
                android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU"
                else -> "code $resultCode"
            }
            Log.w(TAG, "SMS send failed ($error) for ${PhoneNumberKeys.redact(intent.getStringExtra(EXTRA_ADDRESS) ?: "")}")
        }

        if (messageId > 0) {
            updateStatus(context, messageId, action, ok)
        }

        if (action == ACTION_SMS_SENT && token != -1L) {
            SmsSendTracker.onPartResult(token, partCount, ok)
        }
    }

    private fun updateStatus(context: Context, messageId: Long, action: String, ok: Boolean) {
        try {
            val values = android.content.ContentValues().apply {
                when {
                    action == ACTION_SMS_SENT && !ok -> {
                        put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
                        put(Telephony.Sms.ERROR_CODE, resultCode)
                    }
                    action == ACTION_SMS_DELIVERED && ok ->
                        put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
                    action == ACTION_SMS_DELIVERED && !ok ->
                        put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
                    // Sent ack OK: leave STATUS_PENDING until a delivery report arrives.
                }
            }
            if (values.size() > 0) {
                context.contentResolver.update(
                    android.content.ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                    values,
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update send status for message $messageId", e)
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.miss.ga.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.miss.ga.ACTION_SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "EXTRA_MESSAGE_ID"
        const val EXTRA_SEND_TOKEN = "EXTRA_SEND_TOKEN"
        const val EXTRA_PART_COUNT = "EXTRA_PART_COUNT"
        const val EXTRA_ADDRESS = "EXTRA_ADDRESS"
    }
}

/**
 * Aggregates per-part sent results so a multipart send counts as confirmed
 * only when every part was accepted by the radio.
 */
object SmsSendTracker {

    private class State(partCount: Int) {
        val outstanding = AtomicInteger(partCount)
        val failed = AtomicInteger(0)
        val deferred = CompletableDeferred<Boolean>()
    }

    private val states = ConcurrentHashMap<Long, State>()

    fun register(token: Long, partCount: Int): CompletableDeferred<Boolean> {
        val state = State(partCount.coerceAtLeast(1))
        states[token] = state
        return state.deferred
    }

    fun onPartResult(token: Long, partCount: Int, ok: Boolean) {
        val state = states[token] ?: return
        if (!ok) state.failed.incrementAndGet()
        if (state.outstanding.decrementAndGet() <= 0) {
            states.remove(token)
            state.deferred.complete(state.failed.get() == 0)
        }
    }

    /** Abandon tracking (e.g. the radio call threw before intents were fired). */
    fun cancel(token: Long) {
        states.remove(token)?.deferred?.complete(false)
    }
}

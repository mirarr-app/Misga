package com.miss.ga.receiver

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.data.util.PhoneNumberKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Handles direct-reply and mark-as-read actions on message notifications.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(NotificationActions.EXTRA_THREAD_ID, -1L)
        if (threadId <= 0 && intent.action != NotificationActions.ACTION_REPLY) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    NotificationActions.ACTION_REPLY -> {
                        val address = intent.getStringExtra(NotificationActions.EXTRA_ADDRESS).orEmpty()
                        val replyBody = extractReplyText(intent)
                        if (address.isBlank() || replyBody == null) {
                            Log.w(TAG, "Direct reply ignored: missing ${PhoneNumberKeys.redact(address)} or body")
                            return@launch
                        }
                        SmsRepository(context).sendSms(address, replyBody)
                    }
                    NotificationActions.ACTION_MARK_READ -> Unit
                    else -> return@launch
                }
                if (threadId > 0) {
                    SmsRepository(context).markThreadRead(threadId)
                    NotificationActions.cancel(context, threadId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Notification action failed", e)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun extractReplyText(intent: Intent): String? {
        val bundle = RemoteInput.getResultsFromIntent(intent) ?: return null
        return bundle.getCharSequence(NotificationActions.KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "NotifActionReceiver"
    }
}

/** Holds action constants plus helpers shared with [com.miss.ga.engine.NotificationHelper]. */
object NotificationActions {
    const val ACTION_REPLY = "com.miss.ga.ACTION_NOTIFICATION_REPLY"
    const val ACTION_MARK_READ = "com.miss.ga.ACTION_NOTIFICATION_MARK_READ"
    const val KEY_REPLY_TEXT = "com.miss.ga.KEY_NOTIFICATION_REPLY_TEXT"
    const val EXTRA_THREAD_ID = "EXTRA_THREAD_ID"
    const val EXTRA_ADDRESS = "EXTRA_ADDRESS"

    fun cancel(context: Context, threadId: Long) {
        val notificationId = (threadId xor (threadId ushr 32)).toInt()
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(notificationId)
    }
}

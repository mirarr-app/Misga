package com.miss.ga.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    private val dbHelper = MisgaDatabaseHelper.getInstance(context)

    fun isDefaultSmsApp(): Boolean {
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    private val recipientAddressCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val contactNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getThreads(): List<ConversationThread> = withContext(Dispatchers.IO) {
        val threads = mutableListOf<ConversationThread>()
        val contentResolver = context.contentResolver

        val uri = Telephony.Threads.CONTENT_URI.buildUpon()
            .appendQueryParameter("simple", "true")
            .build()

        val projection = arrayOf(
            Telephony.Threads._ID,
            Telephony.Threads.DATE,
            Telephony.Threads.MESSAGE_COUNT,
            Telephony.Threads.RECIPIENT_IDS,
            Telephony.Threads.SNIPPET,
            Telephony.Threads.READ
        )

        try {
            val cursor = contentResolver.query(
                uri,
                projection,
                null, null,
                "${Telephony.Threads.DATE} DESC"
            )

            val spamMetaMap = dbHelper.getAllSpamMetaMap()

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Threads._ID)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Threads.DATE)
                val msgCountIdx = it.getColumnIndexOrThrow(Telephony.Threads.MESSAGE_COUNT)
                val recipientIdsIdx = it.getColumnIndexOrThrow(Telephony.Threads.RECIPIENT_IDS)
                val snippetIdx = it.getColumnIndexOrThrow(Telephony.Threads.SNIPPET)
                val readIdx = it.getColumnIndexOrThrow(Telephony.Threads.READ)

                while (it.moveToNext()) {
                    val threadId = it.getLong(idIdx)
                    val date = it.getLong(dateIdx)
                    val count = it.getInt(msgCountIdx)
                    val recipientIds = it.getString(recipientIdsIdx) ?: ""
                    val snippet = it.getString(snippetIdx) ?: ""
                    val read = it.getInt(readIdx) == 1

                    val address = recipientAddressCache.getOrPut(recipientIds) {
                        resolveRecipientAddress(recipientIds)
                    }
                    val contactName = contactNameCache.getOrPut(address) {
                        resolveContactName(address) ?: ""
                    }.ifBlank { null }

                    // Check unread count for this thread only if unread
                    val unreadCount = if (read) 0 else getUnreadCountForThread(threadId)

                    val normalizedAddr = dbHelper.normalizeAddress(address)
                    val hasSpam = spamMetaMap.values.any { meta ->
                        meta.first // isSpam
                    }

                    threads.add(
                        ConversationThread(
                            threadId = threadId,
                            address = address,
                            contactName = contactName,
                            snippet = snippet,
                            date = date,
                            messageCount = count,
                            unreadCount = unreadCount,
                            hasSpam = hasSpam
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error reading threads", e)
        }

        threads
    }

    suspend fun getMessagesForThread(threadId: Long, address: String): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()
        val contentResolver = context.contentResolver
        val uri = Telephony.Sms.CONTENT_URI

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )

        try {
            val cursor = contentResolver.query(
                uri,
                projection,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )

            val spamMeta = dbHelper.getSpamMetaMapForThread(address)
            val filterEngine = com.miss.ga.engine.SmsFilterEngine(dbHelper)

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    val msgId = it.getLong(idIdx)
                    val tId = it.getLong(threadIdIdx)
                    val addr = it.getString(addrIdx) ?: address
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    val read = it.getInt(readIdx) == 1

                    val meta = spamMeta[msgId]
                    val isSpam: Boolean
                    val matchedRule: String?
                    val isRevealed: Boolean

                    if (meta != null) {
                        isSpam = meta.first
                        matchedRule = meta.second
                        isRevealed = meta.third
                    } else {
                        if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                            val eval = filterEngine.evaluateMessage(addr, body)
                            isSpam = eval.action == FilterAction.SPAM
                            matchedRule = eval.matchedRuleName
                            isRevealed = false
                            if (isSpam) {
                                dbHelper.markMessageSpam(msgId, addr, matchedRule, eval.action)
                            }
                        } else {
                            isSpam = false
                            matchedRule = null
                            isRevealed = false
                        }
                    }

                    messages.add(
                        SmsMessage(
                            id = msgId,
                            threadId = tId,
                            address = addr,
                            body = body,
                            date = date,
                            type = type,
                            read = read,
                            isSpam = isSpam,
                            matchedRuleName = matchedRule,
                            isRevealed = isRevealed
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error reading messages for thread $threadId", e)
        }

        messages
    }

    suspend fun sendSms(address: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (address.isBlank() || body.isBlank()) return@withContext false

        try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
            }

            // If default app, save outgoing message into Telephony.Sms.Sent
            if (isDefaultSmsApp()) {
                val cv = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                }
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, cv)
            }
            true
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to send SMS to $address", e)
            false
        }
    }

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val cv = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                cv,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to mark thread read $threadId", e)
        }
    }

    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to delete message $messageId", e)
            false
        }
    }

    suspend fun revealSpamMessage(messageId: Long, isRevealed: Boolean) = withContext(Dispatchers.IO) {
        dbHelper.setMessageRevealed(messageId, isRevealed)
    }

    suspend fun markMessageNotSpam(messageId: Long) = withContext(Dispatchers.IO) {
        dbHelper.unmarkSpam(messageId)
    }

    private fun getUnreadCountForThread(threadId: Long): Int {
        var count = 0
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
                null
            )
            cursor?.use {
                count = it.count
            }
        } catch (e: Exception) {
            // Ignore
        }
        return count
    }

    private fun resolveRecipientAddress(recipientIds: String): String {
        if (recipientIds.isBlank()) return ""
        val ids = recipientIds.split(" ")
        for (id in ids) {
            if (id.isBlank()) continue
            try {
                val uri = Uri.parse("content://mms-sms/canonical-address/$id")
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        return it.getString(0) ?: ""
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }
        return recipientIds
    }

    suspend fun deleteThread(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete all messages associated with this thread
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
            // Delete thread record
            val threadUri = ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId)
            context.contentResolver.delete(threadUri, null, null)
            true
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to delete thread $threadId", e)
            false
        }
    }

    suspend fun searchContacts(query: String): List<ContactItem> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = if (query.isBlank()) null else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val selectionArgs = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val seen = mutableSetOf<String>()
                while (it.moveToNext() && contacts.size < 40) {
                    val name = it.getString(nameIdx) ?: ""
                    val number = it.getString(numIdx)?.replace(" ", "") ?: ""
                    if (number.isNotBlank() && seen.add(number)) {
                        contacts.add(ContactItem(name = name, number = number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error searching contacts", e)
        }
        contacts
    }

    suspend fun getOrCreateThreadId(address: String): Long = withContext(Dispatchers.IO) {
        try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            0L
        }
    }

    fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            // Ignore permission or query errors
        }
        return null
    }
}

data class ContactItem(
    val name: String,
    val number: String
)


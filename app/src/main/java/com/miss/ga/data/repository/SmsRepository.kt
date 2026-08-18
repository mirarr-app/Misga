package com.miss.ga.data.repository

import android.app.role.RoleManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.db.SpamMetaWrite
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.SearchMessageResult
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.data.util.PhoneNumberKeys
import com.miss.ga.engine.SmsFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    private val dbHelper = MisgaDatabaseHelper.getInstance(context)

    fun isDefaultSmsApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {
                return true
            }
        }
        return try {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun putDefaultSmsSubscription(values: ContentValues) {
        val subId = defaultSmsSubscriptionId()
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            values.put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
    }

    private fun defaultSmsSubscriptionId(): Int {
        return try {
            SmsManager.getDefaultSmsSubscriptionId()
        } catch (_: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    private fun smsManager(): SmsManager {
        val defaultManager = context.getSystemService(SmsManager::class.java)
            ?: SmsManager.getDefault()
        val subId = defaultSmsSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return defaultManager
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            defaultManager.createForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    suspend fun getCachedThreads(): List<ConversationThread> = dbHelper.getCachedThreads()

    suspend fun saveCachedThreads(threads: List<ConversationThread>) =
        dbHelper.replaceCachedThreads(threads)

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
            val canonicalAddresses = loadCanonicalAddressMap()
            val contactNames = loadContactNameMap()

            // Batch query all unread messages in a single fast query
            val unreadCounts = mutableMapOf<Long, Int>()
            val latestUnreadMessage = mutableMapOf<Long, Pair<Long, String>>() // threadId -> (msgId, body)
            try {
                val unreadCursor = contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.BODY),
                    "${Telephony.Sms.READ} = 0",
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
                unreadCursor?.use { uCursor ->
                    val idIdx = uCursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val tIdIdx = uCursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                    val bodyIdx = uCursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    while (uCursor.moveToNext()) {
                        val tId = uCursor.getLong(tIdIdx)
                        val msgId = uCursor.getLong(idIdx)
                        val body = uCursor.getString(bodyIdx) ?: ""
                        unreadCounts[tId] = (unreadCounts[tId] ?: 0) + 1
                        if (!latestUnreadMessage.containsKey(tId)) {
                            latestUnreadMessage[tId] = Pair(msgId, body)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsRepository", "Error querying unread batch", e)
            }

            val latestInboxMessage = mutableMapOf<Long, Pair<Long, String>>() // threadId -> (msgId, body)
            try {
                val inboxCursor = contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.BODY),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
                inboxCursor?.use { iCursor ->
                    val idIdx = iCursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val tIdIdx = iCursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                    val bodyIdx = iCursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    while (iCursor.moveToNext()) {
                        val tId = iCursor.getLong(tIdIdx)
                        if (!latestInboxMessage.containsKey(tId)) {
                            latestInboxMessage[tId] = Pair(
                                iCursor.getLong(idIdx),
                                iCursor.getString(bodyIdx) ?: ""
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsRepository", "Error querying latest inbox batch", e)
            }

            val spamMetaMap = dbHelper.getAllSpamMetaMap()
            val allRules = dbHelper.getAllRules()
            val preparedRules = SmsFilterEngine.prepareRules(allRules)
            val senderPrefs = dbHelper.getAllSenderPreferences()
            val pendingSpamMarks = mutableListOf<SpamMetaWrite>()
            val evaluatedActions = mutableMapOf<Long, FilterAction>()

            fun resolveInboxAction(msgId: Long, address: String, body: String): FilterAction {
                evaluatedActions[msgId]?.let { return it }
                val meta = spamMetaMap[msgId]
                val action = if (meta != null) {
                    if (meta.first) FilterAction.SPAM else FilterAction.NORMAL
                } else {
                    val pref = senderPrefs[dbHelper.normalizeAddress(address)]
                    val eval = SmsFilterEngine.evaluateMessage(address, body, preparedRules, pref)
                    if (eval.action == FilterAction.SPAM) {
                        pendingSpamMarks.add(
                            SpamMetaWrite(
                                messageId = msgId,
                                address = address,
                                matchedRuleName = eval.matchedRuleName,
                                action = eval.action
                            )
                        )
                    }
                    eval.action
                }
                evaluatedActions[msgId] = action
                return action
            }

            val cursor = contentResolver.query(
                uri,
                projection,
                null, null,
                "${Telephony.Threads.DATE} DESC"
            )

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

                    val address = resolveRecipientAddress(recipientIds, canonicalAddresses)
                    val contactName = PhoneNumberKeys.lookup(contactNames, address)

                    // Fast unread count from pre-fetched batch
                    val unreadCount = if (read) 0 else (unreadCounts[threadId] ?: 0)
                    val lastInbox = latestInboxMessage[threadId]
                    val lastMessageAction = if (lastInbox != null) {
                        resolveInboxAction(lastInbox.first, address, lastInbox.second)
                    } else {
                        FilterAction.NORMAL
                    }
                    val isUnreadSpam = unreadCount > 0 && latestUnreadMessage[threadId]?.let { latest ->
                        resolveInboxAction(latest.first, address, latest.second) == FilterAction.SPAM
                    } == true

                    threads.add(
                        ConversationThread(
                            threadId = threadId,
                            address = address,
                            contactName = contactName,
                            snippet = snippet,
                            date = date,
                            messageCount = count,
                            unreadCount = unreadCount,
                            hasSpam = false,
                            isUnreadSpam = isUnreadSpam,
                            lastMessageAction = lastMessageAction
                        )
                    )
                }
            }

            if (pendingSpamMarks.isNotEmpty()) {
                dbHelper.markMessagesSpam(pendingSpamMarks)
            }
            if (cursor != null) {
                dbHelper.replaceCachedThreads(threads)
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
            val allRules = dbHelper.getAllRules()
            val preparedRules = SmsFilterEngine.prepareRules(allRules)
            val senderPref = dbHelper.getSenderPreference(address)
            val pendingSpamMarks = mutableListOf<SpamMetaWrite>()

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
                            val eval = SmsFilterEngine.evaluateMessage(addr, body, preparedRules, senderPref)
                            isSpam = eval.action == FilterAction.SPAM
                            matchedRule = eval.matchedRuleName
                            isRevealed = false
                            if (isSpam) {
                                pendingSpamMarks.add(
                                    SpamMetaWrite(
                                        messageId = msgId,
                                        address = addr,
                                        matchedRuleName = matchedRule,
                                        action = eval.action
                                    )
                                )
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

            if (pendingSpamMarks.isNotEmpty()) {
                dbHelper.markMessagesSpam(pendingSpamMarks)
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error reading messages for thread $threadId", e)
        }

        messages
    }

    suspend fun sendSms(address: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (address.isBlank() || body.isBlank()) return@withContext false

        try {
            val smsManager = smsManager()

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
                    putDefaultSmsSubscription(this)
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

    suspend fun markThreadsRead(threadIds: Collection<Long>) = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext
        try {
            val cv = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            val placeholders = threadIds.joinToString(",") { "?" }
            val args = threadIds.map { it.toString() }.toTypedArray()
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                cv,
                "${Telephony.Sms.THREAD_ID} IN ($placeholders) AND ${Telephony.Sms.READ} = 0",
                args
            )
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to mark threads read $threadIds", e)
        }
    }

    suspend fun markAllMessagesRead() = withContext(Dispatchers.IO) {
        try {
            val cv = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                cv,
                "${Telephony.Sms.READ} = 0",
                null
            )
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to mark all messages read", e)
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

    private fun loadCanonicalAddressMap(): Map<String, String> {
        val cached = canonicalCache
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.loadedAt < LOOKUP_CACHE_TTL_MS) {
            return cached.values
        }
        val map = HashMap<String, String>()
        try {
            val uri = Uri.parse("content://mms-sms/canonical-addresses")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("_id", "address"),
                null, null, null
            )
            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val addrIdx = it.getColumnIndex("address")
                if (idIdx < 0 || addrIdx < 0) return map
                while (it.moveToNext()) {
                    val id = it.getString(idIdx) ?: continue
                    map[id] = it.getString(addrIdx) ?: ""
                }
            }
        } catch (e: Exception) {
            Log.w("SmsRepository", "Batch canonical-address query failed, falling back", e)
        }
        canonicalCache = CachedLookupMap(now, map)
        return map
    }

    private fun loadContactNameMap(): Map<String, String> {
        val cached = contactCache
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.loadedAt < LOOKUP_CACHE_TTL_MS) {
            return cached.values
        }
        val map = HashMap<String, String>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )
            cursor?.use {
                val nameIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: continue
                    val number = it.getString(numIdx) ?: continue
                    if (name.isBlank() || number.isBlank()) continue
                    for (key in PhoneNumberKeys.keys(number)) {
                        map.putIfAbsent(key, name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SmsRepository", "Batch contact query failed, falling back", e)
        }
        contactCache = CachedLookupMap(now, map)
        return map
    }

    private fun resolveRecipientAddress(
        recipientIds: String,
        canonicalAddresses: Map<String, String> = emptyMap()
    ): String {
        if (recipientIds.isBlank()) return ""
        val ids = recipientIds.split(" ")
        for (id in ids) {
            if (id.isBlank()) continue
            val cached = canonicalAddresses[id]
            if (!cached.isNullOrBlank()) return cached
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

    suspend fun deleteThreads(threadIds: Collection<Long>): Boolean = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext false
        var allSuccess = true
        for (id in threadIds) {
            val ok = deleteThread(id)
            if (!ok) allSuccess = false
        }
        allSuccess
    }

    suspend fun searchAllMessages(query: String): List<SearchMessageResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<SearchMessageResult>()
        val contentResolver = context.contentResolver

        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )

        val trimmedQuery = query.trim()
        val selection = "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf("%$trimmedQuery%", "%$trimmedQuery%")

        try {
            val spamMetaMap = dbHelper.getAllSpamMetaMap()
            val contactNames = loadContactNameMap()
            val cursor = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIdx = it.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (it.moveToNext() && results.size < 250) {
                    val msgId = it.getLong(idIdx)
                    var threadId = it.getLong(threadIdIdx)
                    val rawAddress = it.getString(addrIdx) ?: ""
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val read = it.getInt(readIdx) == 1
                    val type = it.getInt(typeIdx)

                    val address = rawAddress
                    if (threadId <= 0 && address.isNotBlank()) {
                        threadId = Telephony.Threads.getOrCreateThreadId(context, address)
                    }

                    val contactName = PhoneNumberKeys.lookup(contactNames, address)

                    val isSpam = spamMetaMap[msgId]?.first ?: false

                    results.add(
                        SearchMessageResult(
                            messageId = msgId,
                            threadId = threadId,
                            address = address,
                            contactName = contactName,
                            body = body,
                            date = date,
                            read = read,
                            type = type,
                            isSpam = isSpam
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error searching all messages for query: $query", e)
        }

        results
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

private data class CachedLookupMap(
    val loadedAt: Long,
    val values: Map<String, String>
)

private const val LOOKUP_CACHE_TTL_MS = 60_000L

@Volatile
private var canonicalCache: CachedLookupMap? = null

@Volatile
private var contactCache: CachedLookupMap? = null


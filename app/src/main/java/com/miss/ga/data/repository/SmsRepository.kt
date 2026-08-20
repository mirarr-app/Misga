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
import com.miss.ga.data.model.SenderPreference
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.data.util.PhoneNumberKeys
import com.miss.ga.engine.FilterRulesCache
import com.miss.ga.engine.IncomingSmsPolicy
import com.miss.ga.engine.NotificationHelper
import com.miss.ga.engine.PreparedFilterRules
import com.miss.ga.engine.SmsFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SmsRepository"

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

    suspend fun getCachedThreads(): List<ConversationThread> =
        dbHelper.getCachedThreads().filterNot { thread ->
            IncomingSmsPolicy.isGhostConversation(thread.address, thread.snippet)
        }

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
            val threadRows = mutableListOf<ThreadProviderRow>()
            val threadIds = mutableSetOf<Long>()
            var threadsCursorOk = false

            val cursor = contentResolver.query(
                uri,
                projection,
                null, null,
                "${Telephony.Threads.DATE} DESC"
            )
            cursor?.use {
                threadsCursorOk = true
                val idIdx = it.getColumnIndexOrThrow(Telephony.Threads._ID)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Threads.DATE)
                val msgCountIdx = it.getColumnIndexOrThrow(Telephony.Threads.MESSAGE_COUNT)
                val recipientIdsIdx = it.getColumnIndexOrThrow(Telephony.Threads.RECIPIENT_IDS)
                val snippetIdx = it.getColumnIndexOrThrow(Telephony.Threads.SNIPPET)
                val readIdx = it.getColumnIndexOrThrow(Telephony.Threads.READ)

                while (it.moveToNext()) {
                    val threadId = it.getLong(idIdx)
                    threadIds.add(threadId)
                    threadRows.add(
                        ThreadProviderRow(
                            threadId = threadId,
                            date = it.getLong(dateIdx),
                            messageCount = it.getInt(msgCountIdx),
                            recipientIds = it.getString(recipientIdsIdx) ?: "",
                            snippet = it.getString(snippetIdx) ?: "",
                            read = it.getInt(readIdx) == 1
                        )
                    )
                }
            }

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
                Log.e(TAG, "Error querying unread batch", e)
            }

            val latestInboxMessage = mutableMapOf<Long, Pair<Long, String>>() // threadId -> (msgId, body)
            val remainingInboxThreadIds = threadIds.toMutableSet()
            try {
                if (remainingInboxThreadIds.isNotEmpty()) {
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
                        while (iCursor.moveToNext() && remainingInboxThreadIds.isNotEmpty()) {
                            val tId = iCursor.getLong(tIdIdx)
                            if (remainingInboxThreadIds.remove(tId)) {
                                latestInboxMessage[tId] = Pair(
                                    iCursor.getLong(idIdx),
                                    iCursor.getString(bodyIdx) ?: ""
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying latest inbox batch", e)
            }

            val metaIds = LinkedHashSet<Long>()
            latestInboxMessage.values.forEach { metaIds.add(it.first) }
            latestUnreadMessage.values.forEach { metaIds.add(it.first) }
            val spamMetaMap = dbHelper.getSpamMetaMapForIds(metaIds)
            val pendingSpamMarks = mutableListOf<SpamMetaWrite>()
            val evaluatedActions = mutableMapOf<Long, FilterAction>()

            val needsFilterEval =
                latestInboxMessage.values.any { it.first !in spamMetaMap } ||
                    latestUnreadMessage.values.any { it.first !in spamMetaMap }

            var preparedRules: PreparedFilterRules? = null
            var senderPrefs = emptyMap<String, SenderPreference>()
            if (needsFilterEval) {
                val cache = FilterRulesCache.getInstance(dbHelper)
                preparedRules = cache.preparedRules()
                senderPrefs = cache.senderPreferences()
            }

            fun resolveInboxAction(msgId: Long, address: String, body: String): FilterAction {
                evaluatedActions[msgId]?.let { return it }
                val meta = spamMetaMap[msgId]
                val action = if (meta != null) {
                    meta.action
                } else {
                    val prepared = preparedRules
                    if (prepared == null) {
                        FilterAction.NORMAL
                    } else {
                        val pref = senderPrefs[dbHelper.normalizeAddress(address)]
                        val eval = SmsFilterEngine.evaluateMessage(address, body, prepared, pref)
                        if (eval.action != FilterAction.NORMAL) {
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
                }
                evaluatedActions[msgId] = action
                return action
            }

            for (row in threadRows) {
                val threadId = row.threadId
                val address = resolveRecipientAddress(row.recipientIds, canonicalAddresses)
                val contactName = PhoneNumberKeys.lookup(contactNames, address)

                val unreadCount = unreadCounts[threadId] ?: 0
                val lastInbox = latestInboxMessage[threadId]
                val lastMessageAction = if (lastInbox != null) {
                    resolveInboxAction(lastInbox.first, address, lastInbox.second)
                } else {
                    FilterAction.NORMAL
                }
                val isUnreadSpam = unreadCount > 0 && latestUnreadMessage[threadId]?.let { latest ->
                    resolveInboxAction(latest.first, address, latest.second) == FilterAction.SPAM
                } == true

                if (IncomingSmsPolicy.isGhostConversation(address, row.snippet)) {
                    continue
                }

                threads.add(
                    ConversationThread(
                        threadId = threadId,
                        address = address,
                        contactName = contactName,
                        snippet = row.snippet,
                        date = row.date,
                        messageCount = row.messageCount,
                        unreadCount = unreadCount,
                        hasSpam = lastMessageAction == FilterAction.SPAM || isUnreadSpam,
                        isUnreadSpam = isUnreadSpam,
                        lastMessageAction = lastMessageAction
                    )
                )
            }

            if (pendingSpamMarks.isNotEmpty()) {
                dbHelper.saveMessagesFilterMeta(pendingSpamMarks)
            }
            if (threadsCursorOk) {
                dbHelper.replaceCachedThreads(threads)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading threads", e)
        }

        threads
    }

    suspend fun getMessagesForThread(
        threadId: Long,
        address: String,
        beforeDate: Long? = null,
        limit: Int = 100
    ): List<SmsMessage> = withContext(Dispatchers.IO) {
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

        val selection: String
        val selectionArgs: Array<String>
        if (beforeDate != null) {
            selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.DATE} < ?"
            selectionArgs = arrayOf(threadId.toString(), beforeDate.toString())
        } else {
            selection = "${Telephony.Sms.THREAD_ID} = ?"
            selectionArgs = arrayOf(threadId.toString())
        }

        try {
            val cursor = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            val spamMeta = dbHelper.getSpamMetaMapForThread(address)
            val cache = FilterRulesCache.getInstance(dbHelper)
            val preparedRules = cache.preparedRules()
            val senderPref = cache.senderPreference(address)
            val pendingSpamMarks = mutableListOf<SpamMetaWrite>()

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (it.moveToNext() && messages.size < limit) {
                    val msgId = it.getLong(idIdx)
                    val tId = it.getLong(threadIdIdx)
                    val addr = it.getString(addrIdx) ?: address
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    val read = it.getInt(readIdx) == 1

                    if (IncomingSmsPolicy.isGhostConversation(addr, body)) {
                        continue
                    }

                    val meta = spamMeta[msgId]
                    val isSpam: Boolean
                    val matchedRule: String?
                    val isRevealed: Boolean

                    if (meta != null) {
                        isSpam = meta.action == FilterAction.SPAM
                        matchedRule = meta.matchedRuleName
                        isRevealed = meta.isRevealed
                    } else {
                        if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                            val eval = SmsFilterEngine.evaluateMessage(addr, body, preparedRules, senderPref)
                            isSpam = eval.action == FilterAction.SPAM
                            matchedRule = eval.matchedRuleName
                            isRevealed = false
                            if (eval.action != FilterAction.NORMAL) {
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
                dbHelper.saveMessagesFilterMeta(pendingSpamMarks)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading messages for thread $threadId", e)
        }

        messages.reverse()
        messages
    }

    suspend fun sendSms(address: String, body: String): SendSmsResult = withContext(Dispatchers.IO) {
        if (address.isBlank() || body.isBlank()) {
            return@withContext SendSmsResult(sent = false, storedInProvider = false)
        }

        try {
            val smsManager = smsManager()
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to ${PhoneNumberKeys.redact(address)}", e)
            return@withContext SendSmsResult(sent = false, storedInProvider = false)
        }

        val storedInProvider = try {
            val cv = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                putDefaultSmsSubscription(this)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, cv) != null
        } catch (e: Exception) {
            Log.w(TAG, "Sent SMS but could not store in provider for ${PhoneNumberKeys.redact(address)}", e)
            false
        }
        SendSmsResult(sent = true, storedInProvider = storedInProvider)
    }

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        markThreadsRead(listOf(threadId))
    }

    suspend fun markThreadsRead(threadIds: Collection<Long>) = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext
        val ids = threadIds.distinct()
        val values = readAndSeenValues()
        for (threadId in ids) {
            try {
                val convUri = ContentUris.withAppendedId(
                    Telephony.MmsSms.CONTENT_CONVERSATIONS_URI,
                    threadId
                )
                context.contentResolver.update(convUri, values, "${Telephony.Sms.READ} = 0", null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark conversation read $threadId", e)
            }
        }
        try {
            val placeholders = ids.joinToString(",") { "?" }
            val args = ids.map { it.toString() }.toTypedArray()
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} IN ($placeholders) AND ${Telephony.Sms.READ} = 0",
                args
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark threads read $ids", e)
        }
        val notificationHelper = NotificationHelper.getInstance(context)
        ids.forEach { notificationHelper.cancelNotification(it) }
    }

    suspend fun markAllMessagesRead() = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                readAndSeenValues(),
                "${Telephony.Sms.READ} = 0",
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark all messages read", e)
        }
    }

    private fun readAndSeenValues(): ContentValues {
        return ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
    }

    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                dbHelper.deleteSpamMetaForMessageIds(listOf(messageId))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete message $messageId", e)
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
            Log.w(TAG, "Batch canonical-address query failed, falling back", e)
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
            Log.w(TAG, "Batch contact query failed, falling back", e)
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
                Log.w(TAG, "canonical-address lookup failed", e)
            }
        }
        return recipientIds
    }

    suspend fun deleteThread(threadId: Long): Boolean = deleteThreads(listOf(threadId))

    suspend fun deleteThreads(threadIds: Collection<Long>): Boolean = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext false
        val ids = threadIds.distinct()
        val cachedAddresses = dbHelper.getCachedThreadAddresses(ids)
        val addresses = LinkedHashSet<String>()
        for (id in ids) {
            val cached = cachedAddresses[id]?.takeIf { it.isNotBlank() }
            val address = cached ?: lookupSmsAddressForThread(id)
            if (address.isNotBlank()) addresses.add(address)
        }

        var smsOk = true
        try {
            for (chunk in ids.chunked(SQLITE_IN_CHUNK_SIZE)) {
                val placeholders = chunk.joinToString(",") { "?" }
                val args = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} IN ($placeholders)",
                    args
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete SMS for threads $ids", e)
            smsOk = false
        }

        for (id in ids) {
            try {
                val threadUri = ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, id)
                context.contentResolver.delete(threadUri, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete thread record $id", e)
            }
        }

        for (address in addresses) {
            try {
                dbHelper.deleteSpamMetaForAddress(address)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete spam meta for ${PhoneNumberKeys.redact(address)}", e)
            }
        }

        smsOk
    }

    private fun lookupSmsAddressForThread(threadId: Long): String {
        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use {
                val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                if (addrIdx >= 0 && it.moveToFirst()) {
                    it.getString(addrIdx) ?: ""
                } else {
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Could not look up address for thread $threadId", e)
            ""
        }
    }

    suspend fun searchAllMessages(query: String): List<SearchMessageResult> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) return@withContext emptyList()
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

        val selection = "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf("%$trimmedQuery%", "%$trimmedQuery%")

        try {
            val contactNames = loadContactNameMap()
            val cursor = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            val hits = mutableListOf<SearchHit>()
            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIdx = it.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (it.moveToNext() && hits.size < 250) {
                    var threadId = it.getLong(threadIdIdx)
                    val address = it.getString(addrIdx) ?: ""
                    val body = it.getString(bodyIdx) ?: ""
                    if (IncomingSmsPolicy.isGhostConversation(address, body)) {
                        continue
                    }
                    if (threadId <= 0 && address.isNotBlank()) {
                        threadId = Telephony.Threads.getOrCreateThreadId(context, address)
                    }
                    hits.add(
                        SearchHit(
                            messageId = it.getLong(idIdx),
                            threadId = threadId,
                            address = address,
                            body = body,
                            date = it.getLong(dateIdx),
                            read = it.getInt(readIdx) == 1,
                            type = it.getInt(typeIdx)
                        )
                    )
                }
            }

            val spamMetaMap = dbHelper.getSpamMetaMapForIds(hits.map { it.messageId })
            val pendingSpamMarks = mutableListOf<SpamMetaWrite>()
            val needsFilterEval = hits.any { hit ->
                hit.type == Telephony.Sms.MESSAGE_TYPE_INBOX && hit.messageId !in spamMetaMap
            }

            var preparedRules: PreparedFilterRules? = null
            var senderPrefs = emptyMap<String, SenderPreference>()
            if (needsFilterEval) {
                val cache = FilterRulesCache.getInstance(dbHelper)
                preparedRules = cache.preparedRules()
                senderPrefs = cache.senderPreferences()
            }

            for (hit in hits) {
                val meta = spamMetaMap[hit.messageId]
                val isSpam = if (meta != null) {
                    meta.action == FilterAction.SPAM
                } else if (hit.type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    val prepared = preparedRules
                    if (prepared == null) {
                        false
                    } else {
                        val pref = senderPrefs[dbHelper.normalizeAddress(hit.address)]
                        val eval = SmsFilterEngine.evaluateMessage(hit.address, hit.body, prepared, pref)
                        if (eval.action != FilterAction.NORMAL) {
                            pendingSpamMarks.add(
                                SpamMetaWrite(
                                    messageId = hit.messageId,
                                    address = hit.address,
                                    matchedRuleName = eval.matchedRuleName,
                                    action = eval.action
                                )
                            )
                        }
                        eval.action == FilterAction.SPAM
                    }
                } else {
                    false
                }

                results.add(
                    SearchMessageResult(
                        messageId = hit.messageId,
                        threadId = hit.threadId,
                        address = hit.address,
                        contactName = PhoneNumberKeys.lookup(contactNames, hit.address),
                        body = hit.body,
                        date = hit.date,
                        read = hit.read,
                        type = hit.type,
                        isSpam = isSpam
                    )
                )
            }

            if (pendingSpamMarks.isNotEmpty()) {
                dbHelper.saveMessagesFilterMeta(pendingSpamMarks)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching all messages for query: $query", e)
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
            Log.e(TAG, "Error searching contacts", e)
        }
        contacts
    }

    suspend fun getOrCreateThreadId(address: String): Long = withContext(Dispatchers.IO) {
        try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            Log.w(TAG, "getOrCreateThreadId failed", e)
            0L
        }
    }

    fun invalidateLookupCaches() {
        canonicalCache = null
        contactCache = null
    }

    fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        PhoneNumberKeys.lookup(loadContactNameMap(), phoneNumber)?.let { return it }
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
            Log.w(TAG, "PhoneLookup query failed", e)
        }
        return null
    }
}

data class SendSmsResult(
    val sent: Boolean,
    val storedInProvider: Boolean
)

data class ContactItem(
    val name: String,
    val number: String
)

private data class ThreadProviderRow(
    val threadId: Long,
    val date: Long,
    val messageCount: Int,
    val recipientIds: String,
    val snippet: String,
    val read: Boolean
)

private data class CachedLookupMap(
    val loadedAt: Long,
    val values: Map<String, String>
)

private data class SearchHit(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int
)

private const val LOOKUP_CACHE_TTL_MS = 60_000L
private const val SQLITE_IN_CHUNK_SIZE = 500

@Volatile
private var canonicalCache: CachedLookupMap? = null

@Volatile
private var contactCache: CachedLookupMap? = null


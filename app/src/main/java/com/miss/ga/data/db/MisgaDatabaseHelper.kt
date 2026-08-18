package com.miss.ga.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import com.miss.ga.data.model.PredefinedRules
import com.miss.ga.data.model.RuleCategory
import com.miss.ga.data.model.SenderPreference
import com.miss.ga.data.util.PhoneNumberKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MisgaDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _rulesChanged = MutableStateFlow(System.currentTimeMillis())
    val rulesChanged: Flow<Long> = _rulesChanged.asStateFlow()

    private val _prefsChanged = MutableStateFlow(System.currentTimeMillis())
    val prefsChanged: Flow<Long> = _prefsChanged.asStateFlow()

    private val _spamMetaChanged = MutableStateFlow(System.currentTimeMillis())
    val spamMetaChanged: Flow<Long> = _spamMetaChanged.asStateFlow()

    @Volatile
    private var predefinedRulesSeeded = false

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS filter_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                pattern TEXT NOT NULL,
                is_regex INTEGER NOT NULL DEFAULT 1,
                action TEXT NOT NULL DEFAULT 'SPAM',
                list_type TEXT NOT NULL DEFAULT 'BLOCKLIST',
                is_enabled INTEGER NOT NULL DEFAULT 1,
                is_predefined INTEGER NOT NULL DEFAULT 0,
                category TEXT NOT NULL DEFAULT 'CUSTOM',
                sender_target TEXT,
                description TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sender_preferences (
                address TEXT PRIMARY KEY,
                display_name TEXT,
                default_action TEXT NOT NULL DEFAULT 'NORMAL',
                custom_sound_uri TEXT,
                is_blocked INTEGER NOT NULL DEFAULT 0,
                notes TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS spam_message_meta (
                message_id INTEGER PRIMARY KEY,
                address TEXT NOT NULL,
                matched_rule_name TEXT,
                is_revealed INTEGER NOT NULL DEFAULT 0,
                is_spam INTEGER NOT NULL DEFAULT 1,
                action TEXT NOT NULL DEFAULT 'SPAM',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS predefined_rule_settings (
                rule_id INTEGER PRIMARY KEY,
                name TEXT,
                pattern TEXT,
                is_regex INTEGER,
                action TEXT NOT NULL,
                list_type TEXT NOT NULL DEFAULT 'BLOCKLIST',
                is_enabled INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                description TEXT,
                is_customized INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        createCachedThreadsTable(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_spam_meta_address ON spam_message_meta(address)")

        seedPredefinedRules(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (predefinedRulesSeeded) return
        seedPredefinedRules(db)
        predefinedRulesSeeded = true
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE filter_rules ADD COLUMN list_type TEXT DEFAULT 'BLOCKLIST'") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE predefined_rule_settings ADD COLUMN name TEXT") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE predefined_rule_settings ADD COLUMN pattern TEXT") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE predefined_rule_settings ADD COLUMN is_regex INTEGER DEFAULT 1") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE predefined_rule_settings ADD COLUMN list_type TEXT DEFAULT 'BLOCKLIST'") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE predefined_rule_settings ADD COLUMN is_deleted INTEGER DEFAULT 0") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE predefined_rule_settings ADD COLUMN description TEXT") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE predefined_rule_settings ADD COLUMN is_customized INTEGER DEFAULT 0") } catch (e: Exception) {}
            createCachedThreadsTable(db)
            try { db.execSQL("ALTER TABLE cached_threads ADD COLUMN last_message_action TEXT DEFAULT 'NORMAL'") } catch (e: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_spam_meta_address ON spam_message_meta(address)") } catch (e: Exception) {}
        }
    }

    // --- Filter Rules Operations ---

    suspend fun getAllRules(): List<FilterRule> = withContext(Dispatchers.IO) {
        val rules = mutableListOf<FilterRule>()

        // 1. Get predefined rules with overridden states
        val predefined = PredefinedRules.getDefaultRules().toMutableList()
        val db = readableDatabase
        val overrides = mutableMapOf<Long, FilterRuleOverride>()

        try {
            val cursor = db.query(
                "predefined_rule_settings",
                arrayOf("rule_id", "is_enabled", "action", "name", "pattern", "is_regex", "list_type", "is_deleted", "description"),
                null, null, null, null, null
            )
            cursor.use {
                val idIdx = it.getColumnIndex("rule_id")
                val enabledIdx = it.getColumnIndex("is_enabled")
                val actionIdx = it.getColumnIndex("action")
                val nameIdx = it.getColumnIndex("name")
                val patternIdx = it.getColumnIndex("pattern")
                val isRegexIdx = it.getColumnIndex("is_regex")
                val listTypeIdx = it.getColumnIndex("list_type")
                val isDeletedIdx = it.getColumnIndex("is_deleted")
                val descIdx = it.getColumnIndex("description")

                while (it.moveToNext()) {
                    val rId = it.getLong(idIdx)
                    val enabled = it.getInt(enabledIdx) == 1
                    val act = try { FilterAction.valueOf(it.getString(actionIdx)) } catch (e: Exception) { FilterAction.SPAM }
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val pattern = if (patternIdx >= 0) it.getString(patternIdx) else null
                    val isRegex = if (isRegexIdx >= 0 && !it.isNull(isRegexIdx)) it.getInt(isRegexIdx) == 1 else true
                    val listType = if (listTypeIdx >= 0 && !it.isNull(listTypeIdx)) {
                        try { com.miss.ga.data.model.RuleListType.valueOf(it.getString(listTypeIdx)) } catch (e: Exception) { null }
                    } else null
                    val isDeleted = if (isDeletedIdx >= 0 && !it.isNull(isDeletedIdx)) it.getInt(isDeletedIdx) == 1 else false
                    val desc = if (descIdx >= 0) it.getString(descIdx) else null

                    overrides[rId] = FilterRuleOverride(
                        isEnabled = enabled,
                        action = act,
                        name = name,
                        pattern = pattern,
                        isRegex = isRegex,
                        listType = listType,
                        isDeleted = isDeleted,
                        description = desc
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback for earlier database tables
        }

        predefined.forEach { rule ->
            val ov = overrides[rule.id]
            if (ov != null) {
                if (!ov.isDeleted) {
                    rules.add(
                        rule.copy(
                            name = ov.name ?: rule.name,
                            pattern = ov.pattern ?: rule.pattern,
                            isRegex = ov.isRegex,
                            isEnabled = ov.isEnabled,
                            action = ov.action,
                            listType = ov.listType ?: rule.listType,
                            description = ov.description ?: rule.description
                        )
                    )
                }
            } else {
                rules.add(rule)
            }
        }

        // 2. Get custom user rules
        val userCursor = db.query(
            "filter_rules",
            null,
            null, null, null, null,
            "created_at DESC"
        )
        userCursor.use {
            while (it.moveToNext()) {
                rules.add(cursorToFilterRule(it))
            }
        }

        rules
    }

    suspend fun getSenderRules(address: String): List<FilterRule> = withContext(Dispatchers.IO) {
        val normalized = normalizeAddress(address)
        val rules = mutableListOf<FilterRule>()
        val cursor = readableDatabase.query(
            "filter_rules",
            null,
            "sender_target IS NOT NULL AND sender_target != ''",
            null, null, null,
            "created_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val rule = cursorToFilterRule(it)
                val target = rule.senderTarget ?: continue
                if (normalizeAddress(target) == normalized) {
                    rules.add(rule)
                }
            }
        }
        rules
    }

    suspend fun insertCustomRule(rule: FilterRule): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("name", rule.name)
            put("pattern", rule.pattern)
            put("is_regex", if (rule.isRegex) 1 else 0)
            put("action", rule.action.name)
            put("list_type", rule.listType.name)
            put("is_enabled", if (rule.isEnabled) 1 else 0)
            put("is_predefined", 0)
            put("category", rule.category.name)
            put("sender_target", rule.senderTarget?.let { normalizeAddress(it) })
            put("description", rule.description)
            put("created_at", System.currentTimeMillis())
        }
        val id = writableDatabase.insert("filter_rules", null, cv)
        _rulesChanged.value = System.currentTimeMillis()
        id
    }

    suspend fun updateRule(rule: FilterRule): Boolean = withContext(Dispatchers.IO) {
        if (rule.isPredefined || rule.id < 0) {
            val cv = ContentValues().apply {
                put("rule_id", rule.id)
                put("name", rule.name)
                put("pattern", rule.pattern)
                put("is_regex", if (rule.isRegex) 1 else 0)
                put("action", rule.action.name)
                put("list_type", rule.listType.name)
                put("is_enabled", if (rule.isEnabled) 1 else 0)
                put("is_deleted", 0)
                put("description", rule.description)
                put("is_customized", 1)
            }
            val rows = writableDatabase.insertWithOnConflict(
                "predefined_rule_settings",
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            _rulesChanged.value = System.currentTimeMillis()
            rows > 0
        } else {
            val cv = ContentValues().apply {
                put("name", rule.name)
                put("pattern", rule.pattern)
                put("is_regex", if (rule.isRegex) 1 else 0)
                put("action", rule.action.name)
                put("list_type", rule.listType.name)
                put("is_enabled", if (rule.isEnabled) 1 else 0)
                put("category", rule.category.name)
                put("sender_target", rule.senderTarget?.let { normalizeAddress(it) })
                put("description", rule.description)
            }
            val rows = writableDatabase.update(
                "filter_rules",
                cv,
                "id = ?",
                arrayOf(rule.id.toString())
            )
            _rulesChanged.value = System.currentTimeMillis()
            rows > 0
        }
    }

    suspend fun deleteRule(ruleId: Long): Boolean = withContext(Dispatchers.IO) {
        val db = writableDatabase
        if (ruleId < 0) {
            val flags = ContentValues().apply {
                put("is_deleted", 1)
                put("is_enabled", 0)
            }
            val ok = updateOrInsertPredefinedSettings(db, ruleId, flags)
            _rulesChanged.value = System.currentTimeMillis()
            ok
        } else {
            val rows = db.delete("filter_rules", "id = ?", arrayOf(ruleId.toString()))
            _rulesChanged.value = System.currentTimeMillis()
            rows > 0
        }
    }

    suspend fun setRuleEnabled(ruleId: Long, isEnabled: Boolean, isPredefined: Boolean) = withContext(Dispatchers.IO) {
        if (isPredefined || ruleId < 0) {
            val flags = ContentValues().apply {
                put("is_enabled", if (isEnabled) 1 else 0)
            }
            updateOrInsertPredefinedSettings(writableDatabase, ruleId, flags)
        } else {
            val cv = ContentValues().apply {
                put("is_enabled", if (isEnabled) 1 else 0)
            }
            writableDatabase.update("filter_rules", cv, "id = ?", arrayOf(ruleId.toString()))
        }
        _rulesChanged.value = System.currentTimeMillis()
    }

    // --- Sender Preferences Operations ---

    suspend fun getSenderPreference(address: String): SenderPreference? = withContext(Dispatchers.IO) {
        val variants = addressLookupKeys(address)
        val placeholders = variants.joinToString(",") { "?" }
        val cursor = readableDatabase.query(
            "sender_preferences",
            null,
            "address IN ($placeholders)",
            variants,
            null, null,
            "updated_at DESC"
        )
        cursor.use {
            if (it.moveToFirst()) cursorToSenderPreference(it) else null
        }
    }

    suspend fun getAllSenderPreferences(): Map<String, SenderPreference> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, SenderPreference>()
        val cursor = readableDatabase.query(
            "sender_preferences",
            null,
            null, null, null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                val pref = cursorToSenderPreference(it)
                val key = PhoneNumberKeys.canonical(pref.address)
                val existing = result[key]
                if (existing == null || pref.updatedAt >= existing.updatedAt) {
                    result[key] = pref
                }
            }
        }
        result
    }

    suspend fun saveSenderPreference(pref: SenderPreference) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("address", normalizeAddress(pref.address))
            put("display_name", pref.displayName)
            put("default_action", pref.defaultAction.name)
            put("custom_sound_uri", pref.customSoundUri)
            put("is_blocked", if (pref.isBlocked) 1 else 0)
            put("notes", pref.notes)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "sender_preferences",
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        _prefsChanged.value = System.currentTimeMillis()
    }

    // --- Spam Metadata Operations ---

    suspend fun markMessageSpam(
        messageId: Long,
        address: String,
        matchedRuleName: String?,
        action: FilterAction
    ) = withContext(Dispatchers.IO) {
        markMessagesSpam(
            listOf(
                SpamMetaWrite(
                    messageId = messageId,
                    address = address,
                    matchedRuleName = matchedRuleName,
                    action = action
                )
            )
        )
    }

    suspend fun markMessagesSpam(entries: List<SpamMetaWrite>) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (entry in entries) {
                val cv = ContentValues().apply {
                    put("message_id", entry.messageId)
                    put("address", normalizeAddress(entry.address))
                    put("matched_rule_name", entry.matchedRuleName)
                    put("is_revealed", 0)
                    put("is_spam", if (entry.action == FilterAction.SPAM) 1 else 0)
                    put("action", entry.action.name)
                    put("created_at", now)
                }
                db.insertWithOnConflict(
                    "spam_message_meta",
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _spamMetaChanged.value = now
    }

    suspend fun setMessageRevealed(messageId: Long, isRevealed: Boolean) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("is_revealed", if (isRevealed) 1 else 0)
        }
        writableDatabase.update("spam_message_meta", cv, "message_id = ?", arrayOf(messageId.toString()))
        _spamMetaChanged.value = System.currentTimeMillis()
    }

    suspend fun unmarkSpam(messageId: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("is_spam", 0)
            put("action", FilterAction.NORMAL.name)
            put("is_revealed", 1)
        }
        writableDatabase.update("spam_message_meta", cv, "message_id = ?", arrayOf(messageId.toString()))
        _spamMetaChanged.value = System.currentTimeMillis()
    }

    suspend fun getSpamMetaMapForThread(address: String): Map<Long, SpamMessageMeta> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Long, SpamMessageMeta>()
            val variants = addressLookupKeys(address)
            val placeholders = variants.joinToString(",") { "?" }
            val cursor = readableDatabase.query(
                "spam_message_meta",
                arrayOf("message_id", "is_spam", "matched_rule_name", "is_revealed", "action"),
                "address IN ($placeholders)",
                variants,
                null, null, null
            )
            cursor.use {
                while (it.moveToNext()) {
                    result[it.getLong(0)] = cursorToSpamMessageMeta(it)
                }
            }
            result
        }

    suspend fun getAllSpamMetaMap(): Map<Long, SpamMessageMeta> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Long, SpamMessageMeta>()
        val cursor = readableDatabase.query(
            "spam_message_meta",
            arrayOf("message_id", "is_spam", "matched_rule_name", "is_revealed", "action"),
            null, null, null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                result[it.getLong(0)] = cursorToSpamMessageMeta(it)
            }
        }
        result
    }

    // --- Cached conversation list ---

    suspend fun getCachedThreads(): List<ConversationThread> = withContext(Dispatchers.IO) {
        val threads = mutableListOf<ConversationThread>()
        val cursor = readableDatabase.query(
            "cached_threads",
            null,
            null, null, null, null,
            "date DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow("thread_id")
            val addrIdx = it.getColumnIndexOrThrow("address")
            val nameIdx = it.getColumnIndexOrThrow("contact_name")
            val snippetIdx = it.getColumnIndexOrThrow("snippet")
            val dateIdx = it.getColumnIndexOrThrow("date")
            val countIdx = it.getColumnIndexOrThrow("message_count")
            val unreadIdx = it.getColumnIndexOrThrow("unread_count")
            val spamIdx = it.getColumnIndexOrThrow("is_unread_spam")
            val hasSpamIdx = it.getColumnIndexOrThrow("has_spam")
            val actionIdx = it.getColumnIndex("last_message_action")
            while (it.moveToNext()) {
                val contactName = if (it.isNull(nameIdx)) null else it.getString(nameIdx)
                val lastMessageAction = if (actionIdx >= 0 && !it.isNull(actionIdx)) {
                    try {
                        FilterAction.valueOf(it.getString(actionIdx))
                    } catch (e: Exception) {
                        FilterAction.NORMAL
                    }
                } else {
                    FilterAction.NORMAL
                }
                threads.add(
                    ConversationThread(
                        threadId = it.getLong(idIdx),
                        address = it.getString(addrIdx) ?: "",
                        contactName = contactName?.ifBlank { null },
                        snippet = it.getString(snippetIdx) ?: "",
                        date = it.getLong(dateIdx),
                        messageCount = it.getInt(countIdx),
                        unreadCount = it.getInt(unreadIdx),
                        hasSpam = it.getInt(hasSpamIdx) == 1,
                        isUnreadSpam = it.getInt(spamIdx) == 1,
                        lastMessageAction = lastMessageAction
                    )
                )
            }
        }
        threads
    }

    suspend fun replaceCachedThreads(threads: List<ConversationThread>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cached_threads", null, null)
            for (thread in threads) {
                val cv = ContentValues().apply {
                    put("thread_id", thread.threadId)
                    put("address", thread.address)
                    put("contact_name", thread.contactName)
                    put("snippet", thread.snippet)
                    put("date", thread.date)
                    put("message_count", thread.messageCount)
                    put("unread_count", thread.unreadCount)
                    put("is_unread_spam", if (thread.isUnreadSpam) 1 else 0)
                    put("has_spam", if (thread.hasSpam) 1 else 0)
                    put("last_message_action", thread.lastMessageAction.name)
                }
                db.insert("cached_threads", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedPredefinedRules(db: SQLiteDatabase) {
        PredefinedRules.getDefaultRules().forEach { rule ->
            val cv = ContentValues().apply {
                put("rule_id", rule.id)
                put("name", rule.name)
                put("pattern", rule.pattern)
                put("is_regex", if (rule.isRegex) 1 else 0)
                put("action", rule.action.name)
                put("list_type", rule.listType.name)
                put("is_enabled", if (rule.isEnabled) 1 else 0)
                put("is_deleted", 0)
                put("description", rule.description)
                put("is_customized", 0)
            }
            db.insertWithOnConflict("predefined_rule_settings", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            val shipped = ContentValues().apply {
                put("name", rule.name)
                put("pattern", rule.pattern)
                put("is_regex", if (rule.isRegex) 1 else 0)
                put("list_type", rule.listType.name)
                put("description", rule.description)
            }
            db.update(
                "predefined_rule_settings",
                shipped,
                "rule_id = ? AND IFNULL(is_deleted, 0) = 0 AND IFNULL(is_customized, 0) = 0",
                arrayOf(rule.id.toString())
            )
        }
    }

    private fun createCachedThreadsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_threads (
                thread_id INTEGER PRIMARY KEY,
                address TEXT NOT NULL,
                contact_name TEXT,
                snippet TEXT,
                date INTEGER NOT NULL,
                message_count INTEGER NOT NULL DEFAULT 0,
                unread_count INTEGER NOT NULL DEFAULT 0,
                is_unread_spam INTEGER NOT NULL DEFAULT 0,
                has_spam INTEGER NOT NULL DEFAULT 0,
                last_message_action TEXT NOT NULL DEFAULT 'NORMAL'
            )
            """.trimIndent()
        )
    }

    private fun cursorToSenderPreference(cursor: Cursor): SenderPreference {
        return SenderPreference(
            address = cursor.getString(cursor.getColumnIndexOrThrow("address")),
            displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
            defaultAction = try {
                FilterAction.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("default_action")))
            } catch (e: Exception) {
                FilterAction.NORMAL
            },
            customSoundUri = cursor.getString(cursor.getColumnIndexOrThrow("custom_sound_uri")),
            isBlocked = cursor.getInt(cursor.getColumnIndexOrThrow("is_blocked")) == 1,
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")) ?: "",
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun cursorToFilterRule(cursor: Cursor): FilterRule {
        val listTypeIdx = cursor.getColumnIndex("list_type")
        val categoryStr = cursor.getString(cursor.getColumnIndexOrThrow("category"))
        val category = try {
            RuleCategory.valueOf(categoryStr)
        } catch (e: Exception) {
            RuleCategory.CUSTOM
        }

        val listType = if (listTypeIdx >= 0 && !cursor.isNull(listTypeIdx)) {
            try {
                com.miss.ga.data.model.RuleListType.valueOf(cursor.getString(listTypeIdx))
            } catch (e: Exception) {
                if (category == RuleCategory.OTP_ALLOWLIST || category == RuleCategory.CUSTOM_ALLOWLIST) {
                    com.miss.ga.data.model.RuleListType.ALLOWLIST
                } else {
                    com.miss.ga.data.model.RuleListType.BLOCKLIST
                }
            }
        } else {
            if (category == RuleCategory.OTP_ALLOWLIST || category == RuleCategory.CUSTOM_ALLOWLIST) {
                com.miss.ga.data.model.RuleListType.ALLOWLIST
            } else {
                com.miss.ga.data.model.RuleListType.BLOCKLIST
            }
        }

        return FilterRule(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            pattern = cursor.getString(cursor.getColumnIndexOrThrow("pattern")),
            isRegex = cursor.getInt(cursor.getColumnIndexOrThrow("is_regex")) == 1,
            action = try {
                FilterAction.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("action")))
            } catch (e: Exception) {
                FilterAction.SPAM
            },
            listType = listType,
            isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("is_enabled")) == 1,
            isPredefined = cursor.getInt(cursor.getColumnIndexOrThrow("is_predefined")) == 1,
            category = category,
            senderTarget = cursor.getString(cursor.getColumnIndexOrThrow("sender_target")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    fun normalizeAddress(address: String): String {
        return PhoneNumberKeys.canonical(address)
    }

    private fun addressLookupKeys(address: String): Array<String> {
        val keys = LinkedHashSet<String>()
        keys.add(normalizeAddress(address))
        keys.addAll(PhoneNumberKeys.keys(address))
        return keys.toTypedArray()
    }

    private fun updateOrInsertPredefinedSettings(
        db: SQLiteDatabase,
        ruleId: Long,
        flags: ContentValues
    ): Boolean {
        val updated = db.update(
            "predefined_rule_settings",
            flags,
            "rule_id = ?",
            arrayOf(ruleId.toString())
        )
        if (updated > 0) return true
        val defaults = PredefinedRules.getDefaultRules().find { it.id == ruleId } ?: return false
        val cv = ContentValues().apply {
            put("rule_id", defaults.id)
            put("name", defaults.name)
            put("pattern", defaults.pattern)
            put("is_regex", if (defaults.isRegex) 1 else 0)
            put("action", defaults.action.name)
            put("list_type", defaults.listType.name)
            put("is_enabled", if (defaults.isEnabled) 1 else 0)
            put("is_deleted", 0)
            put("description", defaults.description)
            put("is_customized", 0)
            putAll(flags)
        }
        return db.insert("predefined_rule_settings", null, cv) != -1L
    }

    private fun cursorToSpamMessageMeta(cursor: Cursor): SpamMessageMeta {
        val isSpam = cursor.getInt(1) == 1
        val rule = if (!cursor.isNull(2)) cursor.getString(2) else null
        val isRevealed = cursor.getInt(3) == 1
        val action = try {
            FilterAction.valueOf(cursor.getString(4))
        } catch (e: Exception) {
            if (isSpam) FilterAction.SPAM else FilterAction.NORMAL
        }
        return SpamMessageMeta(
            isSpam = isSpam,
            matchedRuleName = rule,
            isRevealed = isRevealed,
            action = action
        )
    }

    companion object {
        private const val DATABASE_NAME = "misga_filters.db"
        private const val DATABASE_VERSION = 2

        @Volatile
        private var INSTANCE: MisgaDatabaseHelper? = null

        fun getInstance(context: Context): MisgaDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MisgaDatabaseHelper(context).also { INSTANCE = it }
            }
        }
    }
}

data class SpamMetaWrite(
    val messageId: Long,
    val address: String,
    val matchedRuleName: String?,
    val action: FilterAction
)

data class SpamMessageMeta(
    val isSpam: Boolean,
    val matchedRuleName: String?,
    val isRevealed: Boolean,
    val action: FilterAction
)

data class FilterRuleOverride(
    val isEnabled: Boolean,
    val action: FilterAction,
    val name: String?,
    val pattern: String?,
    val isRegex: Boolean,
    val listType: com.miss.ga.data.model.RuleListType? = null,
    val isDeleted: Boolean,
    val description: String?
)


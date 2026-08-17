package com.example.misga.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.misga.data.model.FilterAction
import com.example.misga.data.model.FilterRule
import com.example.misga.data.model.PredefinedRules
import com.example.misga.data.model.RuleCategory
import com.example.misga.data.model.SenderPreference
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

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS filter_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                pattern TEXT NOT NULL,
                is_regex INTEGER NOT NULL DEFAULT 1,
                action TEXT NOT NULL DEFAULT 'SPAM',
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
                is_enabled INTEGER NOT NULL,
                action TEXT NOT NULL
            )
            """.trimIndent()
        )

        // Prepopulate default predefined rule settings
        PredefinedRules.getDefaultRules().forEach { rule ->
            val cv = ContentValues().apply {
                put("rule_id", rule.id)
                put("is_enabled", if (rule.isEnabled) 1 else 0)
                put("action", rule.action.name)
            }
            db.insertWithOnConflict("predefined_rule_settings", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migration steps
    }

    // --- Filter Rules Operations ---

    suspend fun getAllRules(): List<FilterRule> = withContext(Dispatchers.IO) {
        val rules = mutableListOf<FilterRule>()

        // 1. Get predefined rules with overridden states
        val predefined = PredefinedRules.getDefaultRules().toMutableList()
        val db = readableDatabase
        val cursor = db.query(
            "predefined_rule_settings",
            arrayOf("rule_id", "is_enabled", "action"),
            null, null, null, null, null
        )
        val overrides = mutableMapOf<Long, Pair<Boolean, FilterAction>>()
        cursor.use {
            while (it.moveToNext()) {
                val rId = it.getLong(0)
                val enabled = it.getInt(1) == 1
                val act = try { FilterAction.valueOf(it.getString(2)) } catch (e: Exception) { FilterAction.SPAM }
                overrides[rId] = Pair(enabled, act)
            }
        }

        predefined.forEach { rule ->
            val ov = overrides[rule.id]
            if (ov != null) {
                rules.add(rule.copy(isEnabled = ov.first, action = ov.second))
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

    suspend fun insertCustomRule(rule: FilterRule): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("name", rule.name)
            put("pattern", rule.pattern)
            put("is_regex", if (rule.isRegex) 1 else 0)
            put("action", rule.action.name)
            put("is_enabled", if (rule.isEnabled) 1 else 0)
            put("is_predefined", 0)
            put("category", rule.category.name)
            put("sender_target", rule.senderTarget)
            put("description", rule.description)
            put("created_at", System.currentTimeMillis())
        }
        val id = writableDatabase.insert("filter_rules", null, cv)
        _rulesChanged.value = System.currentTimeMillis()
        id
    }

    suspend fun updateCustomRule(rule: FilterRule): Boolean = withContext(Dispatchers.IO) {
        if (rule.isPredefined) {
            // Update predefined rule override
            val cv = ContentValues().apply {
                put("rule_id", rule.id)
                put("is_enabled", if (rule.isEnabled) 1 else 0)
                put("action", rule.action.name)
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
                put("is_enabled", if (rule.isEnabled) 1 else 0)
                put("category", rule.category.name)
                put("sender_target", rule.senderTarget)
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
        if (ruleId < 0) {
            // Predefined rules cannot be deleted, but disabled
            false
        } else {
            val rows = writableDatabase.delete("filter_rules", "id = ?", arrayOf(ruleId.toString()))
            _rulesChanged.value = System.currentTimeMillis()
            rows > 0
        }
    }

    suspend fun setRuleEnabled(ruleId: Long, isEnabled: Boolean, isPredefined: Boolean) = withContext(Dispatchers.IO) {
        if (isPredefined) {
            val cv = ContentValues().apply {
                put("rule_id", ruleId)
                put("is_enabled", if (isEnabled) 1 else 0)
                put("action", FilterAction.SPAM.name)
            }
            writableDatabase.insertWithOnConflict(
                "predefined_rule_settings",
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
            )
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
        val db = readableDatabase
        val cursor = db.query(
            "sender_preferences",
            null,
            "address = ?",
            arrayOf(normalizeAddress(address)),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                SenderPreference(
                    address = it.getString(it.getColumnIndexOrThrow("address")),
                    displayName = it.getString(it.getColumnIndexOrThrow("display_name")),
                    defaultAction = try {
                        FilterAction.valueOf(it.getString(it.getColumnIndexOrThrow("default_action")))
                    } catch (e: Exception) {
                        FilterAction.NORMAL
                    },
                    customSoundUri = it.getString(it.getColumnIndexOrThrow("custom_sound_uri")),
                    isBlocked = it.getInt(it.getColumnIndexOrThrow("is_blocked")) == 1,
                    notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                    updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
                )
            } else null
        }
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
        val cv = ContentValues().apply {
            put("message_id", messageId)
            put("address", normalizeAddress(address))
            put("matched_rule_name", matchedRuleName)
            put("is_revealed", 0)
            put("is_spam", if (action == FilterAction.SPAM) 1 else 0)
            put("action", action.name)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "spam_message_meta",
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        _spamMetaChanged.value = System.currentTimeMillis()
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

    suspend fun getSpamMetaMapForThread(address: String): Map<Long, Triple<Boolean, String?, Boolean>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Long, Triple<Boolean, String?, Boolean>>()
            val cursor = readableDatabase.query(
                "spam_message_meta",
                arrayOf("message_id", "is_spam", "matched_rule_name", "is_revealed"),
                "address = ?",
                arrayOf(normalizeAddress(address)),
                null, null, null
            )
            cursor.use {
                while (it.moveToNext()) {
                    val msgId = it.getLong(0)
                    val isSpam = it.getInt(1) == 1
                    val rule = if (!it.isNull(2)) it.getString(2) else null
                    val isRevealed = it.getInt(3) == 1
                    result[msgId] = Triple(isSpam, rule, isRevealed)
                }
            }
            result
        }

    suspend fun getAllSpamMetaMap(): Map<Long, Triple<Boolean, String?, Boolean>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Long, Triple<Boolean, String?, Boolean>>()
        val cursor = readableDatabase.query(
            "spam_message_meta",
            arrayOf("message_id", "is_spam", "matched_rule_name", "is_revealed"),
            null, null, null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                val msgId = it.getLong(0)
                val isSpam = it.getInt(1) == 1
                val rule = if (!it.isNull(2)) it.getString(2) else null
                val isRevealed = it.getInt(3) == 1
                result[msgId] = Triple(isSpam, rule, isRevealed)
            }
        }
        result
    }

    private fun cursorToFilterRule(cursor: Cursor): FilterRule {
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
            isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("is_enabled")) == 1,
            isPredefined = cursor.getInt(cursor.getColumnIndexOrThrow("is_predefined")) == 1,
            category = try {
                RuleCategory.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("category")))
            } catch (e: Exception) {
                RuleCategory.CUSTOM
            },
            senderTarget = cursor.getString(cursor.getColumnIndexOrThrow("sender_target")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    fun normalizeAddress(address: String): String {
        return address.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
    }

    companion object {
        private const val DATABASE_NAME = "misga_filters.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: MisgaDatabaseHelper? = null

        fun getInstance(context: Context): MisgaDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MisgaDatabaseHelper(context).also { INSTANCE = it }
            }
        }
    }
}

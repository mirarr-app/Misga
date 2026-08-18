package com.miss.ga.engine

import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.SenderPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.IdentityHashMap

class FilterRulesCache private constructor(private val dbHelper: MisgaDatabaseHelper) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var collectorsStarted = false

    @Volatile
    private var rulesDirty = true

    @Volatile
    private var prefsDirty = true

    private var cachedPrepared: PreparedFilterRules? = null
    private var cachedPrefs: Map<String, SenderPreference>? = null

    private fun ensureCollectorsStarted() {
        if (collectorsStarted) return
        synchronized(this) {
            if (collectorsStarted) return
            collectorsStarted = true
            scope.launch {
                dbHelper.rulesChanged.collect { rulesDirty = true }
            }
            scope.launch {
                dbHelper.prefsChanged.collect { prefsDirty = true }
            }
        }
    }

    suspend fun preparedRules(): PreparedFilterRules {
        ensureCollectorsStarted()
        return mutex.withLock {
            var cached = cachedPrepared
            if (cached != null && !rulesDirty) return@withLock cached
            do {
                rulesDirty = false
                cached = SmsFilterEngine.prepareRules(dbHelper.getAllRules())
                cachedPrepared = cached
            } while (rulesDirty)
            cached!!
        }
    }

    suspend fun senderPreferences(): Map<String, SenderPreference> {
        ensureCollectorsStarted()
        return mutex.withLock {
            var cached = cachedPrefs
            if (cached != null && !prefsDirty) return@withLock cached
            do {
                prefsDirty = false
                cached = dbHelper.getAllSenderPreferences()
                cachedPrefs = cached
            } while (prefsDirty)
            cached!!
        }
    }

    suspend fun senderPreference(address: String): SenderPreference? {
        val prefs = senderPreferences()
        return prefs[dbHelper.normalizeAddress(address)]
    }

    companion object {
        private val caches = IdentityHashMap<MisgaDatabaseHelper, FilterRulesCache>()

        fun getInstance(dbHelper: MisgaDatabaseHelper): FilterRulesCache {
            synchronized(caches) {
                return caches.getOrPut(dbHelper) { FilterRulesCache(dbHelper) }
            }
        }
    }
}

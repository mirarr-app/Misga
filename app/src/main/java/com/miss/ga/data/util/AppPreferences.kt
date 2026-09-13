package com.miss.ga.data.util

import android.content.Context
import android.content.SharedPreferences

interface UserPreferences {
    var showContactsOnly: Boolean
}

class AppPreferences(
    private val prefs: SharedPreferences
) : UserPreferences {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override var showContactsOnly: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CONTACTS_ONLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_CONTACTS_ONLY, value).apply()
        }

    companion object {
        const val PREFS_NAME = "misga_preferences"
        const val KEY_SHOW_CONTACTS_ONLY = "show_contacts_only"
    }
}

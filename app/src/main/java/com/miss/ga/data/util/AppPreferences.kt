package com.miss.ga.data.util

import android.content.Context
import android.content.SharedPreferences

interface UserPreferences {
    var showContactsOnly: Boolean
    var showShamsiDate: Boolean
    var enableDateTapShamsiToggle: Boolean
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

    override var showShamsiDate: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SHAMSI_DATE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_SHAMSI_DATE, value).apply()
        }

    override var enableDateTapShamsiToggle: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_DATE_TAP_SHAMSI_TOGGLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLE_DATE_TAP_SHAMSI_TOGGLE, value).apply()
        }

    companion object {
        const val PREFS_NAME = "misga_preferences"
        const val KEY_SHOW_CONTACTS_ONLY = "show_contacts_only"
        const val KEY_SHOW_SHAMSI_DATE = "show_shamsi_date"
        const val KEY_ENABLE_DATE_TAP_SHAMSI_TOGGLE = "enable_date_tap_shamsi_toggle"
    }
}

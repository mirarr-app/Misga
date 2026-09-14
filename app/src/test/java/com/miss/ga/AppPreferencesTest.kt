package com.miss.ga

import android.content.SharedPreferences
import com.miss.ga.data.util.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class AppPreferencesTest {

    private class InMemorySharedPreferences : InvocationHandler {
        val storage = mutableMapOf<String, Any>()

        val proxy: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
            this
        ) as SharedPreferences

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            return when (method.name) {
                "getBoolean" -> {
                    val key = args!![0] as String
                    val defValue = args[1] as Boolean
                    storage[key] as? Boolean ?: defValue
                }
                "edit" -> createEditorProxy()
                else -> throw UnsupportedOperationException("Method ${method.name} not implemented in test stub")
            }
        }

        private fun createEditorProxy(): SharedPreferences.Editor {
            val pendingChanges = mutableMapOf<String, Any>()
            lateinit var editorProxy: SharedPreferences.Editor
            val editorHandler = InvocationHandler { _, editorMethod, editorArgs ->
                when (editorMethod.name) {
                    "putBoolean" -> {
                        val key = editorArgs!![0] as String
                        val value = editorArgs[1] as Boolean
                        pendingChanges[key] = value
                        editorProxy
                    }
                    "apply", "commit" -> {
                        storage.putAll(pendingChanges)
                        if (editorMethod.name == "commit") true else null
                    }
                    else -> throw UnsupportedOperationException("Editor method ${editorMethod.name} not implemented in test stub")
                }
            }
            editorProxy = Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
                editorHandler
            ) as SharedPreferences.Editor
            return editorProxy
        }
    }

    @Test
    fun defaultShowContactsOnlyIsFalse() {
        val fakePrefs = InMemorySharedPreferences()
        val appPreferences = AppPreferences(fakePrefs.proxy)

        assertFalse(appPreferences.showContactsOnly)
    }

    @Test
    fun showContactsOnlyPersistsWhenSet() {
        val fakePrefs = InMemorySharedPreferences()
        val appPreferences = AppPreferences(fakePrefs.proxy)

        appPreferences.showContactsOnly = true
        assertTrue(appPreferences.showContactsOnly)
        assertEquals(true, fakePrefs.storage[AppPreferences.KEY_SHOW_CONTACTS_ONLY])

        appPreferences.showContactsOnly = false
        assertFalse(appPreferences.showContactsOnly)
        assertEquals(false, fakePrefs.storage[AppPreferences.KEY_SHOW_CONTACTS_ONLY])
    }

    @Test
    fun showContactsOnlyInitializesFromExistingStoredValue() {
        val fakePrefs = InMemorySharedPreferences()
        fakePrefs.storage[AppPreferences.KEY_SHOW_CONTACTS_ONLY] = true

        val appPreferences = AppPreferences(fakePrefs.proxy)
        assertTrue(appPreferences.showContactsOnly)
    }

    @Test
    fun defaultShowShamsiDateIsFalse() {
        val fakePrefs = InMemorySharedPreferences()
        val appPreferences = AppPreferences(fakePrefs.proxy)

        assertFalse(appPreferences.showShamsiDate)
    }

    @Test
    fun showShamsiDatePersistsWhenSet() {
        val fakePrefs = InMemorySharedPreferences()
        val appPreferences = AppPreferences(fakePrefs.proxy)

        appPreferences.showShamsiDate = true
        assertTrue(appPreferences.showShamsiDate)
        assertEquals(true, fakePrefs.storage[AppPreferences.KEY_SHOW_SHAMSI_DATE])

        appPreferences.showShamsiDate = false
        assertFalse(appPreferences.showShamsiDate)
        assertEquals(false, fakePrefs.storage[AppPreferences.KEY_SHOW_SHAMSI_DATE])
    }

    @Test
    fun showShamsiDateInitializesFromExistingStoredValue() {
        val fakePrefs = InMemorySharedPreferences()
        fakePrefs.storage[AppPreferences.KEY_SHOW_SHAMSI_DATE] = true

        val appPreferences = AppPreferences(fakePrefs.proxy)
        assertTrue(appPreferences.showShamsiDate)
    }

    @Test
    fun defaultEnableDateTapShamsiToggleIsTrue() {
        val fakePrefs = InMemorySharedPreferences()
        val appPreferences = AppPreferences(fakePrefs.proxy)

        assertTrue(appPreferences.enableDateTapShamsiToggle)
    }

    @Test
    fun enableDateTapShamsiTogglePersistsWhenSet() {
        val fakePrefs = InMemorySharedPreferences()
        val appPreferences = AppPreferences(fakePrefs.proxy)

        appPreferences.enableDateTapShamsiToggle = false
        assertFalse(appPreferences.enableDateTapShamsiToggle)
        assertEquals(false, fakePrefs.storage[AppPreferences.KEY_ENABLE_DATE_TAP_SHAMSI_TOGGLE])

        appPreferences.enableDateTapShamsiToggle = true
        assertTrue(appPreferences.enableDateTapShamsiToggle)
        assertEquals(true, fakePrefs.storage[AppPreferences.KEY_ENABLE_DATE_TAP_SHAMSI_TOGGLE])
    }

    @Test
    fun enableDateTapShamsiToggleInitializesFromExistingStoredValue() {
        val fakePrefs = InMemorySharedPreferences()
        fakePrefs.storage[AppPreferences.KEY_ENABLE_DATE_TAP_SHAMSI_TOGGLE] = false

        val appPreferences = AppPreferences(fakePrefs.proxy)
        assertFalse(appPreferences.enableDateTapShamsiToggle)
    }

    @Test
    fun defaultShowNotificationDeleteActionIsFalse() {
        val fakePrefs = InMemorySharedPreferences()
        val appPreferences = AppPreferences(fakePrefs.proxy)

        assertFalse(appPreferences.showNotificationDeleteAction)
    }

    @Test
    fun showNotificationDeleteActionPersistsWhenSet() {
        val fakePrefs = InMemorySharedPreferences()
        val appPreferences = AppPreferences(fakePrefs.proxy)

        appPreferences.showNotificationDeleteAction = true
        assertTrue(appPreferences.showNotificationDeleteAction)
        assertEquals(true, fakePrefs.storage[AppPreferences.KEY_SHOW_NOTIFICATION_DELETE_ACTION])

        appPreferences.showNotificationDeleteAction = false
        assertFalse(appPreferences.showNotificationDeleteAction)
        assertEquals(false, fakePrefs.storage[AppPreferences.KEY_SHOW_NOTIFICATION_DELETE_ACTION])
    }

    @Test
    fun showNotificationDeleteActionInitializesFromExistingStoredValue() {
        val fakePrefs = InMemorySharedPreferences()
        fakePrefs.storage[AppPreferences.KEY_SHOW_NOTIFICATION_DELETE_ACTION] = true

        val appPreferences = AppPreferences(fakePrefs.proxy)
        assertTrue(appPreferences.showNotificationDeleteAction)
    }
}

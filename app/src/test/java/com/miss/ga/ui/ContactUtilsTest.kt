package com.miss.ga.ui

import com.miss.ga.data.repository.ContactDetail
import com.miss.ga.data.util.PhoneNumberKeys
import com.miss.ga.ui.util.ContactUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactUtilsTest {

    @Test
    fun isCallable_validPhoneNumbers_returnsTrue() {
        assertTrue(ContactUtils.isCallable("+989121234567"))
        assertTrue(ContactUtils.isCallable("09121234567"))
        assertTrue(ContactUtils.isCallable("9121234567"))
        assertTrue(ContactUtils.isCallable("+1 (555) 234-5678"))
        assertTrue(ContactUtils.isCallable("123"))
        assertTrue(ContactUtils.isCallable("982188888888"))
    }

    @Test
    fun isCallable_alphanumericSenders_returnsFalse() {
        assertFalse(ContactUtils.isCallable(""))
        assertFalse(ContactUtils.isCallable("   "))
        assertFalse(ContactUtils.isCallable("Google"))
        assertFalse(ContactUtils.isCallable("Snapp"))
        assertFalse(ContactUtils.isCallable("BankMellat"))
        assertFalse(ContactUtils.isCallable("Digikala1"))
        assertFalse(ContactUtils.isCallable("SMS2"))
    }

    @Test
    fun phoneNumberKeys_lookupValue_resolvesGenericTypes() {
        val map = mutableMapOf<String, ContactDetail>()
        val detail = ContactDetail(
            name = "Sarah Connor",
            photoUri = "content://media/photo/123",
            lookupUri = "content://contacts/lookup/123/456"
        )
        for (key in PhoneNumberKeys.keys("+989121112233")) {
            map.putIfAbsent(key, detail)
        }

        val found = PhoneNumberKeys.lookupValue(map, "09121112233")
        assertNotNull(found)
        assertEquals("Sarah Connor", found?.name)
        assertEquals("content://media/photo/123", found?.photoUri)
        assertEquals("content://contacts/lookup/123/456", found?.lookupUri)

        val notFound = PhoneNumberKeys.lookupValue(map, "09129998877")
        assertNull(notFound)
    }
}

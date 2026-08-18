package com.miss.ga

import com.miss.ga.data.util.PhoneNumberKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberKeysTest {

    @Test
    fun iranianMobileFormatsShareLookupKeys() {
        val plus = PhoneNumberKeys.keys("+98 912 123 4567")
        val trunk = PhoneNumberKeys.keys("09121234567")
        val national = PhoneNumberKeys.keys("9121234567")

        assertTrue(plus.contains("989121234567"))
        assertTrue(plus.contains("09121234567"))
        assertTrue(plus.contains("9121234567"))
        assertTrue(trunk.intersect(plus).isNotEmpty())
        assertTrue(national.intersect(plus).isNotEmpty())
    }

    @Test
    fun lookupMatchesStoredContactAcrossFormats() {
        val map = mutableMapOf<String, String>()
        PhoneNumberKeys.keys("09121234567").forEach { key ->
            map.putIfAbsent(key, "Ali")
        }

        assertEquals("Ali", PhoneNumberKeys.lookup(map, "+989121234567"))
        assertEquals("Ali", PhoneNumberKeys.lookup(map, "9121234567"))
    }
}

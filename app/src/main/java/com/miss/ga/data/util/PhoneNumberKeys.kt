package com.miss.ga.data.util

/**
 * Builds equivalent phone-number keys so Iranian numbers like
 * `+98912…`, `0912…`, and `912…` can be matched without a
 * ContactsContract lookup per conversation.
 */
object PhoneNumberKeys {
    fun digitsOnly(number: String): String = number.filter { it.isDigit() }

    fun keys(number: String): Set<String> {
        val digits = digitsOnly(number)
        if (digits.isEmpty()) return emptySet()

        val keys = mutableSetOf(digits)
        if (digits.startsWith("98") && digits.length > 10) {
            val national = digits.substring(2)
            keys.add(national)
            keys.add("0$national")
        }
        if (digits.startsWith("0") && digits.length > 1) {
            val withoutTrunk = digits.substring(1)
            keys.add(withoutTrunk)
            keys.add("98$withoutTrunk")
        }
        if (digits.length >= 10) {
            keys.add(digits.takeLast(10))
        }
        return keys
    }

    fun lookup(map: Map<String, String>, number: String): String? {
        for (key in keys(number)) {
            val name = map[key]
            if (!name.isNullOrBlank()) return name
        }
        return null
    }
}

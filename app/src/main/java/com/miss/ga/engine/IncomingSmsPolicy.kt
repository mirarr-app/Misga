package com.miss.ga.engine

/**
 * Decides which incoming PDUs are real user-visible SMS.
 *
 * Iranian carriers and some OEMs deliver silent / SIM-toolkit / class-0
 * "ghost" messages whose originating address is `UNKNOWN_SENDER` (or empty)
 * and whose body is blank or a placeholder such as `NNNN`. Google Messages
 * drops those; storing and notifying them produced empty conversations that
 * disappeared when the user switched default SMS apps.
 */
object IncomingSmsPolicy {

    const val TYPE_ZERO_PROTOCOL_ID = 0x40

    /** Address stored when the PDU has no originating address but a real body. */
    const val STORED_UNKNOWN_ADDRESS = "Unknown"

    fun isUnknownSenderAlias(address: String): Boolean {
        val normalized = normalizeToken(address)
        if (normalized.isEmpty()) return true
        if (normalized in UNKNOWN_SENDER_ALIASES) return true
        if (normalized.startsWith("unknown_sender")) return true
        if (normalized.startsWith("unknownsender")) return true
        return false
    }

    fun isUnusableBody(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return true
        val normalized = normalizeToken(trimmed)
        return normalized.isEmpty() || normalized in PLACEHOLDER_BODIES
    }

    /**
     * Returns true when this PDU should be written to the SMS inbox and
     * (if not spam) notified. Service / silent / empty-unknown messages
     * return false.
     */
    fun shouldKeepInInbox(
        address: String,
        body: String,
        isClassZero: Boolean = false,
        isTypeZero: Boolean = false,
        isStatusReport: Boolean = false,
        isMwiDontStore: Boolean = false
    ): Boolean {
        if (isTypeZero || isStatusReport || isMwiDontStore) return false
        val usableBody = !isUnusableBody(body)
        if (!usableBody) {
            // Class-0 flash SMS and unknown/blank senders with no text are
            // not conversations. Empty bodies from a real number still keep,
            // matching Google Messages.
            if (isClassZero || isUnknownSenderAlias(address)) return false
        }
        return true
    }

    fun isGhostConversation(address: String, snippet: String): Boolean {
        return isUnknownSenderAlias(address) && isUnusableBody(snippet)
    }

    fun storedAddress(address: String): String {
        val trimmed = address.trim()
        return if (trimmed.isEmpty()) STORED_UNKNOWN_ADDRESS else trimmed
    }

    fun displayName(
        contactName: String?,
        address: String,
        unknownLabel: String
    ): String {
        contactName?.takeIf { it.isNotBlank() }?.let { return it }
        return if (isUnknownSenderAlias(address)) unknownLabel else address
    }

    private fun normalizeToken(value: String): String {
        return buildString(value.length) {
            for (ch in value) {
                if (ch.isLetterOrDigit()) append(ch.lowercaseChar())
            }
        }
    }

    private val UNKNOWN_SENDER_ALIASES = setOf(
        "unknown",
        "unknownsender",
        "unknownaddress",
        "unknownnumber",
        "anonymous",
        "private",
        "privatenumber",
        "restricted",
        "hidden",
        "hiddennumber",
        // Persian "unknown sender" / "hidden number"
        "فرستندهناشناس",
        "شمارهمخفی"
    )

    private val PLACEHOLDER_BODIES = setOf(
        "n",
        "nn",
        "nnn",
        "nnnn",
        "na",
        "nul",
        "null",
        "none",
        "empty",
        "messagenotfound",
        "4504",
        "4504messagenotfound"
    )
}

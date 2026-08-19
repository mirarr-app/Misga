package com.miss.ga

import com.miss.ga.engine.IncomingSmsPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingSmsPolicyTest {

    @Test
    fun unknownSenderAliasesAreRecognized() {
        val aliases = listOf(
            "",
            "   ",
            "Unknown",
            "UNKNOWN_SENDER",
            "UNKNOWN_SENDER!",
            "Unknown sender",
            "unknown address",
            "فرستنده ناشناس"
        )
        aliases.forEach { alias ->
            assertTrue("Expected '$alias' to be an unknown-sender alias", IncomingSmsPolicy.isUnknownSenderAlias(alias))
        }
        assertFalse(IncomingSmsPolicy.isUnknownSenderAlias("+989121234567"))
        assertFalse(IncomingSmsPolicy.isUnknownSenderAlias("BankMellat"))
        assertFalse(IncomingSmsPolicy.isUnknownSenderAlias("10001234"))
    }

    @Test
    fun placeholderAndBlankBodiesAreUnusable() {
        assertTrue(IncomingSmsPolicy.isUnusableBody(""))
        assertTrue(IncomingSmsPolicy.isUnusableBody("   "))
        assertTrue(IncomingSmsPolicy.isUnusableBody("NNNN"))
        assertTrue(IncomingSmsPolicy.isUnusableBody("Message not found"))
        assertTrue(IncomingSmsPolicy.isUnusableBody("4504: Message not found"))
        assertFalse(IncomingSmsPolicy.isUnusableBody("رمز پویا: 123456"))
        assertFalse(IncomingSmsPolicy.isUnusableBody("سلام"))
    }

    @Test
    fun dropsGhostUnknownSenderWithEmptyBody() {
        assertFalse(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "UNKNOWN_SENDER!",
                body = ""
            )
        )
        assertFalse(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "Unknown",
                body = "NNNN"
            )
        )
        assertFalse(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "",
                body = "   "
            )
        )
    }

    @Test
    fun dropsSilentAndStatusAndMwiMessages() {
        assertFalse(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "+989121234567",
                body = "ping",
                isTypeZero = true
            )
        )
        assertFalse(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "+989121234567",
                body = "",
                isStatusReport = true
            )
        )
        assertFalse(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "1000",
                body = "",
                isMwiDontStore = true
            )
        )
        assertFalse(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "UNKNOWN_SENDER",
                body = "",
                isClassZero = true
            )
        )
    }

    @Test
    fun keepsRealMessagesIncludingHiddenNumberWithBody() {
        assertTrue(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "+989121234567",
                body = "سلام علی جان"
            )
        )
        assertTrue(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "UNKNOWN_SENDER",
                body = "رمز یکبار مصرف شما: 123456"
            )
        )
        assertTrue(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "BankMellat",
                body = ""
            )
        )
        assertTrue(
            IncomingSmsPolicy.shouldKeepInInbox(
                address = "10001234",
                body = "تخفیف ویژه",
                isClassZero = true
            )
        )
    }

    @Test
    fun ghostConversationHidesEmptyUnknownThreads() {
        assertTrue(IncomingSmsPolicy.isGhostConversation("UNKNOWN_SENDER!", ""))
        assertTrue(IncomingSmsPolicy.isGhostConversation("Unknown", "NNNN"))
        assertFalse(IncomingSmsPolicy.isGhostConversation("UNKNOWN_SENDER", "OTP 1234"))
        assertFalse(IncomingSmsPolicy.isGhostConversation("+989121234567", ""))
    }

    @Test
    fun displayNameUsesUnknownLabelForAliases() {
        assertEquals(
            "Unknown sender",
            IncomingSmsPolicy.displayName(null, "UNKNOWN_SENDER!", "Unknown sender")
        )
        assertEquals(
            "Ali",
            IncomingSmsPolicy.displayName("Ali", "UNKNOWN_SENDER", "Unknown sender")
        )
        assertEquals(
            "+989121234567",
            IncomingSmsPolicy.displayName(null, "+989121234567", "Unknown sender")
        )
    }

    @Test
    fun storedAddressUsesStableUnknownTokenForBlankPduAddress() {
        assertEquals("Unknown", IncomingSmsPolicy.storedAddress(""))
        assertEquals("UNKNOWN_SENDER!", IncomingSmsPolicy.storedAddress(" UNKNOWN_SENDER! "))
        assertEquals("+98912", IncomingSmsPolicy.storedAddress("+98912"))
    }
}

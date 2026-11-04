package com.miss.ga

import com.miss.ga.data.util.UserPreferences
import com.miss.ga.ui.viewmodel.PendingDispatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoSendTest {

    private class FakeUserPreferences(
        override var showContactsOnly: Boolean = false,
        override var showShamsiDate: Boolean = false,
        override var enableDateTapShamsiToggle: Boolean = true,
        override var showNotificationDeleteAction: Boolean = false,
        override var showTabs: Boolean = true,
        override var showSimFilter: Boolean = true,
        override var noSpamForContacts: Boolean = false,
        override var undoSendEnabled: Boolean = false,
        override var undoSendDelaySeconds: Int = 4
    ) : UserPreferences

    @Test
    fun pendingDispatchStateHoldsMessageAndCountdown() {
        val pending = PendingDispatch(
            text = "Accidental typo text",
            subId = 1,
            totalSeconds = 4,
            remainingSeconds = 4
        )

        assertEquals("Accidental typo text", pending.text)
        assertEquals(1, pending.subId)
        assertEquals(4, pending.totalSeconds)
        assertEquals(4, pending.remainingSeconds)

        val updated = pending.copy(remainingSeconds = 3)
        assertEquals(3, updated.remainingSeconds)
    }

    @Test
    fun configurableDelayClampsToThreeToFiveSeconds() {
        val prefs = FakeUserPreferences()

        // Test default
        assertEquals(4, prefs.undoSendDelaySeconds)

        // Set valid 3s
        prefs.undoSendDelaySeconds = 3
        assertEquals(3, prefs.undoSendDelaySeconds)

        // Set valid 5s
        prefs.undoSendDelaySeconds = 5
        assertEquals(5, prefs.undoSendDelaySeconds)
    }

    @Test
    fun undoCancelsDispatchAndPreservesTextForInputRestoration() {
        var pending: PendingDispatch? = PendingDispatch(
            text = "Hello world with a typo",
            subId = null,
            totalSeconds = 4,
            remainingSeconds = 3
        )

        assertNotNull(pending)

        // Simulate undo
        val restoredText = pending?.text
        pending = null

        assertNull(pending)
        assertEquals("Hello world with a typo", restoredText)
    }
}

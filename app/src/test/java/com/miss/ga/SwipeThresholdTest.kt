package com.miss.ga

import androidx.compose.material3.SwipeToDismissBoxValue
import com.miss.ga.ui.screens.SwipeActionDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeThresholdTest {

    private val width = 1000f
    private val fallback = 350f

    @Test
    fun testSettledTargetIsAlwaysAllowed() {
        // Settling back to original position should always succeed regardless of offset or NaN
        assertTrue(SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.Settled, 0f, width, fallback))
        assertTrue(SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.Settled, 50f, width, fallback))
        assertTrue(SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.Settled, -50f, width, fallback))
        assertTrue(SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.Settled, 500f, width, fallback))
        assertTrue(SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.Settled, -500f, width, fallback))
        assertTrue(SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.Settled, Float.NaN, width, fallback))
    }

    @Test
    fun testSmallDriftsFromVerticalScrollOrTapAreRejected() {
        // Typical drifts during vertical scroll or tap are 0-100px (< 10-39% of width)
        val testDrifts = listOf(0f, 2f, 5f, 15f, 30f, 50f, 100f, 200f, 399f)
        for (drift in testDrifts) {
            // StartToEnd (swipe right - mark read/unread)
            assertFalse(
                "Positive drift of $drift should be rejected for StartToEnd",
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, drift, width, fallback)
            )
            // EndToStart (swipe left - delete)
            assertFalse(
                "Negative drift of -$drift should be rejected for EndToStart",
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, -drift, width, fallback)
            )
        }
    }

    @Test
    fun testIntentionalSwipesReachingThresholdAreAllowed() {
        // At 40% (400px on 1000px width), threshold is met
        val thresholdPx = width * SwipeActionDefaults.THRESHOLD_FRACTION

        assertTrue(
            "Exactly at threshold should be allowed for StartToEnd",
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, thresholdPx, width, fallback)
        )
        assertTrue(
            "Exactly at negative threshold should be allowed for EndToStart",
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, -thresholdPx, width, fallback)
        )

        // Above threshold (e.g. 401px, 450px, 500px, 750px, 1000px)
        val intentionalOffsets = listOf(401f, 450f, 500f, 750f, 1000f)
        for (offset in intentionalOffsets) {
            assertTrue(
                "Offset $offset should be allowed for StartToEnd",
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, offset, width, fallback)
            )
            assertTrue(
                "Offset -$offset should be allowed for EndToStart",
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, -offset, width, fallback)
            )
        }
    }

    @Test
    fun testDirectionMismatchIsRejected() {
        // Dragging left (negative offset) must NEVER commit a rightward action (StartToEnd)
        val negativeOffsets = listOf(-1f, -50f, -400f, -500f, -1000f)
        for (offset in negativeOffsets) {
            assertFalse(
                "Negative offset $offset should NOT be allowed for StartToEnd",
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, offset, width, fallback)
            )
        }

        // Dragging right (positive offset) must NEVER commit a leftward action (EndToStart)
        val positiveOffsets = listOf(1f, 50f, 400f, 500f, 1000f)
        for (offset in positiveOffsets) {
            assertFalse(
                "Positive offset $offset should NOT be allowed for EndToStart",
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, offset, width, fallback)
            )
        }
    }

    @Test
    fun testNaNOffsetIsRejected() {
        assertFalse(
            "NaN offset must be rejected for StartToEnd",
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, Float.NaN, width, fallback)
        )
        assertFalse(
            "NaN offset must be rejected for EndToStart",
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, Float.NaN, width, fallback)
        )
    }

    @Test
    fun testFallbackThresholdWhenWidthNotMeasured() {
        // When width is 0 or negative, fallback threshold is used
        assertFalse(
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, 200f, 0f, fallback)
        )
        assertFalse(
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, -349f, 0f, fallback)
        )
        assertTrue(
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, 350f, 0f, fallback)
        )
        assertTrue(
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, -350f, 0f, fallback)
        )
        assertTrue(
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, 400f, 0f, fallback)
        )
        assertFalse(
            "Negative offset with fallback should be rejected for StartToEnd",
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, -400f, 0f, fallback)
        )
        assertFalse(
            "Positive offset with fallback should be rejected for EndToStart",
            SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, 400f, 0f, fallback)
        )
    }

    @Test
    fun testVariousDisplayWidthsThreshold() {
        val testDisplayWidths = listOf(360f, 480f, 720f, 1080f, 1440f, 2160f)
        for (w in testDisplayWidths) {
            val threshold = w * SwipeActionDefaults.THRESHOLD_FRACTION
            // Just below threshold
            assertFalse(
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, threshold - 1f, w, fallback)
            )
            assertFalse(
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, -(threshold - 1f), w, fallback)
            )
            // Exactly at threshold
            assertTrue(
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.StartToEnd, threshold, w, fallback)
            )
            assertTrue(
                SwipeActionDefaults.isDismissAllowed(SwipeToDismissBoxValue.EndToStart, -threshold, w, fallback)
            )
        }
    }

    @Test
    fun testPositionalThresholdCalculation() {
        assertEquals(400f, SwipeActionDefaults.positionalThreshold(1000f), 0.001f)
        assertEquals(144f, SwipeActionDefaults.positionalThreshold(360f), 0.001f)
        assertEquals(240f, SwipeActionDefaults.positionalThreshold(600f), 0.001f)
        assertEquals(0f, SwipeActionDefaults.positionalThreshold(0f), 0.001f)
    }

    @Test
    fun testThresholdFractionValue() {
        assertEquals(0.4f, SwipeActionDefaults.THRESHOLD_FRACTION, 0.001f)
    }
}

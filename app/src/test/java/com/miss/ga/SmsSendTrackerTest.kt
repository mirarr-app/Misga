package com.miss.ga

import com.miss.ga.receiver.SmsSendTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSendTrackerTest {

  @Test
  fun allPartsOkCompletesTrue() {
    val deferred = SmsSendTracker.register(1001, 2)

    SmsSendTracker.onPartResult(1001, 2, true)
    assertFalse(deferred.isCompleted)

    SmsSendTracker.onPartResult(1001, 2, true)
    assertTrue(deferred.isCompleted)
    assertTrue(deferred.getCompleted())
  }

  @Test
  fun oneFailingPartCompletesFalse() {
    val deferred = SmsSendTracker.register(1002, 3)

    SmsSendTracker.onPartResult(1002, 3, true)
    SmsSendTracker.onPartResult(1002, 3, false)
    SmsSendTracker.onPartResult(1002, 3, true)

    assertTrue(deferred.isCompleted)
    assertFalse(deferred.getCompleted())
  }

  @Test
  fun cancelCompletesFalseImmediately() {
    val deferred = SmsSendTracker.register(1003, 4)

    SmsSendTracker.cancel(1003)

    assertTrue(deferred.isCompleted)
    assertFalse(deferred.getCompleted())
  }

  @Test
  fun resultsAfterCancelAreIgnored() {
    val deferred = SmsSendTracker.register(1004, 2)
    SmsSendTracker.cancel(1004)

    SmsSendTracker.onPartResult(1004, 2, true)
    SmsSendTracker.onPartResult(1004, 2, true)

    assertTrue(deferred.isCompleted)
    assertFalse(deferred.getCompleted())
  }

  @Test
  fun resultsAfterCompletionAreIgnored() {
    val deferred = SmsSendTracker.register(1005, 1)
    SmsSendTracker.onPartResult(1005, 1, true)
    assertTrue(deferred.getCompleted())

    SmsSendTracker.onPartResult(1005, 1, false)

    assertTrue(deferred.isCompleted)
    assertTrue(deferred.getCompleted())
  }

  @Test
  fun unknownTokenResultsAreIgnored() {
    SmsSendTracker.onPartResult(999_999, 1, true)
    SmsSendTracker.cancel(999_998)
  }

  @Test
  fun concurrentTokensAreTrackedIndependently() {
    val first = SmsSendTracker.register(2001, 2)
    val second = SmsSendTracker.register(2002, 1)
    val third = SmsSendTracker.register(2003, 2)

    SmsSendTracker.onPartResult(2002, 1, true)
    assertTrue(second.isCompleted)
    assertTrue(second.getCompleted())

    SmsSendTracker.onPartResult(2001, 2, false)
    assertFalse(first.isCompleted)
    SmsSendTracker.onPartResult(2001, 2, true)
    assertTrue(first.isCompleted)
    assertFalse(first.getCompleted())

    SmsSendTracker.onPartResult(2003, 2, true)
    SmsSendTracker.onPartResult(2003, 2, true)
    assertTrue(third.isCompleted)
    assertTrue(third.getCompleted())
  }

  @Test
  fun partCountCoercedToAtLeastOne() {
    val deferred = SmsSendTracker.register(3001, 0)

    SmsSendTracker.onPartResult(3001, 0, true)

    assertTrue(deferred.isCompleted)
    assertTrue(deferred.getCompleted())
  }
}

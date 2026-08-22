package com.miss.ga

import com.miss.ga.engine.IncomingMultipartAssembler
import com.miss.ga.engine.IncomingSmsPart
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingMultipartAssemblerTest {

  private fun part(
    displayAddress: String? = null,
    originatingAddress: String? = null,
    displayBody: String? = null,
    body: String? = null,
    timestamp: Long = 0L
  ) = IncomingSmsPart(
    displayOriginatingAddress = displayAddress,
    originatingAddress = originatingAddress,
    displayMessageBody = displayBody,
    messageBody = body,
    timestampMillis = timestamp
  )

  private fun assemble(vararg parts: IncomingSmsPart) =
    IncomingMultipartAssembler.assemble(parts.toList()) { it }

  @Test
  fun singleMessageFormsOneEntry() {
    val entries = assemble(part(displayAddress = "+989121234567", displayBody = "hello", timestamp = 111L))

    assertEquals(1, entries.size)
    val entry = entries.single()
    assertEquals("+989121234567", entry.rawSender)
    assertEquals("hello", entry.body)
    assertEquals(111L, entry.timestampMillis)
  }

  @Test
  fun multiPartsFromSameSenderAreConcatenatedInOrder() {
    val entries = assemble(
      part(displayAddress = "+98912", displayBody = "part1-", timestamp = 1L),
      part(displayAddress = "+98912", displayBody = "part2-", timestamp = 2L),
      part(displayAddress = "+98912", displayBody = "part3", timestamp = 3L)
    )

    assertEquals(1, entries.size)
    assertEquals("+98912", entries.single().rawSender)
    assertEquals("part1-part2-part3", entries.single().body)
    assertEquals(1L, entries.single().timestampMillis)
  }

  @Test
  fun differentSendersAreGroupedSeparately() {
    val entries = assemble(
      part(displayAddress = "+98911", displayBody = "a", timestamp = 5L),
      part(displayAddress = "+98912", displayBody = "b", timestamp = 6L),
      part(displayAddress = "+98911", displayBody = "c", timestamp = 7L)
    )

    assertEquals(2, entries.size)
    assertEquals("+98911", entries[0].rawSender)
    assertEquals("ac", entries[0].body)
    assertEquals(5L, entries[0].timestampMillis)
    assertEquals("+98912", entries[1].rawSender)
    assertEquals("b", entries[1].body)
    assertEquals(6L, entries[1].timestampMillis)
  }

  @Test
  fun blankOrNullBodyToleratedAsEmptyString() {
    val entries = assemble(
      part(displayAddress = "+98912", displayBody = "", body = null, timestamp = 1L),
      part(displayAddress = "+98912", displayBody = null, body = null, timestamp = 2L),
      part(displayAddress = "+98912", displayBody = "end", timestamp = 3L)
    )

    assertEquals(1, entries.size)
    assertEquals("end", entries.single().body)
  }

  @Test
  fun nullDisplayBodyFallsBackToMessageBodyVerbatim() {
    val entries = assemble(
      part(displayAddress = "+98912", displayBody = null, body = "   real", timestamp = 4L),
      part(displayAddress = "+98912", displayBody = "", body = null, timestamp = 5L)
    )

    assertEquals(1, entries.size)
    assertEquals("   real", entries.single().body)
  }

  @Test
  fun blankDisplayAddressFallsBackToOriginatingAddress() {
    val entries = assemble(
      part(displayAddress = "   ", originatingAddress = "  +98912  ", displayBody = "hi", timestamp = 9L)
    )

    assertEquals("+98912", entries.single().rawSender)
  }

  @Test
  fun nullDisplayAddressFallsBackToTrimmedOriginatingAddress() {
    val entries = assemble(
      part(originatingAddress = "+98913", body = "otp", timestamp = 10L)
    )

    assertEquals("+98913", entries.single().rawSender)
    assertEquals("otp", entries.single().body)
  }

  @Test
  fun bothAddressesBlankYieldEmptyRawSender() {
    val entries = assemble(part(displayAddress = "", originatingAddress = null, body = "x", timestamp = 11L))

    assertEquals("", entries.single().rawSender)
  }

  @Test
  fun timestampComesFromFirstPart() {
    val entries = assemble(
      part(displayAddress = "+98912", displayBody = "one", timestamp = 100L),
      part(displayAddress = "+98912", displayBody = "two", timestamp = 999L)
    )

    assertEquals(100L, entries.single().timestampMillis)
  }

  @Test
  fun emptyInputYieldsNoEntries() {
    assertEquals(0, assemble().size)
  }
}

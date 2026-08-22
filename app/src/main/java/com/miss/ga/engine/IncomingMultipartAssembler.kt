package com.miss.ga.engine

/**
 * Framework-free snapshot of the fields the multipart assembler needs from an SMS PDU.
 * The receiver fills this in with try/catch tolerance around the framework getters.
 */
data class IncomingSmsPart(
    val displayOriginatingAddress: String? = null,
    val originatingAddress: String? = null,
    val displayMessageBody: String? = null,
    val messageBody: String? = null,
    val timestampMillis: Long = 0L
)

data class AssembledIncomingMessages<T>(
    val rawSender: String,
    val body: String,
    val timestampMillis: Long,
    val parts: List<T>
)

object IncomingMultipartAssembler {

    fun <T> assemble(
        messages: List<T>,
        partOf: (T) -> IncomingSmsPart
    ): List<AssembledIncomingMessages<T>> {
        return messages.groupBy { smsAddress(partOf(it)) }.map { (rawSender, group) ->
            AssembledIncomingMessages(
                rawSender = rawSender,
                body = group.joinToString(separator = "") { smsBody(partOf(it)) },
                timestampMillis = group.firstOrNull()?.let { partOf(it).timestampMillis }
                    ?: System.currentTimeMillis(),
                parts = group
            )
        }
    }

    fun smsAddress(part: IncomingSmsPart): String {
        return part.displayOriginatingAddress?.trim().orEmpty().ifBlank {
            part.originatingAddress?.trim().orEmpty()
        }
    }

    fun smsBody(part: IncomingSmsPart): String {
        val display = part.displayMessageBody
        if (!display.isNullOrEmpty()) return display
        return part.messageBody.orEmpty()
    }
}

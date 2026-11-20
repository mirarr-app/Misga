package com.miss.ga.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miss.ga.theme.PillShape

object SmsCounterUtils {
    data class SmsCount(
        val charCount: Int,
        val remainingInSegment: Int,
        val maxInSegment: Int,
        val segmentCount: Int,
        val isUnicode: Boolean
    )

    private val GSM_BASIC_CHARS: Set<Char> = setOf(
        '@', '£', '$', '¥', 'è', 'é', 'ù', 'ì', 'ò', 'Ç', '\n', 'Ø', 'ø', '\r',
        'Å', 'å', 'Δ', '_', 'Φ', 'Γ', 'Λ', 'Ω', 'Π', 'Ψ', 'Σ', 'Θ', 'Ξ',
        'Æ', 'æ', 'ß', 'É', ' ', '!', '"', '#', '¤', '%', '&', '\'', '(', ')',
        '*', '+', ',', '-', '.', '/', ':', ';', '<', '=', '>', '?', '¡',
        'Ä', 'Ö', 'Ñ', 'Ü', '§', '¿', 'ä', 'ö', 'ñ', 'ü', 'à'
    ).plus('0'..'9').plus('A'..'Z').plus('a'..'z')

    private val GSM_EXT_CHARS: Set<Char> = setOf(
        '^', '{', '}', '\\', '[', '~', ']', '|', '€'
    )

    fun isGsm7(text: String): Boolean {
        for (ch in text) {
            if (ch !in GSM_BASIC_CHARS && ch !in GSM_EXT_CHARS) {
                return false
            }
        }
        return true
    }

    fun calculate(text: String): SmsCount {
        if (text.isEmpty()) {
            return SmsCount(
                charCount = 0,
                remainingInSegment = 160,
                maxInSegment = 160,
                segmentCount = 1,
                isUnicode = false
            )
        }

        val isGsm = isGsm7(text)
        if (isGsm) {
            var totalSeptets = 0
            for (ch in text) {
                totalSeptets += if (ch in GSM_EXT_CHARS) 2 else 1
            }

            if (totalSeptets <= 160) {
                return SmsCount(
                    charCount = text.length,
                    remainingInSegment = 160 - totalSeptets,
                    maxInSegment = 160,
                    segmentCount = 1,
                    isUnicode = false
                )
            }

            var segments = 1
            var currentInSegment = 0
            for (ch in text) {
                val charCost = if (ch in GSM_EXT_CHARS) 2 else 1
                if (currentInSegment + charCost > 153) {
                    segments++
                    currentInSegment = charCost
                } else {
                    currentInSegment += charCost
                }
            }
            val remaining = 153 - currentInSegment
            return SmsCount(
                charCount = text.length,
                remainingInSegment = remaining,
                maxInSegment = 153,
                segmentCount = segments,
                isUnicode = false
            )
        } else {
            val codeUnits = text.length
            if (codeUnits <= 70) {
                return SmsCount(
                    charCount = text.length,
                    remainingInSegment = 70 - codeUnits,
                    maxInSegment = 70,
                    segmentCount = 1,
                    isUnicode = true
                )
            }

            var segments = 1
            var currentInSegment = 0
            var i = 0
            while (i < text.length) {
                val isSurrogate = Character.isSurrogate(text[i])
                val charCost = if (isSurrogate && i + 1 < text.length && Character.isSurrogatePair(text[i], text[i + 1])) 2 else 1
                if (currentInSegment + charCost > 67) {
                    segments++
                    currentInSegment = charCost
                } else {
                    currentInSegment += charCost
                }
                i += charCost
            }
            val remaining = 67 - currentInSegment
            return SmsCount(
                charCount = text.length,
                remainingInSegment = remaining,
                maxInSegment = 67,
                segmentCount = segments,
                isUnicode = true
            )
        }
    }
}

@Composable
fun SmsSegmentCounter(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return

    val info = SmsCounterUtils.calculate(text)
    val encodingName = if (info.isUnicode) "Unicode" else "GSM-7"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = PillShape,
            color = if (info.segmentCount > 1) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                text = "${info.charCount}/${info.maxInSegment} (${info.remainingInSegment}) • ${info.segmentCount} SMS ($encodingName)",
                style = MaterialTheme.typography.labelSmall,
                color = if (info.segmentCount > 1) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.outline
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

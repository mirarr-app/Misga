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

data class SmsSegmentInfo(
    val charCount: Int,
    val maxPerSegment: Int,
    val segments: Int,
    val isUnicode: Boolean
)

internal object SmsSegmentCalculator {
    private const val GSM7_BASIC_CHARS =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001BÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val GSM7_EXTENDED_CHARS = "^{}\\[~]|€"

    fun measure(text: String): SmsSegmentInfo {
        if (text.isEmpty()) {
            return SmsSegmentInfo(charCount = 0, maxPerSegment = 160, segments = 1, isUnicode = false)
        }
        var septets = 0
        var isUnicode = false
        for (ch in text) {
            when {
                ch in GSM7_BASIC_CHARS -> septets += 1
                ch in GSM7_EXTENDED_CHARS -> septets += 2
                else -> {
                    isUnicode = true
                    break
                }
            }
        }
        return if (isUnicode) {
            val count = text.length
            val segments = if (count <= 70) 1 else (count + 66) / 67
            val maxPerSegment = if (segments > 1) 67 else 70
            SmsSegmentInfo(charCount = count, maxPerSegment = maxPerSegment, segments = segments, isUnicode = true)
        } else {
            val segments = if (septets <= 160) 1 else (septets + 152) / 153
            val maxPerSegment = if (segments > 1) 153 else 160
            SmsSegmentInfo(charCount = septets, maxPerSegment = maxPerSegment, segments = segments, isUnicode = false)
        }
    }
}

@Composable
fun SmsSegmentCounter(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return

    val info = SmsSegmentCalculator.measure(text)
    val maxPerSegment = info.maxPerSegment
    val count = info.charCount
    val segments = info.segments
    val isUnicode = info.isUnicode

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = PillShape,
            color = if (segments > 1) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                text = "$count/$maxPerSegment • $segments SMS (${if (isUnicode) "Persian/Unicode" else "GSM-7"})",
                style = MaterialTheme.typography.labelSmall,
                color = if (segments > 1) {
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

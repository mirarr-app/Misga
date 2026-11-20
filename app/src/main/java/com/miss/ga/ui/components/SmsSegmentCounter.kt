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
        val isUnicode = text.any { it.code > 127 }
        val count = text.length
        val maxSingle = if (isUnicode) 70 else 160
        val maxConcat = if (isUnicode) 67 else 153

        if (count <= maxSingle) {
            return SmsCount(
                charCount = count,
                remainingInSegment = maxSingle - count,
                maxInSegment = maxSingle,
                segmentCount = 1,
                isUnicode = isUnicode
            )
        }

        val segments = (count + maxConcat - 1) / maxConcat
        val totalCapacity = segments * maxConcat
        val remaining = totalCapacity - count
        return SmsCount(
            charCount = count,
            remainingInSegment = remaining,
            maxInSegment = maxConcat,
            segmentCount = segments,
            isUnicode = isUnicode
        )
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

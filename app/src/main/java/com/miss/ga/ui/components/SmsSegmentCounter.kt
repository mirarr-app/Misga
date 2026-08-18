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

@Composable
fun SmsSegmentCounter(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return

    val isUnicode = text.any { it.code > 127 }
    val maxPerSegment = if (isUnicode) 70 else 160
    val count = text.length
    val segments = if (count == 0) 1 else (count + maxPerSegment - 1) / maxPerSegment

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

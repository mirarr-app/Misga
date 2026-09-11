package com.miss.ga.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SimBadgeShape = RoundedCornerShape(5.dp)

@Composable
fun SimBadge(
    slotNumber: Int,
    modifier: Modifier = Modifier,
    isSent: Boolean = false,
    showIcon: Boolean = false,
    compact: Boolean = false
) {
    val text = if (compact) "$slotNumber" else "SIM $slotNumber"
    val containerColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    }
    val contentColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        shape = SimBadgeShape,
        color = containerColor,
        border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = if (compact) 4.dp else 5.dp,
                vertical = 1.dp
            )
        ) {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

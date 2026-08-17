package com.example.misga.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Specific M3E Expressive Shapes
val IncomingBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 20.dp,
    bottomStart = 4.dp
)

val OutgoingBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 4.dp
)

val PillShape = RoundedCornerShape(percent = 50)
val SquircleCardShape = RoundedCornerShape(22.dp)
val SquircleMediumShape = RoundedCornerShape(16.dp)
val SpamCardShape = RoundedCornerShape(20.dp)
val InputBarShape = RoundedCornerShape(28.dp)
val TagShape = RoundedCornerShape(8.dp)


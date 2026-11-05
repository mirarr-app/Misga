package com.miss.ga.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miss.ga.R
import com.miss.ga.engine.BankCard
import com.miss.ga.engine.FinancialDetails
import com.miss.ga.engine.ShebaNumber
import com.miss.ga.theme.PillShape
import kotlinx.coroutines.delay

@Composable
fun BankCardCopyButton(
    bankCard: BankCard,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val labelText = remember(bankCard) {
        val preview = "${bankCard.number.take(4)} •••• ${bankCard.number.takeLast(4)}"
        if (!bankCard.bankName.isNullOrBlank()) {
            "${bankCard.bankName} ($preview)"
        } else {
            preview
        }
    }

    Surface(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Bank Card", bankCard.number))
            Toast.makeText(
                context,
                context.getString(R.string.card_copied_to_clipboard, bankCard.formatted),
                Toast.LENGTH_SHORT
            ).show()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            copied = true
        },
        shape = PillShape,
        color = if (copied) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        contentColor = if (copied) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        border = BorderStroke(
            1.dp,
            if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
        ),
        shadowElevation = 0.5.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            AnimatedContent(
                targetState = copied,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "CardCopyIcon"
            ) { isCopied ->
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.CreditCard,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            AnimatedContent(
                targetState = copied,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "CardCopyText"
            ) { isCopied ->
                Text(
                    text = if (isCopied) {
                        stringResource(R.string.copied)
                    } else {
                        stringResource(R.string.copy_card_button, labelText)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ShebaCopyButton(
    sheba: ShebaNumber,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val labelText = remember(sheba) {
        val preview = "${sheba.iban.take(4)} •••• ${sheba.iban.takeLast(4)}"
        if (!sheba.bankName.isNullOrBlank()) {
            "${sheba.bankName} ($preview)"
        } else {
            preview
        }
    }

    Surface(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Sheba Number", sheba.iban))
            Toast.makeText(
                context,
                context.getString(R.string.sheba_copied_to_clipboard, sheba.iban),
                Toast.LENGTH_SHORT
            ).show()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            copied = true
        },
        shape = PillShape,
        color = if (copied) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (copied) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        border = BorderStroke(
            1.dp,
            if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
        ),
        shadowElevation = 0.5.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            AnimatedContent(
                targetState = copied,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ShebaCopyIcon"
            ) { isCopied ->
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            AnimatedContent(
                targetState = copied,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ShebaCopyText"
            ) { isCopied ->
                Text(
                    text = if (isCopied) {
                        stringResource(R.string.copied)
                    } else {
                        stringResource(R.string.copy_sheba_button, labelText)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FinancialDetailsPills(
    financialDetails: FinancialDetails,
    modifier: Modifier = Modifier
) {
    if (financialDetails.bankCard == null && financialDetails.sheba == null) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        financialDetails.bankCard?.let { card ->
            BankCardCopyButton(bankCard = card)
        }
        financialDetails.sheba?.let { sheba ->
            ShebaCopyButton(sheba = sheba)
        }
    }
}

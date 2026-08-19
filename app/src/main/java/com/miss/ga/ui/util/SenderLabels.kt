package com.miss.ga.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miss.ga.R
import com.miss.ga.engine.IncomingSmsPolicy

@Composable
fun senderDisplayName(contactName: String?, address: String): String {
    return IncomingSmsPolicy.displayName(
        contactName = contactName,
        address = address,
        unknownLabel = stringResource(R.string.unknown_sender)
    )
}

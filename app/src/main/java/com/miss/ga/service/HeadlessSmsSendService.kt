package com.miss.ga.service

import android.app.RemoteInput
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.data.util.PhoneNumberKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIntent(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling respond-via-message", e)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != null && action != ACTION_RESPOND_VIA_MESSAGE) {
            return
        }

        val addresses = parseRecipients(intent.data)
        val body = parseBody(intent)
        if (addresses.isEmpty() || body.isBlank()) return

        val repository = SmsRepository(applicationContext)
        for (address in addresses) {
            try {
                val result = repository.sendSms(address, body)
                if (!result.sent) {
                    Log.e(TAG, "Failed to send quick reply to ${PhoneNumberKeys.redact(address)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send quick reply to ${PhoneNumberKeys.redact(address)}", e)
            }
        }
    }

    private fun parseRecipients(uri: Uri?): List<String> {
        if (uri == null) return emptyList()
        val scheme = uri.scheme?.lowercase()
        if (scheme != null && scheme !in SMS_URI_SCHEMES) return emptyList()

        val addressPart = schemeSpecificPartWithoutQuery(uri) ?: return emptyList()
        return addressPart.split(';', ',')
            .map { Uri.decode(it).trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseBody(intent: Intent): String {
        val remoteResults = RemoteInput.getResultsFromIntent(intent)
        if (remoteResults != null) {
            charSeqFromBundle(remoteResults, Intent.EXTRA_TEXT)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it.toString() }
            charSeqFromBundle(remoteResults, "android.intent.extra.TEXT")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it.toString() }
            for (key in remoteResults.keySet()) {
                val value = remoteResults.getCharSequence(key)
                if (!value.isNullOrBlank()) return value.toString()
            }
        }

        intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        intent.getStringExtra("android.intent.extra.TEXT")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return uriQueryBody(intent.data).orEmpty()
    }

    private fun uriQueryBody(uri: Uri?): String? {
        if (uri == null) return null
        try {
            uri.getQueryParameter("body")?.takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: Exception) {
            // Opaque sms:/smsto: URIs may not expose hierarchical query params.
        }
        val ssp = uri.schemeSpecificPart ?: return null
        val stripped = ssp.removePrefix("//")
        val qIndex = stripped.indexOf('?')
        if (qIndex < 0) return null
        return queryParam(stripped.substring(qIndex + 1), "body")
    }

    private fun schemeSpecificPartWithoutQuery(uri: Uri): String? {
        var ssp = uri.schemeSpecificPart ?: return null
        ssp = ssp.removePrefix("//")
        val qIndex = ssp.indexOf('?')
        return if (qIndex >= 0) ssp.substring(0, qIndex) else ssp
    }

    private fun queryParam(query: String, key: String): String? {
        if (query.isBlank()) return null
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            val name = Uri.decode(if (eq >= 0) pair.substring(0, eq) else pair)
            if (!name.equals(key, ignoreCase = true)) continue
            val value = if (eq >= 0) Uri.decode(pair.substring(eq + 1)) else ""
            return value.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun charSeqFromBundle(bundle: Bundle, key: String): CharSequence? {
        return bundle.getCharSequence(key)
    }

    companion object {
        private const val TAG = "HeadlessSmsSendService"
        private const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
        private val SMS_URI_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }
}

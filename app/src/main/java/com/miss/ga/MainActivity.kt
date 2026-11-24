package com.miss.ga

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.miss.ga.data.util.AppPreferences
import com.miss.ga.engine.NotificationHelper
import com.miss.ga.engine.ScheduledSmsManager
import com.miss.ga.theme.MISGATheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var pendingChatNav by mutableStateOf<ChatNav?>(null)
    private var pendingComposeNav by mutableStateOf<ComposeNav?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // ConversationsScreen reloads on resume and shows a banner if SMS is still denied.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        requestRequiredPermissions()
        ScheduledSmsManager.processOverdueMessagesAsync(applicationContext)

        setContent {
            MISGATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        pendingChatNav = pendingChatNav,
                        onChatNavHandled = { pendingChatNav = null },
                        pendingComposeNav = pendingComposeNav,
                        onComposeNavHandled = { pendingComposeNav = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ScheduledSmsManager.processOverdueMessagesAsync(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val threadId = intent.getLongExtra(NotificationHelper.EXTRA_THREAD_ID, -1L)
        val address = intent.getStringExtra(NotificationHelper.EXTRA_ADDRESS)
        val contactName = intent.getStringExtra(NotificationHelper.EXTRA_CONTACT_NAME)
        val subId = intent.getIntExtra(NotificationHelper.EXTRA_SUB_ID, -1)
        if (threadId > 0 && !address.isNullOrBlank()) {
            pendingChatNav = ChatNav(
                threadId = threadId,
                address = address,
                contactName = contactName,
                initialSubId = subId
            )
            return
        }

        parseComposeNav(intent)?.let { pendingComposeNav = it }
    }

    private fun parseComposeNav(intent: Intent): ComposeNav? {
        val uri = intent.data
        val scheme = uri?.scheme?.lowercase(Locale.US)
        var recipient = ""
        var body = ""

        if (uri != null && scheme in SMS_URI_SCHEMES) {
            recipient = smsAddressFromUri(uri)
            body = smsBodyFromUri(uri).orEmpty()
        }

        if (recipient.isBlank()) {
            recipient = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                ?: intent.getStringExtra("address")
                ?: intent.getStringArrayExtra("addresses")?.firstOrNull()
                ?: intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.firstOrNull()
                ?: intent.getStringExtra(Intent.EXTRA_EMAIL)
                ?: ""
        }

        if (body.isBlank()) {
            body = intent.getStringExtra("sms_body")
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: ""
        }

        if (recipient.isBlank() && body.isBlank()) return null
        val singleRecipient = recipient.split(',', ';')
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return ComposeNav(address = singleRecipient, body = body.trim())
    }

    private fun requestRequiredPermissions() {
        val prefs = AppPreferences(this)
        if (prefs.hasRequestedStartupPermissions) return

        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        prefs.hasRequestedStartupPermissions = true
        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    companion object {
        private val SMS_URI_SCHEMES = setOf("sms", "smsto", "mms", "mmsto", "tel")

        private fun smsAddressFromUri(uri: Uri): String {
            val ssp = uri.schemeSpecificPart ?: return ""
            val withoutPrefix = ssp.removePrefix("//")
            val qIndex = withoutPrefix.indexOf('?')
            val addressPart = if (qIndex >= 0) withoutPrefix.substring(0, qIndex) else withoutPrefix
            return Uri.decode(addressPart).trim()
        }

        private fun smsBodyFromUri(uri: Uri): String? {
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
    }
}

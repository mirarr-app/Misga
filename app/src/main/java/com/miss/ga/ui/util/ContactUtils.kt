package com.miss.ga.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast

object ContactUtils {
    private const val TAG = "ContactUtils"

    /**
     * Determines whether the given sender address is likely a dialable phone number.
     * Addresses with letters and fewer than 3 digits are considered alphanumeric sender IDs.
     */
    fun isCallable(address: String): Boolean {
        if (address.isBlank()) return false
        val digits = address.filter { it.isDigit() }
        val letters = address.filter { it.isLetter() }
        if (letters.isNotEmpty() && digits.length < 3) return false
        return digits.length >= 3
    }

    /**
     * Resolves the contact lookup URI for a given phone number from the Contacts provider.
     */
    fun resolveContactLookupUri(context: Context, address: String): Uri? {
        if (address.isBlank()) return null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.LOOKUP_KEY
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
                    val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY))
                    return ContactsContract.Contacts.getLookupUri(id, lookupKey)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve contact lookup URI for $address", e)
        }
        return null
    }

    /**
     * Opens the device dialer with the phone number pre-filled.
     */
    fun openDialer(context: Context, address: String): Boolean {
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(address)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open dialer for $address", e)
            Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Opens the contact card in the system Contacts app.
     * Returns true if opened successfully, false if the contact URI could not be resolved.
     */
    fun openContactInfo(context: Context, address: String, contactLookupUri: String? = null): Boolean {
        try {
            val targetUri = if (!contactLookupUri.isNullOrBlank()) {
                Uri.parse(contactLookupUri)
            } else {
                resolveContactLookupUri(context, address)
            }
            if (targetUri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = targetUri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open contact info for $address", e)
        }
        return false
    }

    /**
     * Opens the system Contacts app to create a new contact with the phone number pre-filled.
     */
    fun openAddToContacts(context: Context, address: String, displayName: String? = null): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, address)
                if (!displayName.isNullOrBlank() && displayName != address) {
                    putExtra(ContactsContract.Intents.Insert.NAME, displayName)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open add to contacts for $address", e)
            Toast.makeText(context, "Cannot open contacts", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

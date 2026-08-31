package org.librelab.dialer.lookup

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.text.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContactLookupService — reimplementation of crDroid's ContactInfoHelper.
 *
 * Uses standard Android PhoneLookup to look up contact info for a phone number.
 * Falls back to PhoneLookup alone (no ROM-specific CachedNumberLookupService).
 *
 * crDroid's full implementation also queries:
 * - Enterprise directory (PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI)
 * - Remote directories via CachedNumberLookupService (ROM-specific, not available here)
 * - Call log cache
 *
 * We implement the PhoneLookup path which works on any Android device.
 */
@Singleton
class ContactLookupService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        // Projection for PhoneLookup query — mirrors crDroid's PhoneQuery
        private val PHONE_LOOKUP_PROJECTION = arrayOf(
            PhoneLookup.CONTACT_ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER,
            PhoneLookup.NORMALIZED_NUMBER,
            PhoneLookup.PHOTO_ID,
            PhoneLookup.PHOTO_URI,
        )

        // Column indices (must match PHONE_LOOKUP_PROJECTION order)
        private const val COL_CONTACT_ID = 0
        private const val COL_DISPLAY_NAME = 1
        private const val COL_TYPE = 2
        private const val COL_LABEL = 3
        private const val COL_NUMBER = 4
        private const val COL_NORMALIZED_NUMBER = 5
        private const val COL_PHOTO_ID = 6
        private const val COL_PHOTO_URI = 7
    }

    /**
     * Look up contact info for a phone number.
     *
     * @param number raw phone number
     * @return ContactLookupResult with name, photoUri, numberType, etc., or null if not found
     */
    fun lookupContact(number: String): ContactLookupResult? {
        if (TextUtils.isEmpty(number)) return null

        val normalized = normalizeNumber(number)
        if (normalized.length < 4) return null

        // Use PhoneLookup — works on any Android device
        val uri: Uri = PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(normalized)
            .build()

        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                PHONE_LOOKUP_PROJECTION,
                null,
                null,
                null,
            )

            if (cursor == null) {
                return null
            }

            if (!cursor.moveToFirst()) {
                return null
            }

            val name = cursor.getString(COL_DISPLAY_NAME)
            if (TextUtils.isEmpty(name)) {
                return null
            }

            val photoUriStr = cursor.getString(COL_PHOTO_URI)
            val photoUri = if (!TextUtils.isEmpty(photoUriStr)) Uri.parse(photoUriStr) else null
            val contactId = cursor.getLong(COL_CONTACT_ID)

            ContactLookupResult(
                name = name,
                number = cursor.getString(COL_NUMBER),
                normalizedNumber = cursor.getString(COL_NORMALIZED_NUMBER),
                numberType = cursor.getInt(COL_TYPE),
                numberLabel = cursor.getString(COL_LABEL),
                photoUri = photoUri,
                contactId = contactId,
                lookupUri = ContactsContract.Contacts.getLookupUri(contactId, ""),
            )
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Check if the Contacts content provider is accessible (for availability reporting).
     * Returns true if at least one contact exists.
     */
    fun isContactsAvailable(): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null,
            )
            cursor?.count ?: 0 > 0
        } catch (_: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }

    /**
     * Get total contact count.
     */
    fun getContactCount(): Int {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null,
            )
            cursor?.count ?: 0
        } catch (_: Exception) {
            0
        } finally {
            cursor?.close()
        }
    }

    private fun normalizeNumber(number: String): String {
        // Keep only digits, strip country code prefix
        return number.replace(Regex("[^0-9]"), "")
    }

    /**
     * Contact lookup result — mirrors crDroid's ContactInfo.
     */
    data class ContactLookupResult(
        val name: String,
        val number: String?,
        val normalizedNumber: String?,
        val numberType: Int,       // e.g. Phone.TYPE_MOBILE, TYPE_HOME
        val numberLabel: String?,  // e.g. "Mobile", "Work"
        val photoUri: Uri?,
        val contactId: Long,
        val lookupUri: Uri?,
    )

    /**
     * Get human-readable label for a number type (e.g. "Mobile", "Home").
     */
    fun getNumberTypeLabel(type: Int, label: String?): String {
        return Phone.getTypeLabel(context.resources, type, label).toString()
    }
}

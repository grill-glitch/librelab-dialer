package org.librelab.dialer.ui.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.librelab.dialer.domain.model.Contact
import org.librelab.dialer.domain.model.PhoneNumber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AllContactsRepository — fetches all contacts with phone numbers for the Contacts tab.
 * Mirrors ContactsFragment's LoaderCallbacks<Cursor> behavior.
 */
@Singleton
class AllContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver = context.contentResolver

    /**
     * Fetch all contacts who have at least one phone number.
     * For each contact, also loads all associated phone numbers.
     *
     * Implementation: first query Contacts with HAS_PHONE_NUMBER != 0, then
     * batch-query CommonDataKinds.Phone by contact ID to get actual numbers.
     * This is the standard pattern used by Android contact loader implementations.
     */
    suspend fun getAllContacts(limit: Int = 500): List<Contact> = withContext(Dispatchers.IO) {
        // Step 1: Load contact rows (only those with phone numbers)
        val contactRows = mutableListOf<Contact>()
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.TIMES_CONTACTED,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ),
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER} != 0",
            null,
            "${ContactsContract.Contacts.SORT_KEY_PRIMARY} ASC",
        )

        cursor?.use {
            val idIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookupIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
            val photoThumbIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val starredIdx = it.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)

            while (it.moveToNext()) {
                val id = it.getLong(idIdx)
                contactRows.add(
                    Contact(
                        id = id,
                        lookupKey = it.getString(lookupIdx) ?: "",
                        displayName = it.getString(nameIdx) ?: "",
                        photoUri = it.getString(photoIdx)?.let { u -> Uri.parse(u) },
                        photoThumbnailUri = it.getString(photoThumbIdx)?.let { u -> Uri.parse(u) },
                        isFavorite = it.getInt(starredIdx) == 1,
                    ),
                )
            }
        }

        if (contactRows.isEmpty()) return@withContext emptyList()

        // Step 2: Batch-load phone numbers for all contacts in one query
        val contactIds = contactRows.map { it.id }.toLongArray()
        val phoneNumbersMap = loadPhoneNumbersForContacts(contactIds)

        // Step 3: Attach phone numbers to contacts
        contactRows.map { contact ->
            contact.copy(phoneNumbers = phoneNumbersMap[contact.id] ?: emptyList())
        }
    }

    /**
     * Load all phone numbers for a batch of contacts in a single query.
     * Returns a map of contactId → list of PhoneNumber.
     */
    private fun loadPhoneNumbersForContacts(contactIds: LongArray): Map<Long, List<PhoneNumber>> {
        if (contactIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<Long, MutableList<PhoneNumber>>()
        val placeholders = contactIds.joinToString(",") { "?" }
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN ($placeholders)"

        val phoneCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ),
            selection,
            contactIds.map { it.toString() }.toTypedArray(),
            null,
        )

        phoneCursor?.use {
            val contactIdIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            val normIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)

            while (it.moveToNext()) {
                val contactId = it.getLong(contactIdIdx)
                val phoneNumber = PhoneNumber(
                    number = it.getString(numIdx) ?: "",
                    type = it.getInt(typeIdx),
                    label = it.getString(labelIdx),
                    normalizedNumber = it.getString(normIdx),
                )
                result.getOrPut(contactId) { mutableListOf() }.add(phoneNumber)
            }
        }
        return result
    }
}
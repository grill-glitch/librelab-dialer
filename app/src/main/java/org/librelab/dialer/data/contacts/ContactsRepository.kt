package org.librelab.dialer.data.contacts

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
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
 * Repository for reading contacts.
 * Core logic migrated from SearchFragment / ContactEntry Java code.
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        val CONTACT_PROJECTION = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.STARRED,
            ContactsContract.Contacts.TIMES_CONTACTED,
        )

        val PHONE_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        )
    }

    /**
     * Look up contact by phone number.
     */
    suspend fun lookupContactByNumber(number: String): Contact? = withContext(Dispatchers.IO) {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val cursor: Cursor? = contentResolver.query(
            uri,
            CONTACT_PROJECTION,
            null,
            null,
            null,
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val lookupKey = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: ""
                val photoUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI))?.let { u -> Uri.parse(u) }
                val photoThumbUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI))?.let { u -> Uri.parse(u) }
                val starred = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1

                // Load phone numbers for this contact
                val phones = getPhoneNumbersForContact(id)

                return@withContext Contact(
                    id = id,
                    lookupKey = lookupKey ?: "",
                    displayName = name,
                    photoUri = photoUri,
                    photoThumbnailUri = photoThumbUri,
                    phoneNumbers = phones,
                    isFavorite = starred,
                )
            }
        }
        null
    }

    /**
     * Look up contacts by contact ID.
     */
    suspend fun lookupContactById(id: Long): Contact? = withContext(Dispatchers.IO) {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())
        val cursor: Cursor? = contentResolver.query(
            uri,
            CONTACT_PROJECTION,
            null,
            null,
            null,
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val lookupKey = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: ""
                val photoUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI))?.let { u -> Uri.parse(u) }
                val photoThumbUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI))?.let { u -> Uri.parse(u) }
                val starred = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
                val phones = getPhoneNumbersForContact(id)

                return@withContext Contact(
                    id = id,
                    lookupKey = lookupKey ?: "",
                    displayName = name,
                    photoUri = photoUri,
                    photoThumbnailUri = photoThumbUri,
                    phoneNumbers = phones,
                    isFavorite = starred,
                )
            }
        }
        null
    }

    private fun getPhoneNumbersForContact(contactId: Long): List<PhoneNumber> {
        val phones = mutableListOf<PhoneNumber>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"

        contentResolver.query(uri, PHONE_PROJECTION, selection, arrayOf(contactId.toString()), null)?.use { cursor ->
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            val normIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)

            while (cursor.moveToNext()) {
                phones.add(
                    PhoneNumber(
                        number = cursor.getString(numIdx) ?: "",
                        label = cursor.getString(labelIdx) ?: "",
                        type = cursor.getInt(typeIdx),
                        normalizedNumber = cursor.getString(normIdx),
                    )
                )
            }
        }
        return phones
    }

    /**
     * Get all starred (favorite) contacts.
     */
    suspend fun getFavoriteContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            CONTACT_PROJECTION,
            "${ContactsContract.Contacts.STARRED} = 1",
            null,
            "${ContactsContract.Contacts.TIMES_CONTACTED} DESC",
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val lookupKey = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: ""
                val photoUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI))?.let { u -> Uri.parse(u) }
                val photoThumbUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI))?.let { u -> Uri.parse(u) }

                contacts.add(
                    Contact(
                        id = id,
                        lookupKey = lookupKey ?: "",
                        displayName = name,
                        photoUri = photoUri,
                        photoThumbnailUri = photoThumbUri,
                        isFavorite = true,
                    )
                )
            }
        }
        contacts
    }
}

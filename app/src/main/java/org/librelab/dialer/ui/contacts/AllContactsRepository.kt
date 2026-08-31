package org.librelab.dialer.ui.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.librelab.dialer.domain.model.Contact
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

    suspend fun getAllContacts(limit: Int = 500): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
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
                contacts.add(
                    Contact(
                        id = id,
                        lookupKey = it.getString(lookupIdx) ?: "",
                        displayName = it.getString(nameIdx) ?: "",
                        photoUri = it.getString(photoIdx)?.let { u -> Uri.parse(u) },
                        photoThumbnailUri = it.getString(photoThumbIdx)?.let { u -> Uri.parse(u) },
                        isFavorite = it.getInt(starredIdx) == 1,
                    )
                )
            }
        }
        contacts
    }
}
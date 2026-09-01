package org.librelab.dialer.ui.calllog

import android.content.ContentResolver
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librelab.dialer.domain.model.CallLogEntry
import org.librelab.dialer.domain.model.CallLogGroup
import org.librelab.dialer.domain.model.CallType
import org.librelab.dialer.domain.model.Contact
import org.librelab.dialer.domain.model.PhoneNumber
import javax.inject.Inject

/**
 * ViewModel for CallLogDetailActivity.
 * Loads all call log entries for the same number as the given entry ID.
 */
@HiltViewModel
class CallLogDetailViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val cr = context.contentResolver

    private val _callLogGroup = MutableStateFlow<CallLogGroup?>(null)
    val callLogGroup: StateFlow<CallLogGroup?> = _callLogGroup.asStateFlow()

    init {
        val groupKey = savedStateHandle.get<String>("groupKey")
            ?: savedStateHandle.get<Long>("groupId")?.toString()
        if (groupKey != null) {
            loadGroup(groupKey)
        }
    }

    fun loadGroup(key: String) {
        viewModelScope.launch {
            _callLogGroup.value = queryCallLogGroup(key)
        }
    }

    private fun queryCallLogGroup(key: String): CallLogGroup? {
        cr.query(
            CallLog.Calls.CONTENT_URI,
            PROJECTION,
            "${CallLog.Calls.NUMBER} = ?",
            arrayOf(key),
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use

            val entries = mutableListOf<CallLogEntry>()
            val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val readIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.IS_READ)
            val countryIdx = cursor.getColumnIndex(CallLog.Calls.COUNTRY_ISO)
            val geocIdx = cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
            val voicemailIdx = cursor.getColumnIndex(CallLog.Calls.VOICEMAIL_URI)

            val number = cursor.getString(numberIdx) ?: return@use
            val displayName = lookupContactName(cr, number)
            val photoUri = lookupContactPhotoUri(cr, number)

            do {
                entries.add(
                    CallLogEntry(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID)),
                        number = number,
                        displayName = displayName,
                        photoUri = photoUri,
                        callType = CallType.fromRaw(cursor.getInt(typeIdx)),
                        timestamp = cursor.getLong(dateIdx),
                        duration = cursor.getLong(durationIdx),
                        simId = 0,
                        isRead = cursor.getInt(readIdx) == 1,
                        countryIso = if (countryIdx >= 0) cursor.getString(countryIdx) else null,
                        geocodedLocation = if (geocIdx >= 0) cursor.getString(geocIdx) else null,
                        voicemailUri = if (voicemailIdx >= 0) cursor.getString(voicemailIdx)?.let { android.net.Uri.parse(it) } else null,
                    )
                )
            } while (cursor.moveToNext())

            if (entries.isEmpty()) return@use

            val contact = if (displayName.isNotEmpty()) {
                Contact(
                    id = 0L,
                    lookupKey = "",
                    displayName = displayName,
                    photoUri = photoUri,
                    photoThumbnailUri = null,
                    phoneNumbers = listOf(
                        PhoneNumber(
                            number = number,
                            label = "手机",
                            type = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                        )
                    ),
                    isFavorite = false,
                )
            } else null

            return entries.takeIf { it.isNotEmpty() }?.let {
                CallLogGroup(id = number, entries = it, contact = contact)
            }
        }
        return null
    }

    private fun lookupContactName(cr: ContentResolver, number: String): String {
        // Use the PhoneLookup content provider to look up contact by number
        val lookupUri = android.net.Uri.parse("content://com.android.contacts/phone_lookup/$number")
        cr.query(lookupUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: ""
            }
        }
        return ""
    }

    private fun lookupContactPhotoUri(cr: ContentResolver, number: String): android.net.Uri? {
        val lookupUri = android.net.Uri.parse("content://com.android.contacts/phone_lookup/$number")
        cr.query(lookupUri, arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                if (idx >= 0) {
                    cursor.getString(idx)?.let { return android.net.Uri.parse(it) }
                }
            }
        }
        return null
    }

    fun deleteGroup() {
        _callLogGroup.value?.entries?.forEach { entry ->
            try {
                cr.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} = ?",
                    arrayOf(entry.id.toString()),
                )
            } catch (_: SecurityException) {
                // Requires CALL_LOG permission on some Android versions
            }
        }
    }

    companion object {
        private val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.IS_READ,
            CallLog.Calls.COUNTRY_ISO,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.VOICEMAIL_URI,
        )
    }
}

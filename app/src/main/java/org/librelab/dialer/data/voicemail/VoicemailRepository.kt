package org.librelab.dialer.data.voicemail

import android.content.Context
import android.net.Uri
import android.provider.VoicemailContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class VoicemailEntry(
    val id: Long,
    val number: String,
    val displayName: String,
    val timestamp: Long,
    val duration: Long, // seconds
    val transcription: String?,
    val audioUri: Uri?,
    val isRead: Boolean,
)

/**
 * VoicemailRepository — reads voicemails from the system VoicemailContract.
 * Replaces VoicemailClientImpl.java with a simpler Kotlin implementation.
 */
@Singleton
class VoicemailRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val VOICEMAIL_PROJECTION: Array<String> = arrayOf(
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DISPLAY_NAME,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION,
            VoicemailContract.Voicemails.TRANSCRIPTION,
            VoicemailContract.Voicemails.HAS_CONTENT,
            VoicemailContract.Voicemails.IS_READ,
        )
    }

    suspend fun getVoicemails(limit: Int = 100): List<VoicemailEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<VoicemailEntry>()
        val cursor = context.contentResolver.query(
            VoicemailContract.Voicemails.CONTENT_URI,
            VOICEMAIL_PROJECTION,
            null,
            null,
            "${VoicemailContract.Voicemails.DATE} DESC LIMIT $limit",
        )

        cursor?.use {
            val idIdx = it.getColumnIndexOrThrow(VoicemailContract.Voicemails._ID)
            val numIdx = it.getColumnIndexOrThrow(VoicemailContract.Voicemails.NUMBER)
            val nameIdx = it.getColumnIndexOrThrow(VoicemailContract.Voicemails.DISPLAY_NAME)
            val dateIdx = it.getColumnIndexOrThrow(VoicemailContract.Voicemails.DATE)
            val durIdx = it.getColumnIndexOrThrow(VoicemailContract.Voicemails.DURATION)
            val transIdx = it.getColumnIndexOrThrow(VoicemailContract.Voicemails.TRANSCRIPTION)
            val readIdx = it.getColumnIndexOrThrow(VoicemailContract.Voicemails.IS_READ)

            while (it.moveToNext()) {
                val id = it.getLong(idIdx)
                entries.add(
                    VoicemailEntry(
                        id = id,
                        number = it.getString(numIdx) ?: "",
                        displayName = it.getString(nameIdx) ?: "",
                        timestamp = it.getLong(dateIdx),
                        duration = it.getLong(durIdx),
                        transcription = it.getString(transIdx),
                        audioUri = Uri.withAppendedPath(
                            VoicemailContract.Voicemails.CONTENT_URI,
                            id.toString(),
                        ),
                        isRead = it.getInt(readIdx) == 1,
                    )
                )
            }
        }
        entries
    }

    suspend fun markAsRead(id: Long) = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(VoicemailContract.Voicemails.IS_READ, 1)
        }
        context.contentResolver.update(
            Uri.withAppendedPath(VoicemailContract.Voicemails.CONTENT_URI, id.toString()),
            values,
            null,
            null,
        )
    }

    suspend fun deleteVoicemail(id: Long) = withContext(Dispatchers.IO) {
        context.contentResolver.delete(
            Uri.withAppendedPath(VoicemailContract.Voicemails.CONTENT_URI, id.toString()),
            null,
            null,
        )
    }
}

package org.librelab.dialer.data.callrecord

import android.provider.MediaStore

/**
 * Represents a single call recording.
 *
 * Mirrors crDroid's `CallRecording.java`.
 */
data class CallRecording(
    /** Phone number that was recorded (may be null for unknown numbers). */
    val phoneNumber: String?,
    /** Wall-clock time when recording started (millis since epoch). */
    val creationTime: Long,
    /** Original filename on disk. */
    val fileName: String,
    /** Time when recording was started (millis since epoch). */
    val startTime: Long,
    /** MediaStore media ID for the recorded audio file. */
    val mediaId: Long,
) {

    companion object {
        /** Create an insert values bundle for MediaStore. */
        fun generateMediaInsertValues(
            fileName: String,
            creationTime: Long,
        ): android.content.ContentValues {
            return android.content.ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/amr-wb")
                put(MediaStore.Audio.Media.TITLE, fileName)
                put(MediaStore.Audio.Media.SIZE, 0L)
                put(MediaStore.Audio.Media.DATE_ADDED, creationTime / 1000)
                put(MediaStore.Audio.Media.DATE_MODIFIED, creationTime / 1000)
            }
        }

        /** Create an update values bundle marking the recording as completed. */
        fun generateCompletedValues(): android.content.ContentValues {
            return android.content.ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
        }
    }
}

package org.librelab.dialer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import org.librelab.dialer.R
import org.librelab.dialer.data.callrecord.CallRecording
import org.librelab.dialer.ui.MainActivity
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Call recording service — Kotlin reimplementation of crDroid's CallRecorderService.
 *
 * Records active calls to MediaStore (AMR-WB or M4A/AAC depending on format setting).
 * Runs as a foreground service with a persistent notification while recording.
 *
 * Key differences from crDroid:
 * - Uses Kotlin coroutines for async operations
 * - AIDL binding replaced with a simple Binder interface
 * - Foreground notification created on start
 *
 * crDroid's `ICallRecorderService.aidl` is replaced by direct method calls
 * on the bound service instance.
 */
class CallRecorderService : Service() {

    private val tag = "CallRecorderService"
    private val notifChannelId = "call_recording"

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecording: CallRecording? = null
    private val binder = LocalBinder()

    private val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    inner class LocalBinder : Binder() {
        fun getService(): CallRecorderService = this@CallRecorderService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Creating CallRecorderService")
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Destroying CallRecorderService")
        stopRecordingInternal()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            notifChannelId,
            getString(R.string.recording_active),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Call recording in progress"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, notifChannelId)
            .setContentTitle(getString(R.string.recording_active))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun getAudioSource(): Int {
        // Voice communication source captures both sides of the call
        return android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
    }

    private fun getAudioFormatChoice(): Int {
        val prefs = getSharedPreferences("${packageName}_preferences", MODE_MULTI_PROCESS)
        return prefs.getString(getString(R.string.call_recording_format_key), null)
            ?.toIntOrNull() ?: 0
    }

    /**
     * Start recording for the given phone number.
     *
     * @param phoneNumber number being called (for the filename)
     * @param creationTime wall-clock time when the call was initiated
     * @return true if recording started successfully
     */
    fun startRecording(phoneNumber: String?, creationTime: Long): Boolean {
        if (mediaRecorder != null) {
            Log.d(tag, "Recording already in progress, stopping current")
            stopRecordingInternal()
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(tag, "RECORD_AUDIO permission not granted, can't record")
            return false
        }

        Log.d(tag, "Starting recording")

        mediaRecorder = MediaRecorder(applicationContext)
        try {
            val audioSource = getAudioSource()
            val formatChoice = getAudioFormatChoice()
            mediaRecorder?.apply {
                setAudioSource(audioSource)
                setOutputFormat(
                    if (formatChoice == 0) {
                        MediaRecorder.OutputFormat.AMR_WB
                    } else {
                        MediaRecorder.OutputFormat.MPEG_4
                    }
                )
                setAudioEncoder(
                    if (formatChoice == 0) {
                        MediaRecorder.AudioEncoder.AMR_WB
                    } else {
                        MediaRecorder.AudioEncoder.AAC
                    }
                )
            }
        } catch (e: IllegalStateException) {
            Log.e(tag, "Error initializing media recorder", e)
            releaseMediaRecorder()
            return false
        }

        val fileName = generateFileName(phoneNumber)
        val uri = contentResolver.insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            CallRecording.generateMediaInsertValues(fileName, creationTime),
        ) ?: return false

        try {
            val pfd = contentResolver.openFileDescriptor(uri, "w")
                ?: throw IOException("Opening file for URI $uri failed")
            mediaRecorder?.apply {
                setOutputFile(pfd.fileDescriptor)
                prepare()
            }
            mediaRecorder?.start()

            val mediaId = uri.lastPathSegment?.toLongOrNull() ?: 0L
            currentRecording = CallRecording(
                phoneNumber = phoneNumber,
                creationTime = creationTime,
                fileName = fileName,
                startTime = System.currentTimeMillis(),
                mediaId = mediaId,
            )
            Log.d(tag, "Recording started: $fileName")
            return true
        } catch (e: IOException) {
            Log.w(tag, "Could not start recording", e)
            contentResolver.delete(uri, null, null)
        } catch (e: IllegalStateException) {
            Log.w(tag, "Could not start recording", e)
            contentResolver.delete(uri, null, null)
        } catch (e: RuntimeException) {
            contentResolver.delete(uri, null, null)
            if (e.message?.contains("start failed") == true) {
                Log.w(tag, "Could not start recording", e)
            } else {
                throw e
            }
        }

        releaseMediaRecorder()
        return false
    }

    /**
     * Stop recording and finalize the file.
     *
     * @return the CallRecording metadata, or null if nothing was recording
     */
    fun stopRecording(): CallRecording? {
        return stopRecordingInternal()
    }

    /**
     * Whether recording is currently active.
     */
    fun isRecording(): Boolean = mediaRecorder != null

    /**
     * Get the active recording metadata.
     */
    fun getActiveRecording(): CallRecording? = currentRecording

    private fun stopRecordingInternal(): CallRecording? {
        val recording = currentRecording
        Log.d(tag, "Stopping current recording")
        mediaRecorder?.let { mr ->
            try {
                mr.stop()
            } catch (e: IllegalStateException) {
                Log.e(tag, "Exception closing media recorder", e)
            }
            releaseMediaRecorder()
        }

        recording?.let {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                it.mediaId,
            )
            contentResolver.update(uri, CallRecording.generateCompletedValues(), null, null)
        }

        currentRecording = null
        return recording
    }

    private fun generateFileName(phoneNumber: String?): String {
        val timestamp = dateFormat.format(Date())
        val number = phoneNumber?.takeIf { it.isNotEmpty() } ?: "unknown"
        val formatChoice = getAudioFormatChoice()
        val extension = if (formatChoice == 0) ".amr" else ".m4a"
        return "CallRecord_$timestamp _$number$extension"
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.reset()
        } catch (_: Exception) {
            // ignore
        }
        mediaRecorder?.release()
        mediaRecorder = null
    }

    companion object {
        private const val NOTIF_ID = 1001

        /**
         * Whether call recording is enabled on this device.
         * Override in resources: bool/call_recording_enabled
         */
        fun isEnabled(context: Context): Boolean {
            return try {
                context.resources.getBoolean(R.bool.call_recording_enabled)
            } catch (_: android.content.res.Resources.NotFoundException) {
                false
            }
        }
    }
}

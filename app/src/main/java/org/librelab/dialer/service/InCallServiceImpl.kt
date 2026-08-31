package org.librelab.dialer.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import org.librelab.dialer.data.incall.CallManager
import org.librelab.dialer.lookup.ContactLookupService
import org.librelab.dialer.postcall.PostCallManager
import org.librelab.dialer.ui.incall.InCallActivity
import org.librelab.dialer.ui.settings.SettingsPrefs
import javax.inject.Inject

/**
 * InCallService — system-bound service for managing active calls.
 *
 * Implements:
 * - In-call vibration (outgoing answered, call waiting, hangup, 45s warning)
 * - In-call DND (on call connect)
 * - Smart mute (proximity sensor / face-down detection)
 * - Auto-start recording (on call connect)
 *
 * Migrated from Java to Kotlin.
 */
@AndroidEntryPoint
class InCallServiceImpl : InCallService(), SensorEventListener {

    private val TAG = "InCallServiceImpl"

    companion object {
        // Call.STATE_* constants — values are stable in Android SDK
        private const val STATE_IDLE = 0
        private const val STATE_RINGING = 1
        private const val STATE_DIALING = 2
        private const val STATE_CONNECTING = 3
        private const val STATE_ACTIVE = 4
        private const val STATE_HOLDING = 5
        private const val STATE_DISCONNECTED = 6

        // Call.Callback.EVENT_CALL_WAITING — exact string value from SDK
        private const val EVENT_CALL_WAITING = "android.telecom.event.CALL_WAITING"

        private const val VIBRATE_DURATION_MS = 200L
        private const val VIBRATE_45S_BEFORE_DISCONNECT_MS = 45_000L
    }

    @Inject
    lateinit var callManager: CallManager

    @Inject
    lateinit var settingsPrefs: SettingsPrefs

    @Inject
    lateinit var postCallManager: PostCallManager

    @Inject
    lateinit var contactLookupService: ContactLookupService

    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null

    private lateinit var mainHandler: Handler

    // Per-call vibration flags
    private var hasVibratedForOutgoingAnswer = false
    private var hasVibratedForHangup = false
    private var hasScheduled45sVibration = false
    private var vibrate45sRunnable: Runnable? = null

    // Smart mute state
    private var isSmartMuteEnabled = false
    private var isSmartMuteActive = false

    // DND state
    private var wasDndEnabledBeforeCall = false

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            refreshCallList()
            handleCallStateChanged(call, state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            refreshCallList()
        }

        override fun onConnectionEvent(call: Call, event: String, extras: Bundle) {
            super.onConnectionEvent(call, event, extras)
            if (EVENT_CALL_WAITING == event) {
                handleCallWaiting()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val thread = HandlerThread("InCallServiceThread").apply { start() }
        mainHandler = Handler(thread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.registerCallback(callCallback, mainHandler)
        refreshCallList()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        refreshCallList()
        cancel45sVibration()
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) {
        super.onCanAddCallChanged(canAddCall)
    }

    @Suppress("DEPRECATION")
    override fun onSilenceRinger() {
        super.onSilenceRinger()
    }

    /**
     * Called by Telecom when the in-call UI should be brought to the foreground.
     * With IN_CALL_SERVICE_UI=true, Telecom calls this to request we show our UI.
     * We start InCallActivity which contains our Compose InCallScreen.
     */
    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (showDialpad) {
                putExtra("SHOW_DIALPAD", true)
            }
        }
        startActivity(intent)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        if (::callManager.isInitialized) {
            callManager.updateAudioState(audioState)
        }
        if (isSmartMuteEnabled) {
            syncSmartMute()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        cancel45sVibration()
        restoreDndState()
        stopRecording()
        unbindCallRecorderService()
        // Clear post-call state
        postCallManager.clear()
    }

    // ─── Call state handling ────────────────────────────────────────────────

    private fun handleCallStateChanged(call: Call, state: Int) {
        when (state) {
            STATE_ACTIVE -> onCallConnected(call)
            STATE_DISCONNECTED -> onCallDisconnected(call)
            // RINGING, DIALING, CONNECTING, HOLDING, IDLE — no special action
        }
    }

    private fun onCallConnected(call: Call) {
        callConnectTime = System.currentTimeMillis()

        // Incoming call answered
        vibrateIfEnabled("incall_vibrate_call_waiting", VIBRATE_DURATION_MS)

        // Outgoing call answered (first ACTIVE after DIALING)
        if (!hasVibratedForOutgoingAnswer) {
            hasVibratedForOutgoingAnswer = true
            vibrateIfEnabled("incall_vibrate_outgoing", VIBRATE_DURATION_MS)
        }

        // Auto-start recording
        if (::settingsPrefs.isInitialized && settingsPrefs.getBoolean("recording_auto_start", false)) {
            startRecording()
        }

        // In-call DND
        if (::settingsPrefs.isInitialized && settingsPrefs.getBoolean("incall_dnd", false)) {
            enableInCallDnd()
        }

        // Smart mute
        if (::settingsPrefs.isInitialized && settingsPrefs.getBoolean("smart_mute", false)) {
            enableSmartMute()
        }

        // Schedule 45s warning vibration
        if (::settingsPrefs.isInitialized && settingsPrefs.getBoolean("incall_vibrate_45", false)) {
            schedule45sVibration()
        }
    }

    private fun onCallDisconnected(call: Call) {
        // Hangup vibration
        if (!hasVibratedForHangup) {
            hasVibratedForHangup = true
            vibrateIfEnabled("incall_vibrate_hangup", VIBRATE_DURATION_MS)
        }

        // Post-call: record disconnect so UI can prompt SMS
        val number = call.details?.handle?.schemeSpecificPart
        postCallManager.onCallDisconnected(number, callConnectTime)

        // Contact lookup: find contact name and photo for the call
        val lookupResult = contactLookupService.lookupContact(number ?: "")
        if (lookupResult != null) {
            callManager.updateContactInfo(
                number = number ?: "",
                name = lookupResult.name,
                photoUri = lookupResult.photoUri?.toString(),
                numberTypeLabel = contactLookupService.getNumberTypeLabel(
                    lookupResult.numberType,
                    lookupResult.numberLabel,
                ),
            )
        }

        // Cleanup
        cancel45sVibration()
        disableSmartMute()
        restoreDndState()
        stopRecording()
        unbindCallRecorderService()

        // Reset per-call flags for next call
        hasVibratedForOutgoingAnswer = false
        hasVibratedForHangup = false
    }

    private fun handleCallWaiting() {
        vibrateIfEnabled("incall_vibrate_call_waiting", VIBRATE_DURATION_MS)
    }

    // ─── Vibration ─────────────────────────────────────────────────────────

    private fun vibrateIfEnabled(prefKey: String, durationMs: Long) {
        if (!::settingsPrefs.isInitialized) return
        if (!settingsPrefs.getBoolean(prefKey, false)) return
        if (!vibrator.hasVibrator()) return

        val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(effect)
    }

    private fun schedule45sVibration() {
        cancel45sVibration()
        hasScheduled45sVibration = true
        vibrate45sRunnable = Runnable {
            vibrateIfEnabled("incall_vibrate_45", VIBRATE_DURATION_MS)
            hasScheduled45sVibration = false
        }
        mainHandler.postDelayed(vibrate45sRunnable!!, VIBRATE_45S_BEFORE_DISCONNECT_MS)
    }

    private fun cancel45sVibration() {
        if (hasScheduled45sVibration && vibrate45sRunnable != null) {
            mainHandler.removeCallbacks(vibrate45sRunnable!!)
            hasScheduled45sVibration = false
        }
    }

    // ─── In-call DND ───────────────────────────────────────────────────────

    private fun enableInCallDnd() {
        if (!notificationManager.isNotificationPolicyAccessGranted) return

        val currentFilter = notificationManager.currentInterruptionFilter
        wasDndEnabledBeforeCall = currentFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        if (!wasDndEnabledBeforeCall) {
            @Suppress("NewApi")
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
    }

    private fun restoreDndState() {
        if (!notificationManager.isNotificationPolicyAccessGranted) return

        if (!wasDndEnabledBeforeCall) {
            @Suppress("NewApi")
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        wasDndEnabledBeforeCall = false
    }

    // ─── Smart mute (proximity / face-down detection) ───────────────────────

    private fun enableSmartMute() {
        isSmartMuteEnabled = true
        isSmartMuteActive = false
        proximitySensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun disableSmartMute() {
        isSmartMuteEnabled = false
        isSmartMuteActive = false
        sensorManager.unregisterListener(this)
        if (isSmartMuteActive) {
            setMuted(false)
        }
    }

    private fun syncSmartMute() {
        // Re-evaluate mute state when audio route changes
        if (!isSmartMuteEnabled) return
        // Sensor listener fires on the next proximity event; no action needed here
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isSmartMuteEnabled) return
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: 5f
        val isNear = distance < maxRange

        if (isNear && !isSmartMuteActive) {
            isSmartMuteActive = true
            setMuted(true)
        } else if (!isNear && isSmartMuteActive) {
            isSmartMuteActive = false
            setMuted(false)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not needed
    }

    // ─── Recording ─────────────────────────────────────────────────────────

    private var callRecorderService: CallRecorderService? = null
    private var serviceBound = false
    private var callConnectTime: Long = 0L

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CallRecorderService.LocalBinder
            callRecorderService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            callRecorderService = null
            serviceBound = false
        }
    }

    private fun bindCallRecorderService() {
        if (!serviceBound) {
            val intent = Intent(this, CallRecorderService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindCallRecorderService() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            callRecorderService = null
        }
    }

    private fun startRecording() {
        if (!CallRecorderService.isEnabled(this)) {
            Log.d(TAG, "startRecording: not available on this device")
            return
        }

        bindCallRecorderService()

        val phoneNumber = getCurrentForegroundCallHandle()
        val success = callRecorderService?.startRecording(phoneNumber, callConnectTime) ?: false
        Log.d(TAG, "startRecording: started=$success, number=$phoneNumber")
    }

    private fun stopRecording() {
        val recording = callRecorderService?.stopRecording()
        Log.d(TAG, "stopRecording: $recording")
    }

    private fun getCurrentForegroundCallHandle(): String? {
        val callList = calls.orEmpty()
        for (call in callList) {
            when (call.state) {
                STATE_ACTIVE, STATE_RINGING, STATE_DIALING, STATE_CONNECTING -> {
                    return call.details?.handle?.toString()
                }
            }
        }
        return callList.firstOrNull()?.details?.handle?.toString()
    }

    // ─── Internal helpers ─────────────────────────────────────────────────

    private fun refreshCallList() {
        if (!::callManager.isInitialized) return

        val calls = calls.orEmpty()
        callManager.updateCalls(calls)

        for (call in calls) {
            val id = call.details?.handle?.toString() ?: call.hashCode().toString()
            callManager.setTelecomCall(id, call)
        }
    }

    private fun getCurrentForegroundCall(): Call? {
        val callList = calls.orEmpty()
        if (callList.isEmpty()) return null
        for (call in callList) {
            when (call.state) {
                STATE_ACTIVE, STATE_RINGING, STATE_DIALING, STATE_CONNECTING -> return call
            }
        }
        return callList.firstOrNull()
    }
}

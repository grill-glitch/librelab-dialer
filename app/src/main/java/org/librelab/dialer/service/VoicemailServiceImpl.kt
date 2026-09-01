package org.librelab.dialer.service

import android.util.Log

/**
 * VoicemailService — stub implementation.
 *
 * android.telecom.VoicemailService is a SYSTEM-ONLY API (not available in the
 * public Android SDK, compileSdk=37). The telecom JAR containing this class is
 * not present in the SDK platforms/android-37/android.jar.
 *
 * Actual voicemail handling is done via:
 *   1. CallScreeningService — receives voicemail insertion events from Telecom
 *   2. VoicemailContract — reads/writes voicemails from the system provider
 *   3. VoicemailRepository — query/mark-as-read/delete operations
 *
 * To re-enable VoicemailService:
 *   - Obtain a telecom-stub.jar from AOSP system image that contains
 *     android.telecom.VoicemailService
 *   - Add it as a provided-only compile dependency (not packaged into APK)
 *   - Uncomment the <service> entry in AndroidManifest.xml
 *
 * @see <a href="https://developer.android.com/reference/android/telecom/VoicemailService">VoicemailService</a>
 */
@Suppress("UNUSED")
class VoicemailServiceImpl /* : VoicemailService() */ {

    companion object {
        private const val TAG = "VoicemailServiceImpl"
    }

    // Methods below are stubs — uncomment when VoicemailService SDK is available:
    //
    // override fun onVoicemailMigrationStarted() { }
    // override fun onVoicemailMigrationFinished() { }
    // override fun onInsertVoicemail(number: String?, voicemail: Voicemail) { }
    // override fun onRetrieveVoicemail(voicemail: Voicemail) { }
    // override fun onSetVoicemail(voicemail: Voicemail) { }
    // override fun onDeleteVoicemailAffectedNumbers(phoneNumbers: MutableList<String>) { }

    init {
        Log.w(TAG, "VoicemailServiceImpl is a stub — android.telecom.VoicemailService not in SDK")
    }
}

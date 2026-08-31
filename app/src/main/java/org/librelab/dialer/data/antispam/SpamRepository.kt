package org.librelab.dialer.data.antispam

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librelab.dialer.ui.settings.SettingsPrefs
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SpamRepository — manages spam numbers, blocked numbers, and anti-spam lookups.
 *
 * Anti-spam lookups use the MIUI / HyperOS Yellow Page API (via MiAntiSpamClient),
 * which provides real crowd-sourced marking data for Chinese phone numbers.
 *
 * Caching strategy:
 * - Incoming calls: lookup result is cached for 1 hour (catId + count + source)
 * - Blocked/spam lists: persisted to SharedPreferences, loaded into memory on start
 * - Yellow-page lookups: cached for 24 hours
 *
 * No Xiaomi account required — device identity is auto-generated and persisted.
 */
@Singleton
class SpamRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPrefs,
    private val miClient: MiAntiSpamClient,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("librelab_antispam", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── In-memory blocklists (loaded from SharedPreferences) ───────────────

    private val _spamNumbers = MutableStateFlow(loadStringSet(KEY_SPAM))
    val spamNumbers: StateFlow<Set<String>> = _spamNumbers.asStateFlow()

    private val _blockedNumbers = MutableStateFlow(loadStringSet(KEY_BLOCKED))
    val blockedNumbers: StateFlow<Set<String>> = _blockedNumbers.asStateFlow()

    // ─── API lookup cache (number → cached result) ─────────────────────────

    // Anti-spam cache: number → (LookupResult, timestamp)
    private val spamCache = mutableMapOf<String, CachedResult>()
    private val spamCacheTtlMs = TimeUnit.HOURS.toMillis(1)

    // Yellow-page cache: number → (LookupResult, timestamp)
    private val yellowPageCache = mutableMapOf<String, CachedResult>()
    private val yellowPageCacheTtlMs = TimeUnit.HOURS.toMillis(24)

    // ─── Blocklist checks (synchronous, no network) ────────────────────────

    /**
     * Check if a number is manually marked as spam.
     */
    fun isSpamNumber(number: String): Boolean {
        return _spamNumbers.value.contains(normalize(number))
    }

    /**
     * Check if a number is explicitly blocked by the user.
     */
    fun isBlockedNumber(number: String): Boolean {
        return _blockedNumbers.value.contains(normalize(number))
    }

    /**
     * Combined synchronous check — user blocklist + manually marked spam.
     * Use this for fast pre-filtering before any network call.
     */
    fun shouldRejectCall(number: String): Boolean {
        return isBlockedNumber(number) || isSpamNumber(number)
    }

    /**
     * Check if the MIUI API marks this number as spam (anti-spam category, count > threshold).
     * Returns the mark info if found and above the reporting threshold, null otherwise.
     */
    fun getSpamMarkInfo(number: String): MiAntiSpamClient.AntiSpamMark? {
        if (!settingsPrefs.getBoolean("anti_spam_enabled", true)) return null
        val norm = normalize(number)
        val cached = spamCache[norm]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < spamCacheTtlMs) {
            return cached.result?.antiSpamMark
        }
        return null
    }

    /**
     * Async lookup — queries the MIUI API and updates the in-memory cache.
     * Should be called when a call arrives (in CallScreeningService or InCallService).
     * Does NOT block; runs in the repository's CoroutineScope.
     *
     * @param number raw phone number
     * @param onResult callback with the lookup result (called on IO dispatcher)
     */
    fun lookupAsync(number: String, onResult: (MiAntiSpamClient.LookupResult?) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                lookupInternal(number)
            }
            onResult(result)
        }
    }

    /**
     * Synchronous lookup — fetches encrypt key then queries the MIUI API.
     * Call this from a background thread only (e.g. inside a coroutine with IO dispatcher).
     */
    suspend fun lookup(number: String): MiAntiSpamClient.LookupResult? {
        return withContext(Dispatchers.IO) {
            lookupInternal(number)
        }
    }

    private fun lookupInternal(number: String): MiAntiSpamClient.LookupResult? {
        val norm = normalize(number)
        if (norm.length < 7) return null

        // Check cache first
        val cached = spamCache[norm]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < spamCacheTtlMs) {
            return cached.result
        }

        return try {
            val key = miClient.fetchEncryptKey()
            val result = miClient.lookup(number, key)
            if (result != null) {
                spamCache[norm] = CachedResult(result, System.currentTimeMillis())
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true if the number has a spam mark in the MIUI database with
     * at least [minReports] reports, and the category is in the user's enabled set.
     *
     * @param number raw phone number
     * @param minReports minimum report count to consider the mark trustworthy (default 3)
     */
    fun isSpamByMiui(number: String, minReports: Int = 3): Boolean {
        if (!settingsPrefs.getBoolean("anti_spam_enabled", true)) return false
        val norm = normalize(number)

        val cached = spamCache[norm]
        if (cached == null || System.currentTimeMillis() - cached.timestamp >= spamCacheTtlMs) {
            return false
        }

        val mark = cached.result?.antiSpamMark ?: return false
        if (mark.count < minReports) return false

        // Check if this category is enabled by the user
        val catEnabled = when (mark.catId) {
            1 -> settingsPrefs.getBoolean("anti_spam_block_high_risk", true)
            2 -> settingsPrefs.getBoolean("anti_spam_block_real_estate", false)
            3 -> settingsPrefs.getBoolean("anti_spam_block_ads", false)
            5 -> settingsPrefs.getBoolean("anti_spam_block_delivery", false)
            6 -> settingsPrefs.getBoolean("anti_spam_block_loan", false)
            13 -> settingsPrefs.getBoolean("anti_spam_block_education", false)
            14 -> settingsPrefs.getBoolean("anti_spam_block_repair", false)
            21 -> settingsPrefs.getBoolean("anti_spam_block_insurance", false)
            else -> false
        }
        return catEnabled
    }

    // ─── User blocklist management ──────────────────────────────────────────

    fun markAsSpam(number: String) {
        val updated = _spamNumbers.value + normalize(number)
        _spamNumbers.value = updated
        saveStringSet(KEY_SPAM, updated)
    }

    fun unmarkAsSpam(number: String) {
        val updated = _spamNumbers.value - normalize(number)
        _spamNumbers.value = updated
        saveStringSet(KEY_SPAM, updated)
    }

    fun blockNumber(number: String) {
        val updated = _blockedNumbers.value + normalize(number)
        _blockedNumbers.value = updated
        saveStringSet(KEY_BLOCKED, updated)
    }

    fun unblockNumber(number: String) {
        val updated = _blockedNumbers.value - normalize(number)
        _blockedNumbers.value = updated
        saveStringSet(KEY_BLOCKED, updated)
    }

    // ─── Reverse lookup (PhoneLookup, no network) ───────────────────────────

    /**
     * Find the contact name for a phone number using the system PhoneLookup provider.
     *
     * @param number raw phone number (with or without country code)
     * @return contact display name, or null if not found
     */
    fun lookupContact(number: String): String? {
        if (!settingsPrefs.getBoolean("reverse_lookup_enabled", true)) return null

        val normalized = normalize(number)
        if (normalized.length < 7) return null

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, normalized)

        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )
            if (cursor?.moveToFirst() == true) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun normalize(number: String): String {
        // Remove non-digits and take the last 10 digits (local number)
        return number.replace(Regex("[^0-9]"), "").takeLast(10)
    }

    private fun loadStringSet(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    }

    private fun saveStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    private data class CachedResult(
        val result: MiAntiSpamClient.LookupResult?,
        val timestamp: Long,
    )

    companion object {
        private const val KEY_SPAM = "spam_numbers"
        private const val KEY_BLOCKED = "blocked_numbers"
    }
}

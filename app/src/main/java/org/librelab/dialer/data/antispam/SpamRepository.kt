package org.librelab.dialer.data.antispam

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librelab.dialer.ui.settings.SettingsPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SpamRepository — manages spam and blocked numbers.
 * Persisted via SharedPreferences (production would use DataStore + Room).
 *
 * Also implements category-based spam detection based on user-enabled settings:
 * - Real estate (房产推销)
 * - Ads (广告推销)
 * - Delivery (快递推销)
 * - Loan (贷款推销)
 * - Education (教育培训)
 * - Repair (维修服务)
 * - Insurance (保险推销)
 *
 * Category matching uses prefix/regex patterns on normalized numbers.
 */
@Singleton
class SpamRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPrefs,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("librelab_antispam", Context.MODE_PRIVATE)

    private val _spamNumbers = MutableStateFlow(loadSet(KEY_SPAM))
    val spamNumbers: StateFlow<Set<String>> = _spamNumbers.asStateFlow()

    private val _blockedNumbers = MutableStateFlow(loadSet(KEY_BLOCKED))
    val blockedNumbers: StateFlow<Set<String>> = _blockedNumbers.asStateFlow()

    /**
     * Check if a number is marked as spam (manually reported).
     */
    fun isSpamNumber(number: String): Boolean {
        return _spamNumbers.value.contains(normalize(number))
    }

    /**
     * Check if a number is explicitly blocked (user-added to blocklist).
     */
    fun isBlockedNumber(number: String): Boolean {
        return _blockedNumbers.value.contains(normalize(number))
    }

    /**
     * Check if a number matches any enabled anti-spam category.
     *
     * Returns true if:
     * - The global anti-spam setting is enabled AND
     * - Any enabled category matches this number
     *
     * Category matching is done via prefix/regex on the raw number string.
     */
    fun isBlockedByCategory(number: String): Boolean {
        if (!settingsPrefs.getBoolean("anti_spam_enabled", true)) return false

        val normalized = normalize(number)

        // High risk — numbers that are short or follow suspicious patterns
        // In production this would call a risk-score API. Here we use simple heuristics.
        if (settingsPrefs.getBoolean("anti_spam_block_high_risk", true)) {
            if (isHighRiskNumber(number)) return true
        }

        // Category patterns — these are approximate prefix matches
        // In production these would be backed by a periodically-updated database
        if (settingsPrefs.getBoolean("anti_spam_block_real_estate", false)) {
            if (REAL_ESTATE_PATTERNS.any { pattern -> number.contains(pattern) }) return true
        }

        if (settingsPrefs.getBoolean("anti_spam_block_ads", false)) {
            if (ADS_PATTERNS.any { pattern -> number.contains(pattern) }) return true
        }

        if (settingsPrefs.getBoolean("anti_spam_block_delivery", false)) {
            if (DELIVERY_PATTERNS.any { pattern -> number.contains(pattern) }) return true
        }

        if (settingsPrefs.getBoolean("anti_spam_block_loan", false)) {
            if (LOAN_PATTERNS.any { pattern -> number.contains(pattern) }) return true
        }

        if (settingsPrefs.getBoolean("anti_spam_block_education", false)) {
            if (EDUCATION_PATTERNS.any { pattern -> number.contains(pattern) }) return true
        }

        if (settingsPrefs.getBoolean("anti_spam_block_repair", false)) {
            if (REPAIR_PATTERNS.any { pattern -> number.contains(pattern) }) return true
        }

        if (settingsPrefs.getBoolean("anti_spam_block_insurance", false)) {
            if (INSURANCE_PATTERNS.any { pattern -> number.contains(pattern) }) return true
        }

        return false
    }

    /**
     * Combined check — returns true if the number should be rejected.
     */
    fun shouldRejectCall(number: String): Boolean {
        return isBlockedNumber(number) || isBlockedByCategory(number)
    }

    fun markAsSpam(number: String) {
        val updated = _spamNumbers.value + normalize(number)
        _spamNumbers.value = updated
        saveSet(KEY_SPAM, updated)
    }

    fun unmarkAsSpam(number: String) {
        val updated = _spamNumbers.value - normalize(number)
        _spamNumbers.value = updated
        saveSet(KEY_SPAM, updated)
    }

    fun blockNumber(number: String) {
        val updated = _blockedNumbers.value + normalize(number)
        _blockedNumbers.value = updated
        saveSet(KEY_BLOCKED, updated)
    }

    fun unblockNumber(number: String) {
        val updated = _blockedNumbers.value - normalize(number)
        _blockedNumbers.value = updated
        saveSet(KEY_BLOCKED, updated)
    }

    /**
     * Reverse lookup — find the contact name for a phone number.
     * Uses the system PhoneLookup content provider.
     *
     * @param number the raw phone number (with or without country code)
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

    private fun normalize(number: String): String {
        // Remove non-digits and trim country code prefixes
        return number.replace(Regex("[^0-9]"), "").takeLast(10)
    }

    /**
     * Heuristic high-risk detection.
     * Flags numbers that are unusually short or match known spam characteristics.
     */
    private fun isHighRiskNumber(number: String): Boolean {
        val digits = number.replace(Regex("[^0-9]"), "")
        // Very short numbers (less than 7 digits after stripping) are suspicious
        if (digits.length < 7) return true
        // Numbers that are all the same digit (e.g. 1111111) are likely spam
        if (digits.toSet().size == 1) return true
        return false
    }

    private fun loadSet(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    }

    private fun saveSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    companion object {
        private const val KEY_SPAM = "spam_numbers"
        private const val KEY_BLOCKED = "blocked_numbers"

        // Chinese spam category patterns (commonly used spam hotline prefixes)
        private val REAL_ESTATE_PATTERNS = listOf(
            "101",  // 房产中介
            "102",  // 房产推销
        )
        private val ADS_PATTERNS = listOf(
            "106",  // 广告推销
            "955",  // 骚扰电话
        )
        private val DELIVERY_PATTERNS = listOf(
            "12305",  // 快递投诉
            "953",   // 快递热线
        )
        private val LOAN_PATTERNS = listOf(
            "955",   // 贷款推销
            "110",   // 诈骗
        )
        private val EDUCATION_PATTERNS = listOf(
            "9510",  // 教育培训
        )
        private val REPAIR_PATTERNS = listOf(
            "100",   // 客服
        )
        private val INSURANCE_PATTERNS = listOf(
            "955",  // 保险推销
            "110",  // 诈骗
        )
    }
}

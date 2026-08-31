package org.librelab.dialer.data.antispam

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin reimplementation of the MIUI / HyperOS Yellow Page anti-spam API client.
 * Based on reverse-engineering of:
 *   - com.miui.yellowpage 23.0.260506 (apkmirror)
 *   - HyperOS 3.0.3.0: miui/util/CoderUtils.java (miuisystem.apk)
 *
 * No Xiaomi account required — uses auto-generated device identity (UUID v4 + MD5).
 */
@Singleton
class MiAntiSpamClient @Inject constructor(
    private val deviceIdentity: DeviceIdentity,
) {
    companion object {
        private const val APP_KEY = "yellowpage"
        private const val SECRET = "77eb2e8a5755abd016c0d69ba74b219c"
        private const val API_BASE = "https://api.huangye.miui.com"
        private val UA = (
            "cupid/M2012K11AC; MIUI/V15.0.7.0.TLCCNXM E/V15.0.7 " +
                "B/S L/zh-CN LO/CN"
            )

        /** Maps catId → category name (Chinese) */
        val CATEGORY_NAMES = mapOf(
            1 to "高风险",
            2 to "房产中介",
            3 to "广告推销",
            5 to "快递外卖",
            6 to "贷款推销",
            13 to "教育培训",
            14 to "装修维修",
            21 to "保险理财",
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /** Fetches the server-issued AES key from /spbook/yellowpage/config/data */
    fun fetchEncryptKey(): String {
        val url = "$API_BASE/spbook/yellowpage/config/data?" +
            buildQuery(emptyMap(), "0".repeat(16))
        val body = httpGet(url)
        val json = JSONObject(body)
        if (json.optInt("code") != 0) {
            error("fetchEncryptKey failed: ${json.optString("message")}")
        }
        val data = json.getString("data")
        // data[3:-2] then drop chars 5..8 → 16-byte key
        val s = data.substring(3, data.length - 2)
        return s.substring(0, 5) + s.substring(8)
    }

    /**
     * Lookup a phone number against the MIUI anti-spam / yellow-page database.
     *
     * @return LookupResult with anti-spam mark (catId, count, source) or yellow-page info,
     *         or null if the number is not found / network error.
     */
    fun lookup(number: String, key: String): LookupResult? {
        val params = mapOf(
            "phone" to number,
            "raw_phone" to number,
            "version_code" to "230260506",
            "india_normalize" to "v3",
            "show_india_provider" to "true",
            "app_type" to "yellowpage",
        )
        val url = "$API_BASE/spbook/yellowpage/query?" + buildQuery(params, key)
        return parseLookupResponse(httpGet(url), number)
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP $resp.code for $url")
            resp.body?.string() ?: error("Empty response from $url")
        }
    }

    /**
     * Build the signed query string.
     * Flow (k0.java e()):
     *   1. AES/CBC/PKCS5 encrypt params + device identity → Base64 = _encparam
     *   2. SHA1(appkey + "_encparam" + <encparam> + secret).upper() = sign
     *   3. Return appkey=<key>&sign=<sign>&_encparam=<encparam>
     */
    private fun buildQuery(bizParams: Map<String, Any>, key: String): String {
        val device = deviceIdentity.get()
        val params = bizParams.toMutableMap()
        params["imeimd5"] = device.imeimd5
        params["lg"] = "zh_CN"
        params["region"] = "CN"
        params["sup"] = "mipay"
        params["uuid"] = device.uuid
        params["oaId"] = device.oaId
        params["apkVersion"] = "230260506"
        params["androidVersion"] = "15"
        params["v"] = "16"

        val joined = params.entries
            .joinToString("&") { (k, v) -> "$k=${percentEncode(v.toString())}" }

        val enc = aesEncryptB64(joined, key)
        val sign = sha1("$APP_KEY" + "_encparam" + enc + SECRET).uppercase()

        return "appkey=$APP_KEY&sign=$sign&_encparam=${percentEncode(enc)}"
    }

    private fun aesEncryptB64(plain: String, key: String): String {
        val iv = "0102030405060708".toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES"),
            IvParameterSpec(iv),
        )
        val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun sha1(data: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") {
            "%02x".format(it)
        }
    }

    private fun percentEncode(s: String): String {
        return s.map { c ->
            when {
                c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '~' -> c
                else -> "%%%02X".format(c.code)
            }
        }.joinToString("")
    }

    private fun parseLookupResponse(body: String, number: String): LookupResult? {
        return try {
            val outer = JSONObject(body)
            if (outer.optInt("code") != 0) return null
            val data = outer.optJSONObject("data")
                ?: outer.optString("data").let {
                    if (it.isNotEmpty()) JSONObject(it) else null
                }
                ?: return null

            val atd = data.optJSONObject("atd")
            val yp = data.optJSONObject("yp")
            val riskInfo = data.optJSONObject("phoneRiskInfo")
            val imageDomain = data.optString("image_domain", "")

            LookupResult(
                number = number,
                antiSpamMark = atd?.let {
                    AntiSpamMark(
                        catId = it.optInt("catId"),
                        catTitle = it.optString("catTitle", ""),
                        count = it.optInt("count"),
                        source = it.optInt("source"),
                        provider = it.optInt("provider"),
                    )
                },
                yellowPage = yp?.let {
                    YellowPage(
                        name = it.optString("sName", ""),
                        phones = it.optJSONArray("phone")?.let { arr ->
                            (0 until arr.length()).map { i ->
                                val p = arr.getJSONObject(i)
                                YellowPagePhone(
                                    phone = p.optString("phone", ""),
                                    contactName = p.optString("contactName", ""),
                                )
                            }
                        } ?: emptyList(),
                        sourceUrl = it.optString("sourceUrl", ""),
                    )
                },
                riskInfo = riskInfo?.let {
                    RiskInfo(
                        riskType = it.optString("riskType", ""),
                        riskText = it.optString("riskText", ""),
                    )
                },
                imageDomain = imageDomain,
            )
        } catch (_: Exception) {
            null
        }
    }

    // ─── Data classes ──────────────────────────────────────────────────────

    data class LookupResult(
        val number: String,
        val antiSpamMark: AntiSpamMark?,
        val yellowPage: YellowPage?,
        val riskInfo: RiskInfo?,
        val imageDomain: String,
    ) {
        val isSpam: Boolean get() = antiSpamMark != null
        val isYellowPage: Boolean get() = yellowPage != null
        val hasRisk: Boolean get() = riskInfo?.riskType?.isNotEmpty() == true
    }

    data class AntiSpamMark(
        val catId: Int,
        val catTitle: String,
        val count: Int,
        val source: Int,
        val provider: Int,
    )

    data class YellowPage(
        val name: String,
        val phones: List<YellowPagePhone>,
        val sourceUrl: String,
    )

    data class YellowPagePhone(
        val phone: String,
        val contactName: String,
    )

    data class RiskInfo(
        val riskType: String,
        val riskText: String,
    )
}

/**
 * Auto-generated, persistent device identity for the MIUI API.
 * Stored in SharedPreferences so it survives process restarts.
 * A device identity is REQUIRED for the API to return real data
 * (without it, the server returns a dummy 96110 record for all numbers).
 */
@Singleton
class DeviceIdentity @Inject constructor(
    private val prefs: DeviceIdentityPrefs,
) {
    fun get(): DeviceIdentityData {
        return prefs.load() ?: generateAndSave()
    }

    private fun generateAndSave(): DeviceIdentityData {
        val uuid = UUID.randomUUID().toString()
        val oaId = (1..8).map { "%02x".format((Math.random() * 256).toInt()) }.joinToString("")
        val imeimd5 = md5(uuid)
        val data = DeviceIdentityData(uuid, oaId, imeimd5)
        prefs.save(data)
        return data
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

data class DeviceIdentityData(
    val uuid: String,
    val oaId: String,
    val imeimd5: String,
)

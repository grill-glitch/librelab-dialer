/*
 * Copyright (C) 2026 LibreLab Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.librelab.dialer.antispam;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * MIUI / HyperOS 黄页号码标记 API 客户端 — Java 移植(参考 grill-glitch/mi-anti-spam)。
 *
 * <p>逆向来源:com.miui.yellowpage 23.0.260506 + HyperOS miui/util/CoderUtils.java。
 * 加密链路:
 * <pre>
 *   _encparam = Base64( AES-CBC-PKCS5Padding( 参数串, key, IV="0102030405060708" ) )
 *   sign      = SHA1( appkey + "_encparam" + <_encparam值> + secret ).upper()
 *   key       = GET /spbook/yellowpage/config/data -> data[3:-2] 再去掉 5~8 位
 * </pre>
 * 设备标识(uuid/imeimd5/oaId)持久化在 SharedPreferences,服务端对无标识请求降级。
 */
public class AntiSpamClient {

  private static final String API = "https://api.huangye.miui.com";
  private static final String APPKEY = "yellowpage";
  private static final String SECRET = "77eb2e8a5755abd016c0d69ba74b219c";
  private static final byte[] IV = "0102030405060708".getBytes(StandardCharsets.UTF_8);
  private static final String UA =
      "cupid/M2012K11AC; MIUI/V15.0.7.0.TLCCNXM E/V15.0.7 B/S L/zh-CN LO/CN";
  private static final String PREFS = "antispam_device";
  private static final int TIMEOUT_MS = 8000;

  private final SharedPreferences mPrefs;

  public AntiSpamClient(Context context) {
    mPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  // ---------------- 设备标识 ----------------

  private String deviceUuid() {
    String uuid = mPrefs.getString("uuid", null);
    if (uuid == null) {
      uuid = UUID.randomUUID().toString();
      String oaId = hexBytes(8);
      String imeiMd5 = md5Hex(uuid);
      mPrefs.edit().putString("uuid", uuid).putString("oaId", oaId)
          .putString("imeimd5", imeiMd5).apply();
      return uuid;
    }
    return uuid;
  }

  private String deviceImeiMd5() {
    String imei = mPrefs.getString("imeimd5", null);
    if (imei == null) {
      deviceUuid(); // 生成全部
      imei = mPrefs.getString("imeimd5", null);
    }
    return imei;
  }

  private String deviceOaId() {
    String oaId = mPrefs.getString("oaId", null);
    if (oaId == null) {
      deviceUuid();
      oaId = mPrefs.getString("oaId", null);
    }
    return oaId;
  }

  // ---------------- 加密原语 ----------------

  private static String md5Hex(String s) {
    return hexDigest(s, "MD5");
  }

  private static String hexDigest(String s, String algo) {
    try {
      MessageDigest md = MessageDigest.getInstance(algo);
      byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(d.length * 2);
      for (byte b : d) {
        sb.append(String.format(Locale.US, "%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }

  private static String hexBytes(int n) {
    StringBuilder sb = new StringBuilder(n * 2);
    java.security.SecureRandom rnd = new java.security.SecureRandom();
    for (int i = 0; i < n; i++) {
      sb.append(String.format(Locale.US, "%02x", rnd.nextInt(256)));
    }
    return sb.toString();
  }

  /** Python urllib.parse.quote(s, safe='') 等价:保留字母数字 -_.~,其余 %XX(UTF-8)。 */
  static String quote(String s) {
    StringBuilder sb = new StringBuilder();
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) {
      int c = b & 0xFF;
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
          || c == '-' || c == '_' || c == '.' || c == '~') {
        sb.append((char) c);
      } else {
        sb.append('%').append(String.format(Locale.US, "%02X", c));
      }
    }
    return sb.toString();
  }

  private static String aesEncryptB64(String plain, String key) throws Exception {
    SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(IV));
    byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
    return Base64.encodeToString(enc, Base64.NO_WRAP);
  }

  // ---------------- 签名封装 ----------------

  private String buildQuery(java.util.Map<String, String> bizParams, String key)
      throws Exception {
    java.util.LinkedHashMap<String, String> params = new java.util.LinkedHashMap<>(bizParams);
    params.putIfAbsent("imeimd5", deviceImeiMd5());
    params.putIfAbsent("lg", "zh_CN");
    params.putIfAbsent("region", "CN");
    params.putIfAbsent("sup", "mipay");
    params.putIfAbsent("uuid", deviceUuid());
    params.putIfAbsent("oaId", deviceOaId());
    params.putIfAbsent("apkVersion", "230260506");
    params.putIfAbsent("androidVersion", "15");
    params.putIfAbsent("v", "16");

    StringBuilder joined = new StringBuilder();
    for (java.util.Map.Entry<String, String> e : params.entrySet()) {
      if (joined.length() > 0) {
        joined.append('&');
      }
      joined.append(e.getKey()).append('=').append(quote(e.getValue()));
    }
    String enc = aesEncryptB64(joined.toString(), key);
    String sign = hexDigest(APPKEY + "_encparam" + enc + SECRET, "SHA-1").toUpperCase(Locale.US);
    return "appkey=" + APPKEY + "&sign=" + sign + "&_encparam=" + quote(enc);
  }

  private String http(String urlStr, String key, String method, String body) throws Exception {
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(TIMEOUT_MS);
    conn.setReadTimeout(TIMEOUT_MS);
    conn.setRequestMethod(method);
    conn.setRequestProperty("User-Agent", UA);
    conn.setRequestProperty("Accept", "*/*");
    if (body != null) {
      conn.setDoOutput(true);
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      try (OutputStream os = conn.getOutputStream()) {
        os.write(body.getBytes(StandardCharsets.UTF_8));
      }
    }
    int code = conn.getResponseCode();
    java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
    StringBuilder sb = new StringBuilder();
    try (BufferedReader br = new BufferedReader(
        new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line);
      }
    }
    conn.disconnect();
    if (code >= 400) {
      throw new RuntimeException("HTTP " + code + ": " + sb);
    }
    return sb.toString();
  }

  // ---------------- 公开 API ----------------

  /** 获取服务端下发 AES 密钥(会缓存,失效自动重取)。 */
  public synchronized String fetchEncryptKey() throws Exception {
    String cached = mPrefs.getString("key", null);
    if (cached != null) {
      return cached;
    }
    String url = API + "/spbook/yellowpage/config/data?"
        + buildQuery(new java.util.LinkedHashMap<>(), "0000000000000000");
    String outerStr = http(url, "0000000000000000", "GET", null);
    org.json.JSONObject outer = new org.json.JSONObject(outerStr);
    if (outer.optInt("code") != 0) {
      throw new RuntimeException("config/data failed: " + outerStr);
    }
    String s = outer.getString("data");
    if (s.length() >= 5) {
      s = s.substring(3, s.length() - 2);
    }
    String key = s.length() > 8 ? s.substring(0, 5) + s.substring(8) : s;
    mPrefs.edit().putString("key", key).apply();
    return key;
  }

  /**
   * 查询号码标记/黄页信息。
   *
   * @return 解析后的 JSON 对象(atd/yp/phoneRiskInfo 等),失败时返回含 "error" 键的对象。
   */
  public org.json.JSONObject lookup(String number) {
    try {
      String key = fetchEncryptKey();
      java.util.LinkedHashMap<String, String> params = new java.util.LinkedHashMap<>();
      params.put("phone", number);
      params.put("raw_phone", number);
      params.put("version_code", "230260506");
      params.put("india_normalize", "v3");
      params.put("show_india_provider", "true");
      params.put("app_type", "yellowpage");
      String url = API + "/spbook/yellowpage/query?" + buildQuery(params, key);
      String resp = http(url, key, "GET", null);
      org.json.JSONObject outer = new org.json.JSONObject(resp);
      if (outer.optInt("code") != 0) {
        org.json.JSONObject err = new org.json.JSONObject();
        err.put("error", outer.toString());
        return err;
      }
      Object data = outer.opt("data");
      if (data instanceof String && ((String) data).length() > 0) {
        return new org.json.JSONObject((String) data);
      }
      if (data instanceof org.json.JSONObject) {
        return (org.json.JSONObject) data;
      }
      return new org.json.JSONObject();
    } catch (Exception e) {
      org.json.JSONObject err = new org.json.JSONObject();
      try {
        err.put("error", e.toString());
      } catch (Exception ignored) {
      }
      return err;
    }
  }

  /** 分类表:cId -> [中文名, English]。 */
  public static final java.util.SortedMap<Integer, String[]> CATEGORIES =
      new java.util.TreeMap<>();

  static {
    CATEGORIES.put(1, new String[] {"高风险", "High risk"});
    CATEGORIES.put(2, new String[] {"房产中介", "Real estate"});
    CATEGORIES.put(3, new String[] {"广告推销", "Ads"});
    CATEGORIES.put(5, new String[] {"快递外卖", "Delivery"});
    CATEGORIES.put(6, new String[] {"贷款推销", "Loan offer"});
    CATEGORIES.put(13, new String[] {"教育培训", "Education"});
    CATEGORIES.put(14, new String[] {"装修维修", "Home repair"});
    CATEGORIES.put(21, new String[] {"保险理财", "Insurance"});
  }

  /** 取标记的展示名:atd.catTitle 优先,回退分类表。 */
  public static String categoryName(org.json.JSONObject atd) {
    if (atd != null && atd.optString("catTitle").length() > 0) {
      return atd.optString("catTitle");
    }
    if (atd != null) {
      String[] names = CATEGORIES.get(atd.optInt("catId", -1));
      if (names != null) {
        return names[0];
      }
    }
    return "";
  }

  /** 取标记的 cId(无标记返回 -1)。 */
  public static int categoryId(org.json.JSONObject atd) {
    return atd == null ? -1 : atd.optInt("catId", -1);
  }

  /** 便捷:从 lookup 结果提取 atd 对象。 */
  public static org.json.JSONObject atdOf(org.json.JSONObject result) {
    return result == null ? null : result.optJSONObject("atd");
  }

  /** 便捷:从 lookup 结果提取 yp 对象。 */
  public static org.json.JSONObject ypOf(org.json.JSONObject result) {
    return result == null ? null : result.optJSONObject("yp");
  }
}

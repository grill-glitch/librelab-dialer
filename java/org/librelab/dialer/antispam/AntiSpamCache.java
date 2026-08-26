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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 号码标记查询结果进程内缓存。
 *
 * <p>CallScreeningService 查询后写入,InCallUI 读取显示"来电类型"徽标,
 * 避免同一来电两次网络请求。LRU 容量 {@link #MAX_ENTRIES}。
 */
public class AntiSpamCache {

  private static final int MAX_ENTRIES = 200;

  private static volatile AntiSpamCache sInstance;

  private final LinkedHashMap<String, org.json.JSONObject> mMap =
      new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, org.json.JSONObject> eldest) {
          return size() > MAX_ENTRIES;
        }
      };

  public static AntiSpamCache getInstance(Context context) {
    if (sInstance == null) {
      synchronized (AntiSpamCache.class) {
        if (sInstance == null) {
          sInstance = new AntiSpamCache();
        }
      }
    }
    return sInstance;
  }

  public synchronized org.json.JSONObject get(String number) {
    return mMap.get(number);
  }

  public synchronized void put(String number, org.json.JSONObject result) {
    if (result != null && !result.has("error")) {
      mMap.put(number, result);
    }
  }
}

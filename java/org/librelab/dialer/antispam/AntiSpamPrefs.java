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

import java.util.HashSet;
import java.util.Set;

/**
 * 骚扰拦截设置存储(SharedPreferences)。
 *
 * <p>功能开关:
 * <ul>
 *   <li>{@link #isEnabled()} — 总开关(来电标记 + 拦截)</li>
 *   <li>{@link #shouldBlock(int)} — 指定分类是否拦截(高风险/广告推销等)</li>
 *   <li>{@link #isIconEnabled()} — 显示黄页图标</li>
 * </ul>
 * 默认:开启标记;仅拦截高风险(catId=1);显示图标。
 */
public class AntiSpamPrefs {

  private static final String PREFS = "antispam_settings";
  private static final String KEY_ENABLED = "antispam_enabled";
  private static final String KEY_BLOCK_CATEGORIES = "antispam_block_categories";
  private static final String KEY_ICON = "antispam_icon";

  /** 默认拦截分类:高风险。 */
  private static final Set<String> DEFAULT_BLOCK = new HashSet<>();
  static {
    DEFAULT_BLOCK.add("1");
  }

  private final SharedPreferences mPrefs;

  public AntiSpamPrefs(Context context) {
    mPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public boolean isEnabled() {
    return mPrefs.getBoolean(KEY_ENABLED, true);
  }

  public void setEnabled(boolean enabled) {
    mPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

  public boolean isIconEnabled() {
    return mPrefs.getBoolean(KEY_ICON, true);
  }

  public void setIconEnabled(boolean enabled) {
    mPrefs.edit().putBoolean(KEY_ICON, enabled).apply();
  }

  /** 是否拦截指定分类。 */
  public boolean shouldBlock(int catId) {
    if (!isEnabled() || catId < 0) {
      return false;
    }
    return mPrefs.getStringSet(KEY_BLOCK_CATEGORIES, DEFAULT_BLOCK)
        .contains(String.valueOf(catId));
  }

  /** 设置某分类的拦截状态。 */
  public void setBlockCategory(int catId, boolean block) {
    Set<String> cats = new HashSet<>(
        mPrefs.getStringSet(KEY_BLOCK_CATEGORIES, DEFAULT_BLOCK));
    if (block) {
      cats.add(String.valueOf(catId));
    } else {
      cats.remove(String.valueOf(catId));
    }
    mPrefs.edit().putStringSet(KEY_BLOCK_CATEGORIES, cats).apply();
  }

  /** 当前所有被拦截的分类 id 集合。 */
  public Set<Integer> getBlockedCategories() {
    Set<Integer> result = new HashSet<>();
    for (String s : mPrefs.getStringSet(KEY_BLOCK_CATEGORIES, DEFAULT_BLOCK)) {
      try {
        result.add(Integer.parseInt(s));
      } catch (NumberFormatException ignored) {
      }
    }
    return result;
  }
}

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

package org.librelab.dialer.app.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import org.librelab.dialer.R;
import org.librelab.dialer.antispam.AntiSpamPrefs;

/**
 * 骚扰拦截设置:号码标记开关 + 分类拦截策略。
 *
 * <p>读写 {@link AntiSpamPrefs}(SharedPreferences),与
 * {@link org.librelab.dialer.antispam.AntiSpamCallScreeningService} 共用同一存储。
 */
public class AntiSpamSettingsFragment extends PreferenceFragmentCompat
    implements Preference.OnPreferenceChangeListener {

  private AntiSpamPrefs mAntiSpamPrefs;
  private SharedPreferences mPrefs;

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    addPreferencesFromResource(R.xml.antispam_settings);

    mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    mAntiSpamPrefs = new AntiSpamPrefs(requireContext());

    // 总开关
    SwitchPreferenceCompat enabled = findPreference("antispam_enabled");
    if (enabled != null) {
      enabled.setChecked(mAntiSpamPrefs.isEnabled());
      enabled.setOnPreferenceChangeListener(this);
    }

    // 图标开关
    SwitchPreferenceCompat icon = findPreference("antispam_icon");
    if (icon != null) {
      icon.setChecked(mAntiSpamPrefs.isIconEnabled());
      icon.setOnPreferenceChangeListener(this);
    }

    // 分类拦截开关:直接由 xml 的 defaultValue 初始化,监听同步 AntiSpamPrefs
    for (int catId : new int[] {1, 2, 3, 5, 6, 13, 14, 21}) {
      SwitchPreferenceCompat p = findPreference("antispam_block_" + catId);
      if (p != null) {
        p.setChecked(mAntiSpamPrefs.shouldBlock(catId));
        p.setOnPreferenceChangeListener(this);
      }
    }
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    String key = preference.getKey();
    boolean value = Boolean.TRUE.equals(newValue);
    if ("antispam_enabled".equals(key)) {
      mAntiSpamPrefs.setEnabled(value);
      return true;
    }
    if ("antispam_icon".equals(key)) {
      mAntiSpamPrefs.setIconEnabled(value);
      return true;
    }
    if (key != null && key.startsWith("antispam_block_")) {
      try {
        int catId = Integer.parseInt(key.substring("antispam_block_".length()));
        mAntiSpamPrefs.setBlockCategory(catId, value);
        return true;
      } catch (NumberFormatException ignored) {
        return false;
      }
    }
    return false;
  }
}

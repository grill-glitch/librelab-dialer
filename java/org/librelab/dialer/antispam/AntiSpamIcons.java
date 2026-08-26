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
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.librelab.dialer.R;

/**
 * 号码标记分类 → 图标映射。
 *
 * <p>来电界面底部行按标记分类显示对应图标(高风险=盾牌告警,
 * 房产=房屋,广告=喇叭,快递=包裹,贷款=钱,教育=书,装修=工具,保险=伞)。
 */
public class AntiSpamIcons {

  private AntiSpamIcons() {}

  /** 分类 id → drawable 资源。 */
  public static int iconResForCategory(int catId) {
    switch (catId) {
      case 1:
        return R.drawable.quantum_ic_report_vd_theme_24;
      case 2:
        return R.drawable.antispam_ic_realestate;
      case 3:
        return R.drawable.antispam_ic_ads;
      case 5:
        return R.drawable.antispam_ic_delivery;
      case 6:
        return R.drawable.antispam_ic_loan;
      case 13:
        return R.drawable.antispam_ic_education;
      case 14:
        return R.drawable.antispam_ic_repair;
      case 21:
        return R.drawable.antispam_ic_insurance;
      default:
        return R.drawable.quantum_ic_report_vd_theme_24;
    }
  }

  /** 取分类图标(带主题 tint),未知分类返回默认告警图标。 */
  @Nullable
  public static Drawable iconFor(Context context, int catId) {
    try {
      return context.getDrawable(iconResForCategory(catId));
    } catch (Exception e) {
      return context.getDrawable(R.drawable.quantum_ic_report_vd_theme_24);
    }
  }
}

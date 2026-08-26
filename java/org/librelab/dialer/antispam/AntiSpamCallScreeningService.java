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
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 来电拦截服务(基于 Telecom CallScreeningService,Android 10+)。
 *
 * <p>来电时异步查询 MIUI 黄页标记,命中 {@link AntiSpamPrefs} 中配置的拦截分类
 * 则自动拒接(高风险默认拦截)。查询结果同时写入进程内缓存
 * ({@link AntiSpamCache}),供 InCallUI 显示"来电类型"徽标,避免重复请求。
 *
 * <p>注意:CallScreeningService 无法向 Call extras 写入自定义数据
 * (Telecom 侧只读),因此"显示"职责由 InCallUI 通过 {@link AntiSpamCache}
 * 完成。
 */
public class AntiSpamCallScreeningService extends CallScreeningService {

  private static final String TAG = "AntiSpamCallScreening";

  private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
  private final Handler mMainHandler = new Handler(Looper.getMainLooper());

  @Override
  public void onScreenCall(Call.Details callDetails) {
    if (!new AntiSpamPrefs(this).isEnabled()) {
      return;
    }
    final String number = callDetails.getHandle() == null
        ? null : callDetails.getHandle().getSchemeSpecificPart();
    if (TextUtils.isEmpty(number)) {
      return;
    }
    Log.i(TAG, "Screening call from " + number);
    mExecutor.execute(() -> {
      AntiSpamCache cache = AntiSpamCache.getInstance(this);
      org.json.JSONObject result = cache.get(number);
      if (result == null) {
        result = new AntiSpamClient(this).lookup(number);
        cache.put(number, result);
      }
      final org.json.JSONObject finalResult = result;
      mMainHandler.post(() -> applyScreeningResult(callDetails, finalResult));
    });
  }

  private void applyScreeningResult(Call.Details callDetails, org.json.JSONObject result) {
    org.json.JSONObject atd = AntiSpamClient.atdOf(result);
    int catId = AntiSpamClient.categoryId(atd);
    String label = AntiSpamClient.categoryName(atd);
    AntiSpamPrefs prefs = new AntiSpamPrefs(this);

    boolean shouldBlock = prefs.shouldBlock(catId);
    CallResponse.Builder builder = new CallResponse.Builder();
    builder.setRejectCall(shouldBlock);
    builder.setDisallowCall(shouldBlock);
    builder.setSkipCallLog(false);
    builder.setSkipNotification(false);

    try {
      respondToCall(callDetails, builder.build());
      Log.i(TAG, "Screening done: number catId=" + catId + " label=" + label
          + " blocked=" + shouldBlock);
    } catch (Exception e) {
      Log.e(TAG, "respondToCall failed", e);
    }
  }
}

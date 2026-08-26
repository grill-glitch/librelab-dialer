/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
 * limitations under the License
 */

package org.librelab.dialer.phonelookup.cequint;

import android.content.Context;
import android.telecom.Call;
import android.text.TextUtils;

import org.librelab.dialer.DialerPhoneNumber;
import org.librelab.dialer.common.Assert;
import org.librelab.dialer.common.concurrent.Annotations.BackgroundExecutor;
import org.librelab.dialer.common.concurrent.Annotations.LightweightExecutor;
import org.librelab.dialer.inject.ApplicationContext;
import org.librelab.dialer.location.GeoUtil;
import org.librelab.dialer.oem.CequintCallerIdManager;
import org.librelab.dialer.oem.CequintCallerIdManager.CequintCallerIdContact;
import org.librelab.dialer.phonelookup.PhoneLookup;
import org.librelab.dialer.phonelookup.PhoneLookupInfo;
import org.librelab.dialer.phonelookup.PhoneLookupInfo.CequintInfo;
import org.librelab.dialer.phonenumberproto.DialerPhoneNumberUtil;
import org.librelab.dialer.telecom.TelecomCallUtil;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import javax.inject.Inject;

/** PhoneLookup implementation for Cequint. */
public class CequintPhoneLookup implements PhoneLookup<CequintInfo> {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutorService;
  private final ListeningExecutorService lightweightExecutorService;

  @Inject
  CequintPhoneLookup(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.appContext = appContext;
    this.backgroundExecutorService = backgroundExecutorService;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  @Override
  public ListenableFuture<CequintInfo> lookup(Context appContext, Call call) {
    if (!CequintCallerIdManager.isCequintCallerIdEnabled(appContext)) {
      return Futures.immediateFuture(CequintInfo.getDefaultInstance());
    }

    ListenableFuture<DialerPhoneNumber> dialerPhoneNumberFuture =
        backgroundExecutorService.submit(
            () -> {
              DialerPhoneNumberUtil dialerPhoneNumberUtil = new DialerPhoneNumberUtil();
              return dialerPhoneNumberUtil.parse(
                  TelecomCallUtil.getNumber(call), GeoUtil.getCurrentCountryIso(appContext));
            });
    String callerDisplayName = call.getDetails().getCallerDisplayName();
    boolean isIncomingCall = (call.getDetails().getState() == Call.STATE_RINGING);

    return Futures.transformAsync(
        dialerPhoneNumberFuture,
        dialerPhoneNumber ->
            backgroundExecutorService.submit(
                () ->
                    buildCequintInfo(
                        CequintCallerIdManager.getCequintCallerIdContactForCall(
                            appContext,
                            Assert.isNotNull(dialerPhoneNumber).getNormalizedNumber(),
                            callerDisplayName,
                            isIncomingCall))),
        lightweightExecutorService);
  }

  @Override
  public ListenableFuture<CequintInfo> lookup(DialerPhoneNumber dialerPhoneNumber) {
    if (!CequintCallerIdManager.isCequintCallerIdEnabled(appContext)) {
      return Futures.immediateFuture(CequintInfo.getDefaultInstance());
    }

    return backgroundExecutorService.submit(
        () ->
            buildCequintInfo(
                CequintCallerIdManager.getCequintCallerIdContactForNumber(
                    appContext, dialerPhoneNumber.getNormalizedNumber())));
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, CequintInfo>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, CequintInfo> existingInfoMap) {
    return Futures.immediateFuture(existingInfoMap);
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, CequintInfo subMessage) {
    destination.setCequintInfo(subMessage);
  }

  @Override
  public CequintInfo getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getCequintInfo();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers() {
    // No need to register a content observer as the Cequint content provider doesn't support batch
    // queries.
  }

  @Override
  public void unregisterContentObservers() {
    // Nothing to be done as no content observer is registered.
  }

  @Override
  public ListenableFuture<Void> clearData() {
    return Futures.immediateFuture(null);
  }

  @Override
  public String getLoggingName() {
    return "CequintPhoneLookup";
  }

  /**
   * Builds a {@link CequintInfo} proto based on the given {@link CequintCallerIdContact} returned
   * by {@link CequintCallerIdManager}.
   */
  private static CequintInfo buildCequintInfo(CequintCallerIdContact cequintCallerIdContact) {
    CequintInfo.Builder cequintInfoBuilder = CequintInfo.newBuilder();

    // Every field in CequintCallerIdContact can be null.
    if (!TextUtils.isEmpty(cequintCallerIdContact.name())) {
      cequintInfoBuilder.setName(cequintCallerIdContact.name());
    }
    if (!TextUtils.isEmpty(cequintCallerIdContact.geolocation())) {
      cequintInfoBuilder.setGeolocation(cequintCallerIdContact.geolocation());
    }
    if (!TextUtils.isEmpty(cequintCallerIdContact.photoUri())) {
      cequintInfoBuilder.setPhotoUri(cequintCallerIdContact.photoUri());
    }

    return cequintInfoBuilder.build();
  }
}

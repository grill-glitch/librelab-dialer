/*
 * Copyright (C) 2017 The Android Open Source Project
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

package org.librelab.dialer.binary.aosp;

import org.librelab.dialer.activecalls.ActiveCallsModule;
import org.librelab.dialer.binary.basecomponent.BaseDialerRootComponent;
import org.librelab.dialer.calllog.CallLogModule;
import org.librelab.dialer.common.concurrent.DialerExecutorModule;
import org.librelab.dialer.contacts.ContactsModule;
import org.librelab.dialer.glidephotomanager.GlidePhotoManagerModule;
import org.librelab.dialer.inject.ContextModule;
import org.librelab.dialer.phonelookup.PhoneLookupModule;
import org.librelab.dialer.phonenumbergeoutil.impl.PhoneNumberGeoUtilModule;
import org.librelab.dialer.precall.impl.PreCallModule;
import org.librelab.dialer.preferredsim.PreferredSimModule;
import org.librelab.dialer.preferredsim.suggestion.stub.StubSimSuggestionModule;
import org.librelab.dialer.promotion.impl.PromotionModule;
import org.librelab.dialer.simulator.impl.SimulatorModule;
import org.librelab.dialer.storage.StorageModule;
import org.librelab.dialer.theme.base.impl.AospThemeModule;
import org.librelab.voicemail.impl.VoicemailModule;

import dagger.Component;

import javax.inject.Singleton;

/** Root component for the AOSP Dialer application. */
@Singleton
@Component(
    modules = {
      ActiveCallsModule.class,
      CallLogModule.class,
      ContactsModule.class,
      ContextModule.class,
      DialerExecutorModule.class,
      GlidePhotoManagerModule.class,
      PhoneLookupModule.class,
      PhoneNumberGeoUtilModule.class,
      PreCallModule.class,
      PreferredSimModule.class,
      PromotionModule.class,
      SimulatorModule.class,
      StorageModule.class,
      StubSimSuggestionModule.class,
      AospThemeModule.class,
      VoicemailModule.class,
    })
public interface AospDialerRootComponent extends BaseDialerRootComponent {}

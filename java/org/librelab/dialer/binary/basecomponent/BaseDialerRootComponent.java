/*
 * Copyright (C) 2017 The Android Open Source Project
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

package org.librelab.dialer.binary.basecomponent;

import org.librelab.dialer.activecalls.ActiveCallsComponent;
import org.librelab.dialer.calllog.CallLogComponent;
import org.librelab.dialer.calllog.database.CallLogDatabaseComponent;
import org.librelab.dialer.calllog.ui.CallLogUiComponent;
import org.librelab.dialer.common.concurrent.DialerExecutorComponent;
import org.librelab.dialer.contacts.ContactsComponent;
import org.librelab.dialer.glidephotomanager.GlidePhotoManagerComponent;
import org.librelab.dialer.phonelookup.PhoneLookupComponent;
import org.librelab.dialer.phonelookup.database.PhoneLookupDatabaseComponent;
import org.librelab.dialer.phonenumbergeoutil.PhoneNumberGeoUtilComponent;
import org.librelab.dialer.precall.PreCallComponent;
import org.librelab.dialer.preferredsim.PreferredSimComponent;
import org.librelab.dialer.preferredsim.suggestion.SimSuggestionComponent;
import org.librelab.dialer.promotion.PromotionComponent;
import org.librelab.dialer.simulator.SimulatorComponent;
import org.librelab.dialer.speeddial.loader.UiItemLoaderComponent;
import org.librelab.dialer.storage.StorageComponent;
import org.librelab.dialer.theme.base.ThemeComponent;
import org.librelab.voicemail.VoicemailComponent;

/**
 * Base class for the core application-wide component. All variants of the Dialer app should extend
 * from this component.
 */
public interface BaseDialerRootComponent
    extends ActiveCallsComponent.HasComponent,
        CallLogComponent.HasComponent,
        CallLogDatabaseComponent.HasComponent,
        CallLogUiComponent.HasComponent,
        ContactsComponent.HasComponent,
        DialerExecutorComponent.HasComponent,
        GlidePhotoManagerComponent.HasComponent,
        PhoneLookupComponent.HasComponent,
        PhoneLookupDatabaseComponent.HasComponent,
        PhoneNumberGeoUtilComponent.HasComponent,
        PreCallComponent.HasComponent,
        PreferredSimComponent.HasComponent,
        PromotionComponent.HasComponent,
        UiItemLoaderComponent.HasComponent,
        SimSuggestionComponent.HasComponent,
        SimulatorComponent.HasComponent,
        StorageComponent.HasComponent,
        ThemeComponent.HasComponent,
        VoicemailComponent.HasComponent {}

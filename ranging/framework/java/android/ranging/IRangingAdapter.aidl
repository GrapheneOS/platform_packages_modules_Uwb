/*
 * Copyright 2024 The Android Open Source Project
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

package android.ranging;

import android.content.AttributionSource;
import android.ranging.IOobSendDataListener;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.IRangingCallbacks;
import android.ranging.OobHandle;
import android.ranging.SessionHandle;
import android.ranging.RangingPreference;

/**
*  @hide
*/
interface IRangingAdapter {

    void startRanging(in AttributionSource attributionSource, in SessionHandle sessionHandle,
                 in RangingPreference rangingPreference, in IRangingCallbacks callbacks);

    void stopRanging(in SessionHandle sessionHandle);

    void registerCapabilitiesCallback(in IRangingCapabilitiesCallback rangingCapabilitiesCallback);

    void unregisterCapabilitiesCallback(in IRangingCapabilitiesCallback rangingCapabilitiesCallback);

    void oobDataReceived(in OobHandle oobHandle, in byte[] data);
    void deviceOobDisconnected(in OobHandle oobHandle);
    void deviceOobReconnected(in OobHandle oobHandle);
    void deviceOobClosed(in OobHandle oobHandle);
    void registerOobSendDataListener(in IOobSendDataListener oobSendDataListener);
}
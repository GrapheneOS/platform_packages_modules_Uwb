/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.pm;

import static com.google.uwb.support.fira.FiraParams.PACS_PROFILE_SERVICE_ID;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.util.Log;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;

import com.android.server.uwb.UwbConfigStore;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.data.ServiceProfileData;
import com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo;
import com.android.server.uwb.data.UwbUciConstants;

import com.google.uwb.support.fira.FiraParams.ServiceID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ProfileManager {

    private static final String TAG = "UwbServiceProfileStore";

    public final Map<UUID, ServiceProfileInfo> mServiceProfileMap =
            new HashMap<>();

    public final Map<Integer, List<ServiceProfileInfo>> mAppServiceProfileMap =
            new HashMap<>();

    public final Map<SessionHandle, RangingSessionController> mRangingSessionTable =
            new HashMap<>();

    private static final int MAX_RETRIES = 10;

    private final Context mContext;
    private final Handler mHandler;
    private final UwbConfigStore mUwbConfigStore;
    private final UwbInjector mUwbInjector;

    private boolean mHasNewDataToSerialize = false;


    public ProfileManager(@NonNull Context context, @NonNull Handler handler, @NonNull
             UwbConfigStore uwbConfigStore, UwbInjector uwbInjector) {
        mContext = context;
        mHandler = handler;
        mUwbConfigStore = uwbConfigStore;
        mUwbInjector = uwbInjector;
        mUwbConfigStore.registerStoreData(mUwbInjector
                .makeServiceProfileData(new ServiceProfileStoreData()));
    }

    private class ServiceProfileStoreData implements
            ServiceProfileData.DataSource {

        @Override
        public Map<UUID, ServiceProfileInfo> toSerialize() {
            mHasNewDataToSerialize = false;
            return mServiceProfileMap;
        }

        @Override
        public void fromDeserialized(Map<UUID, ServiceProfileInfo> serviceProfileDataMap) {
            loadServiceProfile(serviceProfileDataMap);
        }

        @Override
        public void reset() {
            mServiceProfileMap.clear();
            mAppServiceProfileMap.clear();
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return mHasNewDataToSerialize;
        }
    }

    public Optional<UUID> addServiceProfile(@ServiceID int serviceID) {
        UUID serviceInstanceID;
        int app_uid = Binder.getCallingUid();
        int num_tries = 0;
        do {
            serviceInstanceID = UUID.randomUUID();
            num_tries++;
        } while(mServiceProfileMap.containsKey(serviceInstanceID) && num_tries < MAX_RETRIES);

        if (num_tries == MAX_RETRIES) {
            return Optional.empty();
        }
        ServiceProfileInfo serviceProfileInfo = new ServiceProfileInfo(serviceInstanceID,
                Binder.getCallingUid(), mContext.getPackageName(), serviceID);
        mServiceProfileMap.put(serviceInstanceID, serviceProfileInfo);
        if (mAppServiceProfileMap.containsKey(app_uid)) {
            List<ServiceProfileInfo> appServiceProfileList = mAppServiceProfileMap.get(app_uid);
            appServiceProfileList.add(serviceProfileInfo);
            mAppServiceProfileMap.put(app_uid, appServiceProfileList);
        } else {
            List<ServiceProfileInfo> appServiceProfileList = new ArrayList();
            appServiceProfileList.add(serviceProfileInfo);
            mAppServiceProfileMap.put(app_uid, appServiceProfileList);
        }
        mHasNewDataToSerialize = true;
        mHandler.post(() -> mUwbConfigStore.saveToStore(true));
        return Optional.of(serviceInstanceID);
    }

    /** Remove existing service profile from profile manager */
    public int removeServiceProfile(UUID serviceInstanceID) {
        int app_uid = Binder.getCallingUid();
        if (mServiceProfileMap.containsKey(serviceInstanceID)) {
            ServiceProfileInfo serviceProfileInfo = mServiceProfileMap.get(serviceInstanceID);
            mServiceProfileMap.remove(serviceInstanceID);

            if (mAppServiceProfileMap.containsKey(app_uid)) {
                List<ServiceProfileInfo> appServiceProfileList =
                        mAppServiceProfileMap.get(app_uid);
                appServiceProfileList.remove(serviceProfileInfo);
                if (appServiceProfileList.isEmpty()) {
                    mAppServiceProfileMap.remove(app_uid);
                } else {
                    mAppServiceProfileMap.put(app_uid, appServiceProfileList);
                }
            }
        }
        else {
            return UwbUciConstants.STATUS_CODE_FAILED;
        }
        mHandler.post(() -> mUwbConfigStore.saveToStore(true));
        return UwbUciConstants.STATUS_CODE_OK;
    }

    public void loadServiceProfile(Map<UUID, ServiceProfileInfo> serviceProfileDataMap) {
        mServiceProfileMap.clear();
        mAppServiceProfileMap.clear();
        for (Map.Entry<UUID, ServiceProfileInfo> entry : serviceProfileDataMap.entrySet()) {
            mServiceProfileMap.put(entry.getKey(), entry.getValue());
            int app_uid = entry.getValue().uid;
            if (mAppServiceProfileMap.containsKey(app_uid)) {
                List<ServiceProfileInfo> appServiceProfileList = mAppServiceProfileMap.get(app_uid);
                appServiceProfileList.add(entry.getValue());
                mAppServiceProfileMap.put(app_uid, appServiceProfileList);
            } else {
                List<ServiceProfileInfo> appServiceProfileList = new ArrayList();
                appServiceProfileList.add(entry.getValue());
                mAppServiceProfileMap.put(app_uid, appServiceProfileList);
            }
        }
    }

    //TODO Send all info related to app as a Persistable bundle
    public List<ServiceProfileInfo> getServiceProfiles() {
        int app_uid = Binder.getCallingUid();
        if (mAppServiceProfileMap.containsKey(app_uid)) {
            return mAppServiceProfileMap.get(app_uid);
        }
        return null;
    }

    /** Initializes state machine and session related info */
    public void activateProfile(AttributionSource attributionSource, SessionHandle sessionHandle,
            UUID serviceInstanceId, IUwbRangingCallbacks rangingCallbacks) {

        if (!mServiceProfileMap.containsKey(serviceInstanceId)) {
            Log.e(TAG, "UUID not found");
            return;
        }
        ServiceProfileInfo profileInfo = mServiceProfileMap.get(serviceInstanceId);

        switch (profileInfo.serviceID) {
            /* Only PACS controlee/responder is supported now*/
            case PACS_PROFILE_SERVICE_ID :
                RangingSessionController rangingSessionController = new PacsControleeSession(
                        sessionHandle,  attributionSource, mContext, mUwbInjector, profileInfo,
                        rangingCallbacks, mHandler);
                mRangingSessionTable.put(sessionHandle, rangingSessionController);
                break;
            default:
                Log.e(TAG, "Service ID not supported yet");
                return;
        }

        /* Session has been initialized, notify app */
        try {
            rangingCallbacks.onRangingOpened(sessionHandle);
            Log.i(TAG, "IUwbRangingCallbacks - onRangingOpened");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingOpened : Failed");
            e.printStackTrace();
        }
    }

    /** Start Ranging */
    public void startRanging(SessionHandle sessionHandle) {
        if (mRangingSessionTable.containsKey(sessionHandle)) {
            RangingSessionController rangingSessionController = mRangingSessionTable.get(
                    sessionHandle);
            rangingSessionController.startSession();
        } else {
            Log.e(TAG, "Session Handle not found");
        }
    }
}

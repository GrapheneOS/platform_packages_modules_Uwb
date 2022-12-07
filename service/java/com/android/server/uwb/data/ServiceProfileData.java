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

package com.android.server.uwb.data;

import static com.android.server.uwb.UwbConfigStore.INITIAL_CONFIG_STORE_DATA_VERSION;
import static com.android.server.uwb.UwbConfigStore.STORE_FILE_USER_GENERAL;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.proto.uwb.UwbConfigProto;
import com.android.server.uwb.UwbConfigStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ServiceProfileData implements UwbConfigStore.StoreData {

    private static String TAG = "ServiceProfileData";

    public ServiceProfileData(DataSource dataSource) {
        this.mDataSource = dataSource;
    }

    public static class ServiceProfileInfo {
        /**
         * Unique 128-bit service instance ID
         */
        public final UUID serviceInstanceID;
        /**
         * App uid
         */
        public final int uid;
        /**
         * App package name
         */
        public final String packageName;
        /**
         * Service ID, like PACS or custom service
         */
        public final int serviceID;
        /**
         * Applet ID for dynamic STS
         */
        private int serviceAppletID;
        /**
         * ADF OID
         */
        // TODO: refactor, not int, use ObjectIdentifier.
        private int serviceAdfID;

        /**
         *
         * serviceAppletID and serviceAdfID will be set after provisioning.
         */
        public ServiceProfileInfo(UUID serviceInstanceID, int uid,
                String packageName, int serviceID) {
            this.serviceInstanceID = serviceInstanceID;
            this.uid = uid;
            this.packageName = packageName;
            this.serviceID = serviceID;
        }

        public void setServiceAppletID(int serviceAppletID) {
            this.serviceAppletID = serviceAppletID;
        }

        public void setServiceAdfID(int serviceAdfID) {
            this.serviceAdfID = serviceAdfID;
        }

        public int getServiceAppletID() {
            return serviceAppletID;
        }

        public int getServiceAdfID() {
            return serviceAdfID;
        }

    }

    /**
     * Interface define the data source for service config store data.
     */
    public interface DataSource {
        /**
         * Retrieve the service config list from the data source to serialize them to disk.
         *
         * @return Map of package name to {@link ServiceProfileInfo}
         */
        Map<UUID, ServiceProfileInfo> toSerialize();

        /**
         * Set the service config list in the data source after serializing them from disk.
         *
         * @param serviceProfileData Map of package name to {@link ServiceProfileInfo}
         */
        void fromDeserialized(Map<UUID, ServiceProfileInfo> serviceProfileData);

        /**
         * Clear internal data structure in preparation for user switch or initial store read.
         */
        void reset();

        /**
         * Indicates whether there is new data to serialize.
         */
        boolean hasNewDataToSerialize();
    }

    /**
     * Data source
     */
    private final DataSource mDataSource;

    /**
     *
     * @param builder
     * Add all service configs to builder so that uwb config store can build and store.
     */
    @Override
    public void serializeData(UwbConfigProto.UwbConfig.Builder builder) {
        for (Map.Entry<UUID, ServiceProfileInfo> entry : mDataSource.toSerialize().entrySet()) {
            UwbConfigProto.ServiceConfig.Builder serviceConfigBuilder =
                    UwbConfigProto.ServiceConfig.newBuilder();
            ServiceProfileInfo serviceProfileInfo = entry.getValue();
            serviceConfigBuilder.setServiceInstanceId(serviceProfileInfo
                    .serviceInstanceID.toString());
            serviceConfigBuilder.setPackageName(serviceProfileInfo.packageName);
            serviceConfigBuilder.setUid(serviceProfileInfo.uid);
            serviceConfigBuilder.setServiceId(serviceProfileInfo.serviceID);
            serviceConfigBuilder.setServiceAppletId(serviceProfileInfo.getServiceAppletID());
            serviceConfigBuilder.setServiceAdfId(serviceProfileInfo.getServiceAdfID());
            builder.addServiceConfig(serviceConfigBuilder.build());
        }
    }
    /**
     *
     * @param uwbConfig
     * Wrapper to check whether we are using correct version of uwb-config-proto
     */
    @Override
    public void deserializeData(@Nullable UwbConfigProto.UwbConfig uwbConfig) {
        if (uwbConfig == null || !uwbConfig.hasVersion()) {
            Log.i(TAG, "No data stored");
            return;
        }

        switch (uwbConfig.getVersion()) {
            case INITIAL_CONFIG_STORE_DATA_VERSION :
                deserializeDataVersion1(uwbConfig);
                break;
            default:
                throw new IllegalArgumentException("Unknown Uwb config store version");
        }
    }

    /**
     * Get all data stored and put it in a map
     */
    public void deserializeDataVersion1(UwbConfigProto.UwbConfig uwbConfig) {
        List<UwbConfigProto.ServiceConfig> serviceConfigList = uwbConfig.getServiceConfigList();
        Map<UUID, ServiceProfileInfo> serviceProfileDataMap = new HashMap<>();
        for (UwbConfigProto.ServiceConfig serviceConfig : serviceConfigList) {
            ServiceProfileInfo serviceProfileInfo = new ServiceProfileInfo(
                    UUID.fromString(serviceConfig.getServiceInstanceId()),
                    serviceConfig.getUid(),
                    serviceConfig.getPackageName(),
                    serviceConfig.getServiceId());
            serviceProfileInfo.setServiceAppletID(serviceConfig.getServiceAppletId());
            serviceProfileInfo.setServiceAdfID(serviceConfig.getServiceAdfId());
            serviceProfileDataMap.put(serviceProfileInfo.serviceInstanceID, serviceProfileInfo);
        }
        mDataSource.fromDeserialized(serviceProfileDataMap);
    }

    @Override
    public void resetData() {
        mDataSource.reset();
    }

    @Override
    public boolean hasNewDataToSerialize() {
        return mDataSource.hasNewDataToSerialize();
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public int getStoreFileId() {
        return STORE_FILE_USER_GENERAL;
    }
}

/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.uwb.info;

import android.os.PersistableBundle;

/***
 * TODO: Should we remove this since it is no longer used?
 */
public class UwbSpecificationInfo {

    private static final String KEY_FIRA_SPECIFICATION_INFO = "fira_stack_info";
    private static final String KEY_CCC_SPECIFICATION_INFO = "ccc_stack_info";
    private static final String KEY_UCI_SPECIFICATION_INFO = "uci_stack_info";
    private static final String KEY_MAC_SPECIFICATION_INFO = "mac_stack_info";
    private static final String KEY_PHY_SPECIFICATION_INFO = "phy_stack_info";
    private static final String KEY_UCITEST_SPECIFICATION_INFO = "ucitest_stack_info";

    private static final String TAG = UwbSpecificationInfo.class.getSimpleName();
    /*  UCI generic device info */
    public int mUciMajorVersion;
    private int mUciMinorVersion;
    private int mUciMaintenanceVersion;

    private int mMacMajorVersion;
    private int mMacMinorVersion;
    private int mMacMaintenanceVersion;

    private int mPhyMajorVersion;
    private int mPhyMinorVersion;
    private int mPhyMaintenanceVersion;

    private int mUciTestMajorVersion;
    private int mUciTestMinorVersion;
    private int mUciTestMaintenanceVersion;

    private int mFiRaMajorVersion = 1;
    private int mFiRaMinorVersion = 1;
    private int mCccMajorVersion = 1;
    private int mCccMinorVersion = 0;

    public UwbSpecificationInfo(int uciMajorVersion,  int uciMaintenanceVersion,
            int uciMinorVersion, int macMajorVersion, int macMinorVersion,
            int macMaintenanceVersion, int phyMajorVersion, int phyMinorVersion,
            int phyMaintenanceVersion, int uciTestMajorVersion, int uciTestMinorVersion,
            int uciTestMaintenanceVersion, int fiRaMajorVersion, int fiRaMinorVersion,
            int cccMajorVersion, int cccMinorVersion) {
        /* Generic */
        this.mUciMajorVersion = uciMajorVersion;
        this.mUciMinorVersion = uciMinorVersion;
        this.mUciMaintenanceVersion = uciMaintenanceVersion;
        this.mMacMajorVersion = macMajorVersion;
        this.mMacMinorVersion = macMinorVersion;
        this.mMacMaintenanceVersion = macMaintenanceVersion;
        this.mPhyMajorVersion = phyMajorVersion;
        this.mPhyMinorVersion = phyMinorVersion;
        this.mPhyMaintenanceVersion = phyMaintenanceVersion;

        this.mUciTestMajorVersion = uciTestMajorVersion;
        this.mUciTestMinorVersion = uciTestMinorVersion;
        this.mUciTestMaintenanceVersion = uciTestMaintenanceVersion;

        this.mFiRaMajorVersion = fiRaMajorVersion;
        this.mFiRaMinorVersion = fiRaMinorVersion;
        this.mCccMajorVersion = cccMajorVersion;
        this.mCccMinorVersion = cccMinorVersion;
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_FIRA_SPECIFICATION_INFO,
                this.mFiRaMajorVersion + "." + this.mFiRaMinorVersion);
        bundle.putString(KEY_CCC_SPECIFICATION_INFO,
                this.mCccMajorVersion + "." + this.mCccMinorVersion);
        bundle.putString(KEY_UCI_SPECIFICATION_INFO,
                this.mUciMajorVersion + "." + this.mUciMinorVersion
                        + "." + this.mUciMaintenanceVersion);
        bundle.putString(KEY_MAC_SPECIFICATION_INFO,
                this.mMacMajorVersion + "." + this.mMacMinorVersion
                        + "." + this.mMacMaintenanceVersion);
        bundle.putString(KEY_PHY_SPECIFICATION_INFO,
                this.mPhyMajorVersion + "." + this.mPhyMinorVersion
                        + "." + this.mPhyMaintenanceVersion);
        bundle.putString(KEY_UCITEST_SPECIFICATION_INFO,
                this.mUciTestMajorVersion + "." + this.mUciTestMinorVersion
                        + "." + this.mUciTestMaintenanceVersion);
        return bundle;
    }
}
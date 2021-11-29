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

package com.android.server.uwb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.uwb.data.UwbUciConstants;
import com.android.uwb.jni.NativeUwbManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Provide functions for making changes to UWB country code.
 * This Country Code is from MCC or phone default setting. This class sends Country Code
 * to UWB venodr via the HAL.
 */
public class UwbCountryCode {
    private static final String TAG = "UwbCountryCode";
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    private final Context mContext;
    private final Handler mHandler;
    private final TelephonyManager mTelephonyManager;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbInjector mUwbInjector;

    private String mTelephonyCountryCode = null;
    private String mCountryCode = null;
    private String mCountryCodeUpdatedTimestamp = null;
    private String mTelephonyCountryTimestamp = null;
    private String mReadyTimestamp = null;

    public UwbCountryCode(
            Context context, NativeUwbManager nativeUwbManager, Handler handler,
            UwbInjector uwbInjector) {
        mContext = context;
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mNativeUwbManager = nativeUwbManager;
        mHandler = handler;
        mUwbInjector = uwbInjector;
    }

    /**
     * Initialize the module.
     */
    public void initialize() {
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String countryCode = intent.getStringExtra(
                                TelephonyManager.EXTRA_NETWORK_COUNTRY);
                        Log.d(TAG, "Country code changed to :" + countryCode);
                        setTelephonyCountryCode(countryCode);
                    }
                },
                new IntentFilter(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED),
                null, mHandler);

        Log.d(TAG, "Default country code from system property is "
                + mUwbInjector.getOemDefaultCountryCode());
        setTelephonyCountryCode(mTelephonyManager.getNetworkCountryIso());
    }

    private boolean setTelephonyCountryCode(String countryCode) {
        if (TextUtils.isEmpty(countryCode)
                && !TextUtils.isEmpty(mTelephonyManager.getNetworkCountryIso())) {
            Log.i(TAG, "Skip Telephony CC update to empty because there is "
                    + "an available CC from default active SIM");
            return false;
        }
        Log.d(TAG, "Set telephony country code to: " + countryCode);
        mTelephonyCountryTimestamp = FORMATTER.format(new Date(System.currentTimeMillis()));
        // Empty country code.
        if (TextUtils.isEmpty(countryCode)) {
            Log.d(TAG, "Received empty country code, reset to default country code");
            mTelephonyCountryCode = null;
        } else {
            mTelephonyCountryCode = countryCode.toUpperCase(Locale.US);
        }
        return setCountryCode();
    }


    private String pickCountryCode() {
        if (mTelephonyCountryCode != null) {
            return mTelephonyCountryCode;
        }
        return mUwbInjector.getOemDefaultCountryCode();
    }

    /**
     * Set country code
     *
     * @return true if the country code is set successfully, false otherwise.
     */
    public boolean setCountryCode() {
        String country = pickCountryCode();
        if (country == null) {
            Log.i(TAG, "No valid country code");
            return false;
        }
        Log.d(TAG, "setCountryCode to " + country);
        mCountryCode = country;
        mCountryCodeUpdatedTimestamp = FORMATTER.format(new Date(System.currentTimeMillis()));
        int status = mNativeUwbManager.setCountryCode(country.getBytes(StandardCharsets.UTF_8));
        return (status != UwbUciConstants.STATUS_CODE_OK);
    }

    /**
     * Method to dump the current state of this UwbCountryCode object.
     */
    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DefaultCountryCode(system property): "
                + mUwbInjector.getOemDefaultCountryCode());
        pw.println("mTelephonyCountryCode: " + mTelephonyCountryCode);
        pw.println("mTelephonyCountryTimestamp: " + mTelephonyCountryTimestamp);
        pw.println("mReadyTimestamp: " + mReadyTimestamp);
        pw.println("mCountryCode: " + mCountryCode);
        pw.println("mCountryCodeUpdatedTimestamp: " + mCountryCodeUpdatedTimestamp);
    }
}

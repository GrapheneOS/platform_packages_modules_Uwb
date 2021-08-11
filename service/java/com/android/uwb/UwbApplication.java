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
package com.android.uwb;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.os.Process;

import com.android.uwb.jni.NativeUwbManager;

import java.util.Iterator;
import java.util.List;

public class UwbApplication extends Application {
    static final String UWB_PROCESS = "com.android.uwb";

    UwbService mUwbService;

    public UwbApplication() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        boolean isMainProcess = false;
        //Check whether we're the main UWB service
        ActivityManager am = this.getSystemService(ActivityManager.class);
        List processes = am.getRunningAppProcesses();
        if (processes == null) {
            return;
        }
        Iterator i = processes.iterator();

        while (i.hasNext()) {
            RunningAppProcessInfo appInfo = (RunningAppProcessInfo) (i.next());
            if (appInfo.pid == Process.myPid()) {
                isMainProcess = (UWB_PROCESS.equals(appInfo.processName));
                break;
            }
        }

        if (isMainProcess) {
            mUwbService = new UwbService(this.getApplicationContext(), new NativeUwbManager());
        }
    }
}

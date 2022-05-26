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
package androidx.core.uwb.backend.impl;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.uwb.backend.IUwb;
import androidx.core.uwb.backend.IUwbClient;

/**
 * Uwb service entry point of the backend.
 */
public class UwbService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        return mBinder;
    }

    private final IUwb.Stub mBinder = new IUwb.Stub() {
        @Override
        public IUwbClient getControleeClient() {
            // TODO (b/234033640): Implement this. How do we reuse gmscore code here?
            return null;
        }
        @Override
        public IUwbClient getControllerClient() {
            // TODO (b/234033640): Implement this. How do we reuse gmscore code here?
            return null;
        }
        @Override
        public int getInterfaceVersion() {
            return this.VERSION;
        }
        @Override
        public String getInterfaceHash() {
            return this.HASH;
        }
    };
}

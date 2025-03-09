/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.ranging;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.os.Binder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.RangingCapabilities;
import android.ranging.RangingCapabilities.RangingTechnologyAvailability;
import android.ranging.RangingCapabilities.TechnologyCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class CapabilitiesProvider {

    /**
     * An adapter to the capability and availability APIs exposed by an underlying technology's
     * stack.
     */
    public abstract static class CapabilitiesAdapter {
        private TechnologyAvailabilityListener mListener;

        protected CapabilitiesAdapter(@NonNull TechnologyAvailabilityListener listener) {
            mListener = listener;
        }

        public @Nullable TechnologyAvailabilityListener getAvailabilityListener() {
            return mListener;
        }

        public abstract @RangingTechnologyAvailability int getAvailability();

        public abstract @Nullable TechnologyCapabilities getCapabilities();
    }

    @IntDef({
            AvailabilityChangedReason.UNKNOWN,
            AvailabilityChangedReason.SYSTEM_POLICY,
    })
    public @interface AvailabilityChangedReason {
        int UNKNOWN = 0;
        int SYSTEM_POLICY = 1;
    }

    private static final String TAG = CapabilitiesProvider.class.getSimpleName();
    private final RangingInjector mRangingInjector;
    private final Map<RangingTechnology, CapabilitiesAdapter> mCapabilityAdapters;
    private RangingCapabilities mCachedCapabilities;

    /** Callbacks provided from the framework */
    private final RemoteCallbackList<IRangingCapabilitiesCallback> mCallbacks =
            new RemoteCallbackList<>();

    public CapabilitiesProvider(RangingInjector rangingInjector) {
        mRangingInjector = rangingInjector;
        mCapabilityAdapters = new HashMap<>();
        mCachedCapabilities = null;
    }

    public synchronized void registerCapabilitiesCallback(
            @NonNull IRangingCapabilitiesCallback callback
    ) {
        mCallbacks.register(callback);
        try {
            callback.onRangingCapabilities(getCapabilities());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call provided capabilities callback", e);
        }
    }

    public synchronized void unregisterCapabilitiesCallback(
            @NonNull IRangingCapabilitiesCallback callback
    ) {
        mCallbacks.unregister(callback);
    }

    public synchronized @NonNull RangingCapabilities getCapabilities() {
        if (mCachedCapabilities == null) {
            initializeAdaptersForAllTechnologies();
            mCachedCapabilities = queryAdaptersForCapabilities().build();
        }
        return mCachedCapabilities;
    }

    private synchronized RangingCapabilities.Builder queryAdaptersForCapabilities() {
        RangingCapabilities.Builder builder = new RangingCapabilities.Builder();
        for (RangingTechnology technology : mCapabilityAdapters.keySet()) {
            CapabilitiesAdapter adapter = mCapabilityAdapters.get(technology);
            // Any calls to the corresponding technology stacks must be
            // done with a clear calling identity.
            long token = Binder.clearCallingIdentity();
            TechnologyCapabilities capabilities = adapter.getCapabilities();
            builder.addAvailability(technology.getValue(), adapter.getAvailability());
            if (capabilities != null) {
                builder.addCapabilities(capabilities);
            }
            Binder.restoreCallingIdentity(token);
        }
        return builder;
    }

    private synchronized void initializeAdaptersForAllTechnologies() {
        Log.i(TAG, "Registering availability listeners for each technology");
        // Any calls to the corresponding technology stacks must be
        // done with a clear calling identity.
        long token = Binder.clearCallingIdentity();
        for (RangingTechnology technology : RangingTechnology.TECHNOLOGIES) {
            mCapabilityAdapters.put(
                    technology,
                    mRangingInjector.createCapabilitiesAdapter(
                            technology,
                            new TechnologyAvailabilityListener(technology)));
        }
        Binder.restoreCallingIdentity(token);
    }

    public class TechnologyAvailabilityListener {
        private final RangingTechnology mTechnology;

        TechnologyAvailabilityListener(RangingTechnology technology) {
            mTechnology = technology;
        }

        public void onAvailabilityChange(
                @RangingTechnologyAvailability int availability,
                @AvailabilityChangedReason int unused
        ) {
            synchronized (CapabilitiesProvider.this) {
                mCachedCapabilities = queryAdaptersForCapabilities()
                        .addAvailability(mTechnology.getValue(), availability)
                        .build();
                synchronized (mCallbacks) {
                    int i = mCallbacks.beginBroadcast();
                    while (i > 0) {
                        i--;
                        try {
                            mCallbacks.getBroadcastItem(i)
                                    .onRangingCapabilities(mCachedCapabilities);
                        } catch (RemoteException e) {
                            Log.w(TAG,
                                    "Failed to notify callback " + i + " of availability change");
                        }
                    }
                    mCallbacks.finishBroadcast();
                }
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of CapabilitiesProvider ----");
        for (Map.Entry<RangingTechnology, CapabilitiesAdapter> adapter :
                mCapabilityAdapters.entrySet()
        ) {
            pw.println("-- Dump of CapabilitiesAdapter for technology " + adapter.getKey() + " --");
            pw.println("Availability: " + adapter.getValue().getAvailability());
            pw.println("Capabilities: " + adapter.getValue().getCapabilities());
            pw.println("-- Dump of CapabilitiesAdapter for technology " + adapter.getKey() + " --");
        }
        pw.println("---- Dump of CapabilitiesProvider ----");
    }
}

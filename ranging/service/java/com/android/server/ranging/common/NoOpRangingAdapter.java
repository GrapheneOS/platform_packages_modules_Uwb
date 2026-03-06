/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.server.ranging.common;

import android.content.AttributionSource;
import android.ranging.RangingDevice;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.session.ConfigurationManager;

import com.google.common.collect.ImmutableSet;

/**
 * Some technologies do not require any API calls to start ranging:
 * <ul>
 *     <li>BLE CS responder</li>
 *     <li>BLE RSSI responder</li>
 * </ul>
 * This adapter is used for these technologies to ensure {@link RangingAdapter} clients receive
 * the proper callbacks. This adapter simply invokes the callbacks directly, rather than propagating
 * them from an underlying technology stack.
 */
public class NoOpRangingAdapter implements RangingAdapter {
    private static final String TAG = NoOpRangingAdapter.class.getSimpleName();

    private final RangingTechnology mTechnology;
    private final Object mLock;

    @GuardedBy("mLock")
    private ImmutableSet<RangingDevice> mPeers;
    @GuardedBy("mLock")
    private Callback mCallback;

    public NoOpRangingAdapter(RangingTechnology technology, Object lock) {
        mTechnology = technology;
        mLock = lock;
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return mTechnology;
    }

    @Override
    public void start(@NonNull ConfigurationManager.TechnologyConfig config,
            @Nullable AttributionSource nonPrivilegedAttributionSource,
            @NonNull Callback callback) {
        synchronized (mLock) {
            if (mCallback != null) {
                Log.w(TAG, "Attempt to start adapter when it was already started");
                mCallback.onClosed(InternalReason.INTERNAL_ERROR);
                return;
            }

            mCallback = callback;
            mPeers = config.getPeerDevices();
            callback.onStarted(mPeers);
        }
    }

    @Override
    public void stop() {
        synchronized (mLock) {
            if (mCallback == null) {
                Log.v(TAG, "Attempted to stop adapter when it was already stopped");
                return;
            }

            mCallback.onStopped(mPeers, InternalReason.LOCAL_REQUEST);
            mCallback.onClosed(InternalReason.LOCAL_REQUEST);
            mCallback = null;
        }
    }

    @Override
    public void appMovedToBackground() { }

    @Override
    public void appMovedToForeground() { }

    @Override
    public void appInBackgroundTimeout() { }

    @Override
    public String toString() {
        synchronized (mLock) {
            return "NoOpRangingAdapter{" +
                    "mTechnology=" + mTechnology +
                    ", mPeers=" + mPeers +
                    '}';
        }
    }
}

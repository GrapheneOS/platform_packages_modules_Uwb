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

package android.ranging;

import android.content.AttributionSource;
import android.os.Process;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * @hide
 */
public final class RangingSessionManager extends IRangingCallbacks.Stub {

    private static final String TAG = "RangingSessionManager";
    private final IRangingAdapter mRangingAdapter;
    private static long sSessionIdCounter = 1;

    private final Map<SessionHandle, RangingSession> mSessions = new ConcurrentHashMap<>();

    public RangingSessionManager(IRangingAdapter rangingAdapter) {
        mRangingAdapter = rangingAdapter;
    }

    public RangingSession createRangingSessionInstance(
            AttributionSource attributionSource, RangingSession.Callback callback, Executor executor
    ) {
        SessionHandle sessionHandle = new SessionHandle(sSessionIdCounter++, attributionSource,
                Process.myPid());
        RangingSession rangingSession = new RangingSession(this,
                attributionSource, sessionHandle, mRangingAdapter, callback, executor);
        mSessions.put(sessionHandle, rangingSession);
        return rangingSession;
    }

    @Override
    public void onStarted(SessionHandle sessionHandle, int technology) {
        if (!mSessions.containsKey(sessionHandle)) {
            Log.e(TAG, "SessionHandle not found");
        }
        mSessions.get(sessionHandle).onRangingStarted(technology);
    }

    @Override
    public void onClosed(SessionHandle sessionHandle, int reason) {
        if (!mSessions.containsKey(sessionHandle)) {
            Log.e(TAG, "SessionHandle not found");
        }
        mSessions.get(sessionHandle).onRangingClosed(reason);
    }

    @Override
    public void onData(SessionHandle sessionHandle, RangingDevice device, RangingData data) {
        if (!mSessions.containsKey(sessionHandle)) {
            Log.e(TAG, "SessionHandle not found");
        }
        mSessions.get(sessionHandle).onData(device, data);
    }
}

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

package com.android.ranging.adapter;

import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.ranging.RangingData;
import com.android.ranging.RangingTechnology;
import com.android.ranging.RangingUtils;

import com.google.common.util.concurrent.ListenableFuture;

public class StatefulAdapter<A extends RangingAdapter> implements RangingAdapter {
    private static final String TAG = StatefulAdapter.class.getSimpleName();

    private final A mAdapter;
    private final RangingUtils.StateMachine<State> mStateMachine;

    /** State of the adapter. */
    public enum State {
        STOPPING,
        STOPPED,
        STARTING,
        STARTED,
    }

    public StatefulAdapter(A adapter) {
        mAdapter = adapter;
        mStateMachine = new RangingUtils.StateMachine<>(State.STOPPED);
    }

    /** @return the state of the adapter */
    @NonNull
    public State getState() {
        return mStateMachine.getState();
    }

    @Override
    public RangingTechnology getType() {
        return mAdapter.getType();
    }

    @Override
    public ListenableFuture<Boolean> isEnabled() throws RemoteException {
        return mAdapter.isEnabled();
    }

    @Override
    public void start(Callback callback) {
        if (mStateMachine.transition(State.STOPPED, State.STARTING)) {
            mAdapter.start(new StateChangeWrapper(callback));
        } else {
            Log.w(TAG, "Failed transition STOPPED -> STARTING: not in STOPPED");
        }
    }

    @Override
    public void stop() {
        if (mStateMachine.transition(State.STARTED, State.STOPPING)) {
            mAdapter.stop();
        } else {
            Log.w(TAG, "Failed transition STARTED -> STOPPING: not in STARTED");
        }
    }

    private class StateChangeWrapper implements Callback {
        private final Callback mCallbacks;

        StateChangeWrapper(Callback callbacks) {
            mCallbacks = callbacks;
        }

        @Override
        public void onStarted() {
            if (!mStateMachine.transition(State.STARTING, State.STARTED)) {
                mCallbacks.onStarted();
            } else {
                Log.e(TAG, "Failed transition STARTING -> STARTED: not in STARTING");
            }
        }

        @Override
        public void onStopped(StoppedReason reason) {
            if (mStateMachine.transition(State.STOPPING, State.STOPPED)) {
                mCallbacks.onStopped(reason);
            } else {
                Log.e(TAG, "Failed transition STOPPING -> STOPPED: not in STOPPING");
            }
        }

        @Override
        public void onRangingData(RangingData rangingData) {
            if (mStateMachine.getState() == State.STARTED) {
                mCallbacks.onRangingData(rangingData);
            } else {
                Log.e(TAG, "Received ranging data but not in STARTED");
            }
        }
    }
}

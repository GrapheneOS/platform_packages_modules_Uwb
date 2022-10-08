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

package com.google.uwb.support.oemextension;

import android.os.PersistableBundle;
import android.uwb.UwbManager;

import com.google.uwb.support.base.RequiredParam;

/**
 * Session status for oem extension callback
 *
 * <p> This is passed as a bundle to oem extension API
 * {@link UwbManager.UwbOemExtensionCallback#onSessionStatusNotificationReceived(PersistableBundle)}
 */
public class SessionStatus {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final long mSessionId;
    private final int mState;
    private final int mReasonCode;
    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String SESSION_ID = "session_id";
    public static final String STATE = "state";
    public static final String REASON_CODE = "reason_code";

    public static int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public long getSessionId() {
        return mSessionId;
    }

    public int getState() {
        return mState;
    }

    public int getReasonCode() {
        return mReasonCode;
    }

    private SessionStatus(long sessionId, int state, int reasonCode) {
        mSessionId = sessionId;
        mState = state;
        mReasonCode = reasonCode;
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putLong(SESSION_ID, mSessionId);
        bundle.putInt(STATE, mState);
        bundle.putInt(REASON_CODE, mReasonCode);
        return bundle;
    }

    public static SessionStatus fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static SessionStatus parseVersion1(PersistableBundle bundle) {
        return new SessionStatus.Builder()
                .setSessionId(bundle.getLong(SESSION_ID))
                .setState(bundle.getInt(STATE))
                .setReasonCode(bundle.getInt(REASON_CODE))
                .build();
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Long> mSessionId = new RequiredParam<>();
        private final RequiredParam<Integer> mState = new RequiredParam<>();
        private final RequiredParam<Integer> mReasonCode = new RequiredParam<>();

        public SessionStatus.Builder setSessionId(long sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        public SessionStatus.Builder setState(int state) {
            mState.set(state);
            return this;
        }

        public SessionStatus.Builder setReasonCode(int reasonCode) {
            mReasonCode.set(reasonCode);
            return this;
        }

        public SessionStatus build() {
            return new SessionStatus(
                    mSessionId.get(),
                    mState.get(),
                    mReasonCode.get());
        }
    }
}

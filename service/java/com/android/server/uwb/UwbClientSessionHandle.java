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

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.uwb.SessionHandle;

import java.util.Objects;

/**
 * Container for storing unique session info.
 */
public class UwbClientSessionHandle {
    private final SessionHandle mSessionHandle;
    private final AttributionSource mAttributionSource;


    public UwbClientSessionHandle(@NonNull SessionHandle sessionHandle,
            @NonNull AttributionSource attributionSource) {
        mSessionHandle = sessionHandle;
        mAttributionSource = attributionSource;
    }

    @Override
    public java.lang.String toString() {
        return "UwbClientSessionHandle{"
                + "mSessionHandle=" + mSessionHandle
                + ", mAttributionSource=" + mAttributionSource
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof UwbClientSessionHandle)) return false;
        UwbClientSessionHandle that = (UwbClientSessionHandle) object;
        return Objects.equals(mSessionHandle, that.mSessionHandle)
                && Objects.equals(mAttributionSource, that.mAttributionSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionHandle, mAttributionSource);
    }

    @NonNull
    public SessionHandle getSessionHandle() {
        return mSessionHandle;
    }

    @NonNull
    public AttributionSource getAttributionSource() {
        return mAttributionSource;
    }
}

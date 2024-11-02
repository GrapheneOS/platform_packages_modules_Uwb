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

package android.ranging.params;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.os.Parcelable;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public abstract class RangingParams implements Parcelable {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            RANGING_SESSION_RAW,
            RANGING_SESSION_OOB,
    })
    public @interface RangingSessionType {
    }

    /** Ranging session with oob performed by the app */
    public static final int RANGING_SESSION_RAW = 0;
    /** Ranging session oob performed by ranging module */
    public static final int RANGING_SESSION_OOB = 1;

    @RangingSessionType
    protected int mRangingSessionType;

    public int getRangingSessionType() {
        return mRangingSessionType;
    }
}

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

package com.android.server.ranging.fusion;

import android.ranging.RangingData;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingTechnology;

import java.util.Optional;
import java.util.Set;

/**
 * Enhances and combines raw data from multiple ranging technologies and/or on-device sensors to
 * produce more accurate distance measurements.
 */
public abstract class FusionEngine {
    /**
     * Incrementally combines data from multiple ranging technologies.
     */
    public interface DataFuser {
        /**
         * Provide data to the fuser.
         *
         * @param data    produced from a ranging technology.
         * @param sources of ranging data. <b>Implementations of this method must not mutate this
         *                parameter.</b>
         * @return fused data if the provided data makes any available.
         */
        Optional<RangingData> fuse(
                @NonNull RangingData data, final @NonNull Set<RangingTechnology> sources
        );
    }

    /**
     * Callbacks to notify on fusion events.
     */
    public interface Callback {
        /**
         * Called when the engine produces fused data.
         *
         * @param data produced by the engine.
         */
        void onData(@NonNull RangingData data);
    }

    protected final DataFuser mFuser;
    protected Callback mCallback;

    /**
     * Construct the fusion engine.
     *
     * @param fuser to use on data provided to this engine.
     */
    protected FusionEngine(@NonNull DataFuser fuser) {
        mFuser = fuser;
        mCallback = null;
    }

    /**
     * Start the fusion engine.
     *
     * @param callback to notify on engine events.
     */
    public void start(@NonNull Callback callback) {
        mCallback = callback;
    }

    /**
     * Stop the fusion engine.
     */
    public void stop() {
    }

    /**
     * Feed data to the engine.
     *
     * @param data produced from a ranging technology.
     */
    public void feed(@NonNull RangingData data) {
        if (mCallback != null) {
            mFuser.fuse(data, getDataSources()).ifPresent(mCallback::onData);
        }
    }

    /**
     * @return the current set of data sources to the fusion engine.
     */
    protected abstract @NonNull Set<RangingTechnology> getDataSources();

    /**
     * Add a technology as a source of data to the engine.
     *
     * @param technology to add.
     */
    public abstract void addDataSource(@NonNull RangingTechnology technology);

    /**
     * Remove a technology as a source of data to the engine.
     *
     * @param technology to remove.
     */
    public abstract void removeDataSource(@NonNull RangingTechnology technology);
}

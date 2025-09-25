/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.uwb.fusion.pose;

import androidx.annotation.NonNull;

import com.android.uwb.fusion.math.Pose;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Optional base implementation for a PoseSource. Provides help to register listeners and
 * publishing.
 */
public abstract class PoseSourceBase implements IPoseSource {
    private final Set<PoseEventListener> mListeners;
    private static final String TAG = "PoseSourceBase";
    private Pose mPose;

    public PoseSourceBase() {
        mListeners = Collections.synchronizedSet(new HashSet<>());
    }

    /**
     * Starts the pose source. Called by the {@link PoseSourceBase} when the first
     * listener subscribes.
     */
    protected abstract void start();

    /**
     * Stops the pose source. Called by the {@link PoseSourceBase} when the last
     * listener unsubscribes.
     */
    protected abstract void stop();

     /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void close() {
        if (mListeners.size() > 0) {
            mListeners.clear();
            stop(); // Run inside the lock to make sure stops and starts are sequential.
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void registerListener(@NonNull PoseEventListener listener) {
        Objects.requireNonNull(listener);
        mListeners.add(listener);
        if (mListeners.size() == 1) {
            start();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean unregisterListener(@NonNull PoseEventListener listener) {
        Objects.requireNonNull(listener);
        boolean removed = mListeners.remove(listener);
        if (removed && mListeners.size() == 0) {
            stop();
        }
        return removed;
    }

    /**
     * Publishes the pose to all listeners.
     *
     * @param pose The updated device pose.
     */
    protected synchronized void publish(@NonNull Pose pose) {
        Objects.requireNonNull(pose);
        mPose = pose;
        mListeners.forEach(listener -> listener.onPoseChanged(pose));
    }

    @Override
    public synchronized Pose getPose() {
        return mPose;
    }
}

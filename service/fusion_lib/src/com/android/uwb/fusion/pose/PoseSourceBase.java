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

import java.util.EnumSet;
import java.util.Objects;

/**
 * Provides pose update information.
 */
public abstract class PoseSourceBase implements AutoCloseable {
    /** The shortest practical update interval for a pose source. */
    public static int MIN_INTERVAL_MS = 1000 / 60; // 60Hz

    /** The longest practical update interval for a pose source. */
    public static int MAX_INTERVAL_MS = 10000; // 0.1Hz.

    /**
     * A set of all possible pose source capabilities.
     */
    public enum Capabilities {
        YAW, PITCH, ROLL, X, Y, Z,
        /**
         * Indicates that a pitch and roll of 0 means that the phone is upright. If this flag
         * is not present, pitch and roll changes are only relative.
         */
        UPRIGHT;

        public static final EnumSet<Capabilities> ALL = EnumSet.allOf(Capabilities.class);
        public static final EnumSet<Capabilities> NONE = EnumSet.noneOf(Capabilities.class);
        public static final EnumSet<Capabilities> ROTATION = EnumSet.of(
                Capabilities.YAW,
                Capabilities.PITCH,
                Capabilities.ROLL
        );
        public static final EnumSet<Capabilities> UPRIGHT_ROTATION = EnumSet.of(
                Capabilities.YAW,
                Capabilities.PITCH,
                Capabilities.ROLL,
                Capabilities.UPRIGHT);
        public static final EnumSet<Capabilities> TRANSLATION = EnumSet.of(
                Capabilities.X,
                Capabilities.Y,
                Capabilities.Z);
    }

    private volatile Pose mPose;


    /**
     * Starts the pose source.
     */
    public abstract void start();

    /**
     * Stops the pose source.
     */
    @Override
    public abstract void close();

    /**
     * Gets the capabilities of this pose source.
     * @return An EnumSet of Capabilities.
     */
    @NonNull
    public abstract EnumSet<Capabilities> getCapabilities();

    /**
     * Gets the current pose.
     * @return The current pose. May be null.
     */
    public Pose getPose() {
        return mPose;
    }

    /**
     * Publishes the pose.
     *
     * @param pose The updated device pose.
     */
    protected void publish(@NonNull Pose pose) {
        Objects.requireNonNull(pose);
        mPose = pose;
    }
}

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

package androidx.core.uwb.backend.impl.internal;

import static androidx.core.uwb.backend.impl.internal.RangingSessionCallback.REASON_FAILED_TO_START;
import static androidx.core.uwb.backend.impl.internal.RangingSessionCallback.REASON_STOP_RANGING_CALLED;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_ID_7;
import static androidx.core.uwb.backend.impl.internal.Utils.INVALID_API_CALL;
import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_OK;
import static androidx.core.uwb.backend.impl.internal.Utils.SUPPORTED_BPRF_PREAMBLE_INDEX;
import static androidx.core.uwb.backend.impl.internal.Utils.TAG;
import static androidx.core.uwb.backend.impl.internal.Utils.UWB_SYSTEM_CALLBACK_FAILURE;

import static com.google.uwb.support.fira.FiraParams.UWB_CHANNEL_9;

import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import android.uwb.UwbManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/** Represents a UWB ranging controller */
@RequiresApi(api = VERSION_CODES.S)
public class RangingController extends RangingDevice {

    private final List<UwbAddress> mDynamicallyAddedPeers = new ArrayList<>();

    @Nullable private RangingSessionCallback mRangingSessionCallback;

    RangingController(
            UwbManager manager, Executor executor, OpAsyncCallbackRunner opAsyncCallbackRunner) {
        super(manager, executor, opAsyncCallbackRunner);
    }

    @Override
    protected FiraOpenSessionParams getOpenSessionParams() {
        requireNonNull(mRangingParameters);
        return ConfigurationManager.createOpenSessionParams(
                FiraParams.RANGING_DEVICE_TYPE_CONTROLLER, getLocalAddress(), mRangingParameters);
    }

    /**
     * gets complex channel. if it's the first time that this function is called, it will check the
     * driver and try to get the best-available settings.
     */
    @SuppressLint("WrongConstant")
    public UwbComplexChannel getComplexChannel() {
        if (isForTesting()) {
            mComplexChannel =
                    new UwbComplexChannel(Utils.channelForTesting, Utils.preambleIndexForTesting);
        }
        if (mComplexChannel == null) {
            mComplexChannel = getBestAvailableComplexChannel();
        }
        return mComplexChannel;
    }

    /** Sets complex channel. */
    public void setComplexChannel(UwbComplexChannel complexChannel) {
        mComplexChannel = complexChannel;
    }

    /**
     * Update the complex channel, even if the complex channel has been set before. Channel 9 is
     * mandatory to all devices. Since system API hasn't implemented capability check yet, channel 9
     * is the best guess for now.
     *
     * @return The complex channel most suitable for this ranging session.
     */
    public UwbComplexChannel getBestAvailableComplexChannel() {
        int preambleIndex =
                SUPPORTED_BPRF_PREAMBLE_INDEX.get(
                        new Random().nextInt(SUPPORTED_BPRF_PREAMBLE_INDEX.size()));
        UwbComplexChannel availableChannel = new UwbComplexChannel(UWB_CHANNEL_9, preambleIndex);
        Log.i(TAG, String.format("set complexChannel to %s", availableChannel));
        return availableChannel;
    }

    @Override
    public synchronized int startRanging(
            RangingSessionCallback callback, ExecutorService backendCallbackExecutor) {
        requireNonNull(mRangingParameters);
        if (mComplexChannel == null) {
            Log.w(TAG, "Need to call getComplexChannel() first");
            return INVALID_API_CALL;
        }

        if (ConfigurationManager.isUnicast(mRangingParameters.getUwbConfigId())
                && mRangingParameters.getPeerAddresses().size() > 1) {
            Log.w(
                    TAG,
                    String.format(
                            "Config ID %d doesn't support one-to-many",
                            mRangingParameters.getUwbConfigId()));
            return INVALID_API_CALL;
        }

        int status = super.startRanging(callback, backendCallbackExecutor);
        if (isAlive()) {
            mRangingSessionCallback = callback;
        }
        return status;
    }

    @Override
    public synchronized int stopRanging() {
        int status = super.stopRanging();
        mRangingSessionCallback = null;
        return status;
    }

    /**
     * Add a new controlee to the controller. If the controleer is added successfully, {@link
     * RangingSessionCallback#onRangingInitialized(UwbDevice)} will be called. If the adding
     * operation failed, {@link RangingSessionCallback#onRangingSuspended(UwbDevice, int)} will be
     * called.
     *
     * @return {@link Utils#INVALID_API_CALL} if this is a unicast session but multiple peers are
     *     configured.
     */
    public synchronized int addControlee(UwbAddress controleeAddress) {
        Log.i(TAG, String.format("Add UWB peer: %s", controleeAddress));
        if (!isAlive()) {
            return INVALID_API_CALL;
        }
        if (ConfigurationManager.isUnicast(mRangingParameters.getUwbConfigId())) {
            return INVALID_API_CALL;
        }
        if (isKnownPeer(controleeAddress) || mDynamicallyAddedPeers.contains(controleeAddress)) {
            return STATUS_OK;
        }
        // Reconfigure the session.
        int[] subSessionIdList =
                mRangingParameters.getUwbConfigId() == CONFIG_ID_7
                        ? new int[] {mRangingParameters.getSubSessionId()}
                        : null;
        byte[] subSessionKeyInfo =
                mRangingParameters.getUwbConfigId() == CONFIG_ID_7
                        ? mRangingParameters.getSubSessionKeyInfo()
                        : null;
        boolean success =
                reconfigureRanging(
                        ConfigurationManager.createReconfigureParams(
                                        mRangingParameters.getUwbConfigId(),
                                        FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD,
                                        new UwbAddress[] {controleeAddress},
                                        subSessionIdList,
                                        subSessionKeyInfo)
                                .toBundle());

        if (success) {
            if (mRangingSessionCallback != null) {
                runOnBackendCallbackThread(
                        () -> {
                            requireNonNull(mRangingSessionCallback);
                            mRangingSessionCallback.onRangingInitialized(
                                    UwbDevice.createForAddress(controleeAddress.toBytes()));
                        });
            }
            mDynamicallyAddedPeers.add(controleeAddress);
        } else {
            if (mRangingSessionCallback != null) {
                runOnBackendCallbackThread(
                        () -> {
                            requireNonNull(mRangingSessionCallback);
                            mRangingSessionCallback.onRangingSuspended(
                                    UwbDevice.createForAddress(controleeAddress.toBytes()),
                                    REASON_FAILED_TO_START);
                        });
            }
        }

        return STATUS_OK;
    }

    /**
     * Remove a controlee from current session.
     *
     * @return returns {@link Utils#STATUS_OK} if the controlee is removed successfully. returns
     *     {@link Utils#INVALID_API_CALL} if:
     *     <ul>
     *       <li>Provided address is not in the controller's peer list
     *       <li>The active profile is unicast
     *     </ul>
     */
    public synchronized int removeControlee(UwbAddress controleeAddress) {
        Log.i(TAG, String.format("Remove UWB peer: %s", controleeAddress));
        if (!isAlive()) {
            Log.w(TAG, "Attempt to remove controlee while session is not active.");
            return INVALID_API_CALL;
        }
        if (!mDynamicallyAddedPeers.contains(controleeAddress)) {
            Log.w(TAG, "Attempt to remove non-existing controlee.");
            return INVALID_API_CALL;
        }

        // Reconfigure the session.
        boolean success =
                reconfigureRanging(
                        ConfigurationManager.createReconfigureParams(
                                        mRangingParameters.getUwbConfigId(),
                                        FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE,
                                        new UwbAddress[] {controleeAddress},
                                        /* subSessionIdList= */ null,
                                        /* subSessionKey= */ null)
                                .toBundle());
        if (!success) {
            return UWB_SYSTEM_CALLBACK_FAILURE;
        }

        if (mRangingSessionCallback != null) {
            runOnBackendCallbackThread(
                    () -> {
                        requireNonNull(mRangingSessionCallback);
                        mRangingSessionCallback.onRangingSuspended(
                                UwbDevice.createForAddress(controleeAddress.toBytes()),
                                REASON_STOP_RANGING_CALLED);
                    });
        }
        mDynamicallyAddedPeers.remove(controleeAddress);
        return STATUS_OK;
    }
}

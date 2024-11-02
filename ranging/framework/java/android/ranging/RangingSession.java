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

package android.ranging;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.AttributionSource;
import android.os.CancellationSignal;
import android.os.RemoteException;
import android.util.Log;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;


/**
 * Represents a session for performing ranging operations. A {@link RangingSession} manages
 * the lifecycle of a ranging operation, including start, stop, and event callbacks.
 *
 * <p>All methods are asynchronous and rely on the provided {@link Executor} to invoke
 * callbacks on appropriate threads.
 *
 * <p>This class implements {@link AutoCloseable}, ensuring that resources can be
 * automatically released when the session is closed.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingSession implements AutoCloseable {
    private static final String TAG = "RangingSession";
    private final AttributionSource mAttributionSource;
    private final SessionHandle mSessionHandle;
    private final IRangingAdapter mRangingAdapter;
    private final RangingSessionManager mRangingSessionManager;
    private final Callback mCallback;
    private final Executor mExecutor;
    private final Map<RangingDevice, ITransportHandle> mTransportHandles =
            new ConcurrentHashMap<>();


    /**
     * @hide
     */
    public RangingSession(RangingSessionManager rangingSessionManager,
            AttributionSource attributionSource,
            SessionHandle sessionHandle, IRangingAdapter rangingAdapter,
            Callback callback, Executor executor) {
        mRangingSessionManager = rangingSessionManager;
        mAttributionSource = attributionSource;
        mSessionHandle = sessionHandle;
        mRangingAdapter = rangingAdapter;
        mCallback = callback;
        mExecutor = executor;
    }

    /**
     * Starts the ranging session with the provided ranging preferences.
     * <p>The {@link RangingSession.Callback#onRangingStarted(int)}
     * (android.ranging.RangingSession)} method is called with
     * {@link android.ranging.RangingManager.RangingTechnology}.
     *
     * <p>The provided {@link RangingPreference} determines the configuration for the session.
     * A {@link CancellationSignal} is returned to allow the caller to cancel the session
     * if needed. If the session is canceled, the {@link #close()} method will be invoked
     * automatically to release resources.
     *
     * @param rangingPreference {@link RangingPreference} the preferences for configuring the
     *                                ranging session.
     * @return a {@link CancellationSignal} to close the session.
     */
    @NonNull
    public CancellationSignal start(@NonNull RangingPreference rangingPreference) {
        try {
            mRangingAdapter.startRanging(mAttributionSource, mSessionHandle, rangingPreference,
                    mRangingSessionManager);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(() -> this.close());

        // TODO: if SmartRangingParams then setupTransportHandles();
        return cancellationSignal;
    }

    private void setupTransportHandles() {
        // TODO: Add TransportHandle initialization
        //      foreach deviceHandle:
        //          mTransportHandles.add(rangingDevice, transportHandle)
        //          transportHandle.registerReceiveCallback(new TransportHandleReceiveCallback
        //          (device))
    }


    /**
     * Stops the ranging session.
     *
     * <p>This method releases any ongoing ranging operations. If the operation fails,
     * it will propagate a {@link RemoteException} from the system server.
     *
     */
    public void stop() {
        try {
            mRangingAdapter.stopRanging(mSessionHandle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void onRangingStarted(int technology) {
        mExecutor.execute(() -> mCallback.onStarted(technology));
    }

    /**
     * @hide
     */
    public void onRangingClosed(int reason) {
        mExecutor.execute(() -> mCallback.onClosed(reason));
    }

    /**
     * @hide
     */
    public void onData(RangingDevice device, RangingData data) {
        mExecutor.execute(() -> mCallback.onResults(device, data));
    }

    /**
     * @hide
     */
    void sendOobData(RangingDevice toDevice, byte[] data) {
        if (!mTransportHandles.containsKey(toDevice)) {
            Log.e(TAG, "TransportHandle not found for session: " + mSessionHandle + ", device: "
                    + toDevice);
        }
        mTransportHandles.get(toDevice).sendData(data);
    }

    /**
     * @hide
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Callback interface for receiving ranging session events.
     */
    public interface Callback {
        /**
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {
                REASON_UNKNOWN,
                REASON_LOCAL_REQUEST,
                REASON_SYSTEM_POLICY,
                REASON_UNSUPPORTED,
        })
        @interface Reason {}

        /**
         * Indicates that the session was closed or failed to open due to an unknown reason
         */
        int REASON_UNKNOWN = 0;

        /**
         * Indicates that the session was closed or failed to open because
         * {@link AutoCloseable#close()} or {@link RangingSession#stop()} was called
         */
        int REASON_LOCAL_REQUEST = 1;

        /**
         * Indicates that the local system policy caused the change, such
         * as privacy policy, power management policy, permissions, and more.
         */
        int REASON_SYSTEM_POLICY = 2;

        /**
         * Indicates that the requested ranging operation is not supported.
         */
        int REASON_UNSUPPORTED = 3;

        /**
         * Called when the ranging session starts successfully.
         *
         * @param technology {@link android.ranging.RangingManager.RangingTechnology }
         * the ranging technology used for the session.
         *
         * @hide
         */
        void onStarted(@RangingManager.RangingTechnology int technology);

        /**
         * Called when the ranging session fails to start.
         *
         * @param reason the reason for the failure, limited to values defined by {@link Reason}.
         */
        void onStartFailed(@Reason int reason, RangingDevice device);

        /**
         * Called when the ranging session is closed.
         *
         * @param reason the reason why the session was closed, limited to values
         *               defined by {@link Reason}.
         */
        void onClosed(@Reason int reason);

        /*public void onRangingStarted(@NonNull RangingStartedParameters
                params);*/

        /**
         * Called when ranging operations stop for a device.
         *
         * @param device the {@link RangingDevice} for which the ranging operation stopped.
         */
        void onRangingStopped(@NonNull RangingDevice device);

        /**
         * Called when ranging data is available for the ranging device.
         *
         * @param device the {@link RangingDevice} for which ranging data was received.
         * @param data the {@link RangingData} received during the ranging session.
         */
        void onResults(@NonNull RangingDevice device, @NonNull RangingData data);
    }

    class TransportHandleReceiveCallback implements ITransportHandle.ReceiveCallback {

        private final OobHandle mOobHandle;

        TransportHandleReceiveCallback(RangingDevice device) {
            mOobHandle = new OobHandle(mSessionHandle, device);
        }

        @Override
        public void onReceiveData(byte[] data) {
            mRangingSessionManager.oobDataReceived(mOobHandle, data);
        }

        @Override
        public void onDisconnect() {
            mRangingSessionManager.deviceOobDisconnected(mOobHandle);
        }

        @Override
        public void onReconnect() {
            mRangingSessionManager.deviceOobReconnected(mOobHandle);
        }

        @Override
        public void onClose() {
            mRangingSessionManager.deviceOobClosed(mOobHandle);
        }
    }
}

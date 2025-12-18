/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

import android.content.AttributionSource;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.uwb.IChannelUsageCallback;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbAdfProvisionStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;
import android.uwb.IUwbVendorUciCallback;
import android.uwb.IUwbOemExtensionCallback;
import android.uwb.IOnUwbActivityEnergyInfoListener;
import android.uwb.LogicalLinkCreationParams;
import android.uwb.LogicalLinkConnectionParams;
import android.uwb.timesync.ITimesyncCallbackListener;

/**
 * @hide
 * TODO(b/211025367): Remove all the duplicate javadocs here.
 */
interface IUwbAdapter {
  /*
   * Register the callbacks used to notify the framework of events and data
   *
   * The provided callback's IUwbAdapterStateCallbacks#onAdapterStateChanged
   * function must be called immediately following registration with the current
   * state of the UWB adapter.
   *
   * @param callbacks callback to provide range and status updates to the framework
   */
  void registerAdapterStateCallbacks(in IUwbAdapterStateCallbacks adapterStateCallbacks);

   /*
    * Register the callbacks used to notify the framework of events and data
    *
    * The provided callback's IUwbUciVendorCallback#onVendorNotificationReceived
    * function must be called immediately following vendorNotification received
    *
    * @param callbacks callback to provide Notification data updates to the framework
    */
   void registerVendorExtensionCallback(in IUwbVendorUciCallback callbacks);

   /*
    * Unregister the callbacks used to notify the framework of events and data
    *
    * Calling this function with an unregistered callback is a no-op
    *
    * @param callbacks callback to unregister
    */
   void unregisterVendorExtensionCallback(in IUwbVendorUciCallback callbacks);

   /*
    * Register the callback to get notified the channel usage events.
    */
   void registerChannelUsageCallback(in IChannelUsageCallback callback);

   /*
    * Unregister the callback used for notification channel usage events.
    */
   void unregisterChannelUsageCallback(in IChannelUsageCallback callback);

   /*
   * Unregister the callbacks used to notify the framework of events and data
   *
   * Calling this function with an unregistered callback is a no-op
   *
   * @param callbacks callback to unregister
   */
  void unregisterAdapterStateCallbacks(in IUwbAdapterStateCallbacks callbacks);

  void registerOemExtensionCallback(in IUwbOemExtensionCallback callbacks);

  void unregisterOemExtensionCallback(in IUwbOemExtensionCallback callbacks);

  /**
   * Register the callback used for notification for timesync events.
   */
  void registerTimesyncCallback(in ITimesyncCallbackListener callback,
                                in String address, in int type);

  /**
   * Unregister the callback used for notification for timesync events.
   */
  void unregisterTimesyncCallback(in ITimesyncCallbackListener callback,
                                  in String address, in int type);

  /**
   * Get the accuracy of the ranging timestamps
   *
   * @param chipId identifier of UWB chip for multi-HAL devices
   *
   * @return accuracy of the ranging timestamps in nanoseconds
   */
  long getTimestampResolutionNanos(in String chipId);

  /**
   * Provides the capabilities and features of the device
   *
   * @param chipId identifier of UWB chip for multi-HAL devices
   *
   * @return specification specific capabilities and features of the device
   */
  PersistableBundle getSpecificationInfo(in String chipId);

  /**
   * Request to open a new ranging session
   *
   * This function does not start the ranging session, but all necessary
   * components must be initialized and ready to start a new ranging
   * session prior to calling IUwbAdapterCallback#onRangingOpened.
   *
   * IUwbAdapterCallbacks#onRangingOpened must be called within
   * RANGING_SESSION_OPEN_THRESHOLD_MS milliseconds of #openRanging being
   * called if the ranging session is opened successfully.
   *
   * IUwbAdapterCallbacks#onRangingOpenFailed must be called within
   * RANGING_SESSION_OPEN_THRESHOLD_MS milliseconds of #openRanging being called
   * if the ranging session fails to be opened.
   *
   * If the provided sessionHandle is already open for the calling client, then
   * #onRangingOpenFailed must be called and the new session must not be opened.
   *
   * @param attributionSource AttributionSource to use for permission enforcement.
   * @param sessionHandle the session handle to open ranging for
   * @param rangingCallbacks the callbacks used to deliver ranging information
   * @param parameters the configuration to use for ranging
   * @param chipId identifier of UWB chip for multi-HAL devices
   */
  void openRanging(in AttributionSource attributionSource,
                   in SessionHandle sessionHandle,
                   in IUwbRangingCallbacks rangingCallbacks,
                   in PersistableBundle parameters,
                   in String chipId);

  /**
   * Request to start ranging
   *
   * IUwbAdapterCallbacks#onRangingStarted must be called within
   * RANGING_SESSION_START_THRESHOLD_MS milliseconds of #startRanging being
   * called if the ranging session starts successfully.
   *
   * IUwbAdapterCallbacks#onRangingStartFailed must be called within
   * RANGING_SESSION_START_THRESHOLD_MS milliseconds of #startRanging being
   * called if the ranging session fails to be started.
   *
   * @param sessionHandle the session handle to start ranging for
   * @param parameters additional configuration required to start ranging
   */
  void startRanging(in SessionHandle sessionHandle,
                    in PersistableBundle parameters);

  /**
   * Request to reconfigure ranging
   *
   * IUwbAdapterCallbacks#onRangingReconfigured must be called after
   * successfully reconfiguring the session.
   *
   * IUwbAdapterCallbacks#onRangingReconfigureFailed must be called after
   * failing to reconfigure the session.
   *
   * A session must not be modified by a failed call to #reconfigureRanging.
   *
   * @param sessionHandle the session handle to start ranging for
   * @param parameters the parameters to reconfigure and their new values
   */
  void reconfigureRanging(in SessionHandle sessionHandle,
                          in PersistableBundle parameters);

  /**
   * Request to stop ranging
   *
   * IUwbAdapterCallbacks#onRangingStopped must be called after
   * successfully stopping the session.
   *
   * IUwbAdapterCallbacks#onRangingStopFailed must be called after failing
   * to stop the session.
   *
   * @param sessionHandle the session handle to stop ranging for
   */
  void stopRanging(in SessionHandle sessionHandle);

  /**
   * Close ranging for the session associated with the given handle
   *
   * Calling with an invalid handle or a handle that has already been closed
   * is a no-op.
   *
   * IUwbAdapterCallbacks#onRangingClosed must be called within
   * RANGING_SESSION_CLOSE_THRESHOLD_MS of #closeRanging being called.
   *
   * @param sessionHandle the session handle to close ranging for
   */
  void closeRanging(in SessionHandle sessionHandle);

  /**
   * This client has requested to clear all ranging sessions opened by this client.
   *
   * @param AttributionSource
   */
  void clearSessions(in AttributionSource attributionSource);

  /**
   * Add a new controlee to an ongoing session.
   * <p>This call may be made when the session is open.
   *
   * <p>On successfully adding a new controlee to the session
   * {@link RangingSession.Callback#onControleeAdded(PersistableBundle)} is invoked.
   *
   * <p>On failure to add a new controlee to the session,
   * {@link RangingSession.Callback#onControleeAddFailed(int, PersistableBundle)}is invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param params the parameters for the new controlee.
   */
  void addControlee(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Remove an existing controlee from an ongoing session.
   * <p>This call may be made when the session is open.
   *
   * <p>On successfully removing an existing controlee from the session
   * {@link RangingSession.Callback#onControleeRemoved(PersistableBundle)} is invoked.
   *
   * <p>On failure to remove an existing controlee from the session,
   * {@link RangingSession.Callback#onControleeRemoveFailed(int, PersistableBundle)}is invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param params the parameters for the existing controlee.
   */
  void removeControlee(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Suspends an ongoing ranging session.
   *
   * <p>A session that has been pauseed may be resumed by calling
   * {@link RangingSession#resume(PersistableBundle)} without the need to open a new session.
   *
   * <p>Suspending a {@link RangingSession} is useful when the lower layers should skip a few
   * ranging rounds for a session without stopping it.
   *
   * <p>If the {@link RangingSession} is no longer needed, use {@link RangingSession#stop()} or
   * {@link RangingSession#close()} to completely close the session.
   *
   * <p>On successfully pauseing the session,
   * {@link RangingSession.Callback#onPaused(PersistableBundle)} is invoked.
   *
   * <p>On failure to pause the session,
   * {@link RangingSession.Callback#onPauseFailed(int, PersistableBundle)} is invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param params protocol specific parameters for pauseing the session.
   */
  void pause(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Resumes a pauseed ranging session.
   *
   * <p>A session that has been previously pauseed using
   * {@link RangingSession#pause(PersistableBundle)} can be resumed by calling
   * {@link RangingSession#resume(PersistableBundle)}.
   *
   * <p>On successfully resuming the session,
   * {@link RangingSession.Callback#onResumed(PersistableBundle)} is invoked.
   *
   * <p>On failure to pause the session,
   * {@link RangingSession.Callback#onResumeFailed(int, PersistableBundle)} is invoked.
   *
   * @param sessionHandle the session handle to close ranging for
   * @param params protocol specific parameters the resuming the session.
   */
  void resume(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Send data to a remote device which is part of this ongoing session.
   * <p>
   * This API supports two transmission modes, depending on the session's link layer mode
   * configuration:
   *
   * <ul>
   *   <li><b>Bypass Logical Link Mode (FiRa 2.0+):</b><br>
   *       In this mode, the data is piggybacked over RRM (initiator → responder) or RIM
   *       (responder → initiator) messages. The target device is identified using the provided
   *       {@link UwbAddress}.
   *       <p><b>Note:</b> This mode is supported on FiRa 2.0-compliant devices and above.</p>
   *   </li>
   *
   *   <li><b>Logical Link Mode (FiRa 3.0+):</b><br>
   *       <p>The remote endpoint is identified via the Logical Link Connect ID, which must be
   *       provided in the {@code params} bundle.</p>
   *       <p>The {@link UwbAddress} parameter is not used in this mode and should be set to the
   *       default broadcast address {@code 0xFFFF}.</p>
   *       <p><b>Note:</b> This mode is supported only on FiRa 3.0-compliant devices and above.
   *   </li>
   * </ul>
   *
   * <p>Regardless of the transmission mode, one of the following callbacks is triggered upon
   * completion:
   * <ul>
   *   <li>{@link RangingSession.Callback#onDataSent(UwbAddress, PersistableBundle)} — invoked on
   *      success</li>
   *   <li>{@link RangingSession.Callback#onDataSendFailed(UwbAddress, int, PersistableBundle)} —
   *      invoked on failure</li>
   * </ul>
   *
   * @param sessionHandle Session Handle of the UWB session.
   * @param remoteDeviceAddress The UWB address of the target device.
   *                          <ul>
   *                              <li>Required in Bypass Logical Link Mode.</li>
   *                              <li>Must be set to {@code 0xFFFF} in Logical Link Mode.</li>
   *                          </ul>
   * @param params A {@link PersistableBundle} containing protocol-specific parameters.
   *               Must include the Logical Link Connect ID when using Logical Link Mode.
   * @param data The raw application data to transmit.
   */
  void sendData(in SessionHandle sessionHandle, in UwbAddress remoteDeviceAddress,
          in PersistableBundle params, in byte[] data);

  /**
    * Set data transfer phase configuration during ranging as well as dedicated data transfer.
    * <p>This is only functional on a FIRA 2.0 compliant device.
    *
    * <p>On successfully sending the data transfer phase config,
    * {@link RangingSession.Callback#onDataTransferPhaseConfigured(PersistableBundle)} is
    * invoked.
    *
    * <p>On failure to send the data transfer phase config,
    * {@link RangingSession.Callback#onDataTransferPhaseConfigFailed(int, PersistableBundle)} is
    * invoked.
    *
    * @param params Protocol specific data transfer phase configuration parameters
    */
  void setDataTransferPhaseConfig(in SessionHandle sessionHandle, in PersistableBundle params);

  /**
   * Disables or enables UWB for a user
   *
   * The provided callback's IUwbAdapterStateCallbacks#onAdapterStateChanged
   * function must be called immediately following state change.
   *
   * @param enabled value representing intent to disable or enable UWB. If
   * true, any subsequent calls to #openRanging will be allowed. If false,
   * all active ranging sessions will be closed and subsequent calls to
   * #openRanging will be disallowed.
   */
  void setEnabled(boolean enabled);

  /**
   * Returns the current enabled/disabled UWB state.
   *
   * Possible values are:
   * IUwbAdapterState#STATE_DISABLED
   * IUwbAdapterState#STATE_ENABLED_ACTIVE
   * IUwbAdapterState#STATE_ENABLED_INACTIVE
   *
   * @return value representing enabled/disabled UWB state.
   */
  int getAdapterState();

  boolean isHwIdleTurnOffEnabled();
  void requestHwEnabled(boolean enabled, in AttributionSource attributionSource, IBinder binder);
  boolean isHwEnableRequested(in AttributionSource attributionSource);

  /**
   * Returns a list of UWB chip infos in a {@link PersistableBundle}.
   *
   * Callers can invoke methods on a specific UWB chip by passing its {@code chipId} to the
   * method, which can be determined by calling:
   * <pre>
   * List<PersistableBundle> chipInfos = getChipInfos();
   * for (PersistableBundle chipInfo : chipInfos) {
   *     String chipId = ChipInfoParams.fromBundle(chipInfo).getChipId();
   * }
   * </pre>
   *
   * @return list of {@link PersistableBundle} containing info about UWB chips for a multi-HAL
   * system, or a list of info for a single chip for a single HAL system.
   */
  List<PersistableBundle> getChipInfos();

  List<String> getChipIds();

  /**
   * Returns the default UWB chip identifier.
   *
   * If callers do not pass a specific {@code chipId} to UWB methods, then the method will be
   * invoked on the default chip, which is determined at system initialization from a configuration
   * file.
   *
   * @return default UWB chip identifier for a multi-HAL system, or the identifier of the only UWB
   * chip in a single HAL system.
   */
  String getDefaultChipId();

  PersistableBundle addServiceProfile(in PersistableBundle parameters);

  int removeServiceProfile(in PersistableBundle parameters);

  PersistableBundle getAllServiceProfiles();

  PersistableBundle getAdfProvisioningAuthorities(in PersistableBundle parameters);

  PersistableBundle getAdfCertificateAndInfo(in PersistableBundle parameters);

  void provisionProfileAdfByScript(in PersistableBundle serviceProfileBundle,
            in IUwbAdfProvisionStateCallbacks callback);

  int removeProfileAdf(in PersistableBundle serviceProfileBundle);

  /**
   * Updates the device pose. This helps the filter engine distinguish position noise from device
   * motion. The device pose would typically come from ARCore. This requires that an application
   * pose source was indicated in the call to openSession.
   */
  void updatePose(in SessionHandle sessionHandle, in PersistableBundle params);

  int sendVendorUciMessage(int mt, int gid, int oid, in byte[] payload);

  /**
   * @hide
   * Sets the Hybrid UWB Session Controller Configuration
   *
   * @param SessionHandle Primary session handle
   * @param params protocol specific parameters to initiate the hybrid session for controller.
   */
  void setHybridSessionControllerConfiguration(in SessionHandle sessionHandle,
        in PersistableBundle params);

  /**
   * @hide
   * Sets the Hybrid UWB Session Controlee Configuration
   *
   * @param SessionHandle Primary session handle
   * @param params protocol specific parameters to initiate the hybrid session for controlee.
   */
  void setHybridSessionControleeConfiguration(in SessionHandle sessionHandle,
        in PersistableBundle params);

  void updateRangingRoundsDtTag(in SessionHandle sessionHandle, in PersistableBundle parameters);

  void getUwbActivityEnergyInfoAsync(in IOnUwbActivityEnergyInfoListener listener);

  /**
   * Returns the max Application Data payload size that can be sent by the UWBS in one ranging
   * round.
   */
  int queryMaxDataSizeBytes(in SessionHandle sessionHandle);

  /**
   * Queries the maximum size of application data (in bytes) that the UWBS can send in a single
   * ranging round for the specified logical link connection.
   *
   * <p>This feature is supported on FiRa 3.0+ compliant devices.</p>
   *
   * @param sessionHandle the session for which the logical link is established.
   * @param connectId logical link connection identifier for which to query the maximum data size.
   * @return the maximum size (in bytes) of application data that can be sent in one ranging round.
   */
  int queryLogicalLinkMaxDataSizeBytes(in SessionHandle sessionHandle, in int connectId);

  /**
   * Establishes a logical link with a remote device for an ongoing ranging session.
   *
   * <p>This feature is supported on Fira 3.0+ compliant devices.</p>
   *
   * <p>Once the logical link creation attempt completes, the system invokes either
   * {@link RangingSession.Callback#onLogicalLinkCreated(SessionHandle, LogicalLinkCreationParams, int)}
   *      if the operation succeeds, or
   * {@link RangingSession.Callback#onLogicalLinkCreationFailed(SessionHandle, LogicalLinkCreationParams, int)
   *      } if it fails.</p>
   *
   * @param sessionHandle The session handle associated with the ongoing session.
   * @param params {@link LogicalLinkCreationParams} containing the parameters for establishing the logical
   *      link connection.
   */
  void createLogicalLink(in SessionHandle sessionHandle, in LogicalLinkCreationParams params);

  /**
   * Sends a request to close an existing logical link in an ongoing ranging session.
   *
   * <p>On completion:</p>
   * <ul>
   *   <li>If the logical link is successfully closed,
   *        {@link RangingSession.Callback#onLogicalLinkClosed(SessionHandle, int)} is invoked.</li>
   *   <li>If closing the logical link fails,
   *        {@link RangingSession.Callback#onLogicalLinkClosureFailed(SessionHandle, int, int)} is
   *        invoked with the failure status.</li>
   * </ul>
   *
   * @param sessionHandle The session handle associated with the logical link.
   * @param connectId The unique identifier of the logical link to close.
   */
  void closeLogicalLink(in SessionHandle sessionHandle, in int connectId);

  /**
   * @hide
   *
   * @return timestamp in microseconds
   */
   long queryUwbsTimestampMicros();

  /**
   * Retrieves the Logical Link parameters associated with the given Logical Link Connection ID
   * or session handle.
   * <p>
   * The Host shall use this API to request the FiRa Controller to return parameters related to
   * an established Logical Link. If {@code connectId} is set to
   * {@link LogicalLinkCreationParams#CONNECT_ID_UNSPECIFIED}, the parameters will be retrieved using the
   * session handle instead of a specific Logical Link Connection ID.
   *
   * @param connectId The Logical Link Connection ID for which the parameters are to be retrieved.
   *           If the value is {@link LogicalLinkCreationParams#CONNECT_ID_UNSPECIFIED}, the request will
   *           fall back to using the session handle.
   *
   * @return {@link LogicalLinkConnectionParams} containing the retrieved Logical Link parameters.
   */
  LogicalLinkConnectionParams getLogicalLinkCreationParams(in SessionHandle sessionHandle,
      in int connectId);

  /**
   * The maximum allowed time to open a ranging session.
   */
  const int RANGING_SESSION_OPEN_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to start a ranging session.
   */
  const int RANGING_SESSION_START_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to notify the framework that a session has been
   * closed.
   */
  const int RANGING_SESSION_CLOSE_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to configure ranging rounds update for DT Tag
   */
  const int RANGING_ROUNDS_UPDATE_DT_TAG_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to configure session data transfer phase config
   */
  const int SESSION_DATA_TRANSFER_PHASE_CONFIG_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to configure hybrid session
   */
  const int SESSION_CONFIGURATION_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to close logical link layer command
   */
  const int CLOSE_LOGICAL_LINK_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time for RF test
   */
  const int RF_TEST_OPERATION_THRESHOLD_MS = 3000; // Value TBD
}

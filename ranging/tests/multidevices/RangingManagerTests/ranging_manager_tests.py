#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
import sys
import time
import logging
from typing import Set
from lib import cs
from lib import ranging_base_test
from lib import rssi
from lib import rtt
from lib import utils
from lib import uwb
from lib.session import RangingSession
from lib.params import *
from lib.ranging_decorator import *
from mobly import asserts
from mobly import config_parser
from mobly import suite_runner
from mobly.controllers import android_device
from android.platform.test.annotations import ApiTest


_TEST_CASES = [
    "test_one_to_one_uwb_ranging_unicast_static_sts",
    "test_one_to_one_uwb_ranging_multicast_provisioned_sts",
    "test_one_to_one_uwb_ranging_unicast_provisioned_sts",
    "test_one_to_one_uwb_ranging_disable_range_data_ntf",
    "test_one_to_one_wifi_rtt_ranging",
    "test_one_to_one_wifi_periodic_rtt_ranging",
    "test_one_to_one_ble_rssi_ranging",
    "test_one_to_one_ble_cs_ranging",
    "test_one_to_one_ranging_with_oob",
    "test_uwb_ranging_measurement_limit",
    "test_ble_rssi_ranging_measurement_limit",
]


SERVICE_UUID = "0000fffb-0000-1000-8000-00805f9b34fc"

class RangingManagerTest(ranging_base_test.RangingBaseTest):
  """Tests for UWB Ranging APIs.

  Attributes:

  android_devices: list of android device objects.
  """

  def __init__(self, configs: config_parser.TestRunConfig):
    """Init method for the test class.

    Args:

    configs: A config_parser.TestRunConfig object.
    """
    super().__init__(configs)
    self.tests = _TEST_CASES

  def _is_cuttlefish_device(self, ad: android_device.AndroidDevice) -> bool:
    product_name = ad.adb.getprop("ro.product.name")
    return "cf_x86" in product_name

  def setup_class(self):
    super().setup_class()
    self.devices = [RangingDecorator(ad) for ad in self.android_devices]
    self.initiator, self.responder = self.devices

    self.initiator.uwb_address = [1, 2]
    self.responder.uwb_address = [3, 4]

  def setup_test(self):
    super().setup_test()
    for device in self.devices:
      utils.set_airplane_mode(device.ad, state=False)
      if device.is_ranging_technology_supported(RangingTechnology.UWB):
        utils.set_uwb_state_and_verify(device.ad, state=True)
      utils.set_snippet_foreground_state(device.ad, isForeground=True)
      utils.set_screen_state(device.ad, on=True)

  def teardown_test(self):
    super().teardown_test()
    for device in self.devices:
      device.clear_ranging_sessions()

  ### Helpers ###

  def _start_mutual_ranging_and_assert_started(
      self,
      session_handle: str,
      initiator_preference: RangingPreference,
      responder_preference: RangingPreference,
      technologies: Set[RangingTechnology],
  ):
    """Starts one-to-one ranging session between initiator and responder.

    Args:
        session_id: id to use for the ranging session.
    """
    self.initiator.start_ranging_and_assert_opened(
        session_handle, initiator_preference
    )
    if responder_preference is not None:
        self.responder.start_ranging_and_assert_opened(
            session_handle, responder_preference
        )

    asserts.assert_true(
        self.initiator.verify_received_data_from_peer_using_technologies(
            session_handle,
            self.responder.id,
            technologies,
        ),
        f"Initiator did not find responder",
    )
    if responder_preference is not None:
        asserts.assert_true(
            self.responder.verify_received_data_from_peer_using_technologies(
                session_handle,
                self.initiator.id,
                technologies,
            ),
            f"Responder did not find initiator",
        )

  def _reset_bt_state(self):
    utils.reset_bt_state(self.initiator.ad)
    utils.reset_bt_state(self.responder.ad)

  def _reset_wifi_state(self):
    utils.reset_wifi_state(self.initiator.ad)
    utils.reset_wifi_state(self.responder.ad)

  def _ble_connect(self):
    """Create BLE GATT connection between initiator and responder.

    """
    # Start and advertise regular server
    self.responder.ad.bluetooth.createAndAdvertiseServer(SERVICE_UUID)
    # Connect to the advertisement
    self.responder.bt_addr = self.initiator.ad.bluetooth.connectGatt(SERVICE_UUID)
    asserts.assert_true(self.responder.bt_addr, "Server not connected")
    connected_devices = self.responder.ad.bluetooth.getConnectedDevices()
    asserts.assert_true(connected_devices, "No clients found connected to server")
    self.initiator.bt_addr = connected_devices[0]

  def _ble_disconnect(self):
    if self.initiator.ad.bluetooth.disconnectGatt(SERVICE_UUID) is False:
        logging.error("Server did not disconnect %s", self.initiator.bt_addr)

  def _ble_bond(self):
    """Create BLE GATT connection and bonding between initiator and responder.

    """
    # Start and advertise regular server
    self.responder.ad.bluetooth.createAndAdvertiseServer(SERVICE_UUID)
    oob_data = self.responder.ad.bluetooth.generateServerLocalOobData()
    asserts.assert_true(oob_data, "OOB data not generated")
    # Connect to the advertisement using OOB data generated on responder.
    self.responder.bt_addr = self.initiator.ad.bluetooth.createBondOob(SERVICE_UUID, oob_data)
    asserts.assert_true(self.responder.bt_addr, "Server not bonded")
    connected_devices = self.responder.ad.bluetooth.getConnectedDevices()
    asserts.assert_true(connected_devices, "No clients found connected to server")
    self.initiator.bt_addr = connected_devices[0]

  def _ble_unbond(self):
    if self.initiator.ad.bluetooth.removeBond(self.responder.bt_addr) is False:
        logging.error("Server not unbonded %s", self.responder.bt_addr)
    if self.responder.ad.bluetooth.removeBond(self.initiator.bt_addr) is False:
        logging.error("Client not unbonded %s", self.initiator.bt_addr)

  ### Test Cases ###

  def _test_one_to_one_uwb_ranging(self, config_id: int):
      """Verifies uwb ranging with peer device, devices range for 10 seconds."""
      SESSION_HANDLE = str(uuid4())
      UWB_SESSION_ID = 5
      TECHNOLOGIES = {RangingTechnology.UWB}

      asserts.skip_if(
          not self.responder.is_ranging_technology_supported(RangingTechnology.UWB),
          f"UWB not supported by responder",
      )
      asserts.skip_if(
          not self.initiator.is_ranging_technology_supported(RangingTechnology.UWB),
          f"UWB not supported by initiator",
      )

      initiator_preference = RangingPreference(
          device_role=DeviceRole.INITIATOR,
          ranging_params=RawInitiatorRangingParams(
              peer_params=[
                  DeviceParams(
                      peer_id=self.responder.id,
                      uwb_params=uwb.UwbRangingParams(
                          session_id=UWB_SESSION_ID,
                          config_id=uwb.ConfigId.UNICAST_DS_TWR,
                          device_address=self.initiator.uwb_address,
                          peer_address=self.responder.uwb_address,
                      ),
                  )
              ],
          ),
      )

      responder_preference = RangingPreference(
          device_role=DeviceRole.RESPONDER,
          ranging_params=RawResponderRangingParams(
              peer_params=DeviceParams(
                  peer_id=self.initiator.id,
                  uwb_params=uwb.UwbRangingParams(
                      session_id=UWB_SESSION_ID,
                      config_id=uwb.ConfigId.UNICAST_DS_TWR,
                      device_address=self.responder.uwb_address,
                      peer_address=self.initiator.uwb_address,
                      ranging_update_rate=uwb.RangingUpdateRate.FREQUENT
                  ),
              ),
          ),
      )

      self._start_mutual_ranging_and_assert_started(
          SESSION_HANDLE,
          initiator_preference,
          responder_preference,
          TECHNOLOGIES,
      )

      time.sleep(10)

      asserts.assert_true(
          self.initiator.verify_received_data_from_peer_using_technologies(
              SESSION_HANDLE, self.responder.id, TECHNOLOGIES
          ),
          "Initiator did not find responder",
      )
      asserts.assert_true(
          self.responder.verify_received_data_from_peer_using_technologies(
              SESSION_HANDLE,
              self.initiator.id,
              TECHNOLOGIES,
          ),
          "Responder did not find initiator",
      )

      self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
      self.responder.stop_ranging_and_assert_closed(SESSION_HANDLE)


  @ApiTest(apis=[
    'android.ranging.RangingData#getDistance',
    'android.ranging.RangingData#getAzimuth',
    'android.ranging.RangingData#getElevation',
    'android.ranging.RangingData#getRangingTechnology',
    'android.ranging.RangingData#getRssi',
    'android.ranging.RangingData#hasRssi',
    'android.ranging.RangingData#getTimestampMillis',
    'android.ranging.RangingMeasurement#getMeasurement',
    'android.ranging.RangingMeasurement#getConfidence',
    'android.ranging.RangingSession.Callback#onOpened()',
    'android.ranging.RangingSession.Callback#onOpenFailed(int)',
    'android.ranging.RangingSession.Callback#onClosed(int)',
    'android.ranging.RangingSession.Callback#onResults(android.ranging.RangingDevice, android.ranging.RangingData)',
    'android.ranging.RangingSession.Callback#onStarted(android.ranging.RangingDevice, int)',
    'android.ranging.RangingSession.Callback#onStopped(android.ranging.RangingDevice, int)',
  ])
  def test_one_to_one_uwb_ranging_unicast_static_sts(self):
    """Verifies uwb ranging with peer device using unicast static sts"""
    self._test_one_to_one_uwb_ranging(uwb.ConfigId.UNICAST_DS_TWR)

  def test_one_to_one_uwb_ranging_multicast_provisioned_sts(self):
    """Verifies uwb ranging with peer device using multicast provisioned sts"""
    self._test_one_to_one_uwb_ranging(uwb.ConfigId.PROVISIONED_MULTICAST_DS_TWR)

  def test_one_to_one_uwb_ranging_unicast_provisioned_sts(self):
      """Verifies uwb ranging with peer device using unicast provisioned sts"""
      self._test_one_to_one_uwb_ranging(uwb.ConfigId.PROVISIONED_UNICAST_DS_TWR)

  def test_one_to_one_uwb_ranging_disable_range_data_ntf(self):
    """Verifies device does not receive range data after disabling range data notifications"""
    SESSION_HANDLE = str(uuid4())
    UWB_SESSION_ID = 5
    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(RangingTechnology.UWB),
        f"UWB not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(RangingTechnology.UWB),
        f"UWB not supported by initiator",
    )
    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=RawInitiatorRangingParams(
            peer_params=[
                DeviceParams(
                    peer_id=self.responder.id,
                    uwb_params=uwb.UwbRangingParams(
                        session_id=UWB_SESSION_ID,
                        config_id=uwb.ConfigId.MULTICAST_DS_TWR,
                        device_address=self.initiator.uwb_address,
                        peer_address=self.responder.uwb_address,
                    ),
                )
            ],
        ),
        enable_range_data_notifications=False,
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=RawResponderRangingParams(
            peer_params=DeviceParams(
                peer_id=self.initiator.id,
                uwb_params=uwb.UwbRangingParams(
                    session_id=UWB_SESSION_ID,
                    config_id=uwb.ConfigId.MULTICAST_DS_TWR,
                    device_address=self.responder.uwb_address,
                    peer_address=self.initiator.uwb_address,
                ),
            ),
        ),
        enable_range_data_notifications=True,
    )

    self.initiator.start_ranging_and_assert_opened(
        SESSION_HANDLE, initiator_preference
    )
    self.responder.start_ranging_and_assert_opened(
        SESSION_HANDLE, responder_preference
    )

    asserts.assert_false(
        self.initiator.verify_received_data_from_peer(
            SESSION_HANDLE, self.responder.id
        ),
        "Initiator found responder but initiator has range data"
        " notifications disabled",
    )
    asserts.assert_true(
        self.responder.verify_received_data_from_peer(
            SESSION_HANDLE, self.initiator.id
        ),
        "Responder did not find initiator but responder has range data"
        " notifications enabled",
    )

    self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
    self.responder.stop_ranging_and_assert_closed(SESSION_HANDLE)

  def test_uwb_ranging_measurement_limit(self):
      """Verifies device does not receive range data after measurement limit"""
      SESSION_HANDLE = str(uuid4())
      UWB_SESSION_ID = 5
      asserts.skip_if(
          not self.responder.is_ranging_technology_supported(RangingTechnology.UWB),
          f"UWB not supported by responder",
      )
      asserts.skip_if(
          not self.initiator.is_ranging_technology_supported(RangingTechnology.UWB),
          f"UWB not supported by initiator",
      )
      initiator_preference = RangingPreference(
          device_role=DeviceRole.INITIATOR,
          ranging_params=RawInitiatorRangingParams(
              peer_params=[
                  DeviceParams(
                      peer_id=self.responder.id,
                      uwb_params=uwb.UwbRangingParams(
                          session_id=UWB_SESSION_ID,
                          config_id=uwb.ConfigId.MULTICAST_DS_TWR,
                          device_address=self.initiator.uwb_address,
                          peer_address=self.responder.uwb_address,
                      ),
                  )
              ],
          ),
          measurement_limit=2,
      )

      responder_preference = RangingPreference(
          device_role=DeviceRole.RESPONDER,
          ranging_params=RawResponderRangingParams(
              peer_params=DeviceParams(
                  peer_id=self.initiator.id,
                  uwb_params=uwb.UwbRangingParams(
                      session_id=UWB_SESSION_ID,
                      config_id=uwb.ConfigId.MULTICAST_DS_TWR,
                      device_address=self.responder.uwb_address,
                      peer_address=self.initiator.uwb_address,
                  ),
              ),
          ),
          measurement_limit=2,
      )

      self.initiator.start_ranging_and_assert_opened(
          SESSION_HANDLE, initiator_preference
      )
      self.responder.start_ranging_and_assert_opened(
          SESSION_HANDLE, responder_preference
      )

      time.sleep(2)

      self.initiator.assert_close_ranging_event_received(SESSION_HANDLE)
      self.responder.assert_close_ranging_event_received(SESSION_HANDLE)

  def test_ble_rssi_ranging_measurement_limit(self):
      """Verifies ble rssi ranging with measurement limit."""
      asserts.skip_if(self._is_cuttlefish_device(self.initiator.ad),
                      "Skipping BLE RSSI test on Cuttlefish")
      SESSION_HANDLE = str(uuid4())

      asserts.skip_if(
          not self.responder.is_ranging_technology_supported(RangingTechnology.BLE_RSSI),
          f"BLE RSSI not supported by responder",
      )
      asserts.skip_if(
          not self.initiator.is_ranging_technology_supported(RangingTechnology.BLE_RSSI),
          f"BLE RSSI not supported by initiator",
      )
      # TODO(rpius): Remove this once the technology is stable.
      self._reset_bt_state()

      try:
          self._ble_connect()
          initiator_preference = RangingPreference(
              device_role=DeviceRole.INITIATOR,
              ranging_params=RawInitiatorRangingParams(
                  peer_params=[
                      DeviceParams(
                          peer_id=self.responder.id,
                          rssi_params=rssi.BleRssiRangingParams(
                              peer_address=self.responder.bt_addr,
                              ranging_update_rate=rssi.RangingUpdateRate.FREQUENT,
                          ),
                      )
                  ],
              ),
              measurement_limit=1,
          )
          self.initiator.start_ranging_and_assert_opened(
              SESSION_HANDLE, initiator_preference
          )
          self.initiator.assert_close_ranging_event_received(SESSION_HANDLE)

      finally:
          self._ble_disconnect()

  def test_one_to_one_wifi_rtt_ranging(self):
    """Verifies wifi rtt ranging with peer device, devices range for 10 seconds."""
    asserts.skip_if(self._is_cuttlefish_device(self.initiator.ad),
                    "Skipping WiFi RTT test on Cuttlefish")
    SESSION_HANDLE = str(uuid4())
    TECHNOLOGIES = {RangingTechnology.WIFI_RTT}

    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(RangingTechnology.WIFI_RTT),
        f"Wifi nan rtt not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(RangingTechnology.WIFI_RTT),
        f"Wifi nan rtt not supported by initiator",
    )
    # TODO(rpius): Remove this once the technology is stable.
    self._reset_wifi_state()

    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=RawInitiatorRangingParams(
            peer_params=[
                DeviceParams(
                    peer_id=self.responder.id,
                    rtt_params=rtt.RttRangingParams(
                        service_name="test_service_name1",
                    ),
                )
            ],
        ),
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=RawResponderRangingParams(
            peer_params=DeviceParams(
                peer_id=self.initiator.id,
                rtt_params=rtt.RttRangingParams(
                    service_name="test_service_name1",
                ),
            ),
        ),
    )

    # Should be able to call _start_mutual_ranging_and_assert_started once we get consistent data.
    self.initiator.start_ranging_and_assert_opened(
        SESSION_HANDLE, initiator_preference
    )
    self.responder.start_ranging_and_assert_opened(
        SESSION_HANDLE, responder_preference
    )

    time.sleep(10)
    asserts.assert_true(
        self.initiator.verify_received_data_from_peer_using_technologies(
            SESSION_HANDLE, self.responder.id, TECHNOLOGIES
        ),
        "Initiator did not find responder",
    )

    # Enable when this is supported.
    # asserts.assert_true(
    #    self.responder.verify_received_data_from_peer_using_technologies(
    #        SESSION_HANDLE, self.initiator.id, TECHNOLOGIES
    #    ),
    #    "Responder did not find initiator",
    #)

    self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
    self.responder.stop_ranging_and_assert_closed(SESSION_HANDLE)

  def test_one_to_one_wifi_periodic_rtt_ranging(self):
    """Verifies wifi periodic rtt ranging with peer device, devices range for 10 seconds."""
    asserts.skip_if(self._is_cuttlefish_device(self.initiator.ad),
                    "Skipping WiFi periodic RTT test on Cuttlefish")
    SESSION_HANDLE = str(uuid4())
    TECHNOLOGIES = {RangingTechnology.WIFI_RTT}

    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(RangingTechnology.WIFI_RTT),
        f"Wifi nan rtt not supported by responder",
    )
    asserts.skip_if(
        not self.responder.ad.ranging.hasPeriodicRangingHwFeature(),
        f"Wifi nan periodic rtt not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(RangingTechnology.WIFI_RTT),
        f"Wifi nan rtt not supported by initiator",
    )
    asserts.skip_if(
        not self.initiator.ad.ranging.hasPeriodicRangingHwFeature(),
        f"Wifi nan periodic rtt not supported by initiator",
    )
    # TODO(rpius): Remove this once the technology is stable.
    self._reset_wifi_state()

    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=RawInitiatorRangingParams(
            peer_params=[
                DeviceParams(
                    peer_id=self.responder.id,
                    rtt_params=rtt.RttRangingParams(
                        service_name="test_service_name1",
                        enable_periodic_ranging_hw_feature=True,
                    ),
                )
            ],
        ),
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=RawResponderRangingParams(
            peer_params=DeviceParams(
                peer_id=self.initiator.id,
                rtt_params=rtt.RttRangingParams(
                    service_name="test_service_name1",
                    enable_periodic_ranging_hw_feature=True,
                ),
            ),
        ),
    )

    # Should be able to call _start_mutual_ranging_and_assert_started once we get consistent data.
    self.initiator.start_ranging_and_assert_opened(
        SESSION_HANDLE, initiator_preference
    )
    self.responder.start_ranging_and_assert_opened(
        SESSION_HANDLE, responder_preference
    )

    time.sleep(10)
    asserts.assert_true(
        self.initiator.verify_received_data_from_peer_using_technologies(
            SESSION_HANDLE, self.responder.id, TECHNOLOGIES
        ),
        "Initiator did not find responder",
    )

    asserts.assert_true(
        self.responder.verify_received_data_from_peer_using_technologies(
            SESSION_HANDLE, self.initiator.id, TECHNOLOGIES
        ),
        "Responder did not find initiator",
    )

    self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
    self.responder.stop_ranging_and_assert_closed(SESSION_HANDLE)

  def test_one_to_one_ble_rssi_ranging(self):
    """Verifies cs ranging with peer device, devices range for 10 seconds."""
    asserts.skip_if(self._is_cuttlefish_device(self.initiator.ad),
                    "Skipping BLE RSSI test on Cuttlefish")
    SESSION_HANDLE = str(uuid4())
    TECHNOLOGIES = {RangingTechnology.BLE_RSSI}

    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(RangingTechnology.BLE_RSSI),
        f"BLE RSSI not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(RangingTechnology.BLE_RSSI),
        f"BLE RSSI not supported by initiator",
    )
    # TODO(rpius): Remove this once the technology is stable.
    self._reset_bt_state()

    try:
      self._ble_connect()
      initiator_preference = RangingPreference(
          device_role=DeviceRole.INITIATOR,
          ranging_params=RawInitiatorRangingParams(
              peer_params=[
                  DeviceParams(
                      peer_id=self.responder.id,
                      rssi_params=rssi.BleRssiRangingParams(
                      peer_address=self.responder.bt_addr,
                      ),
                  )
              ],
          ),
      )

      responder_preference = RangingPreference(
          device_role=DeviceRole.RESPONDER,
          ranging_params=RawResponderRangingParams(
              peer_params=DeviceParams(
                  peer_id=self.initiator.id,
                  rssi_params=rssi.BleRssiRangingParams(
                  peer_address=self.initiator.bt_addr,
                  ),
              ),
          ),
      )

      self._start_mutual_ranging_and_assert_started(
          SESSION_HANDLE,
          initiator_preference,
          responder_preference,
          TECHNOLOGIES,
      )

      time.sleep(10)

      asserts.assert_true(
          self.initiator.verify_received_data_from_peer_using_technologies(
              SESSION_HANDLE,
              self.responder.id,
              TECHNOLOGIES
          ),
          "Initiator did not find responder",
      )
      asserts.assert_true(
          self.responder.verify_received_data_from_peer_using_technologies(
              SESSION_HANDLE,
              self.initiator.id,
              TECHNOLOGIES,
          ),
          "Responder did not find initiator",
      )
    finally:
      self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
      self.responder.stop_ranging_and_assert_closed(SESSION_HANDLE)

      self._ble_disconnect()

  def test_one_to_one_ble_cs_ranging(self):
    """
    Verifies cs ranging with peer device, devices range for 10 seconds.
    This test is only one way since we don't test if responder also can simultaneously get the data.
    """
    asserts.skip_if(self._is_cuttlefish_device(self.initiator.ad),
                    "Skipping BLE CS test on Cuttlefish")
    SESSION_HANDLE = str(uuid4())
    TECHNOLOGIES = {RangingTechnology.BLE_CS}

    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(RangingTechnology.BLE_CS),
        f"BLE_CS not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(RangingTechnology.BLE_CS),
        f"BLE CS not supported by initiator",
    )
    # TODO(rpius): Remove this once the technology is stable.
    self._reset_bt_state()

    try:
      self._ble_bond()
      initiator_preference = RangingPreference(
          device_role=DeviceRole.INITIATOR,
          ranging_params=RawInitiatorRangingParams(
              peer_params=[
                  DeviceParams(
                      peer_id=self.responder.id,
                      cs_params=cs.CsRangingParams(
                        peer_address=self.responder.bt_addr,
                      ),
                  )
              ],
          ),
      )

      self._start_mutual_ranging_and_assert_started(
          SESSION_HANDLE,
          initiator_preference,
          None,
          TECHNOLOGIES,
      )

      time.sleep(10)

      asserts.assert_true(
          self.initiator.verify_received_data_from_peer_using_technologies(
              SESSION_HANDLE,
              self.responder.id,
              TECHNOLOGIES
          ),
          "Initiator did not find responder",
      )
    finally:
      self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)

      self._ble_unbond()

  @ApiTest(apis=[
    'android.ranging.oob.TransportHandle#sendData(byte[])',
    'android.ranging.oob.TransportHandle#registerReceiveCallback(java.util.concurrent.Executor, android.ranging.oob.TransportHandle.ReceiveCallback)',
    'android.ranging.oob.TransportHandle.ReceiveCallback#onSendFailed()',
  ])
  def test_one_to_one_ranging_with_oob(self):
    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(RangingTechnology.UWB),
        f"UWB not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(RangingTechnology.UWB),
        f"UWB not supported by initiator",
    )

    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=OobInitiatorRangingParams(peer_ids=[self.responder.id]),
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=OobResponderRangingParams(peer_id=self.initiator.id),
    )

    session = RangingSession()
    session.set_initiator(self.initiator, initiator_preference)
    session.add_responder(self.responder, responder_preference)

    session.start_and_assert_opened()
    session.assert_exchanged_data()
    session.stop_and_assert_closed()

if __name__ == "__main__":
  if "--" in sys.argv:
    index = sys.argv.index("--")
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]
  suite_runner.run_suite([RangingManagerTest])

# Copyright (C) 2024 The Android Open Source Project
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

import random
import sys
import time
from lib import cs
from lib import ranging_base_test
from lib import rssi
from lib import rtt
from lib import utils
from lib import uwb
from lib import wifipd
from lib.params import *
from lib.ranging_decorator import *
from lib.session import RangingSession
from mobly import asserts
from mobly import config_parser
from mobly import suite_runner
from mobly import utils as mobly_utils
from mobly.controllers import android_device
from typing import Set

import logging
from android.platform.test.annotations import ApiTest
from android.platform.test.annotations import CddTest

_TEST_CASES = [
    "test_one_to_one_uwb_ranging_unicast_static_sts",
    "test_one_to_one_uwb_ranging_multicast_provisioned_sts",
    "test_one_to_one_uwb_ranging_unicast_provisioned_sts",
    "test_one_to_one_uwb_ranging_disable_range_data_ntf",
    "test_one_to_one_wifi_rtt_ranging",
    "test_one_to_one_wifi_periodic_rtt_ranging",
    "test_one_to_one_ble_rssi_ranging",
    "test_one_to_one_ble_cs_ranging",
    "test_one_to_one_uwb_ranging_with_oob",
    "test_one_to_one_ble_cs_ranging_with_oob",
    "test_uwb_ranging_measurement_limit",
    "test_ble_rssi_ranging_measurement_limit",
    "test_ble_cs_ranging_measurement_limit",
    "test_one_to_one_wifi_rtt_ranging_with_oob",
    "test_one_to_one_ble_rssi_ranging_with_oob",
    "test_oob_responder_persists_until_explicitly_stopped",
    "test_dynamic_peer_uwb_ranging",
    "test_uwb_ranging_app_switch_to_bg_and_fg",
    "test_ble_rssi_ranging_app_switch_to_bg_and_fg",
    "test_ble_cs_ranging_app_switch_to_bg_and_fg",
    "test_on_motion_received",
    "test_one_to_one_wifi_pd_ranging",
    "test_one_to_one_wifi_pd_ranging_with_oob",
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

  def _is_emulator_device(self, ad: android_device.AndroidDevice) -> bool:
    product_name = ad.adb.getprop("ro.product.name")
    product_board = ad.adb.getprop("ro.product.board")
    return ("cf_x86" in product_name) or ("goldfish" in product_board)

  def _is_watch(self, ad1: android_device.AndroidDevice, ad2: android_device.AndroidDevice) -> bool:
      return ("watch" in ad1.adb.getprop("ro.build.characteristics")) or \
        ("watch" in ad2.adb.getprop("ro.build.characteristics"))

  def _is_pc(self, ad1: android_device.AndroidDevice, ad2: android_device.AndroidDevice) -> bool:
        return ('feature:android.hardware.type.pc' in ad1.adb.shell('pm list features').decode('utf-8')) or \
          ('feature:android.hardware.type.pc' in ad2.adb.shell('pm list features').decode('utf-8'))

  def setup_class(self):
    super().setup_class()
    self.devices = [RangingDecorator(ad) for ad in self.android_devices]
    self.initiator, self.responder = self.devices

    for device in self.devices:
      utils.set_airplane_mode(device.ad, state=False)
      time.sleep(1)
      if device.is_ranging_technology_supported(RangingTechnology.UWB):
        utils.initialize_uwb_country_code(device.ad)
        utils.request_hw_idle_vote(device.ad, True)

    self.initiator.uwb_address = [1, 2]
    self.responder.uwb_address = [3, 4]

  def teardown_class(self):
      super().teardown_class()
      for device in self.devices:
        if device.is_ranging_technology_supported(RangingTechnology.UWB):
            utils.request_hw_idle_vote(device.ad, False)
        if device.is_ranging_technology_supported(RangingTechnology.WIFI_RTT):
            utils.set_wifi_state_and_verify(device.ad, True)
        if device.is_ranging_technology_supported(RangingTechnology.BLE_CS) or \
            device.is_ranging_technology_supported(RangingTechnology.BLE_RSSI):
            utils.set_bt_state_and_verify(device.ad, True)

  def setup_test(self):
    super().setup_test()
    for device in self.devices:
      if device.is_ranging_technology_supported(RangingTechnology.UWB):
        utils.set_uwb_state_and_verify(device.ad, state=True)
        utils.set_snippet_foreground_state(device.ad, isForeground=True)
      utils.set_screen_state(device.ad, on=True)
    self.initiator.bt_addr = None
    self.responder.bt_addr = None

  def teardown_test(self):
    super().teardown_test()
    mobly_utils.concurrent_exec(
        lambda d: d.clear_ranging_sessions(),
        param_list=[[device] for device in self.devices],
        raise_on_exception=True,
    )
    mobly_utils.concurrent_exec(
        lambda d: d.services.create_output_excerpts_all(self.current_test_info),
        param_list=[[decorator.ad] for decorator in self.devices],
        raise_on_exception=True,
    )

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

  def _enable_bt(self):
    utils.set_bt_state_and_verify(self.initiator.ad, True)
    utils.set_bt_state_and_verify(self.responder.ad, True)

  def _disable_bt(self):
    utils.set_bt_state_and_verify(self.initiator.ad, False)
    utils.set_bt_state_and_verify(self.responder.ad, False)

  def _reset_wifi_state(self):
    utils.reset_wifi_state(self.initiator.ad)
    utils.reset_wifi_state(self.responder.ad)

  def _enable_wifi(self):
        utils.set_wifi_state_and_verify(self.initiator.ad, True)
        utils.set_wifi_state_and_verify(self.responder.ad, True)

  def _disable_wifi(self):
      utils.set_wifi_state_and_verify(self.initiator.ad, False)
      utils.set_wifi_state_and_verify(self.responder.ad, False)

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
    if self.responder.bt_addr and self.initiator.ad.bluetooth.disconnectGatt(SERVICE_UUID) is False:
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
    if self.responder.bt_addr and self.initiator.ad.bluetooth.removeBond(self.responder.bt_addr) is False:
        logging.error("Server not unbonded %s", self.responder.bt_addr)
    if self.initiator.bt_addr and self.responder.ad.bluetooth.removeBond(self.initiator.bt_addr) is False:
        logging.error("Client not unbonded %s", self.initiator.bt_addr)

  def _test_one_to_one_ranging_with_oob(
      self,
      technology: RangingTechnology,
      ranging_mode: RangingMode = RangingMode.AUTO,
      check_responders: bool = True,
      initiator_notif: bool = True,
      responder_notif: bool = True,
  ):
    """Common logic for OOB ranging tests."""
    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(technology),
        f"{technology.name} not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(technology),
        f"{technology.name} not supported by initiator",
    )

    initiator_bt_addr = None
    responder_bt_addr = None
    if technology in [RangingTechnology.BLE_CS, RangingTechnology.BLE_RSSI]:
      self._enable_bt()
      try:
        self._ble_connect()
      except Exception as e:
        asserts.skip("Failed to create ble connection", str(e))

      if technology == RangingTechnology.BLE_CS:
        asserts.skip_if(
            not self.initiator.ad.bluetooth.isRemoteDeviceBonded(),
            f"Responder is not bonded. Please bond manually.",
        )
      initiator_bt_addr = self.initiator.bt_addr
      responder_bt_addr = self.responder.bt_addr
    elif technology == RangingTechnology.WIFI_RTT:
      self._reset_wifi_state()
    elif technology == RangingTechnology.WIFI_PD:
      self._enable_wifi()

    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=OobInitiatorRangingParams(
            peer_ids=[self.responder.id],
            peer_bluetooth_addresses=[responder_bt_addr],
            ranging_mode=ranging_mode,
            ranging_technology_filter=[technology],
        ),
        enable_range_data_notifications=initiator_notif,
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=OobResponderRangingParams(
            peer_id=self.initiator.id,
            peer_bluetooth_address=initiator_bt_addr,
        ),
        enable_range_data_notifications=responder_notif,
    )

    session = RangingSession()
    session.set_initiator(self.initiator, initiator_preference)
    session.add_responder(self.responder, responder_preference)

    try:
      session.start_and_assert_opened(check_responders=check_responders)
      session.assert_received_data(
          technologies=[technology], check_responders=check_responders
      )
    finally:
      session.stop_and_assert_closed(check_responders=check_responders)
      if technology in [RangingTechnology.BLE_CS, RangingTechnology.BLE_RSSI]:
        self._ble_disconnect()

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
    'android.ranging.RangingData#getRangingDataExtras',
    'android.ranging.RangingDataExtras#getUwbSpecificData',
    'android.ranging.uwb.UwbSpecificData#getNonLineOfSight',
    'android.ranging.RangingMeasurement#getMeasurement',
    'android.ranging.RangingMeasurement#getConfidence',
    'android.ranging.RangingSession.Callback#onOpened()',
    'android.ranging.RangingSession.Callback#onOpenFailed(int)',
    'android.ranging.RangingSession.Callback#onClosed(int)',
    'android.ranging.RangingSession.Callback#onResults(android.ranging.RangingDevice, android.ranging.RangingData)',
    'android.ranging.RangingSession.Callback#onStarted(android.ranging.RangingDevice, int)',
    'android.ranging.RangingSession.Callback#onStopped(android.ranging.RangingDevice, int)',
    'android.os.Parcel#writeBlob(byte[])',
  ])
  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_one_to_one_uwb_ranging_unicast_static_sts(self):
    """Verifies uwb ranging with peer device using unicast static sts"""
    self._test_one_to_one_uwb_ranging(uwb.ConfigId.UNICAST_DS_TWR)

  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_one_to_one_uwb_ranging_multicast_provisioned_sts(self):
    """Verifies uwb ranging with peer device using multicast provisioned sts"""
    self._test_one_to_one_uwb_ranging(uwb.ConfigId.PROVISIONED_MULTICAST_DS_TWR)

  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_one_to_one_uwb_ranging_unicast_provisioned_sts(self):
      """Verifies uwb ranging with peer device using unicast provisioned sts"""
      self._test_one_to_one_uwb_ranging(uwb.ConfigId.PROVISIONED_UNICAST_DS_TWR)

  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
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

  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_uwb_ranging_app_switch_to_bg_and_fg(self):
      """ verifies Uwb ranging with foreground and background"""
      #TODO: b/474105892 Add support via test api for AL.
      asserts.skip_if(
          self._is_pc(self.initiator.ad, self.responder.ad),
          f"Skip test on AL",
      )
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
                  ),
              ),
          ),
      )
      self.initiator.start_ranging_and_assert_opened(
          SESSION_HANDLE, initiator_preference
      )
      self.responder.start_ranging_and_assert_opened(
          SESSION_HANDLE, responder_preference
      )
      """Range for 2 seconds"""
      time.sleep(2)

      """Moving app to background"""
      self.initiator.move_snippet_to_bg()
      time.sleep(2)
      self.initiator.clear_event_cache()
      asserts.assert_false(
          self.initiator.verify_received_data_from_peer_using_technologies(
              SESSION_HANDLE, self.responder.id, TECHNOLOGIES,
          ),
          "Initiator should not receive in bg",
      )

      """Moving app to foreground"""
      self.initiator.move_snippet_to_fg()
      self.initiator.clear_event_cache()
      asserts.assert_true(
          self.initiator.verify_received_data_from_peer_using_technologies(
              SESSION_HANDLE, self.responder.id, TECHNOLOGIES,
          ),
          "Initiator should not receive in fg",
      )

      self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
      self.responder.stop_ranging_and_assert_closed(SESSION_HANDLE)
  @CddTest(requirements = ['7.4.3/C-10-1'])
  def test_ble_rssi_ranging_app_switch_to_bg_and_fg(self):
      """ verifies ble rssi ranging with foreground and background"""
      #TODO: b/474105892 Add support via test api for AL.
      asserts.skip_if(
          self._is_pc(self.initiator.ad, self.responder.ad),
          f"Skip test on AL",
      )
      SESSION_HANDLE = str(uuid4())
      TECHNOLOGIES = {RangingTechnology.BLE_RSSI}
      asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                      "Skipping BLE RSSI test on emulator")
      asserts.skip_if(self._is_watch(self.initiator.ad, self.responder.ad),
                      "Skipping the test on wearables")

      asserts.skip_if(
          not self.responder.is_ranging_technology_supported(RangingTechnology.BLE_RSSI),
          f"BLE_RSSI not supported by responder",
      )
      asserts.skip_if(
          not self.initiator.is_ranging_technology_supported(RangingTechnology.BLE_RSSI),
          f"BLE_RSSI not supported by initiator",
      )
      self._enable_bt()

      try:
          self._ble_connect()
      except Exception as e:
          asserts.skip("Failed to create ble connection", str(e))

      try:
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
          )
          self.initiator.start_ranging_and_assert_opened(
              SESSION_HANDLE, initiator_preference
          )
          """Moving app to background"""
          self.initiator.move_snippet_to_bg()
          time.sleep(2)
          self.initiator.clear_event_cache()
          asserts.assert_false(
                  self.initiator.verify_received_data_from_peer_using_technologies(
                         SESSION_HANDLE, self.responder.id, TECHNOLOGIES,
                  ),
                  " Initiator should not receive in bg",
          )
          """Moving app to foreground"""
          self.initiator.move_snippet_to_fg()
          asserts.assert_true(
                  self.initiator.verify_received_data_from_peer_using_technologies(
                         SESSION_HANDLE, self.responder.id, TECHNOLOGIES,
                  ),
                  "Initiator should receive in fg",
          )

      finally:
          self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
          self._ble_disconnect()
  @CddTest(requirements = ['7.3.13/C-11-1,C-11-2'])
  def test_ble_cs_ranging_app_switch_to_bg_and_fg(self):
      """ verifies ble cs ranging with foreground and background"""
      #TODO: b/474105892 Add support via test api for AL.
      asserts.skip_if(
          self._is_pc(self.initiator.ad, self.responder.ad),
          f"Skip test on AL",
      )
      SESSION_HANDLE = str(uuid4())
      TECHNOLOGIES = {RangingTechnology.BLE_CS}
      asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                      "Skipping BLE RSSI test on emulator")
      asserts.skip_if(self._is_watch(self.initiator.ad, self.responder.ad),
                            "Skipping the test on wearables")

      asserts.skip_if(
          not self.responder.is_ranging_technology_supported(RangingTechnology.BLE_CS),
          f"BLE_CS not supported by responder",
      )
      asserts.skip_if(
          not self.initiator.is_ranging_technology_supported(RangingTechnology.BLE_CS),
          f"BLE_CS not supported by initiator",
      )
      self._enable_bt()
      try:
          self._ble_connect()
      except Exception as e:
          asserts.skip("Failed to create ble connection", str(e))

      asserts.skip_if(
          not self.initiator.ad.bluetooth.isRemoteDeviceBonded(),
          f"Responder is not bonded. Please bond manually.",
      )

      try:
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
          self.initiator.start_ranging_and_assert_opened(
              SESSION_HANDLE, initiator_preference
          )
          """Moving app to background"""
          self.initiator.move_snippet_to_bg()
          time.sleep(2)
          self.initiator.clear_event_cache()
          asserts.assert_false(
              self.initiator.verify_received_data_from_peer_using_technologies(
                  SESSION_HANDLE, self.responder.id, TECHNOLOGIES,
              ),
              " Initiator should not receive in bg",
          )

          """Moving app to foreground"""
          self.initiator.move_snippet_to_fg()
          asserts.assert_true(
              self.initiator.verify_received_data_from_peer_using_technologies(
                  SESSION_HANDLE, self.responder.id, TECHNOLOGIES,
              ),
              "Initiator should receive in fg",
          )

      finally:
          self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
          self._ble_disconnect()

  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_dynamic_peer_uwb_ranging(self):
      """verifies dynamic peer with UWB"""

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
                      peer_id=str(uuid4()),
                      uwb_params=uwb.UwbRangingParams(
                          session_id=UWB_SESSION_ID,
                          config_id=uwb.ConfigId.MULTICAST_DS_TWR,
                          device_address=self.initiator.uwb_address,
                          peer_address=[7,8],
                      ),
                  )
              ],
          ),
      )
      self.initiator.start_ranging_and_assert_opened(
          SESSION_HANDLE, initiator_preference
      )
      ranging_params_responder=RawResponderRangingParams(
          peer_params=DeviceParams(
              peer_id=self.responder.id,
              uwb_params=uwb.UwbRangingParams(
                  session_id=UWB_SESSION_ID,
                  config_id=uwb.ConfigId.MULTICAST_DS_TWR,
                  device_address=self.initiator.uwb_address,
                  peer_address=self.responder.uwb_address,
              )
          ),
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
                  )
              ),
          )
      )
      self.responder.start_ranging_and_assert_opened(
          SESSION_HANDLE, responder_preference
      )

      self.initiator.add_device_to_session(SESSION_HANDLE, ranging_params_responder)
      logging.info("add a device %s", self.initiator.uwb_address)

      #verify at least one responder replied
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
      peer_params=DeviceParams(
          peer_id=self.responder.id,
      )

      self.initiator.remove_device_from_session(SESSION_HANDLE, peer_params)
      # Responder will stop ranging due to inband signal from controlee
      self.responder.assert_close_ranging_event_received(SESSION_HANDLE)
      logging.info("remove a device %s", self.initiator.uwb_address)
      self.initiator.clear_event_cache()

      asserts.assert_false(
          self.initiator.verify_received_data_from_peer_using_technologies(
              SESSION_HANDLE, self.responder.id, TECHNOLOGIES,
          ),
          "Initiator found responder",
      )

      self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
  @CddTest(requirements = ['7.4.2.5/C-1-1,C-1-2'])
  def test_uwb_ranging_measurement_limit(self):
      """Verifies device does not receive range data after measurement limit."""
      SESSION_HANDLE = str(uuid4())
      UWB_SESSION_ID = 5
      asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                      "Skipping ranging measurement limit test on emulator")
      asserts.skip_if(
          not self.responder.is_ranging_technology_supported(RangingTechnology.UWB),
          f"UWB not supported by responder",
      )
      asserts.skip_if(
          not self.initiator.is_ranging_technology_supported(RangingTechnology.UWB),
          f"UWB not supported by initiator",
      )

      asserts.skip_if(self.initiator.ad.uwb.getSpecificationInfo()["fira"]["uci_version"] < 2,
          f"Measurements limit is supported from Fira 2.0",
      )
      asserts.skip_if(self.responder.ad.uwb.getSpecificationInfo()["fira"]["uci_version"] < 2,
         f"Measurements limit in supported from Fira 2.0",
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

  @CddTest(requirements = ['7.4.3/C-10-1'])
  def test_ble_rssi_ranging_measurement_limit(self):
      """Verifies ble rssi ranging with measurement limit.
      """
      asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                      "Skipping BLE RSSI test on emulator")
      asserts.skip_if(self._is_watch(self.initiator.ad, self.responder.ad),
                            "Skipping the test on wearables")
      SESSION_HANDLE = str(uuid4())

      asserts.skip_if(
          not self.responder.is_ranging_technology_supported(RangingTechnology.BLE_RSSI),
          f"BLE RSSI not supported by responder",
      )
      asserts.skip_if(
          not self.initiator.is_ranging_technology_supported(RangingTechnology.BLE_RSSI),
          f"BLE RSSI not supported by initiator",
      )
      self._enable_bt()

      try:
          self._ble_connect()
      except Exception as e:
          asserts.skip("Failed to create ble connection", str(e))

      try:
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
              measurement_limit=4,
          )
          self.initiator.start_ranging_and_assert_opened(
              SESSION_HANDLE, initiator_preference
          )
          self.initiator.assert_close_ranging_event_received(SESSION_HANDLE)
      finally:
          self._ble_disconnect()

  @ApiTest(apis=[
          'android.net.wifi.rtt.WifiRttManager#cancelRanging(android.os.WorkSource)',
          'android.ranging.RangingData#getDistanceStandardDeviationMeters',
          'android.ranging.RangingData#hasDistanceStandardDeviation',
          'android.ranging.RangingData#getRangingDataExtras',
          'android.ranging.RangingDataExtras#getWifiRttSpecificData',
          'android.ranging.wifi.rtt.WifiRttSpecificData#getDistanceStandardDeviationMeters',
          'android.ranging.wifi.rtt.WifiRttSpecificData#getLci',
          'android.ranging.wifi.rtt.WifiRttSpecificData#getMeasurementBandwidth',
          'android.ranging.wifi.rtt.WifiRttSpecificData#getMeasurementChannelFrequencyMHz',
          'android.ranging.wifi.rtt.WifiRttSpecificData#getNumAttemptedMeasurements',
          'android.ranging.wifi.rtt.WifiRttSpecificData#getNumSuccessfulMeasurements',
  ])
  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_one_to_one_wifi_rtt_ranging(self):
    """Verifies wifi rtt ranging with peer device, devices range for 10 seconds."""
    asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                    "Skipping WiFi RTT test on emulator")
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
    test_service_name = "test_service_name" + str(random.randint(1,100))
    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=RawInitiatorRangingParams(
            peer_params=[
                DeviceParams(
                    peer_id=self.responder.id,
                    rtt_params=rtt.RttRangingParams(
                        service_name=test_service_name,
                    ),
                )
            ],
        ),
        enable_range_data_notifications=True,
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=RawResponderRangingParams(
            peer_params=DeviceParams(
                peer_id=self.initiator.id,
                rtt_params=rtt.RttRangingParams(
                    service_name=test_service_name,
                ),
            ),
        ),
        enable_range_data_notifications=False,
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

  @CddTest(requirements = ['7.4.2.5/C-1-1,C-1-2'])
  def test_one_to_one_wifi_periodic_rtt_ranging(self):
    """Verifies wifi periodic rtt ranging with peer device, devices range for 10 seconds."""
    asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                    "Skipping WiFi periodic RTT test on emulator")
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

    test_service_name = "test_periodic_service_name" + str(random.randint(1,100))
    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=RawInitiatorRangingParams(
            peer_params=[
                DeviceParams(
                    peer_id=self.responder.id,
                    rtt_params=rtt.RttRangingParams(
                        service_name=test_service_name,
                        enable_periodic_ranging_hw_feature=True,
                    ),
                )
            ],
        ),
        enable_range_data_notifications=True,
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=RawResponderRangingParams(
            peer_params=DeviceParams(
                peer_id=self.initiator.id,
                rtt_params=rtt.RttRangingParams(
                    service_name=test_service_name,
                    enable_periodic_ranging_hw_feature=True,
                ),
            ),
        ),
        enable_range_data_notifications=False,
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

    # Enable when this is supported
    #asserts.assert_true(
    #    self.responder.verify_received_data_from_peer_using_technologies(
    #        SESSION_HANDLE, self.initiator.id, TECHNOLOGIES
    #    ),
    #    "Responder did not find initiator",
    #)

    self.initiator.stop_ranging_and_assert_closed(SESSION_HANDLE)
    self.responder.stop_ranging_and_assert_closed(SESSION_HANDLE)

  @ApiTest(apis=[
      'android.bluetooth.le.DistanceMeasurementSession#stopSession',
      'android.content.AttributionSource#checkCallingUid',
      'java.util#copyOf(byte[], int)',
      'java.util#copyOfRange(byte[], int, int)',
  ])
  @CddTest(requirements = ['7.4.3/C-10-1'])
  def test_one_to_one_ble_rssi_ranging(self):
    """Verifies rssi ranging with peer device, devices range for 10 seconds."""
    asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                    "Skipping BLE RSSI test on emulator")
    asserts.skip_if(self._is_watch(self.initiator.ad, self.responder.ad),
                          "Skipping the test on wearables")
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
    self._enable_bt()

    try:
        self._ble_connect()
    except Exception as e:
        asserts.skip("Failed to create ble connection", str(e))

    try:
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
      self._ble_disconnect()

  @ApiTest(apis=[
      'android.bluetooth.le.DistanceMeasurementSession#stopSession',
      'android.bluetooth.le.DistanceMeasurementParams#getMaxDurationSeconds',
      'android.ranging.RangingDataExtras#getBleSpecificData',
      'android.ranging.BleSpecificData#getDelaySpreadMeters',
      'android.ranging.BleSpecificData#getRemoteTxPowerDbm',
  ])
  @CddTest(requirements = ['7.3.13/C-11-1,C-11-2'])
  def test_one_to_one_ble_cs_ranging(self):
    """
    Verifies cs ranging with peer device, devices range for 10 seconds.
    This test is only one way since we don't test if responder also can simultaneously get the data.
    """
    asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                    "Skipping BLE CS test on emulator")
    asserts.skip_if(self._is_watch(self.initiator.ad, self.responder.ad),
                          "Skipping the test on wearables")
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
    self._enable_bt()
    try:
        self._ble_connect()
    except Exception as e:
        asserts.skip("Failed to create ble connection", str(e))

    asserts.skip_if(
        not self.initiator.ad.bluetooth.isRemoteDeviceBonded(),
        f"Responder is not bonded. Please bond manually.",
    )

    try:
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
      self._ble_disconnect()

  @ApiTest(apis=[
    'android.ranging.oob.TransportHandle#sendData(byte[])',
    'android.ranging.oob.TransportHandle#registerReceiveCallback(java.util.concurrent.Executor, android.ranging.oob.TransportHandle.ReceiveCallback)',
    'android.ranging.oob.TransportHandle.ReceiveCallback#onSendFailed()',
  ])
  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_one_to_one_uwb_ranging_with_oob(self):
    """Verifies UWB ranging with OOB."""
    self._test_one_to_one_ranging_with_oob(
        technology=RangingTechnology.UWB,
        ranging_mode=RangingMode.HIGH_ACCURACY,
    )

  @CddTest(requirements = ['7.3.13/C-11-1,C-11-2'])
  def test_one_to_one_ble_cs_ranging_with_oob(self):
    """Verifies BLE CS ranging with OOB."""
    asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                      "Skipping BLE CS test on emulator")
    asserts.skip_if(self._is_watch(self.initiator.ad, self.responder.ad),
                          "Skipping the test on wearables")

    self._test_one_to_one_ranging_with_oob(
        technology=RangingTechnology.BLE_CS,
        ranging_mode=RangingMode.HIGH_ACCURACY_PREFERRED,
        check_responders=False,
    )

  @CddTest(requirements = ['7.3.13/C-11-1,C-11-2'])
  def test_ble_cs_ranging_measurement_limit(self):
      """Verifies ble cs ranging with measurement limit."""
      asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                      "Skipping BLE CS test on emulator")
      asserts.skip_if(self._is_watch(self.initiator.ad, self.responder.ad),
                            "Skipping the test on wearables")
      SESSION_HANDLE = str(uuid4())
      TECHNOLOGIES = {RangingTechnology.BLE_CS}

      asserts.skip_if(
          not self.responder.is_ranging_technology_supported(RangingTechnology.BLE_CS),
          f"BLE CS not supported by responder",
      )
      asserts.skip_if(
          not self.initiator.is_ranging_technology_supported(RangingTechnology.BLE_CS),
          f"BLE CS not supported by initiator",
      )
      self._enable_bt()
      try:
          self._ble_connect()
      except Exception as e:
          asserts.skip("Failed to create ble connection", str(e))

      asserts.skip_if(
          not self.initiator.ad.bluetooth.isRemoteDeviceBonded(),
          f"Responder is not bonded. Please bond manually.",
      )

      try:
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
              measurement_limit=4,
        )
        self.initiator.start_ranging_and_assert_opened(
            SESSION_HANDLE, initiator_preference
        )
        time.sleep(10)
        self.initiator.assert_close_ranging_event_received(SESSION_HANDLE)

      finally:
        self._ble_disconnect()
  @CddTest(requirements = ['7.4.2.5/C-1-1,C-1-2'])
  def test_one_to_one_wifi_rtt_ranging_with_oob(self):
    """Verifies WiFi RTT ranging with OOB."""
    self._test_one_to_one_ranging_with_oob(
        technology=RangingTechnology.WIFI_RTT,
        ranging_mode=RangingMode.HIGH_ACCURACY_PREFERRED,
        check_responders=False,
        responder_notif=False,
    )

  @CddTest(requirements = ['7.4.3/C-10-1'])
  def test_one_to_one_ble_rssi_ranging_with_oob(self):
    """Verifies BLE RSSI ranging with OOB."""
    asserts.skip_if(self._is_emulator_device(self.initiator.ad),
                      "Skipping BLE RSSI test on emulator")
    asserts.skip_if(self._is_watch(self.initiator.ad, self.responder.ad),
                          "Skipping the test on wearables")

    self._test_one_to_one_ranging_with_oob(
        technology=RangingTechnology.BLE_RSSI,
        ranging_mode=RangingMode.AUTO,
        check_responders=False,
    )

  @CddTest(requirements = ['7.4.2.10/C-1-1,C-1-2,C-1-3,C-1-4,C-1-6,C-1-7'])
  def test_one_to_one_wifi_pd_ranging_with_oob(self):
    """Verifies WiFi PD ranging with OOB."""
    self._test_one_to_one_ranging_with_oob(
        technology=RangingTechnology.WIFI_PD,
        ranging_mode=RangingMode.AUTO,
    )

  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_oob_responder_persists_until_explicitly_stopped(self):
    """Verifies oob responder persists until explicitly stopped.
    """
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
        ranging_params=OobInitiatorRangingParams(
            peer_ids=[self.responder.id],
            ranging_mode=RangingMode.HIGH_ACCURACY,
            ranging_technology_filter=[RangingTechnology.UWB],
        ),
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=OobResponderRangingParams(peer_id=self.initiator.id),
    )

    session = RangingSession()
    session.set_initiator(self.initiator, initiator_preference)
    session.add_responder(self.responder, responder_preference)

    session.start_and_assert_opened()
    session.assert_received_data()
    session.stop_and_assert_closed(stop_responders=False, check_responders=False)

    time.sleep(1)

    session.start_and_assert_opened(start_responders=False, check_responders=False)
    session.assert_received_data()
    session.stop_and_assert_closed()

  @ApiTest(apis=[
      'android.ranging.MotionState#getMotionState()',
      'android.ranging.RangingSession.Callback#onMotionReceived(android.ranging.RangingDevice, android.ranging.MotionState)',
  ])
  @CddTest(requirements = ['7.3.13/C-1-1,C-1-2'])
  def test_on_motion_received(self):
    """Verifies onMotionReceived callback is triggered."""
    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(RangingTechnology.UWB),
        "UWB not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(RangingTechnology.UWB),
        "UWB not supported by initiator",
    )

    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=OobInitiatorRangingParams(
            peer_ids=[self.responder.id],
            ranging_mode=RangingMode.HIGH_ACCURACY,
            ranging_technology_filter=[RangingTechnology.UWB],
        ),
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=OobResponderRangingParams(peer_id=self.initiator.id),
    )

    session = RangingSession()
    session.set_initiator(self.initiator, initiator_preference)
    session.add_responder(self.responder, responder_preference)

    session.start_and_assert_opened()
    session.assert_received_data()

    # Verify responder can send motion event to initiator.
    session.send_motion_event_and_assert_received(self.responder.id, self.initiator.id)

    session.stop_and_assert_closed()

  @CddTest(requirements = ['7.4.2.10/C-1-1,C-1-2,C-1-3,C-1-4,C-1-6,C-1-7'])
  def test_one_to_one_wifi_pd_ranging(self):
    """Verifies wifi pd ranging with peer device, devices range for 10 seconds."""
    SESSION_HANDLE = str(uuid4())
    TECHNOLOGIES = {RangingTechnology.WIFI_PD}

    asserts.skip_if(
        not self.responder.is_ranging_technology_supported(RangingTechnology.WIFI_PD),
        f"Wifi PD not supported by responder",
    )
    asserts.skip_if(
        not self.initiator.is_ranging_technology_supported(RangingTechnology.WIFI_PD),
        f"Wifi PD not supported by initiator",
    )
    self._enable_wifi()

    initiator_caps = self.initiator.ad.ranging.getWifiPdCapabilities()
    responder_caps = self.responder.ad.ranging.getWifiPdCapabilities()

    asserts.assert_true(
        initiator_caps, "Failed to get Wifi PD capabilities from initiator"
    )
    asserts.assert_true(
        responder_caps, "Failed to get Wifi PD capabilities from responder"
    )
    responder_mac_address = responder_caps.get("mac_address")
    asserts.assert_true(
        responder_mac_address,
        "Failed to get Wifi PD MAC address from responder caps",
    )
    initiator_mac_address = initiator_caps.get("mac_address")
    asserts.assert_true(
        initiator_mac_address,
        "Failed to get Wifi PD MAC address from initiator caps",
    )

    wifi_pd_params = wifipd.get_best_wifi_pd_params(
        initiator_caps, responder_caps, responder_mac_address
    )

    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=RawInitiatorRangingParams(
            peer_params=[
                DeviceParams(
                    peer_id=self.responder.id,
                    wifi_pd_params=wifi_pd_params,
                )
            ],
        ),
        enable_range_data_notifications=True,
    )

    responder_wifi_pd_params = wifipd.get_best_wifi_pd_params(
        initiator_caps, responder_caps, initiator_mac_address
    )

    responder_preference = RangingPreference(
        device_role=DeviceRole.RESPONDER,
        ranging_params=RawResponderRangingParams(
            peer_params=DeviceParams(
                peer_id=self.initiator.id,
                wifi_pd_params=responder_wifi_pd_params,
            ),
        ),
        enable_range_data_notifications=True,
    )

    self.responder.start_ranging_and_assert_opened(
        SESSION_HANDLE, responder_preference
    )
    self.initiator.start_ranging_and_assert_opened(
        SESSION_HANDLE, initiator_preference
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

if __name__ == "__main__":
  if "--" in sys.argv:
    index = sys.argv.index("--")
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]
  suite_runner.run_suite([RangingManagerTest])

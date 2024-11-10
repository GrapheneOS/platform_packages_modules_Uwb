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
from typing import Set
from lib import ranging_base_test
from lib import rtt
from lib import utils
from lib import uwb
from lib.params import *
from lib.ranging_decorator import *
from mobly import asserts
from mobly import config_parser
from mobly import suite_runner


_TEST_CASES = (
    "test_one_to_one_uwb_ranging",
    "test_one_to_one_uwb_ranging_provisioned_sts",
    "test_one_to_one_uwb_ranging_disable_range_data_ntf",
    "test_one_to_one_rtt_ranging",
)


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

  def setup_class(self):
    super().setup_class()
    self.devices = [RangingDecorator(ad) for ad in self.android_devices]
    self.initiator, self.responder = self.devices

    self.initiator.uwb_address = [1, 2]
    self.responder.uwb_address = [3, 4]

  def setup_test(self):
    super().setup_test()
    for device in self.devices:
      utils.set_airplane_mode(device.ad, isEnabled=False)
      utils.set_snippet_foreground_state(device.ad, isForeground=True)

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
    self.initiator.start_ranging_and_assert_started(
        session_handle, initiator_preference
    )
    self.responder.start_ranging_and_assert_started(
        session_handle, responder_preference
    )

    asserts.assert_true(
        self.initiator.verify_peer_found_with_technologies(
            session_handle,
            self.responder.id,
            technologies,
        ),
        f"Initiator did not find responder",
    )
    asserts.assert_true(
        self.responder.verify_peer_found_with_technologies(
            session_handle,
            self.initiator.id,
            technologies,
        ),
        f"Responder did not find initiator",
    )

  ### Test Cases ###

  def test_one_to_one_uwb_ranging(self):
    """Verifies uwb ranging with peer device, devices range for 10 seconds."""
    SESSION_HANDLE = str(uuid4())
    UWB_SESSION_ID = 5
    TECHNOLOGIES = {RangingTechnology.UWB}

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
        self.initiator.verify_peer_found_with_technologies(
            SESSION_HANDLE, self.responder.id, TECHNOLOGIES
        ),
        "Initiator did not find responder",
    )
    asserts.assert_true(
        self.responder.verify_peer_found_with_technologies(
            SESSION_HANDLE,
            self.initiator.id,
            TECHNOLOGIES,
        ),
        "Responder did not find initiator",
    )

    self.initiator.stop_ranging_and_assert_stopped(SESSION_HANDLE)
    self.responder.stop_ranging_and_assert_stopped(SESSION_HANDLE)

  def test_one_to_one_uwb_ranging_provisioned_sts(self):
    """Verifies uwb ranging with peer device using provisioned sts"""
    SESSION_HANDLE = str(uuid4())
    UWB_SESSION_ID = 5
    TECHNOLOGIES = {RangingTechnology.UWB}

    initiator_preference = RangingPreference(
        device_role=DeviceRole.INITIATOR,
        ranging_params=RawInitiatorRangingParams(
            peer_params=[
                DeviceParams(
                    peer_id=self.responder.id,
                    uwb_params=uwb.UwbRangingParams(
                        session_id=UWB_SESSION_ID,
                        config_id=uwb.ConfigId.PROVISIONED_UNICAST_DS_TWR,
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
                    config_id=uwb.ConfigId.PROVISIONED_UNICAST_DS_TWR,
                    device_address=self.responder.uwb_address,
                    peer_address=self.initiator.uwb_address,
                ),
            ),
        ),
    )

    self._start_mutual_ranging_and_assert_started(
        SESSION_HANDLE, initiator_preference, responder_preference, TECHNOLOGIES
    )

    self.initiator.stop_ranging_and_assert_stopped(SESSION_HANDLE)
    self.responder.stop_ranging_and_assert_stopped(SESSION_HANDLE)

  def test_one_to_one_uwb_ranging_disable_range_data_ntf(self):
    """Verifies device does not receive range data after disabling range data notifications"""
    SESSION_HANDLE = str(uuid4())
    UWB_SESSION_ID = 5

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
        enable_range_data_notifications=False,
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
        enable_range_data_notifications=True,
    )

    self.initiator.start_ranging_and_assert_started(
        SESSION_HANDLE, initiator_preference
    )
    self.responder.start_ranging_and_assert_started(
        SESSION_HANDLE, responder_preference
    )

    asserts.assert_false(
        self.initiator.verify_peer_found_with_any_technology(
            SESSION_HANDLE, self.responder.id
        ),
        "Initiator found responder but initiator has range data"
        " notifications disabled",
    )
    asserts.assert_true(
        self.responder.verify_peer_found_with_any_technology(
            SESSION_HANDLE, self.initiator.id
        ),
        "Responder did not find initiator but responder has range data"
        " notifications enabled",
    )

    self.initiator.stop_ranging_and_assert_stopped(SESSION_HANDLE)
    self.responder.stop_ranging_and_assert_stopped(SESSION_HANDLE)


  def test_one_to_one_rtt_ranging(self):
    """Verifies uwb ranging with peer device, devices range for 10 seconds."""
    SESSION_HANDLE = str(uuid4())
    TECHNOLOGIES = {RangingTechnology.WIFI_RTT}

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
    self.initiator.start_ranging_and_assert_started(
        SESSION_HANDLE, initiator_preference
    )
    self.responder.start_ranging_and_assert_started(
        SESSION_HANDLE, responder_preference
  )

    time.sleep(10)
    asserts.assert_true(
        self.initiator.verify_peer_found_with_technologies(
            SESSION_HANDLE, self.responder.id, TECHNOLOGIES
        ),
        "Initiator did not find responder",
    )

    # Enable when this is supported.
    # asserts.assert_true(
    #     self.responder.verify_peer_found_with_technologies(
    #         SESSION_HANDLE,
    #         self.initiator.id,
    #         TECHNOLOGIES,
    #     ),
    #     "Responder did not find initiator",
    # )

    self.initiator.stop_ranging_and_assert_stopped(SESSION_HANDLE)
    self.responder.stop_ranging_and_assert_stopped(SESSION_HANDLE)
if __name__ == "__main__":
  if "--" in sys.argv:
    index = sys.argv.index("--")
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]
  suite_runner.run_suite([RangingManagerTest])

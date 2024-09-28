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

from lib import generic_ranging_decorator
from lib import ranging_base_test
from mobly import asserts
from mobly import config_parser
from mobly import suite_runner
from test_utils import uwb_test_utils
from uwb import uwb_ranging_params as params

RESPONDER_STOP_CALLBACK_TIMEOUT = 60

_TEST_CASES = (
    "test_one_to_one_ranging",
    "test_one_to_one_ranging_provisioned_sts",
    "test_one_to_one_ranging_disable_range_data_ntf",
)


class RangingTest(ranging_base_test.RangingBaseTest):
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
    self.uwb_devices = [
        generic_ranging_decorator.GenericRangingDecorator(ad)
        for ad in self.android_devices
    ]
    self.initiator, self.responder = self.uwb_devices
    self.device_addresses = self.user_params.get(
        "device_addresses", [[1, 2], [3, 4]]
    )
    self.initiator_addr, self.responder_addr = self.device_addresses
    self.new_responder_addr = [4, 5]
    # self.p_sts_sub_session_id = 11
    # self.p_sts_sub_session_key = [
    #     8, 7, 6, 5, 4, 3, 2, 1, 1, 2, 3, 4, 5, 6, 7, 8]
    # self.block_stride_length = random.randint(1, 10)

  def setup_test(self):
    super().setup_test()
    for uwb_device in self.uwb_devices:
      uwb_test_utils.set_airplane_mode(uwb_device.ad, isEnabled=False)
      uwb_test_utils.set_snippet_foreground_state(
          uwb_device.ad, isForeground=True
      )

  def teardown_test(self):
    super().teardown_test()
    for uwb_device in self.uwb_devices:
      uwb_device.clear_all_uwb_ranging_sessions()

  ### Helpers ###

  def _start_and_verify_mutual_ranging(
      self,
      initiator_params: params.UwbRangingParams,
      responder_params: params.UwbRangingParams,
      session_id: int,
  ):
    """Starts one-to-one ranging session between initiator and responder.

    Args:
        session_id: id to use for the ranging session.
    """
    self.initiator.start_uwb_ranging_session(initiator_params)
    self.responder.start_uwb_ranging_session(responder_params)

    uwb_test_utils.assert_uwb_peer_found(
        self.initiator, self.responder_addr, session_id
    )
    uwb_test_utils.assert_uwb_peer_found(
        self.responder, self.initiator_addr, session_id
    )

  ### Test Cases ###

  def test_one_to_one_ranging(self):
    """Verifies ranging with peer device, devices range for 10 seconds."""
    initiator_params = params.UwbRangingParams(
        config_type=params.Constants.ConfigType.UNICAST_DS_TWR,
        session_id=5,
        peer_addresses=[self.responder_addr],
        device_address=self.initiator_addr,
        device_role=params.Constants.DeviceRole.CONTROLLER,
    )
    responder_params = params.UwbRangingParams(
        config_type=params.Constants.ConfigType.UNICAST_DS_TWR,
        session_id=5,
        peer_addresses=[self.initiator_addr],
        device_address=self.responder_addr,
        device_role=params.Constants.DeviceRole.CONTROLEE,
    )
    self._start_and_verify_mutual_ranging(
        initiator_params, responder_params, session_id=5
    )

    time.sleep(10)

    uwb_test_utils.assert_uwb_peer_found(
        self.initiator, self.responder_addr, session_id=5
    )
    uwb_test_utils.assert_uwb_peer_found(
        self.responder, self.initiator_addr, session_id=5
    )

    self.initiator.stop_uwb_ranging_session(session_id=5)
    self.responder.stop_uwb_ranging_session(session_id=5)

  def test_one_to_one_ranging_provisioned_sts(self):
    """Verifies ranging with peer device using provisioned sts"""
    initiator_params = params.UwbRangingParams(
        config_type=params.Constants.ConfigType.PROVISIONED_UNICAST_DS_TWR,
        session_id=5,
        peer_addresses=[self.responder_addr],
        device_address=self.initiator_addr,
        device_role=params.Constants.DeviceRole.CONTROLLER,
    )
    responder_params = params.UwbRangingParams(
        config_type=params.Constants.ConfigType.PROVISIONED_UNICAST_DS_TWR,
        session_id=5,
        peer_addresses=[self.initiator_addr],
        device_address=self.responder_addr,
        device_role=params.Constants.DeviceRole.CONTROLEE,
    )

    self._start_and_verify_mutual_ranging(
        initiator_params, responder_params, session_id=5
    )

    self.initiator.stop_uwb_ranging_session(session_id=5)
    self.responder.stop_uwb_ranging_session(session_id=5)

  def test_one_to_one_ranging_disable_range_data_ntf(self):
    """Verifies device does not receive range data after disabling range data notifications"""
    initiator_params = params.UwbRangingParams(
        config_type=params.Constants.ConfigType.UNICAST_DS_TWR,
        session_id=5,
        peer_addresses=[self.responder_addr],
        device_address=self.initiator_addr,
        device_role=params.Constants.DeviceRole.CONTROLLER,
        range_data_config_type=params.Constants.RangeDataConfigType.DISABLE,
    )
    responder_params = params.UwbRangingParams(
        config_type=params.Constants.ConfigType.UNICAST_DS_TWR,
        session_id=5,
        peer_addresses=[self.initiator_addr],
        device_address=self.responder_addr,
        device_role=params.Constants.DeviceRole.CONTROLEE,
        range_data_config_type=params.Constants.RangeDataConfigType.ENABLE,
    )

    self.initiator.start_uwb_ranging_session(initiator_params)
    self.responder.start_uwb_ranging_session(responder_params)

    try:
      uwb_test_utils.assert_uwb_peer_found(
          self.initiator, self.responder_addr, session_id=5
      )
      asserts.fail((
          "Initiator found responder even though initiator has range data"
          "notifications disabled"
      ))
    except TimeoutError:
      pass
    uwb_test_utils.assert_uwb_peer_found(
        self.responder, self.initiator_addr, session_id=5
    )


if __name__ == "__main__":
  if "--" in sys.argv:
    index = sys.argv.index("--")
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]
  suite_runner.run_suite([RangingTest])

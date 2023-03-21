"""Tests for Provisioned STS FIRA Ranging."""

import sys
from typing import List

from mobly import config_parser
from mobly import test_runner
from mobly import records
import timeout_decorator

from lib import uwb_base_test
from lib import uwb_ranging_decorator
from lib import uwb_ranging_params
from test_utils import uwb_test_utils


RESPONDER_STOP_CALLBACK_TIMEOUT = 60

_TEST_CASES = (
    "test_provisioned_sts_device_tracker_profile",
    "test_provisioned_sts_device_tracker_stop_initiator",
    "test_provisioned_sts_device_tracker_stop_responder",
    "test_provisioned_sts_device_tracker_airplane_mode_toggle",
    "test_provisioned_sts_nearby_share_profile",
    "test_provisioned_sts_nearby_share_stop_initiator",
    "test_provisioned_sts_nearby_share_stop_responder",
    "test_provisioned_sts_nearby_share_airplane_mode_toggle",
)


class ProvisionedStsRangingTest(uwb_base_test.UwbBaseTest):
  """Tests for Provisioned STS FIRA Ranging.

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
        uwb_ranging_decorator.UwbRangingDecorator(ad)
        for ad in self.android_devices
    ]
    self.initiator, self.responder = self.uwb_devices
    self.device_addresses = self.user_params.get("device_addresses",
                                                 [[1, 2], [3, 4]])
    self.initiator_addr, self.responder_addr = self.device_addresses
    self.new_responder_addr = [4, 5]

    # abort class if uwb is disabled
    for ad in self.android_devices:
      asserts.abort_class_if(
          not uwb_test_utils.verify_uwb_state_callback(
              ad=ad, uwb_event="Inactive", timeout=120
          ),
          "Uwb is not enabled",
      )

    # device tracker initiator params
    self.device_tracker_initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        multi_node_mode=(
            uwb_ranging_params.FiraParamEnums.MULTI_NODE_MODE_UNICAST
        ),
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
        sts_config=uwb_ranging_params.FiraParamEnums.STS_CONFIG_PROVISIONED,
    )

    # device tracker responder params
    self.device_tracker_responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        multi_node_mode=(
            uwb_ranging_params.FiraParamEnums.MULTI_NODE_MODE_UNICAST
        ),
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
        sts_config=uwb_ranging_params.FiraParamEnums.STS_CONFIG_PROVISIONED,
    )

    # nearby share initiator params
    self.nearby_share_initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
        sts_config=uwb_ranging_params.FiraParamEnums.STS_CONFIG_PROVISIONED,
    )

    # nearby share responder params
    self.nearby_share_responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
        sts_config=uwb_ranging_params.FiraParamEnums.STS_CONFIG_PROVISIONED,
    )

  def setup_test(self):
    super().setup_test()
    for uwb_device in self.uwb_devices:
      try:
        uwb_device.close_ranging()
      except timeout_decorator.TimeoutError:
        uwb_device.log.warn("Failed to cleanup ranging sessions")
    for uwb_device in self.uwb_devices:
      uwb_test_utils.set_airplane_mode(uwb_device.ad, False)

  def teardown_test(self):
    super().teardown_test()
    self.responder.stop_ranging()
    self.initiator.stop_ranging()
    self.responder.close_ranging()
    self.initiator.close_ranging()

  def on_fail(self, record):
    for count, ad in enumerate(self.android_devices):
      test_name = "initiator" if not count else "responder"
      ad.take_bug_report(
          test_name=test_name, destination=self.current_test_info.output_path)

  ### Helper Methods ###

  def _verify_one_to_one_ranging(
      self, initiator: uwb_ranging_decorator.UwbRangingDecorator,
      responder: uwb_ranging_decorator.UwbRangingDecorator,
      initiator_params: uwb_ranging_params.UwbRangingParams,
      responder_params: uwb_ranging_params.UwbRangingParams,
      peer_addr: List[int]):
    """Verifies ranging between two uwb devices.

    Args:
      initiator: uwb device object.
      responder: uwb device object.
      initiator_params: ranging params for initiator.
      responder_params: ranging params for responder.
      peer_addr: address of uwb device.
    """
    initiator.open_fira_ranging(initiator_params)
    responder.open_fira_ranging(responder_params)
    initiator.start_fira_ranging()
    responder.start_fira_ranging()
    uwb_test_utils.verify_peer_found(initiator, peer_addr)

  def _verify_stop_initiator_callback(
      self, initiator: uwb_ranging_decorator.UwbRangingDecorator,
      responder: uwb_ranging_decorator.UwbRangingDecorator,
      initiator_params: uwb_ranging_params.UwbRangingParams,
      responder_params: uwb_ranging_params.UwbRangingParams,
      peer_addr: List[int]):
    """Verifies stop callback on initiator.

    Args:
      initiator: uwb device object.
      responder: uwb device object.
      initiator_params: ranging params for initiator.
      responder_params: ranging params for responder.
      peer_addr: address of uwb device.
    """

    # Verify ranging
    self._verify_one_to_one_ranging(initiator, responder, initiator_params,
                                    responder_params, peer_addr)

    # Verify Stopped callbacks
    initiator.stop_ranging()
    responder.verify_callback_received(
        "Stopped", timeout=RESPONDER_STOP_CALLBACK_TIMEOUT)

    # Restart and verify ranging
    initiator.start_fira_ranging()
    responder.start_fira_ranging()
    uwb_test_utils.verify_peer_found(initiator, peer_addr)

  def _verify_stop_responder_callback(
      self, initiator: uwb_ranging_decorator.UwbRangingDecorator,
      responder: uwb_ranging_decorator.UwbRangingDecorator,
      initiator_params: uwb_ranging_params.UwbRangingParams,
      responder_params: uwb_ranging_params.UwbRangingParams,
      peer_addr: List[int]):
    """Verifies stop callback on responder.

    Args:
      initiator: uwb device object.
      responder: uwb device object.
      initiator_params: ranging params for initiator.
      responder_params: ranging params for responder.
      peer_addr: address of uwb device.
    """

    # Verify ranging
    self._verify_one_to_one_ranging(initiator, responder, initiator_params,
                                    responder_params, peer_addr)

    # Verify Stopped callbacks
    responder.stop_ranging()

    # Restart and verify ranging
    responder.start_fira_ranging()
    uwb_test_utils.verify_peer_found(initiator, peer_addr)

  def _verify_one_to_one_ranging_airplane_mode_toggle(
      self, initiator: uwb_ranging_decorator.UwbRangingDecorator,
      responder: uwb_ranging_decorator.UwbRangingDecorator,
      initiator_params: uwb_ranging_params.UwbRangingParams,
      responder_params: uwb_ranging_params.UwbRangingParams,
      peer_addr: List[int]):
    """Verifies ranging with airplane mode toggle.

    Args:
      initiator: uwb device object.
      responder: uwb device object.
      initiator_params: ranging params for initiator.
      responder_params: ranging params for responder.
      peer_addr: address of uwb device.
    """

    # Verify ranging before APM toggle
    self._verify_one_to_one_ranging(initiator, responder, initiator_params,
                                    responder_params, peer_addr)

    # Enable APM on initiator and verify callbacks
    initiator.clear_ranging_session_callback_events()
    responder.clear_ranging_session_callback_events()
    uwb_test_utils.set_airplane_mode(initiator.ad, True)
    initiator.verify_callback_received("Closed")
    responder.verify_callback_received(
        "Stopped", timeout=RESPONDER_STOP_CALLBACK_TIMEOUT)

    # Disable APM, restart and verify ranging
    uwb_test_utils.set_airplane_mode(initiator.ad, False)
    uwb_test_utils.verify_uwb_state_callback(initiator.ad, "Inactive")
    initiator.open_fira_ranging(initiator_params)
    initiator.start_fira_ranging()
    responder.start_fira_ranging()
    uwb_test_utils.verify_peer_found(initiator, peer_addr)

    # Enable APM on responder and verify callbacks
    responder.clear_ranging_session_callback_events()
    uwb_test_utils.set_airplane_mode(responder.ad, True)
    responder.verify_callback_received("Closed")

    # Disable APM, restart and verify ranging
    uwb_test_utils.set_airplane_mode(responder.ad, False)
    uwb_test_utils.verify_uwb_state_callback(responder.ad, "Inactive")
    responder.open_fira_ranging(responder_params)
    responder.start_fira_ranging()
    uwb_test_utils.verify_peer_found(initiator, peer_addr)

  ### Test Cases ###

  @records.uid("5886f410-cfb6-46df-a5c2-3e8f610b6612")
  def test_provisioned_sts_device_tracker_profile(self):
    """Verifies provisioned sts ranging for device tracker profile."""
    self._verify_one_to_one_ranging(
        self.initiator,
        self.responder,
        self.device_tracker_initiator_params,
        self.device_tracker_responder_params,
        self.responder_addr,
    )

  @records.uid("2d14ecab-b7b8-4dcc-bceb-71ac4f63f29f")
  def test_provisioned_sts_device_tracker_stop_initiator(self):
    """Verifies provisioned sts stop initiator for device tracker profile."""
    self._verify_stop_initiator_callback(
        self.initiator,
        self.responder,
        self.device_tracker_initiator_params,
        self.device_tracker_responder_params,
        self.responder_addr,
    )

  @records.uid("a794dea0-8395-4525-ad89-66757662d3d6")
  def test_provisioned_sts_device_tracker_stop_responder(self):
    """Verifies provisioned sts stop responder for device tracker profile."""
    self._verify_stop_responder_callback(
        self.initiator,
        self.responder,
        self.device_tracker_initiator_params,
        self.device_tracker_responder_params,
        self.responder_addr,
    )

  @records.uid("a2836a10-8e31-4c41-ba6f-03a1a049fd47")
  def test_provisioned_sts_device_tracker_airplane_mode_toggle(self):
    """Verifies provisioned sts ranging with airplane mode toggle."""
    self._verify_one_to_one_ranging_airplane_mode_toggle(
        self.initiator,
        self.responder,
        self.device_tracker_initiator_params,
        self.device_tracker_responder_params,
        self.responder_addr,
    )

  @records.uid("58b6094c-4f6b-4555-bac0-0426ce79449e")
  def test_provisioned_sts_nearby_share_profile(self):
    """Verifies provisioned sts ranging for nearby share profile."""
    self._verify_one_to_one_ranging(
        self.initiator,
        self.responder,
        self.nearby_share_initiator_params,
        self.nearby_share_responder_params,
        self.responder_addr,
    )

  @records.uid("c5743eeb-25a7-4609-8ce6-96f2ab9ca8d5")
  def test_provisioned_sts_nearby_share_stop_initiator(self):
    """Verifies provisioned sts stop initiator for nearby share profile."""
    self._verify_stop_initiator_callback(
        self.initiator,
        self.responder,
        self.nearby_share_initiator_params,
        self.nearby_share_responder_params,
        self.responder_addr,
    )

  @records.uid("aa0b790e-d5de-4287-a082-3ac9e896e455")
  def test_provisioned_sts_nearby_share_stop_responder(self):
    """Verifies provisioned sts stop responder for nearby share profile."""
    self._verify_stop_responder_callback(
        self.initiator,
        self.responder,
        self.nearby_share_initiator_params,
        self.nearby_share_responder_params,
        self.responder_addr,
    )

  @records.uid("49f63d26-1ba8-4b16-8840-724dedeecebb")
  def test_provisioned_sts_nearby_share_airplane_mode_toggle(self):
    """Verifies provisioned sts ranging with airplane mode toggle."""
    self._verify_one_to_one_ranging_airplane_mode_toggle(
        self.initiator,
        self.responder,
        self.nearby_share_initiator_params,
        self.nearby_share_responder_params,
        self.responder_addr,
    )

if __name__ == "__main__":
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1:]
  test_runner.main()

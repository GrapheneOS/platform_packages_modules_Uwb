"""Tests for Uwb Ranging APIs."""

import logging
import random
import sys
from typing import List

from mobly import asserts
from mobly import config_parser
from mobly import test_runner
from mobly import signals
import timeout_decorator

from lib import uwb_base_test
from lib import uwb_ranging_decorator
from lib import uwb_ranging_params
from test_utils import uwb_test_utils

RESPONDER_STOP_CALLBACK_TIMEOUT = 60

_TEST_CASES = (
    "test_ranging_device_tracker_profile_default",
    "test_ranging_nearby_share_profile_default",
    "test_ranging_device_tracker_profile_reconfigure_ranging_interval",
    "test_ranging_nearby_share_profile_reconfigure_ranging_interval",
    "test_ranging_device_tracker_profile_no_aoa_report",
    "test_ranging_nearby_share_profile_hopping_mode_disabled",
    "test_ranging_rr_ss_twr_deferred_device_tracker_profile",
    "test_ranging_rr_ss_twr_deferred_nearby_share_profile",
    "test_stop_initiator_ranging_device_tracker_profile",
    "test_stop_initiator_ranging_nearby_share_profile",
    "test_stop_responder_ranging_device_tracker_profile",
    "test_stop_responder_ranging_nearby_share_profile",
    "test_ranging_device_tracker_profile_with_airplane_mode_toggle",
    "test_ranging_nearby_share_profile_with_airplane_mode_toggle",
)


class RangingTest(uwb_base_test.UwbBaseTest):
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
        uwb_ranging_decorator.UwbRangingDecorator(ad)
        for ad in self.android_devices
    ]
    self.initiator, self.responder = self.uwb_devices
    self.device_addresses = self.user_params.get("device_addresses",
                                                 [[1, 2], [3, 4]])
    self.initiator_addr, self.responder_addr = self.device_addresses
    self.new_responder_addr = [4, 5]
    self.block_stride_length = random.randint(1, 10)

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

  def _verify_one_to_one_ranging_reconfigured_params(
      self, initiator: uwb_ranging_decorator.UwbRangingDecorator,
      responder: uwb_ranging_decorator.UwbRangingDecorator,
      initiator_params: uwb_ranging_params.UwbRangingParams,
      responder_params: uwb_ranging_params.UwbRangingParams,
      peer_addr: List[int]):
    """Verifies ranging between two uwb devices with reconfigured params.

    Args:
      initiator: The uwb device object.
      responder: The uwb device object.
      initiator_params: The ranging params for initiator.
      responder_params: The ranging params for responder.
      peer_addr: The new address of uwb device.
    """
    # change responder addr and verify peer cannot be found
    responder_params.update(device_address=peer_addr)
    responder.open_fira_ranging(responder_params)
    responder.start_fira_ranging()
    try:
      uwb_test_utils.verify_peer_found(initiator, peer_addr)
      asserts.fail("Peer found without reconfiguring initiator.")
    except signals.TestFailure:
      logging.info("Peer %s not found as expected", peer_addr)

    # reconfigure initiator with new peer addr and verify peer found
    reconfigure_params = uwb_ranging_params.UwbRangingReconfigureParams(
        action=uwb_ranging_params.FiraParamEnums
        .MULTICAST_LIST_UPDATE_ACTION_ADD,
        address_list=[peer_addr])
    initiator.reconfigure_fira_ranging(reconfigure_params)
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
    callback = "uwb_state_%s" % random.randint(1, 100)
    handler = initiator.ad.uwb.registerUwbAdapterStateCallback(callback)
    uwb_test_utils.set_airplane_mode(initiator.ad, True)
    uwb_test_utils.verify_uwb_state_callback(initiator.ad, "Disabled", handler)
    initiator.verify_callback_received("Closed")
    responder.verify_callback_received(
        "Stopped", timeout=RESPONDER_STOP_CALLBACK_TIMEOUT)

    # Disable APM, restart and verify ranging
    handler.getAll("UwbAdapterStateCallback")
    uwb_test_utils.set_airplane_mode(initiator.ad, False)
    uwb_test_utils.verify_uwb_state_callback(initiator.ad, "Inactive", handler)
    initiator.ad.uwb.unregisterUwbAdapterStateCallback(callback)
    initiator.open_fira_ranging(initiator_params)
    initiator.start_fira_ranging()
    responder.start_fira_ranging()
    uwb_test_utils.verify_peer_found(initiator, peer_addr)

    # Enable APM on responder and verify callbacks
    responder.clear_ranging_session_callback_events()
    callback = "uwb_state_%s" % random.randint(1, 100)
    handler = responder.ad.uwb.registerUwbAdapterStateCallback(callback)
    uwb_test_utils.set_airplane_mode(responder.ad, True)
    uwb_test_utils.verify_uwb_state_callback(responder.ad, "Disabled", handler)
    responder.verify_callback_received("Closed")

    # Disable APM, restart and verify ranging
    handler.getAll("UwbAdapterStateCallback")
    uwb_test_utils.set_airplane_mode(responder.ad, False)
    uwb_test_utils.verify_uwb_state_callback(responder.ad, "Inactive", handler)
    responder.ad.uwb.unregisterUwbAdapterStateCallback(callback)
    responder.open_fira_ranging(responder_params)
    responder.start_fira_ranging()
    uwb_test_utils.verify_peer_found(initiator, peer_addr)

  def _verify_one_to_one_ranging_reconfigure_ranging_interval(
      self, initiator: uwb_ranging_decorator.UwbRangingDecorator,
      block_stride_length: int, peer_addr: List[int]):
    """Verifies ranging with reconfigured ranging interval.

    Args:
      initiator: The uwb device object.
      block_stride_length: The new block stride length to reconfigure.
      peer_addr: address of the responder.
    """
    initiator.log.info("Reconfigure block stride length to: %s" %
                       block_stride_length)
    reconfigure_params = uwb_ranging_params.UwbRangingReconfigureParams(
        block_stride_length=block_stride_length)
    initiator.reconfigure_fira_ranging(reconfigure_params)
    uwb_test_utils.verify_peer_found(initiator, peer_addr)

  ### Test Cases ###

  def test_ranging_default_params(self):
    """Verifies ranging with default Fira parameters."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)
    self.responder.stop_ranging()
    self.responder.close_ranging()
    self._verify_one_to_one_ranging_reconfigured_params(
        self.initiator, self.responder, initiator_params, responder_params,
        self.new_responder_addr)

  def test_ranging_device_tracker_profile_default(self):
    """Verifies ranging with device tracker profile default values."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_nearby_share_profile_default(self):
    """Verifies ranging for device nearby share with default profile."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)
    self.responder.stop_ranging()
    self.responder.close_ranging()
    self._verify_one_to_one_ranging_reconfigured_params(
        self.initiator, self.responder, initiator_params, responder_params,
        self.new_responder_addr)

  def test_open_ranging_with_same_session_id_nearby_share(self):
    """Verifies ranging for device nearby share with same session id."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)
    self.responder.close_ranging()
    self.initiator.close_ranging()
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_open_ranging_with_same_session_id_device_tracker(self):
    """Verifies ranging with device tracker profile with same session id."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)
    self.responder.close_ranging()
    self.initiator.close_ranging()
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_default_params_reconfigure_ranging_interval(self):
    """Verifies ranging with default Fira parameters."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)
    self._verify_one_to_one_ranging_reconfigure_ranging_interval(
        self.initiator, self.block_stride_length, self.responder_addr)

  def test_ranging_device_tracker_profile_reconfigure_ranging_interval(self):
    """Verifies ranging with device tracker profile default values."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)
    self._verify_one_to_one_ranging_reconfigure_ranging_interval(
        self.initiator, self.block_stride_length, self.responder_addr)

  def test_ranging_nearby_share_profile_reconfigure_ranging_interval(self):
    """Verifies ranging for device nearby share with default profile."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)
    self._verify_one_to_one_ranging_reconfigure_ranging_interval(
        self.initiator, self.block_stride_length, self.responder_addr)

  def test_ranging_device_tracker_profile_ch9_pr12(self):
    """Verifies ranging with device tracker for channel 9 and preamble 12."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_12,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_12,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_device_tracker_profile_ch5_pr11(self):
    """Verifies ranging with device tracker for channel 5 and preamble 11."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_5,
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_11,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_5,
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_11,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_device_tracker_profile_ch9_pr11(self):
    """Verifies device tracking profile with channel 9 and preamble 11."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_11,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_11,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_device_tracker_profile_ch5_pr10(self):
    """Verifies device tracking profile with channel 5 and preamble 10."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_5,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_5,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_device_tracker_profile_ch9_pr9(self):
    """Verifies ranging with device tracker for channel 9 and preamble 9."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_9,
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_9,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_9,
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_9,
        multi_node_mode=uwb_ranging_params
        .FiraParamEnums.MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_device_tracker_profile_ch5_pr9(self):
    """Verifies ranging with device tracker for channel 5 and preamble 9."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_5,
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_9,
        multi_node_mode=uwb_ranging_params
        .FiraParamEnums.MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_5,
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_9,
        multi_node_mode=uwb_ranging_params
        .FiraParamEnums.MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_device_tracker_profile_ch5_pr12(self):
    """Verifies ranging with device tracker for channel 5 and preamble 12."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_5,
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_12,
        multi_node_mode=uwb_ranging_params
        .FiraParamEnums.MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        channel=uwb_ranging_params.FiraParamEnums.UWB_CHANNEL_5,
        preamble=uwb_ranging_params.FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_12,
        multi_node_mode=uwb_ranging_params
        .FiraParamEnums.MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_device_tracker_profile_no_aoa_report(self):
    """Verifies ranging with device tracker profile with no aoa report."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        aoa_result_request=uwb_ranging_params.FiraParamEnums
        .AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        aoa_result_request=uwb_ranging_params.FiraParamEnums
        .AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT,
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)
    try:
      self.initiator.get_aoa_azimuth_measurement(self.responder_addr)
      asserts.fail("Received AoA measurement.")
    except ValueError:
      pass

  def test_ranging_nearby_share_profile_hopping_mode_disabled(self):
    """Verifies ranging with nearby share profile with hopping mode disabled."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        ranging_interval_ms=200,
        hopping_mode=uwb_ranging_params.FiraParamEnums.HOPPING_MODE_DISABLE,
        slots_per_ranging_round=20,
        initiation_time_ms=100,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        ranging_interval_ms=200,
        hopping_mode=uwb_ranging_params.FiraParamEnums.HOPPING_MODE_DISABLE,
        slots_per_ranging_round=20,
        initiation_time_ms=100,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_rr_ss_twr_deferred_default_params(self):
    """Verifies ranging with default Fira parameters and Ranging Round 1."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        ranging_round_usage=uwb_ranging_params.FiraParamEnums
        .RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        ranging_round_usage=uwb_ranging_params.FiraParamEnums
        .RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_rr_ss_twr_deferred_device_tracker_profile(self):
    """Verifies ranging with device tracker profile and ranging round 1."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
        ranging_round_usage=uwb_ranging_params.FiraParamEnums
        .RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
        ranging_round_usage=uwb_ranging_params.FiraParamEnums
        .RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_ranging_rr_ss_twr_deferred_nearby_share_profile(self):
    """Verifies ranging for nearby share profile and ranging round 1."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
        ranging_round_usage=uwb_ranging_params.FiraParamEnums
        .RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
        ranging_round_usage=uwb_ranging_params.FiraParamEnums
        .RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE,
    )
    self._verify_one_to_one_ranging(self.initiator, self.responder,
                                    initiator_params, responder_params,
                                    self.responder_addr)

  def test_stop_initiator_ranging_device_tracker_profile(self):
    """Verifies initiator stop ranging callbacks with device tracker profile."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_stop_initiator_callback(
        self.initiator, self.responder, initiator_params, responder_params,
        self.responder_addr)

  def test_stop_initiator_ranging_nearby_share_profile(self):
    """Verifies initiator stop ranging callbacks for nearby share profile."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    self._verify_stop_initiator_callback(
        self.initiator, self.responder, initiator_params, responder_params,
        self.responder_addr)

  def test_stop_responder_ranging_device_tracker_profile(self):
    """Verifies responder stop ranging callbacks with device tracker profile."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_stop_responder_callback(
        self.initiator, self.responder, initiator_params, responder_params,
        self.responder_addr)

  def test_stop_responder_ranging_nearby_share_profile(self):
    """Verifies responder stop ranging callbacks for nearby share profile."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    self._verify_stop_responder_callback(
        self.initiator, self.responder, initiator_params, responder_params,
        self.responder_addr)

  def test_ranging_device_tracker_profile_with_airplane_mode_toggle(self):
    """Verifies ranging with device tracker profile and airplane mode toggle."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        multi_node_mode=uwb_ranging_params.FiraParamEnums
        .MULTI_NODE_MODE_UNICAST,
        initiation_time_ms=100,
        ranging_interval_ms=240,
        slots_per_ranging_round=6,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging_airplane_mode_toggle(
        self.initiator, self.responder, initiator_params, responder_params,
        self.responder_addr)

  def test_ranging_nearby_share_profile_with_airplane_mode_toggle(self):
    """Verifies ranging for nearby share profile and APM toggle."""
    initiator_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
        device_address=self.initiator_addr,
        destination_addresses=[self.responder_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    responder_params = uwb_ranging_params.UwbRangingParams(
        device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
        device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
        device_address=self.responder_addr,
        destination_addresses=[self.initiator_addr],
        initiation_time_ms=100,
        ranging_interval_ms=200,
        slots_per_ranging_round=20,
        in_band_termination_attempt_count=3,
    )
    self._verify_one_to_one_ranging_airplane_mode_toggle(
        self.initiator, self.responder, initiator_params, responder_params,
        self.responder_addr)

if __name__ == "__main__":
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1:]
  test_runner.main()

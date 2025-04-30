"""Tests for UwbManager APIs."""

import random
import sys
from lib import uwb_base_test
from mobly import asserts
from mobly import config_parser
from mobly import suite_runner
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from test_utils import uwb_test_utils

_TEST_CASES = (
    "test_toggle_uwb_state",
    "test_uwb_state_with_airplane_mode_toggle",
    "test_uwb_state_off_with_airplane_mode_toggle",
)


class UwbManagerTest(uwb_base_test.UwbBaseTest):
  """Tests for UwbManager platform APIs.

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
    self.dut = self.android_devices[0]

  def setup_test(self):
    super().setup_test()
    self.callback = "uwb_state_%s" % random.randint(1, 100)
    self.handler = self.dut.uwb.registerUwbAdapterStateCallback(self.callback)
    if not uwb_test_utils.get_uwb_state(self.dut):
      uwb_test_utils.set_uwb_state_and_verify(self.dut, True, self.handler)

  def teardown_test(self):
    super().teardown_test()
    uwb_test_utils.set_airplane_mode(self.dut, False)
    self.dut.uwb.unregisterUwbAdapterStateCallback(self.callback)

  ### Helper methods ###

  def _test_uwb_state_after_reboot(
      self,
      dut: android_device.AndroidDevice,
      state: bool,
      handler: callback_handler_v2.CallbackHandlerV2,
  ):
    """Sets UWB state and verifies it is persistent after reboot.

    Args:
      dut: android device object.
      state: bool, True for UWB mode on, False for off.
      handler: callback handler.
    """
    uwb_test_utils.set_uwb_state_and_verify(dut, state)
    dut.reboot()
    uwb_test_utils.request_hw_enable_if_required(dut)
    uwb_test_utils.initialize_uwb_country_code_if_not_set(dut)
    state_after_reboot = uwb_test_utils.get_uwb_state(dut)
    asserts.assert_equal(
        state, state_after_reboot,
        "Uwb state before reboot: %s;  after reboot: %s" %
        (state, state_after_reboot))

  def _test_uwb_state_with_airplane_mode(
      self,
      expected_uwb_state: bool,
      toggle_airplane_mode: bool = False,
      toggle_uwb_state: bool = False,
  ):
    """Verifies UWB state with airplane mode togglgings.

    Args:
      expected_uwb_state: expected UWB state.
      toggle_airplane_mode: toggle APM when it's True.
      toggle_uwb_state: toggle UWB state when it's True.
    """
    prev_uwb_state = uwb_test_utils.get_uwb_state(self.dut)

    # Toggles the states.
    if toggle_airplane_mode:
      airplane_mode = not uwb_test_utils.get_airplane_mode(self.dut)
      uwb_test_utils.set_airplane_mode(self.dut, airplane_mode)

    if toggle_uwb_state:
      self.dut.uwb.setUwbEnabled(not prev_uwb_state)

    # Checks the uwb state transition.
    if prev_uwb_state != expected_uwb_state:
      event_str = "Inactive" if expected_uwb_state else "Disabled"
      callback_received = uwb_test_utils.verify_uwb_state_callback(
        self.dut, event_str, self.handler)

      # Sets UWB country code when UWB state expected to be enabled.
      # Cached country code lost when airplane mode turned on,
      if not callback_received and expected_uwb_state:
        uwb_test_utils.initialize_uwb_country_code_if_not_set(self.dut)

    # Checks the current uwb state.
    uwb_state = uwb_test_utils.get_uwb_state(self.dut)
    asserts.assert_equal(uwb_state, expected_uwb_state,
        "Unexpected UWB state %s after APM-Toggle=%s, UWB-Toggle=%s" %
        (uwb_state, toggle_airplane_mode, toggle_uwb_state))

  ### Test Cases ###

  def test_toggle_uwb_state(self):
    """Disables and verifies UWB state."""
    uwb_test_utils.set_uwb_state_and_verify(self.dut, False, self.handler)
    uwb_test_utils.set_uwb_state_and_verify(self.dut, True, self.handler)

  def test_enable_uwb_state(self):
    """Enables and verifies UWB state."""
    uwb_test_utils.set_uwb_state_and_verify(self.dut, True, self.handler)

  def test_uwb_state_after_reboot_with_uwb_off(self):
    """Sets UWB state to off and verifies it is persistent after reboot."""
    self._test_uwb_state_after_reboot(self.dut, False, self.handler)

  def test_uwb_state_after_reboot_with_uwb_on(self):
    """Sets UWB state to on and verifies it is persistent after reboot."""
    self._test_uwb_state_after_reboot(self.dut, True, self.handler)

  def test_uwb_state_with_airplane_mode_toggle(self):
    """Verifies UWB is disabled with airplane mode on."""

    # Starts the test with UWB on + APM off.
    uwb_test_utils.set_airplane_mode(self.dut, False)

    # Enable APM. Verify UWB is disabled.
    self._test_uwb_state_with_airplane_mode(
        expected_uwb_state = False,
        toggle_airplane_mode = True)

    # Enable UWB with APM ON. Verify UWB is still disabled.
    self._test_uwb_state_with_airplane_mode(
        expected_uwb_state = False,
        toggle_uwb_state = True)

    # Disable APM. Verify UWB is enabled.
    self._test_uwb_state_with_airplane_mode(
        expected_uwb_state = True,
        toggle_airplane_mode = True)

  def test_uwb_state_off_with_airplane_mode_toggle(self):
    """Verifies UWB disabled state is persistent with airplane mode toggle."""

    # Starts the test with UWB off + APM off.
    uwb_test_utils.set_airplane_mode(self.dut, False)

    # disable UWB.
    self._test_uwb_state_with_airplane_mode(
        expected_uwb_state = False,
        toggle_uwb_state = True)

    # enable airplane mode and verify UWB is disabled.
    self._test_uwb_state_with_airplane_mode(
        expected_uwb_state = False,
        toggle_airplane_mode = True)

    # disable airplane mode and verify UWB is disabled.
    self._test_uwb_state_with_airplane_mode(
        expected_uwb_state = False,
        toggle_airplane_mode = True)

    # enable UWB.
    self._test_uwb_state_with_airplane_mode(
        expected_uwb_state = True,
        toggle_uwb_state = True)

if __name__ == "__main__":
  if "--" in sys.argv:
    index = sys.argv.index("--")
    sys.argv = sys.argv[:1] + sys.argv[index + 1:]
  suite_runner.run_suite([UwbManagerTest])

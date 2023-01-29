"""Tests for UwbManager APIs."""

import sys

from mobly import asserts
from mobly import config_parser
from mobly import test_runner
from mobly import records
from mobly.controllers import android_device

from lib import uwb_base_test
from test_utils import uwb_test_utils


_TEST_CASES = (
    "test_default_uwb_state",
    "test_disable_uwb_state",
    "test_enable_uwb_state",
    # "test_uwb_state_after_reboot_with_uwb_off",
    # "test_uwb_state_after_reboot_with_uwb_on",
    "test_uwb_state_with_airplane_mode_on",
    "test_toggle_uwb_state_with_airplane_mode_on",
    "test_uwb_state_with_airplane_mode_off",
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

  def on_fail(self, record):
    self.dut.take_bug_report(destination=self.current_test_info.output_path)

  ### Helper methods ###

  def _test_uwb_state_after_reboot(self, dut: android_device.AndroidDevice,
                                   state: bool):
    """Sets UWB state and verifies it is persistent after reboot.

    Args:
      dut: android device object.
      state: bool, True for UWB mode on, False for off.
    """
    uwb_test_utils.set_uwb_state_and_verify(dut, state)
    dut.reboot()
    dut.adb.shell(["cmd", "uwb", "force-country-code", "enabled", "US"])
    state_after_reboot = uwb_test_utils.get_uwb_state(dut)
    asserts.assert_equal(
        state, state_after_reboot,
        "Uwb state before reboot: %s;  after reboot: %s" %
        (state, state_after_reboot))

  ### Test Cases ###

  @records.uid("2d2aff95-0273-478c-be85-097085db74cd")
  def test_default_uwb_state(self):
    """Verifies default UWB state is On after flashing the device."""
    asserts.assert_true(uwb_test_utils.get_uwb_state(self.dut),
                        "UWB state: Off; Expected: On.")

  @records.uid("a6a69945-39e8-404f-8d15-d87857cdf5af")
  def test_disable_uwb_state(self):
    """Disables and verifies UWB state."""
    uwb_test_utils.set_uwb_state_and_verify(self.dut, False)

  @records.uid("a9283694-aa0b-4032-9701-f45bb15cb0fa")
  def test_enable_uwb_state(self):
    """Enables and verifies UWB state."""
    uwb_test_utils.set_uwb_state_and_verify(self.dut, True)

  @records.uid("cda92ecc-c04f-46c6-b433-a5fc30fbd822")
  def test_uwb_state_after_reboot_with_uwb_off(self):
    """Sets UWB state to off and verifies it is persistent after reboot."""
    self._test_uwb_state_after_reboot(self.dut, False)

  @records.uid("6d8c4e95-57d6-4d97-9838-ab5c492b6f1c")
  def test_uwb_state_after_reboot_with_uwb_on(self):
    """Sets UWB state to on and verifies it is persistent after reboot."""
    self._test_uwb_state_after_reboot(self.dut, True)

  @records.uid("a9d2b85a-895f-48ad-87a0-aebd19b45264")
  def test_uwb_state_with_airplane_mode_on(self):
    """Verifies UWB is disabled with airplane mode on."""
    uwb_test_utils.set_airplane_mode(self.dut, True)
    asserts.assert_true(
        uwb_test_utils.verify_uwb_state_callback(self.dut, "Disabled"),
        "UWB is not disabled with airplane mode On.")

  @records.uid("29886f5b-5f91-4ffa-99e8-6d90c21d70de")
  def test_toggle_uwb_state_with_airplane_mode_on(self):
    """Verifies UWB cannot be turned on with airplane mode On."""
    uwb_test_utils.set_airplane_mode(self.dut, True)
    asserts.assert_true(
        uwb_test_utils.verify_uwb_state_callback(self.dut, "Disabled"),
        "UWB is not disabled with airplane mode On.")
    self.dut.uwb.setUwbEnabled(True)
    asserts.assert_true(
        uwb_test_utils.verify_uwb_state_callback(self.dut, "Disabled"),
        "Enabling UWB with airplane mode On should not work.")

  @records.uid("f1008ca6-88d6-4fad-8b88-7dea4f331810")
  def test_uwb_state_with_airplane_mode_off(self):
    """Verifies UWB is disabled with airplane mode off."""
    uwb_test_utils.set_airplane_mode(self.dut, False)
    asserts.assert_true(
        uwb_test_utils.verify_uwb_state_callback(self.dut, "Inactive"),
        "UWB is not enabled with airplane mode Off.")

  @records.uid("dd3a7c5c-25b2-4c86-8ed1-2b6ff246b7b6")
  def test_uwb_state_off_with_airplane_mode_toggle(self):
    """Verifies UWB disabled state is persistent with airplane mode toggle."""

    # disable UWB
    uwb_test_utils.set_uwb_state_and_verify(self.dut, False)

    # enable airplane mode and verify UWB is disabled.
    uwb_test_utils.set_airplane_mode(self.dut, True)
    asserts.assert_true(
        uwb_test_utils.verify_uwb_state_callback(self.dut, "Disabled"),
        "UWB is not disabled with airplane mode On.")

    # disable airplane mode and verify UWB is disabled.
    uwb_test_utils.set_airplane_mode(self.dut, False)
    asserts.assert_false(
        uwb_test_utils.verify_uwb_state_callback(self.dut, "Inactive"),
        "UWB state: On, Expected state: Off")

    # enable UWB
    uwb_test_utils.set_uwb_state_and_verify(self.dut, True)

if __name__ == "__main__":
  index = sys.argv.index('--')
  sys.argv = sys.argv[:1] + sys.argv[index + 1:]
  test_runner.main()

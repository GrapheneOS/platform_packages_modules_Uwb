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
"""Test utils for UWB."""

import time
from lib.ranging_decorator import RangingTechnology
from mobly import asserts
from mobly.controllers import android_device

WAIT_TIME_SEC = 3


def initialize_uwb_country_code_if_necessary(ad: android_device.AndroidDevice):
  """Sets UWB country code to US if the device does not have it set.

  Note: This intentionally relies on an unstable API (shell command) since we
  don't want to expose an API that allows users to circumvent the UWB
  regulatory requirements.

  Args:
    ad: android device object.
    handler: callback handler.
  """
  # Wait to see if UWB state is reported as enabled. If not, this could be
  # because the country code is not set. Try forcing the country code in that
  # case.
  if is_technology_enabled(ad, RangingTechnology.UWB, timeout_s=60):
    return

  try:
    ad.adb.shell(["cmd", "uwb", "force-country-code", "enabled", "US"])
  except ad.adb.AdbError:
    ad.log.warning("Unable to force uwb country code")

  # Unable to get UWB enabled even after setting country code, abort!
  asserts.assert_true(
      is_technology_enabled(ad, RangingTechnology.UWB, timeout_s=60),
      "Uwb was not enabled after setting country code",
  )

def _is_technology_state(
    ad: android_device.AndroidDevice,
    technology: RangingTechnology,
    state: bool,
    timeout_s=WAIT_TIME_SEC,
) -> bool:
  """Checks if the provided technology becomes enabled/disabled

  Args:

  ad: android device object.
  technology: to check for enablement.
  state: bool, True for on, False for off.
  timeout_s: how long to wait for enablement before failing, in seconds.
  """
  start_time = time.time()
  while state != ad.ranging.isTechnologyEnabled(technology):
    if time.time() - start_time > timeout_s:
      return False
  return True


def is_technology_enabled(
    ad: android_device.AndroidDevice,
    technology: RangingTechnology,
    timeout_s=WAIT_TIME_SEC,
) -> bool:
  """Checks if the provided technology becomes enabled

  Args:

  ad: android device object.
  technology: to check for enablement.
  timeout_s: how long to wait for enablement before failing, in seconds.
  """
  return _is_technology_state(ad, technology, True, timeout_s)


def set_airplane_mode(ad: android_device.AndroidDevice, state: bool):
  """Sets the airplane mode to the given state.

  Args:
    ad: android device object.
    state: True for Airplane mode enabled, False for disabled.
  """
  ad.ranging.setAirplaneMode(state)
  start_time = time.time()
  while get_airplane_mode(ad) != state:
    time.sleep(0.5)
    if time.time() - start_time > WAIT_TIME_SEC:
      asserts.fail(f"Failed to set airplane mode to: {state}")


def get_airplane_mode(ad: android_device.AndroidDevice) -> bool:
  """Gets the current airplane mode setting.

  Args:
    ad: android device object.

  Returns:
    True if airplane mode On, False for Off.
  """
  state = ad.adb.shell(["settings", "get", "global", "airplane_mode_on"])
  return bool(int(state.decode().strip()))

def set_uwb_state_and_verify(
    ad: android_device.AndroidDevice,
    state: bool
):
  """Sets UWB state to on or off and verifies it.

  Args:
    ad: android device object.
    state: bool, True for UWB on, False for off.
  """
  failure_msg = "enabled" if state else "disabled"
  ad.uwb.setUwbEnabled(state)
  asserts.assert_true(_is_technology_state(ad, RangingTechnology.UWB, state, timeout_s=60),
                      "Uwb is not %s" % failure_msg)


def set_bt_state_and_verify(
    ad: android_device.AndroidDevice,
    state: bool
):
  """Sets BT state to on or off and verifies it.

  Args:
    ad: android device object.
    state: bool, True for BT on, False for off.
  """
  failure_msg = "enabled" if state else "disabled"
  if state:
    ad.bluetooth.enableBluetooth()
  else:
    ad.bluetooth.disableBluetooth()
  # Check for BLE RSSI or BLE CS availability
  asserts.assert_true(_is_technology_state(ad, RangingTechnology.BLE_CS, state, timeout_s=60),
                      "BT is not %s" % failure_msg)


def set_screen_rotation_landscape(
    ad: android_device.AndroidDevice, isLandscape: bool
):
  """Sets screen orientation to landscape or portrait mode.

  Args:
    ad: android device object.
    isLandscape: True for landscape mode, False for potrait.
  """
  ad.adb.shell(["settings", "put", "system", "accelerometer_rotation", "0"])
  ad.adb.shell([
      "settings",
      "put",
      "system",
      "user_rotation",
      "1" if isLandscape else "0",
  ])


def set_snippet_foreground_state(
    ad: android_device.AndroidDevice, isForeground: bool
):
  """Sets the snippet app's foreground/background state.

  Args:
    ad: android device object.
    isForeground: True to move snippet to foreground, False for background.
  """
  ad.adb.shell([
      "cmd",
      "uwb",
      "simulate-app-state-change",
      "com.google.snippet.ranging",
      "foreground" if isForeground else "background",
  ])

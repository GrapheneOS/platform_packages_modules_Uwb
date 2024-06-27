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

import logging
import time
from typing import List

from lib import generic_ranging_decorator
from mobly import asserts
from mobly.controllers import android_device

WAIT_TIME_SEC = 3


def verify_peer_found(
    device: generic_ranging_decorator.GenericRangingDecorator,
    peer_addr: List[int],
    session_id: int,
    timeout_s=WAIT_TIME_SEC,
):
    """Verifies that the UWB peer was found.

    Args:
      device: uwb ranging device.
      peer_addr: uwb peer device address.
      session: session id.
    """
    device.ad.log.info(f"Looking for peer {peer_addr}...")
    start_time = time.time()

    while not device.is_uwb_peer_found(peer_addr, session_id):
        if time.time() - start_time > timeout_s:
            raise TimeoutError(f"UWB peer with address {peer_addr} not found")

    logging.info(
        (f"Peer {peer_addr} found in" f"{round(time.time() - start_time, 2)} seconds")
    )


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
    if is_uwb_enabled(ad, timeout_s=120):
        return

    try:
        ad.adb.shell(["cmd", "uwb", "force-country-code", "enabled", "US"])
    except adb.AdbError:
        logging.warning("Unable to force country code")

    # Unable to get UWB enabled even after setting country code, abort!
    asserts.fail(not is_uwb_enabled(ad, timeout_s=120), "Uwb is not enabled")


def is_uwb_enabled(ad: android_device.AndroidDevice, timeout_s=WAIT_TIME_SEC) -> bool:
    """Checks if UWB becomes enabled before the provided timeout_s"""
    start_time = time.time()
    while not ad.ranging.isUwbEnabled():
        if time.time() - start_time > timeout_s:
            return False

    return True


def set_airplane_mode(ad: android_device.AndroidDevice, isEnabled: bool):
    """Sets the airplane mode to the given state.

    Args:
      ad: android device object.
      isEnabled: True for Airplane mode enabled, False for disabled.
    """
    ad.ranging.setAirplaneMode(isEnabled)
    start_time = time.time()
    while get_airplane_mode(ad) != isEnabled:
        time.sleep(0.5)
        if time.time() - start_time > WAIT_TIME_SEC:
            asserts.fail(f"Failed to set airplane mode to: {isEnabled}")


def get_airplane_mode(ad: android_device.AndroidDevice) -> bool:
    """Gets the current airplane mode setting.

    Args:
      ad: android device object.

    Returns:
      True if airplane mode On, False for Off.
    """
    state = ad.adb.shell(["settings", "get", "global", "airplane_mode_on"])
    return bool(int(state.decode().strip()))


def set_screen_rotation_landscape(ad: android_device.AndroidDevice, isLandscape: bool):
    """Sets screen orientation to landscape or portrait mode.

    Args:
      ad: android device object.
      isLandscape: True for landscape mode, False for potrait.
    """
    ad.adb.shell(["settings", "put", "system", "accelerometer_rotation", "0"])
    ad.adb.shell(
        ["settings", "put", "system", "user_rotation", "1" if isLandscape else "0"]
    )


def set_snippet_foreground_state(ad: android_device.AndroidDevice, isForeground: bool):
    """Sets the snippet app's foreground/background state.

    Args:
      ad: android device object.
      isForeground: True to move snippet to foreground, False for background.
    """
    ad.adb.shell(
        [
            "cmd",
            "uwb",
            "simulate-app-state-change",
            "multidevices.snippet.ranging",
            "foreground" if isForeground else "background",
        ]
    )

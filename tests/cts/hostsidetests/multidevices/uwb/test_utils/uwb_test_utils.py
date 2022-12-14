"""Test utils for UWB."""

import logging
import random
import time
from typing import List
from mobly import asserts
from mobly.controllers import android_device

from lib import uwb_ranging_decorator


WAIT_TIME_SEC = 3


def _get_uwb_state_callback_status(ad: android_device.AndroidDevice,
                                   uwb_event: str) -> bool:
  """Checks if expected callback is received.

  Args:
    ad: android device object.
    uwb_event: uwb state callback.

  Returns:
    True if expected callback is received, False if not.
  """
  callback_key = "uwb_state_%s" % random.randint(1, 100)
  handler = ad.uwb.registerUwbAdapterStateCallback(callback_key)
  try:
    event = handler.waitAndGet("UwbAdapterStateCallback", timeout=WAIT_TIME_SEC)
    event_received = event.data["uwbAdapterStateEvent"]
    logging.debug("Received event - %s", event_received)
    status = event_received == uwb_event
  except TimeoutError as t:
    raise Exception("Event 'UwbAdapterStateCallback' not received.") from t
  finally:
    ad.uwb.unregisterUwbAdapterStateCallback(callback_key)
  return status


def verify_uwb_state_callback(ad: android_device.AndroidDevice,
                              uwb_event: str,
                              timeout: int = WAIT_TIME_SEC) -> bool:
  """Verifies expected UWB callback is received.

  Args:
    ad: android device object.
    uwb_event: expected callback event.
    timeout: timeout for callback event.

  Returns:
    True if expected callback is received, False if not.
  """
  callback_status = False
  start_time = time.time()
  while time.time() - start_time < timeout:
    time.sleep(0.1)
    if _get_uwb_state_callback_status(ad, uwb_event):
      logging.debug("Received the '%s' callback in %ss", uwb_event,
                    round(time.time() - start_time, 2))
      callback_status = True
      break
  return callback_status


def get_uwb_state(ad: android_device.AndroidDevice):
  """Gets the current UWB state.

  Args:
    ad: android device object.

  Returns:
    UWB state, True if enabled, False if not.
  """
  if ad.build_info["build_id"].startswith("S"):
    uwb_state = bool(ad.uwb.getAdapterState())
  else:
    uwb_state = ad.uwb.isUwbEnabled()
  return uwb_state


def set_uwb_state_and_verify(ad: android_device.AndroidDevice, state: bool):
  """Sets UWB state to on or off and verifies it.

  Args:
    ad: android device object.
    state: bool, True for UWB on, False for off.
  """
  failure_msg = "enabled" if state else "disabled"
  ad.uwb.setUwbEnabled(state)
  event_str = "Inactive" if state else "Disabled"
  asserts.assert_true(verify_uwb_state_callback(ad, event_str),
                      "Uwb is not %s" % failure_msg)


def verify_peer_found(ranging_dut: uwb_ranging_decorator.UwbRangingDecorator,
                      peer_addr: List[int]):
  """Verifies if the UWB peer is found.

  Args:
    ranging_dut: uwb ranging device.
    peer_addr: uwb peer device address.
  """
  start_time = time.time()
  while not ranging_dut.is_uwb_peer_found(peer_addr):
    if time.time() - start_time > WAIT_TIME_SEC:
      asserts.fail("UWB peer with address %s not found" % peer_addr)
  logging.info("Peer %s found in %s seconds", peer_addr,
               round(time.time() - start_time, 2))


def set_airplane_mode(ad: android_device.AndroidDevice, state: bool):
  """Sets the airplane mode to the given state.

  Args:
    ad: android device object.
    state: bool, True for Airplane mode on, False for off.
  """
  ad.adb.shell(
      ["settings", "put", "global", "airplane_mode_on",
       str(int(state))])
  ad.adb.shell([
      "am", "broadcast", "-a", "android.intent.action.AIRPLANE_MODE", "--ez",
      "state",
      str(state)
  ])
  start_time = time.time()
  while get_airplane_mode(ad) != state:
    time.sleep(0.5)
    if time.time() - start_time > WAIT_TIME_SEC:
      asserts.fail("Failed to set airplane mode to: %s" % state)


def get_airplane_mode(ad: android_device.AndroidDevice) -> bool:
  """Gets the airplane mode.

  Args:
    ad: android device object.

  Returns:
    True if airplane mode On, False for Off.
  """
  state = ad.adb.shell(["settings", "get", "global", "airplane_mode_on"])
  return bool(int(state.decode().strip()))


def set_screen_rotation(ad: android_device.AndroidDevice, val: int):
  """Sets screen orientation to landscape or portrait mode.

  Args:
    ad: android device object.
    val: False for potrait, True 1 for landscape mode.
  """
  ad.adb.shell(["settings", "put", "system", "accelerometer_rotation", "0"])
  ad.adb.shell(["settings", "put", "system", "user_rotation", str(val)])

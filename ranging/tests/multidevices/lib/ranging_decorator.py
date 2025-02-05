import copy
import dataclasses
from enum import IntEnum, StrEnum
from typing import Set, Dict
from uuid import uuid4
from lib.params import RangingPreference
from mobly.controllers.android_device import AndroidDevice
from mobly.controllers.android_device_lib.callback_handler_v2 import (
    CallbackHandlerV2,
)
from mobly.snippet.callback_event import CallbackEvent


CALLBACK_WAIT_TIME_SEC = 5.0


class RangingTechnology(IntEnum):
  UWB = 0
  BLE_CS = 1
  WIFI_RTT = 2
  BLE_RSSI = 3


class Event(StrEnum):
  OPENED = "OPENED"
  OPEN_FAILED = "OPEN_FAILED"
  STARTED = "STARTED"
  DATA = "DATA"
  STOPPED = "STOPPED"
  CLOSED = "CLOSED"
  OOB_SEND_CAPABILITIES_REQUEST = "OOB_SEND_CAPABILITIES_REQUEST",
  OOB_SEND_CAPABILITIES_RESPONSE = "OOB_SEND_CAPABILITIES_RESPONSE",
  OOB_SEND_SET_CONFIGURATION = "OOB_SEND_SET_CONFIGURATION",
  OOB_SEND_STOP_RANGING = "OOB_SEND_STOP_RANGING",
  OOB_SEND_UNKNOWN = "OOB_SEND_UNKNOWN",
  OOB_CLOSED = "OOB_CLOSED"


class RangingDecorator:

  def __init__(self, ad: AndroidDevice):
    """Initialize the ranging device.

    Args:
        ad: android device object
    """
    self.id = str(uuid4())
    self.ad = ad
    self._callback_events: Dict[str, CallbackHandlerV2] = {}
    self.log = self.ad.log
    self.uwb_address = None
    self.bt_addr = None

  def start_ranging(
      self, session_handle: str, preference: RangingPreference
  ):
    """Start ranging with the specified preference. """
    handler = self.ad.ranging.startRanging(
        session_handle, dataclasses.asdict(preference)
    )
    self._callback_events[session_handle] = handler

  def start_ranging_and_assert_opened(
      self, session_handle: str, preference: RangingPreference
  ):
    """Start ranging with the specified preference and wait for onStarted event.

    Throws:
      CallbackHandlerTimeoutError if ranging does not successfully start.
    """
    self.start_ranging(session_handle, preference)
    self.assert_ranging_event_received(session_handle, Event.OPENED)

  def is_ranging_technology_supported(
      self, ranging_technology: RangingTechnology
  ) -> bool:
    """Checks whether a specific ranging technology is supported by the device"""
    return self.ad.ranging.isTechnologySupported(ranging_technology)

  def stop_ranging(self, session_handle: str):
    self.ad.ranging.stopRanging(session_handle)

  def assert_closed(self, session_handle: str):
    self.assert_close_ranging_event_received(session_handle)
    self._callback_events.pop(session_handle)

  def stop_ranging_and_assert_closed(self, session_handle: str):
    """Stop ranging and wait for onStopped event.

    Throws:
      CallbackHandlerTimeoutError if ranging was not successfully stopped.
    """
    self.stop_ranging(session_handle)
    self.assert_closed(session_handle)

  def assert_close_ranging_event_received(self, session_handle: str):
    self.assert_ranging_event_received(session_handle, Event.CLOSED)

  def assert_ranging_event_received(
      self,
      session_handle: str,
      event: Event,
      timeout_s: float = CALLBACK_WAIT_TIME_SEC,
  ) -> CallbackEvent:
    """Asserts that the expected event is received before a timeout.

    Args:
      session_handle: identifier for the ranging session.
      event: expected ranging event.
      timeout_s: timeout in seconds.

    Returns:
      The event

    Throws:
      CallbackHandlerTimeoutError if the expected event was not received.
    """
    handler = self._callback_events[session_handle]
    return handler.waitAndGet(event, timeout=timeout_s)

  def verify_received_data_from_peer_using_technologies(
      self,
      session_handle: str,
      peer_id: str,
      technologies: Set[RangingTechnology],
      timeout_s: float = CALLBACK_WAIT_TIME_SEC,
  ) -> bool:
    """Verifies that the peer sends us data from all provided technologies before a timeout.

    Args:
      session_handle: unique identifier for the ranging session.
      peer_id: UUID of the peer.
      technologies: set of ranging technologies over which we want to receive
        data.
      timeout_s: timeout in seconds.

    Returns:
        True if peer was found, False otherwise
    """
    remaining_technologies = copy.deepcopy(technologies)

    def predicate(event):
      technology = event.data["technology"]

      if event.data["peer_id"] == peer_id and technology in copy.deepcopy(
          remaining_technologies
      ):
        remaining_technologies.remove(technology)

      return not bool(remaining_technologies)

    handler = self._callback_events[session_handle]
    try:
      handler.waitForEvent(Event.DATA, predicate, timeout=timeout_s)
      return True
    except Exception:
      return False

  def verify_received_data_from_peer(
      self,
      session_handle: str,
      peer_id: str,
      timeout_s: float = CALLBACK_WAIT_TIME_SEC,
  ) -> bool:
    """Verifies that the peer sends us data using any technology before a timeout.

    Args:
      session_handle: unique identifier for the ranging session.
      peer_id: UUID of the peer.
      timeout_s: timeout in seconds.

    Returns:
        True if peer received data, False otherwise
    """

    def predicate(event):
      return event.data["peer_id"] == peer_id

    handler = self._callback_events[session_handle]
    try:
      handler.waitForEvent(Event.DATA, predicate, timeout=timeout_s)
      return True
    except Exception:
      return False

  def clear_ranging_sessions(self):
    """Stop all ranging sessions and clear callback events"""
    for session_handle in self._callback_events.keys():
      self.ad.ranging.stopRanging(session_handle)

    self._callback_events.clear()

  def get_callback_handler(self, session_handle: str) -> CallbackHandlerV2:
    """Get the mobly CallbackHandler associated with the provided session"""
    return self._callback_events.get(session_handle, None)


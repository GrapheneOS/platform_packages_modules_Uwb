import copy
import dataclasses
from enum import IntEnum, StrEnum
from typing import Set
from uuid import uuid4
from lib.params import RangingPreference
from mobly.controllers import android_device


CALLBACK_WAIT_TIME_SEC = 5


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


class RangingDecorator:

  def __init__(self, ad: android_device.AndroidDevice):
    """Initialize the ranging device.

    Args:
        ad: android device object
    """
    self.id = str(uuid4())
    self.ad = ad
    self._event_handlers = {}
    self.log = self.ad.log
    self.uwb_address = None
    self.bt_addr = None

  def start_ranging_and_assert_opened(
      self, session_handle: str, preference: RangingPreference
  ):
    """Start ranging with the specified preference and wait for onStarted event.

    Throws:
      CallbackHandlerTimeoutError if ranging does not successfully start.
    """
    handler = self.ad.ranging.startRanging(
        session_handle, dataclasses.asdict(preference)
    )
    self._event_handlers[session_handle] = handler
    self.assert_ranging_event_received(session_handle, Event.OPENED)

  def is_ranging_technology_supported(self, ranging_technology : RangingTechnology) -> bool:

    """Checks whether a specific ranging technology is supported by the device"""
    return self.ad.ranging.isTechnologySupported(ranging_technology)


  def stop_ranging_and_assert_closed(self, session_handle: str):
    """Stop ranging and wait for onStopped event.

    Throws:
      CallbackHandlerTimeoutError if ranging was not successfully stopped.
    """
    self.ad.ranging.stopRanging(session_handle)
    self.assert_ranging_event_received(session_handle, Event.CLOSED)
    self._event_handlers.pop(session_handle)

  def assert_ranging_event_received(
      self,
      session_handle: str,
      event: Event,
      timeout_s: int = CALLBACK_WAIT_TIME_SEC,
  ):
    """Asserts that the expected event is received before a timeout.

    Args:
      session_handle: identifier for the ranging session.
      event: expected ranging event.
      timeout_s: timeout in seconds.

    Throws:
      CallbackHandlerTimeoutError if the expected event was not received.
    """
    handler = self._event_handlers[session_handle]
    handler.waitAndGet(event, timeout=timeout_s)

  def verify_received_data_from_peer_using_technologies(
      self,
      session_handle: str,
      peer_id: str,
      technologies: Set[RangingTechnology],
      timeout_s: int = CALLBACK_WAIT_TIME_SEC,
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
      peer = event.data["peer"]
      technology = event.data["technology"]

      if peer == peer_id and technology in copy.deepcopy(
          remaining_technologies
      ):
        remaining_technologies.remove(technology)

      return not bool(remaining_technologies)

    handler = self._event_handlers[session_handle]
    try:
      handler.waitForEvent(Event.DATA, predicate, timeout=timeout_s)
      return True
    except Exception:
      return False

  def verify_received_data_from_peer(
      self,
      session_handle: str,
      peer_id: str,
      timeout_s: int = CALLBACK_WAIT_TIME_SEC,
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
      return event.data["peer"] == peer_id

    handler = self._event_handlers[session_handle]
    try:
      handler.waitForEvent(Event.DATA, predicate, timeout=timeout_s)
      return True
    except Exception:
      return False

  def clear_ranging_sessions(self):
    """Stop all ranging sessions and clear callback events"""
    for session_handle in self._event_handlers.keys():
      self.ad.ranging.stopRanging(session_handle)

    self._event_handlers.clear()

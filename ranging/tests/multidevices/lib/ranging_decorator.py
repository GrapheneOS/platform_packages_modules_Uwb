import copy
import dataclasses
from enum import IntEnum, StrEnum
from uuid import uuid4
from lib.params import RangingPreference
from mobly.controllers import android_device


CALLBACK_WAIT_TIME_SEC = 5


class RangingTechnology(IntEnum):
  UWB = 0
  BT_CS = 1
  WIFI_RTT = 2
  BLE_RSSI = 3


class Event(StrEnum):
  STARTED = "STARTED"
  START_FAILED = "START_FAILED"
  CLOSED = "CLOSED"
  STOPPED = "STOPPED"
  RESULTS_RECEIVED = "RESULTS_RECEIVED"


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

  def start_ranging_and_assert_started(
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
    self.assert_ranging_event_received(session_handle, Event.STARTED)

  def stop_ranging_and_assert_stopped(self, session_handle: str):
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

  def verify_peer_found_with_technologies(
      self,
      session_handle: str,
      peer_id: str,
      technologies: RangingTechnology,
      timeout_s: int = CALLBACK_WAIT_TIME_SEC,
  ) -> bool:
    """Verifies that the peer was found with all provided technologies before a timeout.

    Args:
      session_handle: unique identifier for the ranging session.
      peer_id: UUID of the peer.
      technologies: set of ranging technology with which to look for the peer.
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
      handler.waitForEvent(Event.RESULTS_RECEIVED, predicate, timeout=timeout_s)
      return True
    except Exception:
      return False

  def verify_peer_found_with_any_technology(
      self,
      session_handle: str,
      peer_id: str,
      timeout_s: int = CALLBACK_WAIT_TIME_SEC,
  ) -> bool:
    """Verifies that the peer was found with any technology before a timeout.

    Args:
      session_handle: unique identifier for the ranging session.
      peer_id: UUID of the peer.
      timeout_s: timeout in seconds.

    Returns:
        True if peer was found, False otherwise
    """

    def predicate(event):
      return event.data["peer"] == peer_id

    handler = self._event_handlers[session_handle]
    try:
      handler.waitForEvent(Event.RESULTS_RECEIVED, predicate, timeout=timeout_s)
      return True
    except Exception:
      return False

  def clear_ranging_sessions(self):
    """Stop all ranging sessions and clear callback events"""
    for session_handle in self._event_handlers.keys():
      self.ad.ranging.stopRanging(session_handle)

    self._event_handlers.clear()

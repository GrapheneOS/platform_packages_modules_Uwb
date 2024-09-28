import time
from typing import List
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import jsonrpc_client_base
from mobly.snippet import errors
from uwb import uwb_ranging_params

CALLBACK_WAIT_TIME_SEC = 3
STOP_CALLBACK_WAIT_TIME_SEC = 6


class GenericRangingDecorator:

  def __init__(self, ad: android_device.AndroidDevice):
    """Initialize the ranging device.

    Args:
        ad: android device object
    """
    self.ad = ad
    self._event_handlers = {}
    self.log = self.ad.log

  def start_uwb_ranging_session(
      self, params: uwb_ranging_params.UwbRangingParams
  ):
    handler = self.ad.ranging.startUwbRanging(params.to_dict())
    self._event_handlers[params.session_id] = handler
    self.verify_ranging_event_received("Started", params.session_id)

  def stop_uwb_ranging_session(self, session_id: int):
    self.ad.ranging.stopUwbRanging(session_id)
    self.verify_ranging_event_received("Stopped", session_id)
    self._event_handlers.pop(session_id)

  def clear_all_uwb_ranging_sessions(self):
    for session_id in self._event_handlers.keys():
      self.ad.ranging.stopUwbRanging(session_id)
      self.clear_ranging_callback_events(session_id)

    self._event_handlers.clear()

  def clear_ranging_callback_events(self, session_id: int):
    """Clear 'GenericRangingCallback' events from EventCache.

    Args:
      session_id: ranging session id.
    """
    self._event_handlers[session_id].getAll("GenericRangingCallback")

  def verify_ranging_event_received(
      self,
      ranging_event: str,
      session_id: int,
      timeout_s: int = CALLBACK_WAIT_TIME_SEC,
  ) -> bool:
    """Verifies that the expected event is received before a timeout.

    Args:
      ranging_event: expected ranging event.
      session: ranging session.
      timeout_s: timeout in seconds.

    Returns:
      True if the expected event was received.
    """
    handler = self._event_handlers[session_id]

    start_time = time.time()
    while time.time() - start_time < timeout_s:
      try:
        event = handler.waitAndGet("GenericRangingCallback", timeout=timeout_s)
        event_received = event.data["genericRangingSessionEvent"]
        self.ad.log.debug("Received event - %s" % event_received)
        if event_received == ranging_event:
          self.ad.log.debug(
              f"Received event {ranging_event} in"
              f" {round(time.time() - start_time, 2)} secs"
          )
          self.clear_ranging_callback_events(session_id)
          return True
      except errors.CallbackHandlerTimeoutError:
        self.log.warn("Failed to receive 'RangingSessionCallback' event")

    return False

  def verify_uwb_peer_found(
      self,
      addr: List[int],
      session_id: int,
      timeout_s: int = CALLBACK_WAIT_TIME_SEC,
  ):
    """Verifies that the UWB peer is found before a timeout.

    Args:
      addr: peer address.
      session_id: ranging session id.
      timeout_s: timeout in seconds.

    Returns:
      True if the peer was found.
    """
    start_time = time.time()
    while time.time() - start_time < timeout_s:
      self.verify_ranging_event_received("ReportReceived", session_id)
      if self.ad.ranging.verifyUwbPeerFound(addr, session_id):
        return True

    return False

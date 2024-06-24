import time
from uwb import uwb_ranging_params
from typing import List
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import jsonrpc_client_base
from mobly.snippet import errors

CALLBACK_WAIT_TIME_SEC = 3
STOP_CALLBACK_WAIT_TIME_SEC = 6


class GenericRangingDecorator():

    def __init__(self, ad: android_device.AndroidDevice):
        """Initialize the ranging device.

        Args:
        ad: android device object
        """
        self.ad = ad
        self._callback_keys = {}
        self._event_handlers = {}
        self.log = self.ad.log

    def start_uwb_ranging(self, params: uwb_ranging_params.UwbRangingParams, session_id: int):
        callback_key = "fira_session_%s" % session_id
        handler = self.ad.ranging.startUwbRanging(callback_key, params.to_dict())
        self._event_handlers[session_id] = handler
        self.verify_callback_received("Started", session_id)

    def stop_uwb_ranging(self,session_id: int):
        callback_key = "fira_session_%s" % session_id
        self.ad.ranging.stopUwbRanging(callback_key)
        self.verify_callback_received("Stopped", session_id)

    def clear_ranging_session_callback_events(self, ranging_session_id: int = 0):
        """Clear 'GenericRangingCallback' events from EventCache.

        Args:
        ranging_session_id: ranging session id.
        """
        handler = self._event_handlers[ranging_session_id]
        handler.getAll("GenericRangingCallback")

    def verify_callback_received(self,
                                 ranging_event: str,
                                 session: int = 0,
                                 timeout: int = CALLBACK_WAIT_TIME_SEC) -> bool:
        """Verifies if the expected callback is received.

        Args:
          ranging_event: Expected ranging event.
          session: ranging session.
          timeout: callback timeout.

        Returns:
        True if callback was received, False if not.

        Raises:
          TimeoutError: if the expected callback event is not received.
        """
        handler = self._event_handlers[session]
        start_time = time.time()
        while time.time() - start_time < timeout:
            try:
                event = handler.waitAndGet("GenericRangingCallback", timeout=timeout)
                event_received = event.data["genericRangingSessionEvent"]
                self.ad.log.debug("Received event - %s" % event_received)
                if event_received == ranging_event:
                    self.ad.log.debug("Received the '%s' callback in %ss" %
                                    (ranging_event, round(time.time() - start_time, 2)))
                    self.clear_ranging_session_callback_events(session)
                    return True
            except errors.CallbackHandlerTimeoutError as e:
                self.log.warn("Failed to receive 'RangingSessionCallback' event")
        raise TimeoutError("Failed to receive '%s' event" % ranging_event)

    def is_uwb_peer_found(self, addr: List[int], session: int = 0) -> bool:
        """Verifies if the UWB peer is found.

        Args:
        addr: peer address.
        session: ranging session.

        Returns:
        True if peer is found, False if not.
        """
        return self.verify_callback_received("ReportReceived", session)

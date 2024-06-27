import time
from uwb import uwb_ranging_params
from typing import List
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import jsonrpc_client_base
from mobly.snippet import errors

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

    def start_uwb_ranging_session(self, params: uwb_ranging_params.UwbRangingParams):
        handler = self.ad.ranging.startUwbRanging(params.to_dict())
        self._event_handlers[params.session_id] = handler
        self.verify_callback_received("Started", params.session_id)

    def stop_uwb_ranging_session(self, session_id: int):
        self.ad.ranging.stopUwbRanging(session_id)
        self.verify_callback_received("Stopped", session_id)
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

    def verify_callback_received(
        self,
        ranging_event: str,
        session_id: int,
        timeout_s: int = CALLBACK_WAIT_TIME_SEC,
    ) -> bool:
        """Verifies if the expected callback is received.

        Args:
          ranging_event: Expected ranging event.
          session: ranging session.
          timeout_s: callback timeout in seconds.

        Returns:
        True if callback was received, False if not.

        Raises:
          TimeoutError: if the expected callback event is not received.
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
                        "Received the '%s' callback in %ss"
                        % (ranging_event, round(time.time() - start_time, 2))
                    )
                    self.clear_ranging_callback_events(session_id)
                    return True
            except errors.CallbackHandlerTimeoutError as e:
                self.log.warn("Failed to receive 'RangingSessionCallback' event")
        raise TimeoutError("Failed to receive '%s' event" % ranging_event)

    def is_uwb_peer_found(self, addr: List[int], session_id: int) -> bool:
        """Verifies if the UWB peer is found.

        Args:
        addr: peer address.
        session_id: ranging session id.

        Returns:
        True if peer is found, False if not.
        """
        return self.verify_callback_received("ReportReceived", session_id)

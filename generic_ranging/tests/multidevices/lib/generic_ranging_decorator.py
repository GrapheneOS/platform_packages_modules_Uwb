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

    def start_uwb_ranging(self, params: uwb_ranging_params.UwbRangingParams):
        callback_key = "fira_session_%s" % 1
        handler = self.ad.ranging.startUwbRanging(callback_key, params.to_dict())

    def stop_uwb_ranging(self, params: uwb_ranging_params.UwbRangingParams):
        callback_key = "fira_session_%s" % 1
        handler = self.ad.ranging.stopUwbRanging(callback_key)

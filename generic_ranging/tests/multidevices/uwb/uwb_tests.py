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
import random
import sys
import threading
import time
from typing import List, Optional

from uwb import uwb_ranging_params
from lib import ranging_base_test
from lib import generic_ranging_decorator
from mobly import asserts
from mobly import config_parser
from mobly import signals
from mobly import suite_runner
from test_utils import uwb_test_utils

RESPONDER_STOP_CALLBACK_TIMEOUT = 60

_TEST_CASES = (
    "test_one_to_one_ranging",
)


class RangingTest(ranging_base_test.RangingBaseTest):
    """Tests for UWB Ranging APIs.

    Attributes:
    android_devices: list of android device objects.
    """

    def __init__(self, configs: config_parser.TestRunConfig):
        """Init method for the test class.

        Args:
        configs: A config_parser.TestRunConfig object.
        """
        super().__init__(configs)
        self.tests = _TEST_CASES

    def setup_class(self):
        super().setup_class()
        self.uwb_devices = [
            generic_ranging_decorator.GenericRangingDecorator(ad)
            for ad in self.android_devices
        ]
        self.initiator, self.responder = self.uwb_devices
        self.device_addresses = self.user_params.get("device_addresses",
                                                     [[1, 2], [3, 4]])
        self.initiator_addr, self.responder_addr = self.device_addresses
        self.new_responder_addr = [4, 5]
        # self.p_sts_sub_session_id = 11
        # self.p_sts_sub_session_key = [
        #     8, 7, 6, 5, 4, 3, 2, 1, 1, 2, 3, 4, 5, 6, 7, 8]
        # self.block_stride_length = random.randint(1, 10)

    def setup_test(self):
        super().setup_test()
        # for uwb_device in self.uwb_devices:
        #     try:
        #         uwb_device.close_ranging()
        #     except TimeoutError:
        #         uwb_device.log.warn("Failed to cleanup ranging sessions")
        # for uwb_device in self.uwb_devices:
        #     uwb_test_utils.set_airplane_mode(uwb_device.ad, False)
        #     self._reset_snippet_fg_bg_state(uwb_device)

    def teardown_test(self):
        super().teardown_test()

    ### Test Cases ###

    def test_one_to_one_ranging(self):
        initiator_params = uwb_ranging_params.UwbRangingParams(
            config_id=1,
            session_id=5,
            sub_session_id=1,
            device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_INITIATOR,
            device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLLER,
            device_address=self.initiator_addr,
            destination_addresses=[self.responder_addr],
        )
        responder_params = uwb_ranging_params.UwbRangingParams(
            config_id=1,
            session_id=5,
            sub_session_id=1,
            device_role=uwb_ranging_params.FiraParamEnums.DEVICE_ROLE_RESPONDER,
            device_type=uwb_ranging_params.FiraParamEnums.DEVICE_TYPE_CONTROLEE,
            device_address=self.responder_addr,
            destination_addresses=[self.initiator_addr],
        )
        self.initiator.start_uwb_ranging(initiator_params)
        self.responder.start_uwb_ranging(responder_params)

        time.sleep(20)
        self.initiator.stop_uwb_ranging(initiator_params)
        self.responder.stop_uwb_ranging(responder_params)


if __name__ == "__main__":
    if "--" in sys.argv:
        index = sys.argv.index("--")
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    suite_runner.run_suite([RangingTest])

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
"""Ranging base test."""

import re

from lib import utils
from mobly import base_test
from mobly import test_runner
from mobly.controllers import android_device


RELEASE_ID_REGEX = re.compile(r"\w+\.\d+\.\d+")


class RangingBaseTest(base_test.BaseTestClass):
  """Base class for Uwb tests."""

  def setup_class(self):
    """Sets up the Android devices for Uwb test."""
    super().setup_class()
    self.android_devices = self.register_controller(
        android_device, min_number=2
    )
    for ad in self.android_devices:
      ad.load_snippet("ranging", "com.google.snippet.ranging")
      ad.load_snippet("uwb", "com.google.snippet.uwb")
      ad.load_snippet("bluetooth", "com.google.snippet.bluetooth")

  def setup_test(self):
    super().setup_test()
    for ad in self.android_devices:
      ad.ranging.logInfo(
          "*** TEST START: " + self.current_test_info.name + " ***"
      )

  def teardown_test(self):
    super().teardown_test()
    for ad in self.android_devices:
      ad.ranging.logInfo(
          "*** TEST END: " + self.current_test_info.name + " ***"
      )

  def teardown_class(self):
    super().teardown_class()

  def on_fail(self, record):
    test_name = record.test_name
    # Single device test
    for count, ad in enumerate(self.android_devices):
      device_name = "initiator" if not count else "responder"
      test_device_name = test_name + "_" + device_name
      ad.take_bug_report(
          test_name=test_device_name,
          destination=self.current_test_info.output_path,
      )


if __name__ == "__main__":
  test_runner.main()

"""Uwb base test."""

import logging
import re

from mobly import base_test
from mobly import test_runner
from mobly import records
from mobly.controllers import android_device

RELEASE_ID_REGEX = re.compile(r"\w+\.\d+\.\d+")


class UwbBaseTest(base_test.BaseTestClass):
  """Base class for Uwb tests."""

  def setup_class(self):
    """Sets up the Android devices for Uwb test."""
    super().setup_class()
    self.android_devices = self.register_controller(android_device,
                                                    min_number=2)
    for ad in self.android_devices:
      ad.load_snippet("uwb", "com.google.snippet.uwb")

    if "set_country_code" in self.user_params:
      for ad in self.android_devices:
        ad.adb.shell(
            ["cmd", "uwb", "force-country-code", "enabled", "US"], timeout=3
        )

  def teardown_class(self):
    super().teardown_class()
    self._record_all()

  def _get_effort_name(self) -> str:
    """Gets the TestTracker effort name from the Android build ID.

    Returns:
      Testtracker effort name for the test results.
    """
    effort_name = "UNKNOWN"
    for record in self._controller_manager.get_controller_info_records():
      if record.controller_name == "AndroidDevice" and record.controller_info:
        build_info = record.controller_info[0]["build_info"]
        if re.match(RELEASE_ID_REGEX, build_info["build_id"]):
          effort_name = build_info["build_id"]
        else:
          effort_name = build_info[android_device.BuildInfoConstants
                                   .BUILD_VERSION_INCREMENTAL.build_info_key]
        break
    return effort_name

  def _record(self, tr_record: records.TestResultRecord):
    """Records TestTracker data for a single test.

    Args:
      tr_record: test case record.
    """
    self.record_data({
        "Test Class": tr_record.test_class,
        "Test Name": tr_record.test_name,
        "sponge_properties": {
            "test_tracker_effort_name": self._get_effort_name(),
            "test_tracker_uuid": tr_record.uid
        }
    })

  def _record_all(self):
    """Records TestTracker data for all tests."""
    tr_record_dict = {}
    for tr_record in self.results.executed:
      if not tr_record.uid:
        logging.warning("Missing UID for test %s", tr_record.test_name)
        continue
      if tr_record.uid in tr_record_dict:
        record = tr_record_dict[tr_record.uid]
        if record in self.results.failed or record in self.results.error:
          continue
      tr_record_dict[tr_record.uid] = tr_record
    for tr_record in tr_record_dict.values():
      self._record(tr_record)


if __name__ == "__main__":
  test_runner.main()

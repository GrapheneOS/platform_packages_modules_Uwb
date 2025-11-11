"""Mobly base test for ranging tests."""

import datetime
import time
import uuid

from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb

import lib.params as ranging_params

_BLUETOOTH_SNIPPET_PACKAGE = 'com.google.snippet.bluetooth'
_RANGING_SNIPPET_PACKAGE = 'com.google.snippet.ranging'
_UWB_SNIPPET_PACKAGE = 'com.google.snippet.uwb'

_DELAY_AFTER_CHANGE_BLUETOOTH_STATUS = datetime.timedelta(seconds=5)
_DELAY_BETWEEN_ACTIONS = datetime.timedelta(seconds=1)
_WAIT_FOR_BLE_RSSI_TIMEOUT = datetime.timedelta(seconds=60)


class RangingBaseTestClass(base_test.BaseTestClass):
  """Mobly base test class for ranging tests."""

  ads: list[android_device.AndroidDevice]
  initiator: android_device.AndroidDevice
  responder: android_device.AndroidDevice

  def _setup_android_device(self, ad: android_device.AndroidDevice) -> None:
    """Sets up an Android device for ranging test."""
    ad.id = str(uuid.uuid4())

    # Enable Bluetooth HCI snoop log.
    # NOTE: These setprop commands might not be effective on all OEM devices,
    # especially on user builds.
    try:
      ad.adb.shell('setprop persist.bluetooth.btsnooplogmode full')
      ad.adb.shell('setprop persist.bluetooth.btsnoopsize 0xfffffffffffffff')
    except adb.AdbError:
      ad.log.exception(
          "Failed to set btsnoop props. "
          "This is expected on user builds or if 'adb root' was not run."
      )

    ad.adb.shell('svc bluetooth disable')
    time.sleep(_DELAY_AFTER_CHANGE_BLUETOOTH_STATUS.total_seconds())
    ad.adb.shell('svc bluetooth enable')
    time.sleep(_DELAY_AFTER_CHANGE_BLUETOOTH_STATUS.total_seconds())

    ad.load_snippet('mbs', android_device.MBS_PACKAGE)

    ad.load_snippet('uwb', _UWB_SNIPPET_PACKAGE)
    ad.adb.shell('cmd uwb force-country-code enabled US')
    if not ad.uwb.isUwbEnabled():
      ad.uwb.setUwbEnabled(True)
    ad.unload_snippet('uwb')

    ad.load_snippet('ranging', _RANGING_SNIPPET_PACKAGE)
    ad.adb.shell(
        f'cmd uwb simulate-app-state-change {_RANGING_SNIPPET_PACKAGE}'
        ' foreground'
    )

    ad.load_snippet('bluetooth', _BLUETOOTH_SNIPPET_PACKAGE)
    end_time = time.monotonic() + _WAIT_FOR_BLE_RSSI_TIMEOUT.total_seconds()
    while time.monotonic() < end_time:
      if ad.ranging.isTechnologyEnabled(
          ranging_params.RangingTechnology.BLE_RSSI
      ):
        break
      time.sleep(_DELAY_BETWEEN_ACTIONS.total_seconds())
    else:
      asserts.fail(f'{ad} BLE RSSI is not enabled on the device.')
    ad.bluetooth.reset()

  def setup_class(self) -> None:
    super().setup_class()
    self.ads = self.register_controller(android_device, min_number=2)
    utils.concurrent_exec(
        self._setup_android_device,
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )
    self.initiator, self.responder = self.ads
    self.initiator.debug_tag, self.responder.debug_tag = (
        'Initiator',
        'Responder',
    )
    self.initiator.uwb_address = [1, 2]
    self.responder.uwb_address = [3, 4]

  def teardown_test(self) -> None:
    utils.concurrent_exec(
        lambda d: d.ranging.stopAllActiveRanging(),
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )
    utils.concurrent_exec(
        lambda d: d.services.create_output_excerpts_all(self.current_test_info),
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )

  def on_fail(self, record: records.TestResultRecord) -> None:
    android_device.take_bug_reports(
        self.ads, destination=self.current_test_info.output_path
    )
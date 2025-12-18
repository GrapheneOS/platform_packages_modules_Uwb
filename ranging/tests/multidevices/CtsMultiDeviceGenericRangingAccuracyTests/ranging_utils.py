"""Utils for ranging tests."""

from collections.abc import Sequence
from contextlib import suppress
import dataclasses
import datetime
import logging
import random
import statistics
import time
from types import MappingProxyType
import uuid

from mobly import asserts
from mobly import signals
from mobly import utils as mobly_utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import errors as snippet_errors

import lib.params as ranging_params


_DEFAULT_RANGING_MEASURE_COUNTS = 1000
_WAIT_FOR_RANGING_DATA_TIMEOUT = datetime.timedelta(minutes=20)
_WAIT_FOR_RSSI_TIMEOUT_SEC = 2400
_BATCH_INTERVAL_SEC = 5
_NO_DATA_TIMEOUT_SEC = 300

_BLE_CS_ACCEPTABLE_RANGE_DEVIATION = 0.5
_BLE_CS_PASS_RATE_THRESHOLD = 0.9

_UWB_ACCEPTABLE_RANGE_DEVIATION = 0.3
_UWB_ACCEPTABLE_MEDIAN_DEVIATION = 0.25


def skip_if_technology_not_supported(
    devices: list[android_device.AndroidDevice],
    technology: ranging_params.RangingTechnology,
) -> None:
  """Skips the test if any device does not support the specified technology."""
  for device in devices:
    asserts.skip_if(
        not device.ranging.isTechnologySupported(technology),
        f"{device} {technology.name} not supported",
    )


def log_ble_cs_distance_within_tolerance(
    real_distance_in_meters: int,
    measured_distance_data: list[float],
    log_path: str,
) -> None:
  """Gets the Test metrics for BLE_CS.

  Args:
    real_distance_in_meters: The real distance in meters between two devices.
    measured_distance_data: A list of measured distance data.
    log_path: The log path to save the test metrics.
  """
  acceptable_range = real_distance_in_meters * _BLE_CS_ACCEPTABLE_RANGE_DEVIATION
  filtered_data = [
      data
      for data in measured_distance_data
      if abs(data - real_distance_in_meters) <= acceptable_range
  ]
  num_measurements = len(measured_distance_data)
  num_passed = len(filtered_data)
  pass_rate = 0.0 if num_measurements == 0 else (num_passed / num_measurements)

  acceptable_threshold_count = num_measurements * _BLE_CS_PASS_RATE_THRESHOLD

  logging.info(
      "[BLE_CS Metric @ %sm] Data to compare: num_passed = %d, "
      "acceptable_threshold_count = %.0f, pass_rate = %.2f%%",
      real_distance_in_meters,
      num_passed,
      acceptable_threshold_count,
      pass_rate * 100.0,
  )


def log_uwb_distance_within_tolerance(
    real_distance_in_meters: int,
    measured_distance_datas: Sequence[list[float]],
    log_path: str,
) -> None:
  """Gets the Test metrics for UWB.

  Args:
    real_distance_in_meters: The real distance in meters between two devices.
    measured_distance_datas: A list of measured distance data from initiator and
      responder.
    log_path: The log path to save the test metrics.
  """
  acceptable_range = real_distance_in_meters * _UWB_ACCEPTABLE_RANGE_DEVIATION
  uwb_ranging_test_metrics = []
  for measured_distance_data in measured_distance_datas:
    quantiles = statistics.quantiles(measured_distance_data, n=40)

    measured_distance_range = quantiles[38] - quantiles[0]
    measured_distance_median = quantiles[19]

    uwb_ranging_test_metrics.append(
        {
            "real_distance_in_meters": real_distance_in_meters,
            "measured_distance_range": measured_distance_range,
            "measured_distance_median": measured_distance_median,
        }
    )

  for test_metric in uwb_ranging_test_metrics:
    logging.info(
        "[UWB Metric @ %sm] Data to compare (Range): "
        "measured_distance_range = %s, acceptable_range = %s",
        real_distance_in_meters,
        test_metric["measured_distance_range"],
        acceptable_range,
    )

    deviation = (
        abs(test_metric["measured_distance_median"] - real_distance_in_meters)
        / real_distance_in_meters
    )
    logging.info(
        "[UWB Metric @ %sm] Data to compare (Median Deviation): "
        "measured_deviation = %s, acceptable_deviation = %s",
        real_distance_in_meters,
        deviation,
        _UWB_ACCEPTABLE_MEDIAN_DEVIATION,
    )


def log_wifi_rtt_distance_within_tolerance(
    real_distance_in_meters: int,
    measured_distance_datas: Sequence[list[float]],
    log_path: str,
) -> None:
  """Gets the Test metrics for WIFI_RTT.

  Based on CDD 7.4.2.5. Wi-Fi Neighbor Awareness Networking (NAN):
  [7.4.2.5/H-1-1] Devices MUST report the range accurately to within +/-2 meters
  at 80 MHz bandwidth at the 68th percentile.

  Args:
    real_distance_in_meters: The real distance in meters between two devices.
    measured_distance_datas: A sequence containing lists of measured distance
      data. For Wi-Fi RTT, this will typically only contain the initiator's
      data, as only the initiator receives ranging results.
    log_path: The log path to save the test metrics.
  """
  # Per CDD [7.4.2.5/H-1-1], using the +/-2 meters threshold for 80MHz
  # bandwidth as the test requirement.
  acceptable_error_in_meters = 2.0
  pass_rate_threshold = 0.68

  wifi_rtt_ranging_test_metrics = []
  for measured_distance_data in measured_distance_datas:
    asserts.assert_true(
        measured_distance_data, "Measured distance data cannot be empty."
    )
    num_measurements = len(measured_distance_data)

    # The logic is based on the CTS verifier test.
    filtered_data = [
        data
        for data in measured_distance_data
        if abs(data - real_distance_in_meters) <= acceptable_error_in_meters
    ]
    pass_rate = len(filtered_data) / num_measurements

    # Calculate other metrics for logging.
    errors = [abs(data - real_distance_in_meters) for data in measured_distance_data]
    error_quantiles = statistics.quantiles(errors, n=100)
    error_at_68_percentile = error_quantiles[67]

    distance_quantiles = statistics.quantiles(measured_distance_data, n=100)
    measured_distance_median = distance_quantiles[49]

    wifi_rtt_ranging_test_metrics.append(
        {
            "real_distance_in_meters": real_distance_in_meters,
            "pass_rate": pass_rate,
            "measured_distance_median": measured_distance_median,
            "error_at68percentile": error_at_68_percentile,
        }
    )

  for i, test_metric in enumerate(wifi_rtt_ranging_test_metrics):
    role = "initiator" if i == 0 else "responder"
    logging.info(
        "[WiFi_RTT Metric @ %sm] (%s) Data to compare: "
        "measured_pass_rate = %.2f%%, pass_rate_threshold = %.0f%%",
        real_distance_in_meters,
        role,
        test_metric["pass_rate"] * 100.0,
        pass_rate_threshold * 100.0,
    )


def log_ble_rssi_precision_within_tolerance(
    acceptable_spread_dbm: int,
    rssi_data: list[int],
    log_path: str,
) -> None:
  """Gets the Test metrics for BLE RSSI.

  Based on CDD 7.4.3. Bluetooth:
  [C-10-1] MUST have RSSI measurements be within +/-9 dBm for 95% of the
  measurements at 1m distance.
  This implies the spread (max - min) of the middle 95% of data must be <= 18 dBm.

  Args:
    measured_rssi_datas: A sequence containing lists of measured RSSI data (in dBm).
      Typically contains only the initiator's (scanner's) data.
    log_path: The log path to save the test metrics.
  """
  # Per CDD [C-10-1], +/- 9dBm means the total spread allowed is 18 dBm.

  asserts.assert_not_equal(
      len(rssi_data), 0, "Measured RSSI data is empty, skipping calculation."
  )

  sorted_data = sorted(rssi_data)
  count = len(sorted_data)

  idx_min = int(count * 0.025)
  idx_max = min(int(count * 0.975), count - 1)

  spread_95th = sorted_data[idx_max] - sorted_data[idx_min]
  median_rssi = statistics.median(sorted_data)

  log_msg = (
      f"[BLE_RSSI Metric] Spread(95%)={spread_95th} dBm"
      f" (Threshold<={acceptable_spread_dbm}). "
      f"Median={median_rssi:.1f} dBm."
      f" Range95=[{sorted_data[idx_min]}, {sorted_data[idx_max]}]"
  )

  if spread_95th <= acceptable_spread_dbm:
    logging.info(f"{log_msg} - PASS")
  else:
    logging.error(f"{log_msg} - FAIL")


def log_ble_rx_tx_offset_precision(
    tx_rssi_data: list[int],
    rx_rssi_data: list[int],
    target_dbm: int,
    tolerance: int,
    log_path: str,
) -> None:
  """Verifies that the median of RSSI data is within target +/- tolerance.

  Args:
      rssi_data: List of measured RSSI values.
      target_dbm: The expected median value (e.g., -55).
      tolerance: The allowed deviation (e.g., 10).
  """
  """Verifies that the median of both Tx and Rx RSSI data is within target +/- tolerance.

    Args:
        tx_rssi_data: List of measured RSSI values for Tx test (Ref device measuring DUT).
        rx_rssi_data: List of measured RSSI values for Rx test (DUT measuring Ref device).
        target_dbm: The expected median value (e.g., -55).
        tolerance: The allowed deviation (e.g., 10).
    """
  asserts.assert_not_equal(
      len(tx_rssi_data), 0, "Tx Measured RSSI data is empty, skipping calculation."
  )
  asserts.assert_not_equal(
      len(rx_rssi_data), 0, "Rx Measured RSSI data is empty, skipping calculation."
  )

  min_limit = target_dbm - tolerance
  max_limit = target_dbm + tolerance

  def verify_single_dataset(data: list[int], label: str) -> bool:
    sorted_data = sorted(data)
    count = len(sorted_data)
    median = statistics.median(sorted_data)

    log_msg = (
        f"[{label}] Count={count}, Median={median:.1f} dBm. "
        f"Target Range=[{min_limit}, {max_limit}] dBm."
    )

    if min_limit <= median <= max_limit:
      logging.info(f"{log_msg} - PASS")
      return True
    else:
      logging.error(f"{log_msg} - FAIL")
      return False

  tx_passed = verify_single_dataset(tx_rssi_data, "Tx Test/DUT Power")
  rx_passed = verify_single_dataset(rx_rssi_data, "Rx Test/DUT Sensitivity")
  if not (tx_passed and rx_passed):
    logging.info("BLE RSSI Median Verification Failed!")


def get_ranging_distance(
    ranging_handler: callback_handler_v2.CallbackHandlerV2,
    technology: ranging_params.RangingTechnology,
    peer_id: str,
) -> float:
  """Gets one distance data from the ranging event."""
  try:
    event = ranging_handler.waitForEvent(
        "DATA",
        lambda e: e.data["technology"] == technology and e.data["peer_id"] == peer_id,
    )
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f"{ranging_handler._device} Failed"  # pylint: disable=protected-access
        f" to find peer {peer_id} with technology {technology.name}"
    )
  else:
    return event.data["distance"]


def start_ranging_and_get_distance_data(
    initiator: android_device.AndroidDevice,
    responder: android_device.AndroidDevice,
    technology: ranging_params.RangingTechnology,
    initiator_preference: ranging_params.RangingPreference,
    responder_preference: ranging_params.RangingPreference | None = None,
    ranging_measure_count: int = _DEFAULT_RANGING_MEASURE_COUNTS,
) -> tuple[list[float], list[float]]:
  """Starts ranging and gets all measured distance data."""
  session_handle = str(uuid.uuid4())

  initiator_ranging_handler = initiator.ranging.startRanging(
      session_handle, dataclasses.asdict(initiator_preference)
  )
  try:
    initiator_ranging_handler.waitAndGet("OPENED")
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f"{initiator} Failed to open ranging session with preference"
        f" {initiator_preference}"
    )

  if responder_preference is not None:
    responder_ranging_handler = responder.ranging.startRanging(
        session_handle, dataclasses.asdict(responder_preference)
    )
    try:
      responder_ranging_handler.waitAndGet("OPENED")
    except snippet_errors.CallbackHandlerTimeoutError:
      asserts.fail(
          f"{responder} Failed to open ranging session with preference"
          f" {responder_preference}"
      )
  else:
    responder_ranging_handler = None

  initiator_distance_list = []
  responder_distance_list = []
  ranging_end_time = time.monotonic() + _WAIT_FOR_RANGING_DATA_TIMEOUT.total_seconds()
  for i in range(ranging_measure_count):
    if time.monotonic() >= ranging_end_time:
      logging.info(
          "Collecting data reached to the timeout. Expected %d data points, but"
          " only got %d.",
          ranging_measure_count,
          len(initiator_distance_list),
      )
      break

    logging.debug(
        "Ranging data collection: Iteration %d/%d", i + 1, ranging_measure_count
    )

    if responder_ranging_handler is None:
      initiator_distance = get_ranging_distance(
          initiator_ranging_handler, technology, responder.id
      )
      logging.debug("  [Iter %d] Got Initiator distance: %s", i + 1, initiator_distance)
    else:
      initiator_distance, responder_distance = mobly_utils.concurrent_exec(
          get_ranging_distance,
          param_list=[
              [initiator_ranging_handler, technology, responder.id],
              [responder_ranging_handler, technology, initiator.id],
          ],
          raise_on_exception=True,
      )
      logging.debug("  [Iter %d] Got Initiator distance: %s", i + 1, initiator_distance)
      logging.debug("  [Iter %d] Got Responder distance: %s", i + 1, responder_distance)
      responder_distance_list.append(responder_distance)

    initiator_distance_list.append(initiator_distance)
    time.sleep(0.02)

  initiator.ranging.stopRanging(session_handle)
  initiator_ranging_handler.waitAndGet("CLOSED")
  if responder_ranging_handler is not None:
    responder.ranging.stopRanging(session_handle)
    responder_ranging_handler.waitAndGet("CLOSED")

  return (
      initiator_distance_list,
      responder_distance_list,
  )  # pytype: disable=bad-return-type


def start_rtt_ranging_and_get_distance_data(
    initiator: android_device.AndroidDevice,
    responder: android_device.AndroidDevice,
    technology: ranging_params.RangingTechnology,
    initiator_preference: ranging_params.RangingPreference,
    responder_preference: ranging_params.RangingPreference,
    ranging_measure_count: int = _DEFAULT_RANGING_MEASURE_COUNTS,
) -> list[float]:
  """Starts RTT ranging and gets all measured distance data from initiator.

  For Wi-Fi RTT, only the initiator device receives ranging results. The
  responder participates in the ranging exchange but does not get distance
  measurements. Therefore, this function only collects data from the initiator.

  Args:
    initiator: The device acting as the ranging initiator.
    responder: The device acting as the ranging responder.
    technology: The ranging technology to use.
    initiator_preference: Ranging preferences for the initiator.
    responder_preference: Ranging preferences for the responder.
    ranging_measure_count: The number of ranging measurements to collect.

  Returns:
    A list of distance measurements collected by the initiator.
  """
  session_handle = str(uuid.uuid4())

  initiator_ranging_handler = initiator.ranging.startRanging(
      session_handle, dataclasses.asdict(initiator_preference)
  )
  responder_ranging_handler = responder.ranging.startRanging(
      session_handle, dataclasses.asdict(responder_preference)
  )
  try:
    initiator_ranging_handler.waitAndGet("OPENED")
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f"{initiator} Failed to open ranging session with preference"
        f" {initiator_preference}"
    )

  try:
    responder_ranging_handler.waitAndGet("OPENED")
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f"{responder} Failed to open ranging session with preference"
        f" {responder_preference}"
    )

  initiator_distance_list = []
  ranging_end_time = time.monotonic() + _WAIT_FOR_RANGING_DATA_TIMEOUT.total_seconds()
  for _ in range(ranging_measure_count):
    if time.monotonic() >= ranging_end_time:
      logging.info(
          "Collecting data reached to the timeout. Expected %d data points, but"
          " only got %d.",
          ranging_measure_count,
          len(initiator_distance_list),
      )
      break
    initiator_distance = get_ranging_distance(
        initiator_ranging_handler, technology, responder.id
    )
    initiator_distance_list.append(initiator_distance)

  initiator.ranging.stopRanging(session_handle)
  initiator_ranging_handler.waitAndGet("CLOSED")
  responder.ranging.stopRanging(session_handle)
  responder_ranging_handler.waitAndGet("CLOSED")

  return initiator_distance_list


def start_ble_rssi_test_and_get_data_pure_scan(
    scanner: android_device.AndroidDevice,
    advertiser: android_device.AndroidDevice,
    sample_count: int,
) -> list[int]:
  """Starts BLE RSSI testing in pure scanning mode and gets measured RSSI data.

  This method simulates the connection-less mechanism used in CTS Verifier.
  The Advertiser broadcasts with a unique name to bypass MAC randomization,
  while the Scanner filters for that specific name to collect RSSI values.

  Args:
    scanner: The device acting as the Scanner (receives RSSI).
    advertiser: The device acting as the Advertiser (transmits BLE packets).
    sample_count: The number of RSSI measurements to collect.

  Returns:
    A list of RSSI measurements (in dBm) collected by the Scanner.
  """
  advertiser.log.info("Starting BLE advertising...")

  unique_name = f"Target_{random.randint(1000, 9999)}"
  advertiser.mbs.btSetName(unique_name)
  advertiser_handler = advertiser.mbs.bleStartAdvertising(
      dict(ranging_params.ADVERTISE_SETTINGS), dict(ranging_params.ADVERTISE_DATA), None
  )

  try:
    advertiser_handler.waitAndGet("onStartSuccess", timeout=5)
    advertiser.log.info("Advertising started successfully.")
  except snippet_errors.ApiError as e:
    asserts.fail(f"Advertiser failed to start: {e}")

  scanner.log.info("Starting BLE scanning...")

  scan_filter = [{"DeviceName": unique_name}]
  scanner_handler = scanner.mbs.bleStartScan(
      scan_filter, dict(ranging_params.SCAN_SETTINGS)
  )

  rssi_list = []
  timeout = time.monotonic() + _WAIT_FOR_RSSI_TIMEOUT_SEC
  timeout_no_data = time.monotonic() + _NO_DATA_TIMEOUT_SEC

  logging.info("Starting Event Loop. Target: %d samples.", sample_count)

  try:
    while len(rssi_list) < sample_count and time.monotonic() < timeout:

      time.sleep(_BATCH_INTERVAL_SEC)
      try:
        events = scanner_handler.getAll("onScanResult")
      except Exception as e:
        logging.warning(f"RPC Error fetching batch events: {e}")
        continue

      if not events:
        if not rssi_list and time.monotonic() > timeout_no_data:
          logging.error(f"Early Stop: No RSSI data after {_NO_DATA_TIMEOUT_SEC}s.")
          break
        continue

      for event in events:
        res = event.data.get("result", {})
        if res.get("ScanRecord", {}).get("DeviceName") == unique_name:
          if (rssi := res.get("Rssi")) is not None:
            rssi_list.append(int(rssi))
            if len(rssi_list) >= sample_count:
              break

      logging.info(
          f"Collected {len(rssi_list)}/{sample_count} samples (Last batch: {len(events)})"
      )

    if len(rssi_list) < sample_count:
      logging.warning(
          "Timeout or Early Stop! Collected %d/%d samples.",
          len(rssi_list),
          sample_count,
      )

  finally:
    logging.info("Cleaning up BLE sessions...")
    with suppress(snippet_errors.ApiError):
      advertiser.mbs.bleStopAdvertising(advertiser_handler.callback_id)
    with suppress(snippet_errors.ApiError):
      scanner.mbs.bleStopScan(scanner_handler.callback_id)

  return rssi_list

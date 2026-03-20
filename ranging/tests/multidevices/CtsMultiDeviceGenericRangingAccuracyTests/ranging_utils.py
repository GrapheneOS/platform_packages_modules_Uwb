"""Utils for ranging tests."""

from collections.abc import Sequence
from contextlib import suppress
import dataclasses
import datetime
import logging
import random
import statistics
import time
import uuid

from mobly import asserts
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
_WIFI_RTT_PASS_RATE_THRESHOLD = 0.68
_WIFI_PD_PASS_RATE_THRESHOLD = 0.68

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


def is_wear_device(ad: android_device.AndroidDevice) -> bool:
  """Returns True if the device is a WearOS device."""
  return "watch" in ad.adb.getprop("ro.build.characteristics")


def skip_if_any_device_is_wear(
    devices: list[android_device.AndroidDevice],
) -> None:
  """Skips the test if any device is a WearOS device."""
  for device in devices:
    asserts.skip_if(is_wear_device(device), f"{device} is a WearOS device")


def log_ble_cs_distance_within_tolerance(
    real_distance_in_meters: int,
    measured_distance_data: list[float],
    reference_device_name: str,
) -> dict[str, str]:
  """Gets the Test metrics for BLE_CS.

  Args:
    real_distance_in_meters: The real distance in meters between two devices.
    measured_distance_data: A list of measured distance data.

  Returns:
    A dict containing the calculated metrics of the ranging results.
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
  pass_percentage = pass_rate * 100.0

  return {
      "initiator": {
          "reference_device": reference_device_name,
          "percentage_results_in_range": f"{pass_percentage:.2f}%",
          "results_in_range": pass_rate,
      }
  }


def log_uwb_distance_within_tolerance(
    real_distance_in_meters: int,
    measured_distance_datas: Sequence[list[float]],
    initiator_device_name: str,
    responder_device_name: str,
) -> dict[str, dict[str, str | int | float]]:
  """Gets the Test metrics for UWB.

  Args:
    real_distance_in_meters: The real distance in meters between two devices.
    measured_distance_datas: A list of measured distance data from initiator and
      responder.

  Returns:
    A dict containing the calculated metrics of the ranging results.
  """
  acceptable_range_limit = real_distance_in_meters * _UWB_ACCEPTABLE_RANGE_DEVIATION

  metrics_result = {}  # To store both initiator and responder data

  for i, measured_distance_data in enumerate(measured_distance_datas):
    measured_distance_range = 0.0
    measured_distance_median = 0.0

    if len(measured_distance_data) >= 2:
      quantiles = statistics.quantiles(measured_distance_data, n=40)
      measured_distance_range = quantiles[38] - quantiles[0]
      measured_distance_median = quantiles[19]
    elif len(measured_distance_data) == 1:
      measured_distance_median = measured_distance_data[0]

    is_range_passed = measured_distance_range <= acceptable_range_limit
    median_deviation = (
        abs(measured_distance_median - real_distance_in_meters)
        / real_distance_in_meters
    )
    is_median_passed = median_deviation <= _UWB_ACCEPTABLE_MEDIAN_DEVIATION
    is_passed = is_range_passed and is_median_passed
    status_str = "PASS" if is_passed else "FAIL"

    role_key = "initiator" if i == 0 else "responder"
    ref_key = responder_device_name if i == 0 else initiator_device_name

    metrics_result[role_key] = {
        "reference_device": ref_key,
        "real_distance_in_meters": real_distance_in_meters,
        "measured_distance_range": measured_distance_range,
        "measured_distance_median": measured_distance_median,
    }

    range_status_str = "OK" if is_range_passed else "FAIL"
    median_status_str = "OK" if is_median_passed else "FAIL"

    logging.info(
        "[UWB Metric (%s) @ %sm] - %s\n"
        "  Range: %.4f (Limit: <=%.4f) -> %s\n"
        "  Median: %.4f, Deviation: %.2f%% (Limit: <=%.2f%%) -> %s",
        role_key,
        real_distance_in_meters,
        status_str,
        measured_distance_range,
        acceptable_range_limit,
        range_status_str,
        measured_distance_median,
        median_deviation * 100,
        _UWB_ACCEPTABLE_MEDIAN_DEVIATION * 100,
        median_status_str,
    )

  return metrics_result


def log_wifi_rtt_distance_within_tolerance(
    real_distance_in_meters: int,
    measured_distance_datas: Sequence[list[float]],
    reference_device_name: str,
) -> dict[str, dict[str, str | float]]:
  """Gets the Test metrics for WIFI_RTT.

  Based on CDD 7.4.2.5. Wi-Fi Neighbor Awareness Networking (NAN):
  [7.4.2.5/H-1-1] Devices MUST report the range accurately to within +/-2 meters
  at 80 MHz bandwidth at the 68th percentile.

  Args:
    real_distance_in_meters: The real distance in meters between two devices.
    measured_distance_datas: A sequence containing lists of measured distance
      data. For Wi-Fi RTT, this will typically only contain the initiator's
      data, as only the initiator receives ranging results.

  Returns:
    A dict containing the calculated metrics of the ranging results.
  """
  # Per CDD [7.4.2.5/H-1-1], using the +/-2 meters threshold for 80MHz
  # bandwidth as the test requirement.
  acceptable_error_in_meters = 2.0

  for measured_distance_data in measured_distance_datas:
    asserts.assert_true(
        measured_distance_data, "Measured distance data cannot be empty."
    )
    num_measurements = len(measured_distance_data)

    filtered_data = [
        data
        for data in measured_distance_data
        if abs(data - real_distance_in_meters) <= acceptable_error_in_meters
    ]
    pass_rate = len(filtered_data) / num_measurements

    errors = [abs(data - real_distance_in_meters) for data in measured_distance_data]
    error_quantiles = statistics.quantiles(errors, n=100)
    error_at_68_percentile = error_quantiles[67]
    is_passed = pass_rate >= _WIFI_RTT_PASS_RATE_THRESHOLD
    status_str = "PASSED" if is_passed else "FAILED"
  logging.info(
      "Test WIFI_RTT status: %s Pass Rate=%.2f, Error at 68 percentile=%.4f",
      status_str,
      pass_rate,
      error_at_68_percentile,
  )

  return {
      "initiator": {
          "reference_device": reference_device_name,
          "pass_rate": pass_rate,
          "error_at_68_percentile": error_at_68_percentile,
      }
  }


def log_wifi_pd_distance_within_tolerance(
        real_distance_in_meters: int,
        measured_distance_datas: Sequence[list[float]],
        reference_device_name: str,
) -> dict[str, dict[str, str | float]]:
    """Gets the Test metrics for WIFI_PD.

    Based on CDD 7.4.2.10. Wi-Fi Proximity Detection (Peer-to-Peer Proximity Ranging):
    [7.4.2.10/C-1-6] Devices MUST report the range accurately to within +/-2 meters
    at 80 MHz bandwidth at the 68th percentile.

    Args:
      real_distance_in_meters: The real distance in meters between two devices.
      measured_distance_datas: A sequence containing lists of measured distance
        data. For Wi-Fi PD, this will typically only contain the initiator's
        data, as only the initiator receives ranging results.

    Returns:
      A dict containing the calculated metrics of the ranging results.
    """
    # Per CDD [7.4.2.5/H-1-1], using the +/-2 meters threshold for 80MHz
    # bandwidth as the test requirement.
    acceptable_error_in_meters = 2.0

    for measured_distance_data in measured_distance_datas:
        asserts.assert_true(
            measured_distance_data, "Measured distance data cannot be empty."
        )
        num_measurements = len(measured_distance_data)

        filtered_data = [
            data
            for data in measured_distance_data
            if abs(data - real_distance_in_meters) <= acceptable_error_in_meters
        ]
        pass_rate = len(filtered_data) / num_measurements

        errors = [abs(data - real_distance_in_meters) for data in measured_distance_data]
        error_quantiles = statistics.quantiles(errors, n=100)
        error_at_68_percentile = error_quantiles[67]
        is_passed = pass_rate >= _WIFI_PD_PASS_RATE_THRESHOLD
        status_str = "PASSED" if is_passed else "FAILED"
    logging.info(
        "Test WIFI_PD status: %s Pass Rate=%.2f, Error at 68 percentile=%.4f",
        status_str,
        pass_rate,
        error_at_68_percentile,
    )

    return {
        "initiator": {
            "reference_device": reference_device_name,
            "pass_rate": pass_rate,
            "error_at_68_percentile": error_at_68_percentile,
        }
    }


def log_ble_rssi_precision_within_tolerance(
    acceptable_spread_dbm: int,
    reference_device_name: str,
    rssi_data: list[int],
) -> dict[str, str | float]:
  """Gets the Test metrics for BLE RSSI.

  Based on CDD 7.4.3. Bluetooth:
  [C-10-1] MUST have RSSI measurements be within +/-9 dBm for 95% of the
  measurements at 1m distance.
  This implies the spread (max - min) of the middle 95% of data must be <= 18 dBm.

  Args:
    acceptable_spread_dbm: The maximum allowed spread in dBm (e.g., 18).
    reference_device_name: The name or serial number of the reference device
        (responder) for logging purposes.
    rssi_data: A list of measured RSSI values (in dBm).

  Returns:
    A dict containing the calculated metrics of the ranging results.
  """
  # Per CDD [C-10-1], +/- 9dBm means the total spread allowed is 18 dBm.

  asserts.assert_not_equal(len(rssi_data), 0, "Measured RSSI data is empty.")
  sorted_data = sorted(rssi_data)
  count = len(sorted_data)

  idx_min = int(count * 0.025)
  idx_max = min(int(count * 0.975), count - 1)

  spread_95th = sorted_data[idx_max] - sorted_data[idx_min]
  median_rssi = statistics.median(sorted_data)

  log_msg = (
      f"[BLE_RSSI Metric] Spread(95%)={spread_95th} dBm, Median={median_rssi:.1f} dBm"
  )

  if spread_95th <= acceptable_spread_dbm:
    logging.info(f"{log_msg} - PASS")
  else:
    logging.error(f"{log_msg} - FAIL")

  return {
      "reference_device": reference_device_name,
      "rssi_range95_percentile": spread_95th,
  }


def log_ble_rx_tx_offset_precision(
    tx_rssi_data: list[int],
    rx_rssi_data: list[int],
    reference_device: str,
    target_dbm: int,
    tolerance: int,
) -> dict[str, str | float]:
  """Verifies that the median of both Tx and Rx RSSI data is within target +/- tolerance.

  Args:
    tx_rssi_data: List of measured RSSI values for Tx test (Ref device measuring DUT).
    rx_rssi_data: List of measured RSSI values for Rx test (DUT measuring Ref device).
    reference_device_name: The name or serial number of the reference device
        (responder) for logging purposes.
    target_dbm: The expected median value (e.g., -55).
    tolerance: The allowed deviation (e.g., 10).

  Returns:
    A dict containing the calculated metrics of the ranging results.
  """
  asserts.assert_not_equal(
      len(tx_rssi_data), 0, "Tx Measured RSSI data is empty, skipping calculation."
  )
  asserts.assert_not_equal(
      len(rx_rssi_data), 0, "Rx Measured RSSI data is empty, skipping calculation."
  )

  min_limit = target_dbm - tolerance
  max_limit = target_dbm + tolerance

  def verify_single_dataset(data: list[int], label: str, role_key: str) -> float:
    sorted_data = sorted(data)
    count = len(sorted_data)
    median = statistics.median(sorted_data)

    log_msg = (
        f"[{label}] Count={count}, Median={median:.1f} dBm. "
        f"Target Range=[{min_limit}, {max_limit}] dBm."
    )

    if min_limit <= median <= max_limit:
      logging.info("%s - PASS", log_msg)
    else:
      logging.error("%s - FAIL", log_msg)
    return median

  tx_rssi_median = verify_single_dataset(
      tx_rssi_data, "Tx Test/DUT Power", "tx_measurement"
  )
  rx_rssi_median = verify_single_dataset(
      rx_rssi_data, "Rx Test/DUT Sensitivity", "rx_measurement"
  )

  return {
      "reference_device": reference_device,
      "rssi_median_dut": tx_rssi_median,
      "rssi_median_ref": rx_rssi_median,
  }


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


def start_wifi_ranging_and_get_distance_data(
    initiator: android_device.AndroidDevice,
    responder: android_device.AndroidDevice,
    technology: ranging_params.RangingTechnology,
    initiator_preference: ranging_params.RangingPreference,
    responder_preference: ranging_params.RangingPreference,
    ranging_measure_count: int = _DEFAULT_RANGING_MEASURE_COUNTS,
) -> list[float]:
  """Starts Wi-Fi RTT or PD ranging and gets all measured distance data from initiator.

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

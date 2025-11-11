"""Utils for ranging tests."""

from collections.abc import Sequence
import dataclasses
import datetime
import logging
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
        f'{device} {technology.name} not supported',
    )


def verify_ble_cs_distance_within_tolerance(
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
  acceptable_range = (
      real_distance_in_meters * _BLE_CS_ACCEPTABLE_RANGE_DEVIATION
  )
  filtered_data = [
      data
      for data in measured_distance_data
      if abs(data - real_distance_in_meters) <= acceptable_range
  ]
  num_measurements = len(measured_distance_data)
  num_passed = len(filtered_data)
  pass_rate = 0.0 if num_measurements == 0 else (num_passed / num_measurements)

  acceptable_threshold_count = num_measurements * _BLE_CS_PASS_RATE_THRESHOLD

  asserts.assert_greater_equal(
      num_passed,
      acceptable_threshold_count,
      f'in {real_distance_in_meters}m, pass rate {pass_rate:0.2%} is less than'
      f' acceptable threshold ({acceptable_threshold_count});',
  )


def verify_uwb_distance_within_tolerance(
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

    uwb_ranging_test_metrics.append({
        'real_distance_in_meters': real_distance_in_meters,
        'measured_distance_range': measured_distance_range,
        'measured_distance_median': measured_distance_median,
    })

  for test_metric in uwb_ranging_test_metrics:
    asserts.assert_less_equal(
        test_metric['measured_distance_range'],
        acceptable_range,
        f'In {real_distance_in_meters}m, measured distance range'
        f' {test_metric["measured_distance_range"]} is larger than'
        f' acceptable value {acceptable_range}',
    )

    deviation = (
        abs(test_metric['measured_distance_median'] - real_distance_in_meters)
        / real_distance_in_meters
    )
    asserts.assert_less_equal(
        deviation,
        _UWB_ACCEPTABLE_MEDIAN_DEVIATION,
        f'In {real_distance_in_meters}m, measured distance median deviation'
        f' {deviation} is larger than acceptable value'
        f' {_UWB_ACCEPTABLE_MEDIAN_DEVIATION}',
    )


def verify_wifi_rtt_distance_within_tolerance(
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
        measured_distance_data, 'Measured distance data cannot be empty.'
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
    errors = [
        abs(data - real_distance_in_meters) for data in measured_distance_data
    ]
    error_quantiles = statistics.quantiles(errors, n=100)
    error_at_68_percentile = error_quantiles[67]

    distance_quantiles = statistics.quantiles(measured_distance_data, n=100)
    measured_distance_median = distance_quantiles[49]

    wifi_rtt_ranging_test_metrics.append({
        'real_distance_in_meters': real_distance_in_meters,
        'pass_rate': pass_rate,
        'measured_distance_median': measured_distance_median,
        'error_at68percentile': error_at_68_percentile,
    })

  for i, test_metric in enumerate(wifi_rtt_ranging_test_metrics):
    role = 'initiator' if i == 0 else 'responder'
    asserts.assert_greater_equal(
        test_metric['pass_rate'],
        pass_rate_threshold,
        f'For {role} in {real_distance_in_meters}m, the pass rate'
        f' ({test_metric["pass_rate"]:.2%}) is less than the acceptable'
        f' threshold ({pass_rate_threshold:.0%}).',
    )


def get_ranging_distance(
    ranging_handler: callback_handler_v2.CallbackHandlerV2,
    technology: ranging_params.RangingTechnology,
    peer_id: str,
) -> float:
  """Gets one distance data from the ranging event."""
  try:
    event = ranging_handler.waitForEvent(
        'DATA',
        lambda e: e.data['technology'] == technology
                  and e.data['peer_id'] == peer_id,
    )
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f'{ranging_handler._device} Failed'  # pylint: disable=protected-access
        f' to find peer {peer_id} with technology {technology.name}'
    )
  else:
    return event.data['distance']


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
    initiator_ranging_handler.waitAndGet('OPENED')
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f'{initiator} Failed to open ranging session with preference'
        f' {initiator_preference}'
    )

  if responder_preference is not None:
    responder_ranging_handler = responder.ranging.startRanging(
        session_handle, dataclasses.asdict(responder_preference)
    )
    try:
      responder_ranging_handler.waitAndGet('OPENED')
    except snippet_errors.CallbackHandlerTimeoutError:
      asserts.fail(
          f'{responder} Failed to open ranging session with preference'
          f' {responder_preference}'
      )
  else:
    responder_ranging_handler = None

  initiator_distance_list = []
  responder_distance_list = []
  ranging_end_time = (
      time.monotonic() + _WAIT_FOR_RANGING_DATA_TIMEOUT.total_seconds()
  )
  for i in range(ranging_measure_count):
    if time.monotonic() >= ranging_end_time:
      logging.info(
          'Collecting data reached to the timeout. Expected %d data points, but'
          ' only got %d.',
          ranging_measure_count,
          len(initiator_distance_list),
      )
      break

    logging.debug(f'Ranging data collection: Iteration {i+1}/{ranging_measure_count}')

    if responder_ranging_handler is None:
      initiator_distance = get_ranging_distance(
          initiator_ranging_handler, technology, responder.id
      )
      logging.debug(f'  [Iter {i+1}] Got Initiator distance: {initiator_distance}')
    else:
      initiator_distance, responder_distance = mobly_utils.concurrent_exec(
          get_ranging_distance,
          param_list=[
              [initiator_ranging_handler, technology, responder.id],
              [responder_ranging_handler, technology, initiator.id],
          ],
          raise_on_exception=True,
      )
      logging.debug(f'  [Iter {i+1}] Got Initiator distance: {initiator_distance}')
      logging.debug(f'  [Iter {i+1}] Got Responder distance: {responder_distance}')
      responder_distance_list.append(responder_distance)

    initiator_distance_list.append(initiator_distance)
    time.sleep(0.02)

  initiator.ranging.stopRanging(session_handle)
  initiator_ranging_handler.waitAndGet('CLOSED')
  if responder_ranging_handler is not None:
    responder.ranging.stopRanging(session_handle)
    responder_ranging_handler.waitAndGet('CLOSED')

  return initiator_distance_list, responder_distance_list  # pytype: disable=bad-return-type


def start_cs_ranging_and_get_distance_data(
    initiator: android_device.AndroidDevice,
    responder: android_device.AndroidDevice,
    technology: ranging_params.RangingTechnology,
    initiator_preference: ranging_params.RangingPreference,
    responder_preference: ranging_params.RangingPreference,
    ranging_measure_count: int = _DEFAULT_RANGING_MEASURE_COUNTS,
) -> list[float]:
  """Starts CS ranging and gets all measured distance data from initiator.

  For BLE CS, only the initiator device receives ranging results.

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

  try:
    initiator_ranging_handler.waitAndGet('OPENED')
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f'{initiator} Failed to open ranging session with preference'
        f' {initiator_preference}'
    )

  responder_ranging_handler = responder.ranging.startRanging(
      session_handle, dataclasses.asdict(responder_preference)
  )

  try:
    responder_ranging_handler.waitAndGet('OPENED')
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f'{responder} Failed to open ranging session with preference'
        f' {responder_preference}'
    )
  initiator_distance_list = []
  ranging_end_time = (
      time.monotonic() + _WAIT_FOR_RANGING_DATA_TIMEOUT.total_seconds()
  )

  for _ in range(ranging_measure_count):
    if time.monotonic() >= ranging_end_time:
      logging.info(
          'Collecting data reached to the timeout. Expected %d data points, but'
          ' only got %d.',
          ranging_measure_count,
          len(initiator_distance_list),
      )
      break
    initiator_distance = get_ranging_distance(
        initiator_ranging_handler, technology, responder.id
    )
    initiator_distance_list.append(initiator_distance)

  initiator.ranging.stopRanging(session_handle)
  initiator_ranging_handler.waitAndGet('CLOSED')
  responder.ranging.stopRanging(session_handle)
  responder_ranging_handler.waitAndGet('CLOSED')

  return initiator_distance_list


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
    initiator_ranging_handler.waitAndGet('OPENED')
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f'{initiator} Failed to open ranging session with preference'
        f' {initiator_preference}'
    )

  try:
    responder_ranging_handler.waitAndGet('OPENED')
  except snippet_errors.CallbackHandlerTimeoutError:
    asserts.fail(
        f'{responder} Failed to open ranging session with preference'
        f' {responder_preference}'
    )

  initiator_distance_list = []
  ranging_end_time = (
      time.monotonic() + _WAIT_FOR_RANGING_DATA_TIMEOUT.total_seconds()
  )
  for _ in range(ranging_measure_count):
    if time.monotonic() >= ranging_end_time:
      logging.info(
          'Collecting data reached to the timeout. Expected %d data points, but'
          ' only got %d.',
          ranging_measure_count,
          len(initiator_distance_list),
      )
      break
    initiator_distance = get_ranging_distance(
        initiator_ranging_handler, technology, responder.id
    )
    initiator_distance_list.append(initiator_distance)

  initiator.ranging.stopRanging(session_handle)
  initiator_ranging_handler.waitAndGet('CLOSED')
  responder.ranging.stopRanging(session_handle)
  responder_ranging_handler.waitAndGet('CLOSED')

  return initiator_distance_list

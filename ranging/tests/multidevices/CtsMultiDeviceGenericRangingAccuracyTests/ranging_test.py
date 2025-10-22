"""Mobly test for ranging.

The tests in this file are designed to be run with devices placed 1 meter apart.
"""

from android.platform.test.annotations import CddTest
from mobly import test_runner
from mobly import utils

from . import bluetooth_utils
from . import ranging_accuracy_base_test
from . import ranging_utils
import lib.params as ranging_params
import lib.uwb as uwb_params

# The count of ranging measurements for each technology.
_DISTANCE_IN_METERS = 1
_NUMBER_OF_BLE_CS_TEST_SAMPLES = 100
_NUMBER_OF_UWB_TEST_SAMPLES = 1000
_NUMBER_OF_WIFI_RTT_TEST_SAMPLES = 100


class RangingTest(ranging_accuracy_base_test.RangingBaseTestClass):
  """Tests ranging accuracy for different technologies between two devices."""

  @CddTest(requirements = ["7.4.9/C-1-3", "7.4.9/C-1-4"])
  def test_uwb_ranging(self) -> None:
    """Test UWB ranging at 1 meter between the devices.

    Test Preconditions:
      * Two Android devices that support UWB.
      * The devices are placed 1 meter apart.

    Test Steps:
      1. Set up UWB initiator and responder preferences for a unicast session.
      2. Start the ranging session on both devices.
      3. Collect 1000 ranging measurements (_NUMBER_OF_UWB_TEST_SAMPLES).
      4. Stop the ranging session.
      5. Verify that the median and 95% inter-percentile range of the
         measured distances are within the acceptable tolerance for 1m.

    Expected Results:
      * The ranging session starts successfully.
      * The measured distance metrics (median deviation, range) pass
        the accuracy and consistency checks defined in
        ranging_utils.verify_uwb_distance_within_tolerance.
    """
    technology = ranging_params.RangingTechnology.UWB
    ranging_utils.skip_if_technology_not_supported(
        [self.initiator, self.responder], technology
    )

    uwb_session_id = 5
    ranging_measure_count = _NUMBER_OF_UWB_TEST_SAMPLES
    initiator_preference = ranging_params.RangingPreference(
        device_role=ranging_params.DeviceRole.INITIATOR,
        ranging_params=ranging_params.RawInitiatorRangingParams(
            peer_params=[
                ranging_params.DeviceParams(
                    peer_id=self.responder.id,
                    uwb_params=ranging_params.UwbRangingParams(
                        session_id=uwb_session_id,
                        config_id=uwb_params.ConfigId.UNICAST_DS_TWR,
                        device_address=self.initiator.uwb_address,
                        peer_address=self.responder.uwb_address,
                    ),
                )
            ],
        ),
        sensor_fusion_params=ranging_params.SensorFusionParams(
            is_sensor_fusion_enabled=False
        ),
    )
    responder_preference = ranging_params.RangingPreference(
        device_role=ranging_params.DeviceRole.RESPONDER,
        ranging_params=ranging_params.RawResponderRangingParams(
            peer_params=ranging_params.DeviceParams(
                peer_id=self.initiator.id,
                uwb_params=ranging_params.UwbRangingParams(
                    session_id=uwb_session_id,
                    config_id=uwb_params.ConfigId.UNICAST_DS_TWR,
                    device_address=self.responder.uwb_address,
                    peer_address=self.initiator.uwb_address,
                ),
            ),
        ),
        sensor_fusion_params=ranging_params.SensorFusionParams(
            is_sensor_fusion_enabled=False
        ),
    )

    initiator_distances, responder_distances = (
        ranging_utils.start_ranging_and_get_distance_data(
            self.initiator,
            self.responder,
            technology,
            initiator_preference,
            responder_preference,
            ranging_measure_count,
        )
    )
    ranging_utils.verify_uwb_distance_within_tolerance(
        real_distance_in_meters=_DISTANCE_IN_METERS,
        measured_distance_datas=[initiator_distances, responder_distances],
        log_path=self.current_test_info.output_path,
    )

  # TODO(b/454692647): Keep @retry commented out. It reports retry_# cases
  # to CTS-V, which blocks overriding failures.
  # @retry(max_count=2)
  @CddTest(requirements = "7.4.3/C-4-2")
  def test_channel_sounding_ranging(self) -> None:
    """Test Channel Sounding ranging at 1 meter between the devices.

    Test Preconditions:
      * Two Android devices that support BLE Channel Sounding (BLE_CS).
      * The devices are placed 1 meter apart.

    Test Steps:
      1. Perform BLE bonding between the two devices.
      2. Set up BLE_CS initiator and responder preferences (with sensor fusion
         enabled).
      3. Start the ranging session on both devices.
      4. Collect 100 ranging measurements (_NUMBER_OF_BLE_CS_TEST_SAMPLES)
         from the initiator. (Note: Only initiator receives BLE CS results).
      5. Stop the ranging session.
      6. Verify that at least 90% of the measurements are within the
         acceptable tolerance for 1m.
      7. Perform BLE unbond to clean up the device state.

    Expected Results:
      * The BLE bonding and ranging session starts successfully.
      * The measured distance metrics pass the accuracy checks defined in
        ranging_utils.verify_ble_cs_distance_within_tolerance.
      * Devices are successfully unbonded.
    """
    technology = ranging_params.RangingTechnology.BLE_CS
    ranging_utils.skip_if_technology_not_supported(
        [self.initiator, self.responder], technology
    )

    self.initiator.bt_address, self.responder.bt_address = (
        bluetooth_utils.ble_bond(self.initiator, self.responder)
    )
    try:
      ranging_measure_count = _NUMBER_OF_BLE_CS_TEST_SAMPLES
      initiator_preference = ranging_params.RangingPreference(
          device_role=ranging_params.DeviceRole.INITIATOR,
          ranging_params=ranging_params.RawInitiatorRangingParams(
              peer_params=[
                  ranging_params.DeviceParams(
                      peer_id=self.responder.id,
                      cs_params=ranging_params.CsRangingParams(
                          peer_address=self.responder.bt_address,
                      ),
                  )
              ],
          ),
          sensor_fusion_params=ranging_params.SensorFusionParams(
              is_sensor_fusion_enabled=False
          ),
      )

      initiator_distances, _ = (
          ranging_utils.start_ranging_and_get_distance_data(
              self.initiator,
              self.responder,
              technology,
              initiator_preference,
              None,
              ranging_measure_count,
          )
      )
      ranging_utils.verify_ble_cs_distance_within_tolerance(
          _DISTANCE_IN_METERS,
          initiator_distances,
          log_path=self.current_test_info.output_path,
      )
    finally:
      bluetooth_utils.ble_unbond(
          self.initiator,
          self.responder,
          self.initiator.bt_address,
          self.responder.bt_address,
      )

  @CddTest(requirements = ["7.4.2.5/H-1-1"])
  def test_wifi_rtt_ranging(self) -> None:
    """Test Wi-Fi RTT ranging at 1 meter between the devices.

    Test Preconditions:
      * Two Android devices that support Wi-Fi RTT.
      * The devices are placed 1 meter apart.

    Test Steps:
      1. Enable Wi-Fi on both devices.
      2. Generate a random service name for the RTT session.
      3. Set up Wi-Fi RTT initiator and responder preferences.
      4. Start the ranging session on both devices.
      5. Collect 100 ranging measurements (_NUMBER_OF_WIFI_RTT_TEST_SAMPLES)
         from the initiator. (Note: Only initiator receives Wi-Fi RTT results).
      6. Stop the ranging session.
      7. Verify that the 68th percentile of measurements are within the
         acceptable tolerance (+/- 2m) as required by CDD.

    Expected Results:
      * The ranging session starts successfully.
      * The measured distance metrics pass the accuracy checks defined in
        ranging_utils.verify_wifi_rtt_distance_within_tolerance.
    """

    technology = ranging_params.RangingTechnology.WIFI_RTT
    ranging_utils.skip_if_technology_not_supported(
        [self.initiator, self.responder], technology
    )

    test_service_name = utils.rand_ascii_str(8)
    ranging_measure_count = _NUMBER_OF_WIFI_RTT_TEST_SAMPLES

    utils.concurrent_exec(
        lambda device: device.mbs.wifiEnable(),
        [[self.initiator], [self.responder]],
    )

    initiator_preference = ranging_params.RangingPreference(
        device_role=ranging_params.DeviceRole.INITIATOR,
        ranging_params=ranging_params.RawInitiatorRangingParams(
            peer_params=[
                ranging_params.DeviceParams(
                    peer_id=self.responder.id,
                    rtt_params=ranging_params.RttRangingParams(
                        service_name=test_service_name,
                    ),
                )
            ],
        ),
        sensor_fusion_params=ranging_params.SensorFusionParams(
            is_sensor_fusion_enabled=False
        ),
    )

    responder_preference = ranging_params.RangingPreference(
        device_role=ranging_params.DeviceRole.RESPONDER,
        ranging_params=ranging_params.RawResponderRangingParams(
            peer_params=ranging_params.DeviceParams(
                peer_id=self.initiator.id,
                rtt_params=ranging_params.RttRangingParams(
                    service_name=test_service_name,
                ),
            ),
        ),
        sensor_fusion_params=ranging_params.SensorFusionParams(
            is_sensor_fusion_enabled=False
        ),
        enable_range_data_notifications=False,
    )

    initiator_distances = ranging_utils.start_rtt_ranging_and_get_distance_data(
        self.initiator,
        self.responder,
        technology,
        initiator_preference,
        responder_preference,
        ranging_measure_count,
    )
    ranging_utils.verify_wifi_rtt_distance_within_tolerance(
        _DISTANCE_IN_METERS,
        [initiator_distances],
        log_path=self.current_test_info.output_path,
    )


if __name__ == '__main__':
  # This replicates the behavior of the original test suite file,
  # which defined which test classes to run.
  test_runner.main()
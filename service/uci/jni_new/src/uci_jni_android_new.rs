// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Main implementation of uci_jni_android_new.

use std::collections::HashMap;
use std::convert::TryInto;
use std::sync::{Arc, Once};

use jni::objects::{GlobalRef, JMethodID, JObject, JValue};
use jni::signature::TypeSignature;
use jni::{AttachGuard, JavaVM};
use uwb_core::error::{Error as UwbCoreError, Result as UwbCoreResult};
use uwb_core::uci::uci_manager_sync::{NotificationManager, NotificationManagerBuilder};
use uwb_core::uci::{CoreNotification, RangingMeasurements, SessionNotification, SessionRangeData};
use uwb_uci_packets::{
    ControleeStatus, ExtendedAddressTwoWayRangingMeasurement, MacAddressIndicator, ReasonCode,
    SessionState, ShortAddressTwoWayRangingMeasurement, StatusCode,
};

const UWB_RANGING_DATA_CLASS: &str = "com/android/server/uwb/data/UwbRangingData";
const UWB_TWO_WAY_MEASUREMENT_CLASS: &str = "com/android/server/uwb/data/UwbTwoWayMeasurement";
const MULTICAST_LIST_UPDATE_STATUS_CLASS: &str =
    "com/android/server/uwb/data/UwbMulticastListUpdateStatus";
const SHORT_MAC_ADDRESS_LEN: i32 = 2;
const EXTENDED_MAC_ADDRESS_LEN: i32 = 8;

enum MacAddress {
    Short(u16),
    Extended(u64),
}
impl MacAddress {
    fn into_ne_bytes(self) -> Vec<u8> {
        match self {
            MacAddress::Short(val) => val.to_ne_bytes().into(),
            MacAddress::Extended(val) => val.to_ne_bytes().into(),
        }
    }
}
struct TwoWayRangingMeasurement {
    mac_address: MacAddress,
    status: StatusCode,
    nlos: u8,
    distance: u16,
    aoa_azimuth: u16,
    aoa_azimuth_fom: u8,
    aoa_elevation: u16,
    aoa_elevation_fom: u8,
    aoa_destination_azimuth: u16,
    aoa_destination_azimuth_fom: u8,
    aoa_destination_elevation: u16,
    aoa_destination_elevation_fom: u8,
    slot_index: u8,
    rssi: u8,
}

impl From<ShortAddressTwoWayRangingMeasurement> for TwoWayRangingMeasurement {
    fn from(measurement: ShortAddressTwoWayRangingMeasurement) -> Self {
        TwoWayRangingMeasurement {
            mac_address: MacAddress::Short(measurement.mac_address),
            status: (measurement.status),
            nlos: (measurement.nlos),
            distance: (measurement.distance),
            aoa_azimuth: (measurement.aoa_azimuth),
            aoa_azimuth_fom: (measurement.aoa_azimuth_fom),
            aoa_elevation: (measurement.aoa_elevation),
            aoa_elevation_fom: (measurement.aoa_elevation_fom),
            aoa_destination_azimuth: (measurement.aoa_destination_azimuth),
            aoa_destination_azimuth_fom: (measurement.aoa_destination_azimuth_fom),
            aoa_destination_elevation: (measurement.aoa_destination_elevation),
            aoa_destination_elevation_fom: (measurement.aoa_destination_elevation_fom),
            slot_index: (measurement.slot_index),
            rssi: (measurement.rssi),
        }
    }
}

impl From<ExtendedAddressTwoWayRangingMeasurement> for TwoWayRangingMeasurement {
    fn from(measurement: ExtendedAddressTwoWayRangingMeasurement) -> Self {
        TwoWayRangingMeasurement {
            mac_address: MacAddress::Extended(measurement.mac_address),
            status: (measurement.status),
            nlos: (measurement.nlos),
            distance: (measurement.distance),
            aoa_azimuth: (measurement.aoa_azimuth),
            aoa_azimuth_fom: (measurement.aoa_azimuth_fom),
            aoa_elevation: (measurement.aoa_elevation),
            aoa_elevation_fom: (measurement.aoa_elevation_fom),
            aoa_destination_azimuth: (measurement.aoa_destination_azimuth),
            aoa_destination_azimuth_fom: (measurement.aoa_destination_azimuth_fom),
            aoa_destination_elevation: (measurement.aoa_destination_elevation),
            aoa_destination_elevation_fom: (measurement.aoa_destination_elevation_fom),
            slot_index: (measurement.slot_index),
            rssi: (measurement.rssi),
        }
    }
}

#[allow(dead_code)]
/// takes a JavaVM to a static reference.
///
/// JavaVM is shared as multiple JavaVM within a single process is not allowed
/// per [JNI spec](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html)
/// The unique JavaVM need to be shared over (potentially) different threads.
fn to_static_ref(jvm: JavaVM) -> &'static Arc<JavaVM> {
    static mut JVM: Option<Arc<JavaVM>> = None;
    static INIT: Once = Once::new();
    // Safety: follows [this pattern](https://doc.rust-lang.org/std/sync/struct.Once.html).
    // Modification to static mut is nested inside call_once.
    unsafe {
        INIT.call_once(|| {
            JVM = Some(Arc::new(jvm));
        });
        JVM.as_ref().unwrap()
    }
}

pub(crate) struct NotificationManagerAndroid {
    // 'static annotation is needed as env is 'sent' by tokio::task::spawn_local.
    env: AttachGuard<'static>,
    callback_obj: GlobalRef,
    // *_jmethod_id are cached for faster callback using call_method_unchecked
    jmethod_id_map: HashMap<String, JMethodID<'static>>,
}

impl NotificationManagerAndroid {
    fn cached_jni_call(&mut self, name: &str, sig: &str, args: &[JValue]) -> UwbCoreResult<()> {
        let type_signature =
            TypeSignature::from_str(sig).map_err(|_| UwbCoreError::BadParameters)?;
        if type_signature.args.len() != args.len() {
            return Err(UwbCoreError::BadParameters);
        }
        let name_signature = name.to_owned() + sig;
        if self.jmethod_id_map.get(&name_signature).is_none() {
            self.jmethod_id_map.insert(
                name_signature.clone(),
                self.env
                    .get_method_id(self.callback_obj.as_obj(), name, sig)
                    .map_err(|_| UwbCoreError::BadParameters)?,
            );
        }
        match self.env.call_method_unchecked(
            self.callback_obj.as_obj(),
            self.jmethod_id_map.get(&name_signature).unwrap().to_owned(),
            type_signature.ret,
            args,
        ) {
            Ok(_) => Ok(()),
            Err(_) => Err(UwbCoreError::Unknown),
        }
    }

    fn on_session_status_notification(
        &mut self,
        session_id: u32,
        session_state: SessionState,
        reason_code: ReasonCode,
    ) -> UwbCoreResult<()> {
        self.cached_jni_call(
            "onSessionStatusNotificationReceived",
            "(JII)V",
            &[
                JValue::Long(session_id as i64),
                JValue::Int(session_state as i32),
                JValue::Int(reason_code as i32),
            ],
        )
    }

    fn on_session_update_multicast_notification(
        &mut self,
        session_id: u32,
        remaining_multicast_list_size: usize,
        status_list: Vec<ControleeStatus>,
    ) -> UwbCoreResult<()> {
        let remaining_multicast_list_size: i32 =
            remaining_multicast_list_size.try_into().map_err(|_| UwbCoreError::BadParameters)?;
        let count: i32 = status_list.len().try_into().map_err(|_| UwbCoreError::BadParameters)?;
        let mac_address_jintarray =
            self.env.new_int_array(count).map_err(|_| UwbCoreError::Unknown)?;
        let subsession_id_jlongarray =
            self.env.new_long_array(count).map_err(|_| UwbCoreError::Unknown)?;
        let status_jintarray = self.env.new_int_array(count).map_err(|_| UwbCoreError::Unknown)?;
        let (mac_address_vec, (subsession_id_vec, status_vec)): (Vec<_>, (Vec<_>, Vec<_>)) =
            status_list
                .into_iter()
                .map(|cs| (cs.mac_address as i32, (cs.subsession_id as i64, cs.status as i32)))
                .unzip();
        self.env
            .set_int_array_region(mac_address_jintarray, 0, &mac_address_vec)
            .map_err(|_| UwbCoreError::Unknown)?;
        self.env
            .set_long_array_region(subsession_id_jlongarray, 0, &subsession_id_vec)
            .map_err(|_| UwbCoreError::Unknown)?;
        self.env
            .set_int_array_region(status_jintarray, 0, &status_vec)
            .map_err(|_| UwbCoreError::Unknown)?;
        let multicast_update_jclass = self
            .env
            .find_class(MULTICAST_LIST_UPDATE_STATUS_CLASS)
            .map_err(|_| UwbCoreError::Unknown)?;
        let method_sig = "(L".to_owned() + MULTICAST_LIST_UPDATE_STATUS_CLASS + ";)V";
        let multicast_update_jobject = self
            .env
            .new_object(
                multicast_update_jclass,
                "(JII[I[J[I)V",
                &[
                    JValue::Long(session_id as i64),
                    JValue::Int(remaining_multicast_list_size),
                    JValue::Int(count),
                    JValue::Object(JObject::from(mac_address_jintarray)),
                    JValue::Object(JObject::from(subsession_id_jlongarray)),
                    JValue::Object(JObject::from(status_jintarray)),
                ],
            )
            .map_err(|_| UwbCoreError::Unknown)?;
        self.cached_jni_call(
            "onMulticastListUpdateNotificationReceived",
            &method_sig,
            &[JValue::Object(multicast_update_jobject)],
        )
    }

    fn on_session_range_data_notification(
        &mut self,
        range_data: SessionRangeData,
    ) -> UwbCoreResult<()> {
        let measurement_jclass = self
            .env
            .find_class(UWB_TWO_WAY_MEASUREMENT_CLASS)
            .map_err(|_| UwbCoreError::Unknown)?;
        let bytearray_len: i32 = match &range_data.ranging_measurements {
            uwb_core::uci::RangingMeasurements::Short(_) => SHORT_MAC_ADDRESS_LEN,
            uwb_core::uci::RangingMeasurements::Extended(_) => EXTENDED_MAC_ADDRESS_LEN,
        };
        let address_jbytearray =
            self.env.new_byte_array(bytearray_len).map_err(|_| UwbCoreError::Unknown)?;
        let zero_initiated_measurement_jobject = self
            .env
            .new_object(
                measurement_jclass,
                "([BIIIIIIIIIIIII)V",
                &[
                    JValue::Object(JObject::from(address_jbytearray)),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Int(0),
                ],
            )
            .map_err(|_| UwbCoreError::Unknown)?;
        let measurement_count: i32 = match &range_data.ranging_measurements {
            RangingMeasurements::Short(v) => v.len(),
            RangingMeasurements::Extended(v) => v.len(),
        }
        .try_into()
        .map_err(|_| UwbCoreError::BadParameters)?;
        let mac_indicator = match &range_data.ranging_measurements {
            RangingMeasurements::Short(_) => MacAddressIndicator::ShortAddress,
            RangingMeasurements::Extended(_) => MacAddressIndicator::ExtendedAddress,
        };
        let measurements_jobjectarray = self
            .env
            .new_object_array(
                measurement_count,
                measurement_jclass,
                zero_initiated_measurement_jobject,
            )
            .map_err(|_| UwbCoreError::Unknown)?;
        for (i, measurement) in match range_data.ranging_measurements {
            RangingMeasurements::Short(v) => {
                v.into_iter().map(TwoWayRangingMeasurement::from).collect::<Vec<_>>()
            }
            RangingMeasurements::Extended(v) => {
                v.into_iter().map(TwoWayRangingMeasurement::from).collect::<Vec<_>>()
            }
        }
        .into_iter()
        .enumerate()
        {
            // cast to i8 as java do not support unsigned:
            let mac_address_i8 = measurement
                .mac_address
                .into_ne_bytes()
                .iter()
                .map(|b| b.to_owned() as i8)
                .collect::<Vec<_>>();
            let mac_address_jbytearray = self
                .env
                .new_byte_array(mac_address_i8.len() as i32)
                .map_err(|_| UwbCoreError::Unknown)?;
            self.env
                .set_byte_array_region(mac_address_jbytearray, 0, &mac_address_i8)
                .map_err(|_| UwbCoreError::Unknown)?;
            // casting as i32 is fine since it is wider than actual integer type.
            let measurement_jobject = self
                .env
                .new_object(
                    measurement_jclass,
                    "([BIIIIIIIIIIIII)V",
                    &[
                        JValue::Object(JObject::from(mac_address_jbytearray)),
                        JValue::Int(measurement.status as i32),
                        JValue::Int(measurement.nlos as i32),
                        JValue::Int(measurement.distance as i32),
                        JValue::Int(measurement.aoa_azimuth as i32),
                        JValue::Int(measurement.aoa_azimuth_fom as i32),
                        JValue::Int(measurement.aoa_elevation as i32),
                        JValue::Int(measurement.aoa_elevation_fom as i32),
                        JValue::Int(measurement.aoa_destination_azimuth as i32),
                        JValue::Int(measurement.aoa_destination_azimuth_fom as i32),
                        JValue::Int(measurement.aoa_destination_elevation as i32),
                        JValue::Int(measurement.aoa_destination_elevation_fom as i32),
                        JValue::Int(measurement.slot_index as i32),
                        JValue::Int(measurement.rssi as i32),
                    ],
                )
                .map_err(|_| UwbCoreError::Unknown)?;
            self.env
                .set_object_array_element(measurements_jobjectarray, i as i32, measurement_jobject)
                .map_err(|_| UwbCoreError::Unknown)?;
        }
        // Create UwbRangingData
        let ranging_data_jclass =
            self.env.find_class(UWB_RANGING_DATA_CLASS).map_err(|_| UwbCoreError::Unknown)?;
        let method_sig = "(JJIJIII[L".to_owned() + UWB_TWO_WAY_MEASUREMENT_CLASS + ";)V";
        let range_data_jobject = self
            .env
            .new_object(
                ranging_data_jclass,
                &method_sig,
                &[
                    JValue::Long(range_data.sequence_number as i64),
                    JValue::Long(range_data.session_id as i64),
                    JValue::Int(0x0), // TODO: rcr_indicator u8 missing
                    JValue::Long(range_data.current_ranging_interval_ms as i64),
                    JValue::Int(range_data.ranging_measurement_type as i32),
                    JValue::Int(mac_indicator as i32),
                    JValue::Int(measurement_count),
                    JValue::Object(JObject::from(measurements_jobjectarray)),
                ],
            )
            .map_err(|_| UwbCoreError::Unknown)?;
        let method_sig = "(L".to_owned() + UWB_RANGING_DATA_CLASS + ";)V";
        self.cached_jni_call(
            "onRangeDataNotificationReceived",
            &method_sig,
            &[JValue::Object(range_data_jobject)],
        )
    }
}

impl NotificationManager for NotificationManagerAndroid {
    fn on_core_notification(&mut self, core_notification: CoreNotification) -> UwbCoreResult<()> {
        match core_notification {
            CoreNotification::DeviceStatus(device_state) => self.cached_jni_call(
                "onDeviceStatusNotificationReceived",
                "(I)V",
                &[JValue::Int(device_state as i32)],
            ),
            CoreNotification::GenericError(generic_error) => self.cached_jni_call(
                "onCoreGenericErrorNotificationReceived",
                "(I)V",
                &[JValue::Int(generic_error as i32)],
            ),
        }
    }

    fn on_session_notification(
        &mut self,
        session_notification: SessionNotification,
    ) -> UwbCoreResult<()> {
        match session_notification {
            SessionNotification::Status { session_id, session_state, reason_code } => {
                self.on_session_status_notification(session_id, session_state, reason_code)
            }
            SessionNotification::UpdateControllerMulticastList {
                session_id,
                remaining_multicast_list_size,
                status_list,
            } => self.on_session_update_multicast_notification(
                session_id,
                remaining_multicast_list_size,
                status_list,
            ),
            SessionNotification::RangeData(range_data) => {
                self.on_session_range_data_notification(range_data)
            }
        }
    }

    fn on_vendor_notification(
        &mut self,
        vendor_notification: uwb_core::params::RawVendorMessage,
    ) -> UwbCoreResult<()> {
        let payload_jbytearray = self
            .env
            .byte_array_from_slice(&vendor_notification.payload)
            .map_err(|_| UwbCoreError::Unknown)?;
        self.cached_jni_call(
            "onVendorUciNotificationReceived",
            "(II[B)V",
            &[
                // Java only has signed integer. The range for signed int32 should be sufficient.
                JValue::Int(
                    vendor_notification.gid.try_into().map_err(|_| UwbCoreError::BadParameters)?,
                ),
                JValue::Int(
                    vendor_notification.oid.try_into().map_err(|_| UwbCoreError::BadParameters)?,
                ),
                JValue::Object(JObject::from(payload_jbytearray)),
            ],
        )
    }
}
struct NotificationManagerAndroidBuilder {
    vm: &'static Arc<JavaVM>,
    callback_obj: GlobalRef,
}
impl NotificationManagerBuilder<NotificationManagerAndroid> for NotificationManagerAndroidBuilder {
    fn build(self) -> Option<NotificationManagerAndroid> {
        if let Ok(env) = self.vm.attach_current_thread() {
            Some(NotificationManagerAndroid {
                env,
                callback_obj: self.callback_obj,
                jmethod_id_map: HashMap::new(),
            })
        } else {
            None
        }
    }
}

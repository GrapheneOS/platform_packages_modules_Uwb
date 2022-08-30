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
use std::iter::zip;
use std::sync::{Arc, Once};

use jni::errors::Error as JNIError;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JString, JValue};
use jni::signature::{JavaType, TypeSignature};
use jni::sys::{
    jboolean, jbyte, jbyteArray, jint, jintArray, jlong, jobject, jobjectArray, jshortArray,
};
use jni::{AttachGuard, JNIEnv, JavaVM};
use log::{debug, error};
use num_traits::cast::FromPrimitive;
use num_traits::ToPrimitive;
use uci_hal_android::uci_hal_android::UciHalAndroid;
use uwb_core::error::{Error as UwbCoreError, Result as UwbCoreResult};
use uwb_core::params::{CountryCode, RawVendorMessage, SetAppConfigResponse};
use uwb_core::uci::uci_manager_sync::{
    NotificationManager, NotificationManagerBuilder, UciManagerSync,
};
use uwb_core::uci::{CoreNotification, RangingMeasurements, SessionNotification, SessionRangeData};
use uwb_uci_packets::{
    AppConfigTlv, AppConfigTlvType, CapTlv, Controlee, ControleeStatus,
    ExtendedAddressTwoWayRangingMeasurement, MacAddressIndicator, PowerStats, ReasonCode,
    ResetConfig, SessionState, SessionType, ShortAddressTwoWayRangingMeasurement, StatusCode,
    UpdateMulticastListAction,
};

// Byte size of mac address length:
const SHORT_MAC_ADDRESS_LEN: i32 = 2;
const EXTENDED_MAC_ADDRESS_LEN: i32 = 8;

// Name of java classes for UWB response and notifications:
const CONFIG_STATUS_DATA_CLASS: &str = "com/android/server/uwb/data/UwbConfigStatusData";
const MULTICAST_LIST_UPDATE_STATUS_CLASS: &str =
    "com/android/server/uwb/data/UwbMulticastListUpdateStatus";
const POWER_STATS_CLASS: &str = "com/android/server/uwb/info/UwbPowerStats";
const TLV_DATA_CLASS: &str = "com/android/server/uwb/data/UwbTlvData";
const UWB_RANGING_DATA_CLASS: &str = "com/android/server/uwb/data/UwbRangingData";
const UWB_TWO_WAY_MEASUREMENT_CLASS: &str = "com/android/server/uwb/data/UwbTwoWayMeasurement";
const VENDOR_RESPONSE_CLASS: &str = "com/android/server/uwb/data/UwbVendorUciResponse";

#[derive(Debug, thiserror::Error)]
enum Error {
    #[error("UwbCore error: {0:?}")]
    UwbCoreError(#[from] UwbCoreError),
    #[error("JNI error: {0:?}")]
    JNIError(#[from] JNIError),
}
type Result<T> = std::result::Result<T, Error>;

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

/// takes a JavaVM to a static reference.
///
/// JavaVM is shared as multiple JavaVM within a single process is not allowed
/// per [JNI spec](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html)
/// The unique JavaVM need to be shared over (potentially) different threads.
mod unique_jvm {
    use super::*;
    static mut JVM: Option<Arc<JavaVM>> = None;
    static INIT: Once = Once::new();
    /// set_once sets the unique JavaVM that can be then accessed using get_static_ref()
    ///
    /// The function shall only be called once.
    pub(crate) fn set_once(jvm: JavaVM) -> UwbCoreResult<()> {
        // Safety: follows [this pattern](https://doc.rust-lang.org/std/sync/struct.Once.html).
        // Modification to static mut is nested inside call_once.
        unsafe {
            INIT.call_once(|| {
                JVM = Some(Arc::new(jvm));
            });
        }
        Ok(())
    }
    /// Gets a 'static reference to the unique JavaVM. Returns None if set_once() was never called.
    pub(crate) fn get_static_ref() -> Option<&'static Arc<JavaVM>> {
        // Safety: follows [this pattern](https://doc.rust-lang.org/std/sync/struct.Once.html).
        // Modification to static mut is nested inside call_once.
        unsafe { JVM.as_ref() }
    }
}

pub(crate) struct NotificationManagerAndroid {
    chip_id: String,
    // 'static annotation is needed as env is 'sent' by tokio::task::spawn_local.
    env: AttachGuard<'static>,
    /// Global reference to the class loader object (java/lang/ClassLoader) from the java thread
    /// that local java UCI classes can be loaded.
    class_loader_obj: GlobalRef,
    /// Global reference to the java class holding the various UCI notification callback functions.
    callback_obj: GlobalRef,
    // *_jmethod_id are cached for faster callback using call_method_unchecked
    jmethod_id_map: HashMap<String, JMethodID<'static>>,
    // jclass are cached for faster callback
    jclass_map: HashMap<String, GlobalRef>,
}

impl NotificationManagerAndroid {
    /// Finds JClass stored in jclass map. Should be a member function, but disjoint field borrow
    /// checker fails and mutability of individual fields has to be annotated.
    fn find_local_class<'a>(
        jclass_map: &'a mut HashMap<String, GlobalRef>,
        class_loader_obj: &'a GlobalRef,
        env: &'a AttachGuard<'static>,
        class_name: &'a str,
    ) -> UwbCoreResult<JClass<'a>> {
        debug!("UCI JNI: find local class {}", class_name);
        // Look for cached class
        if jclass_map.get(class_name).is_none() {
            // Find class using the class loader object, needed as this call is initiated from a
            // different native thread.
            let class_value = env
                .call_method(
                    class_loader_obj.as_obj(),
                    "findClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    &[JValue::Object(JObject::from(env.new_string(class_name).map_err(|e| {
                        error!("UCI JNI: failed to create Java String: {:?}", e);
                        UwbCoreError::Unknown
                    })?))],
                )
                .map_err(|e| {
                    error!("UCI JNI: failed to find java class {}: {:?}", class_name, e);
                    UwbCoreError::Unknown
                })?;
            let jclass = match class_value.l() {
                Ok(obj) => Ok(JClass::from(obj)),
                Err(e) => {
                    error!("UCI JNI: failed to find java class {}: {:?}", class_name, e);
                    Err(UwbCoreError::Unknown)
                }
            }?;
            // Cache JClass as a global reference.
            jclass_map.insert(
                class_name.to_owned(),
                env.new_global_ref(jclass).map_err(|e| {
                    error!("UCI JNI: global reference conversion failed: {:?}", e);
                    UwbCoreError::Unknown
                })?,
            );
        }
        // Return JClass
        Ok(jclass_map.get(class_name).unwrap().as_obj().into())
    }

    fn cached_jni_call(&mut self, name: &str, sig: &str, args: &[JValue]) -> UwbCoreResult<()> {
        debug!("UCI JNI: callback {}", name);
        let type_signature = TypeSignature::from_str(sig).map_err(|e| {
            error!("UCI JNI: Invalid type signature: {:?}", e);
            UwbCoreError::BadParameters
        })?;
        if type_signature.args.len() != args.len() {
            error!(
                "UCI: type_signature requires {} args, but {} is provided",
                type_signature.args.len(),
                args.len()
            );
            return Err(UwbCoreError::BadParameters);
        }
        let name_signature = name.to_owned() + sig;
        if self.jmethod_id_map.get(&name_signature).is_none() {
            self.jmethod_id_map.insert(
                name_signature.clone(),
                self.env.get_method_id(self.callback_obj.as_obj(), name, sig).map_err(|e| {
                    error!("UCI JNI: failed to get method: {:?}", e);
                    UwbCoreError::Unknown
                })?,
            );
        }
        match self.env.call_method_unchecked(
            self.callback_obj.as_obj(),
            self.jmethod_id_map.get(&name_signature).unwrap().to_owned(),
            type_signature.ret,
            args,
        ) {
            Ok(_) => Ok(()),
            Err(_) => {
                error!("UCI JNI: callback {} failed!", name);
                Err(UwbCoreError::Unknown)
            }
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
        let multicast_update_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            MULTICAST_LIST_UPDATE_STATUS_CLASS,
        )?;
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
        let measurement_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            UWB_TWO_WAY_MEASUREMENT_CLASS,
        )?;
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
            .map_err(|e| {
                error!("UCI JNI: measurement object creation failed: {:?}", e);
                UwbCoreError::Unknown
            })?;
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
                .map_err(|e| {
                    error!("UCI JNI: measurement object creation failed: {:?}", e);
                    UwbCoreError::Unknown
                })?;
            self.env
                .set_object_array_element(measurements_jobjectarray, i as i32, measurement_jobject)
                .map_err(|e| {
                    error!("UCI JNI: measurement object copy failed: {:?}", e);
                    UwbCoreError::Unknown
                })?;
        }
        // Create UwbRangingData
        let ranging_data_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            UWB_RANGING_DATA_CLASS,
        )?;
        let method_sig = "(JJIJIII[L".to_owned() + UWB_TWO_WAY_MEASUREMENT_CLASS + ";)V";
        let range_data_jobject = self
            .env
            .new_object(
                ranging_data_jclass,
                &method_sig,
                &[
                    JValue::Long(range_data.sequence_number as i64),
                    JValue::Long(range_data.session_id as i64),
                    JValue::Int(0x0), // TODO(b/241336806): rcr_indicator u8 missing in core library
                    JValue::Long(range_data.current_ranging_interval_ms as i64),
                    JValue::Int(range_data.ranging_measurement_type as i32),
                    JValue::Int(mac_indicator as i32),
                    JValue::Int(measurement_count),
                    JValue::Object(JObject::from(measurements_jobjectarray)),
                ],
            )
            .map_err(|e| {
                error!("UCI JNI: Ranging Data object creation failed: {:?}", e);
                UwbCoreError::Unknown
            })?;
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
        debug!("UCI JNI: core notification callback.");
        match core_notification {
            CoreNotification::DeviceStatus(device_state) => self.cached_jni_call(
                "onDeviceStatusNotificationReceived",
                "(ILjava/lang/String;)V",
                &[
                    JValue::Int(device_state as i32),
                    JValue::Object(JObject::from(self.env.new_string(&self.chip_id).unwrap())),
                ],
            ),
            CoreNotification::GenericError(generic_error) => self.cached_jni_call(
                "onCoreGenericErrorNotificationReceived",
                "(ILjava/lang/String;)V",
                &[
                    JValue::Int(generic_error as i32),
                    JValue::Object(JObject::from(self.env.new_string(&self.chip_id).unwrap())),
                ],
            ),
        }
    }

    fn on_session_notification(
        &mut self,
        session_notification: SessionNotification,
    ) -> UwbCoreResult<()> {
        debug!("UCI JNI: session notification callback.");
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
        debug!("UCI JNI: vendor notification callback.");
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
    chip_id: String,
    vm: &'static Arc<JavaVM>,
    class_loader_obj: GlobalRef,
    callback_obj: GlobalRef,
}

impl NotificationManagerBuilder<NotificationManagerAndroid> for NotificationManagerAndroidBuilder {
    fn build(self) -> Option<NotificationManagerAndroid> {
        if let Ok(env) = self.vm.attach_current_thread() {
            Some(NotificationManagerAndroid {
                chip_id: self.chip_id,
                env,
                class_loader_obj: self.class_loader_obj,
                callback_obj: self.callback_obj,
                jmethod_id_map: HashMap::new(),
                jclass_map: HashMap::new(),
            })
        } else {
            None
        }
    }
}

/// Dispatcher is managed by Java side. Construction and Destruction are provoked by JNI function
/// nativeDispatcherNew and nativeDispatcherDestroy respectively.
/// Destruction does NOT wait until the spawned threads are closed.
pub struct Dispatcher {
    manager_map: HashMap<String, UciManagerSync>,
}
impl Dispatcher {
    /// Constructs Dispatcher.
    pub fn new<T: AsRef<str>>(
        vm: &'static Arc<JavaVM>,
        class_loader_obj: GlobalRef,
        callback_obj: GlobalRef,
        chip_ids: &[T],
    ) -> UwbCoreResult<Dispatcher> {
        let mut manager_map = HashMap::<String, UciManagerSync>::new();

        for chip_id in chip_ids {
            let manager = UciManagerSync::new(
                UciHalAndroid::new(chip_id.as_ref()),
                NotificationManagerAndroidBuilder {
                    chip_id: chip_id.as_ref().to_owned(),
                    vm,
                    class_loader_obj: class_loader_obj.clone(),
                    callback_obj: callback_obj.clone(),
                },
            )?;
            manager_map.insert(chip_id.as_ref().to_string(), manager);
        }
        Ok(Self { manager_map })
    }

    /// Constructs dispatcher, and return a pointer owning it.
    pub fn new_as_ptr<T: AsRef<str>>(
        vm: &'static Arc<JavaVM>,
        class_loader_obj: GlobalRef,
        callback_obj: GlobalRef,
        chip_ids: &[T],
    ) -> UwbCoreResult<*mut Dispatcher> {
        let dispatcher = Dispatcher::new(vm, class_loader_obj, callback_obj, chip_ids)?;
        Ok(Box::into_raw(Box::new(dispatcher)))
    }

    /// Destroys the Dispatcher pointed by dispatcher_ptr
    ///
    /// # Safety
    /// Dispatcher_ptr must point to a valid dispatcher object it owns.
    pub unsafe fn destroy_ptr(dispatcher_ptr: *mut Dispatcher) {
        let _ = Box::from_raw(dispatcher_ptr);
    }
}

/// Macro capturing the name of the function calling this macro.
///
/// function_name()! -> &'static str
/// Returns the function name as 'static reference.
macro_rules! function_name {
    () => {{
        // Declares function f inside current function.
        fn f() {}
        fn type_name_of<T>(_: T) -> &'static str {
            std::any::type_name::<T>()
        }
        // type name of f is struct_or_crate_name::calling_function_name::f
        let name = type_name_of(f);
        // Find and cut the rest of the path:
        // Third to last character, up to the first semicolon: is calling_function_name
        match &name[..name.len() - 3].rfind(':') {
            Some(pos) => &name[pos + 1..name.len() - 3],
            None => &name[..name.len() - 3],
        }
    }};
}

fn boolean_result_helper<T>(result: Result<T>, error_msg: &str) -> jboolean {
    match result {
        Ok(_) => true,
        Err(e) => {
            error!("{} failed with {:?}", error_msg, &e);
            false
        }
    }
    .into()
}

fn byte_result_helper<T>(result: Result<T>, error_msg: &str) -> jbyte {
    // StatusCode do not overflow i8
    result_to_status_code(result, error_msg).to_i8().unwrap()
}

/// helper function to convert Result to StatusCode
fn result_to_status_code<T>(result: Result<T>, error_msg: &str) -> StatusCode {
    match result.map_err(|e| {
        error!("{} failed with {:?}", error_msg, &e);
        e
    }) {
        Ok(_) => StatusCode::UciStatusOk,
        Err(Error::UwbCoreError(UwbCoreError::BadParameters)) => StatusCode::UciStatusInvalidParam,
        Err(Error::UwbCoreError(UwbCoreError::MaxSessionsExceeded)) => {
            StatusCode::UciStatusMaxSessionsExceeded
        }
        Err(Error::UwbCoreError(UwbCoreError::CommandRetry)) => StatusCode::UciStatusCommandRetry,
        // For JNIError and other UwbCoreError, only generic fail can be given.
        Err(_) => StatusCode::UciStatusFailed,
    }
}

/// helper function to convert Result to Option
fn option_result_helper<T>(result: Result<T>, error_msg: &str) -> Option<T> {
    result
        .map_err(|e| {
            error!("{} failed with {:?}", error_msg, &e);
            e
        })
        .ok()
}

/// Initialize native library. Captures VM:
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeInit(
    env: JNIEnv,
    _obj: JObject,
) -> jboolean {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("uwb")
            .with_min_level(log::Level::Trace)
            .with_filter("trace,jni=info"),
    );
    debug!("{}: enter", function_name!());
    boolean_result_helper(native_init(env), function_name!())
}

fn native_init(env: JNIEnv) -> Result<()> {
    let jvm = env.get_java_vm()?;
    unique_jvm::set_once(jvm).map_err(|e| e.into())
}

/// Get max session number
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetMaxSessionNumber(
    _env: JNIEnv,
    _obj: JObject,
) -> jint {
    debug!("{}: enter", function_name!());
    5
}

/// get mutable reference to UciManagerSync with chip_id.
///
/// # Safety
/// Must be called from a Java object holding a valid or null mDispatcherPointer, and remains valid
/// until env and obj goes out of scope.
unsafe fn get_uci_manager<'a>(
    env: JNIEnv<'a>,
    obj: JObject<'a>,
    chip_id: JString,
) -> Result<&'a mut UciManagerSync> {
    let dispatcher_ptr_value = env.get_field(obj, "mDispatcherPointer", "J")?.j()?;
    if dispatcher_ptr_value == 0 {
        return Err(Error::UwbCoreError(UwbCoreError::BadParameters));
    }
    let chip_id_str = String::from(env.get_string(chip_id)?);
    let dispatcher_ptr = dispatcher_ptr_value as *mut Dispatcher;
    match (*dispatcher_ptr).manager_map.get_mut(&chip_id_str) {
        Some(m) => Ok(m),
        None => Err(Error::UwbCoreError(UwbCoreError::BadParameters)),
    }
}

/// Turn on Single UWB chip.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoInitialize(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jboolean {
    debug!("{}: enter", function_name!());
    boolean_result_helper(native_do_initialize(env, obj, chip_id), function_name!())
}

fn native_do_initialize(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<()> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.open_hal().map_err(|e| e.into())
}

/// Turn off single UWB chip.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoDeinitialize(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jboolean {
    debug!("{}: enter", function_name!());
    boolean_result_helper(native_do_deinitialize(env, obj, chip_id), function_name!())
}

fn native_do_deinitialize(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<()> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.close_hal(true).map_err(|e| e.into())
}

/// Get nanos. Not currently used and returns placeholder value.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos(
    _env: JNIEnv,
    _obj: JObject,
) -> jlong {
    debug!("{}: enter", function_name!());
    0
}

/// Reset a single UWB device by sending UciDeviceReset command. Return value defined by
/// <AndroidRoot>/external/uwb/src/rust/uwb_uci_packets/uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDeviceReset(
    env: JNIEnv,
    obj: JObject,
    _reset_config: jbyte,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_device_reset(env, obj, chip_id), function_name!())
}

fn native_device_reset(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<()> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.device_reset(ResetConfig::UwbsReset).map_err(|e| e.into())
}

/// Init the session on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionInit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    session_type: jbyte,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(
        native_session_init(env, obj, session_id, session_type, chip_id),
        function_name!(),
    )
}

fn native_session_init(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    session_type: jbyte,
    chip_id: JString,
) -> Result<()> {
    let session_type =
        SessionType::from_u8(session_type as u8).ok_or(UwbCoreError::BadParameters)?;
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.session_init(session_id as u32, session_type).map_err(|e| e.into())
}

/// DeInit the session on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionDeInit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_session_deinit(env, obj, session_id, chip_id), function_name!())
}

fn native_session_deinit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<()> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.session_deinit(session_id as u32).map_err(|e| e.into())
}

/// Get session count on a single UWB device. return -1 if failed
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionCount(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_get_session_count(env, obj, chip_id), function_name!()) {
        // Max session count is 5, will not overflow i8
        Some(c) => c as i8,
        None => -1,
    }
}

fn native_get_session_count(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<u8> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.session_get_count().map_err(|e| e.into())
}

/// Start ranging on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStart(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_ranging_start(env, obj, session_id, chip_id), function_name!())
}

fn native_ranging_start(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<()> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.range_start(session_id as u32).map_err(|e| e.into())
}

/// Stop ranging on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStop(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_ranging_stop(env, obj, session_id, chip_id), function_name!())
}

fn native_ranging_stop(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<()> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.range_stop(session_id as u32).map_err(|e| e.into())
}

/// Get session stateon a single UWB device. Return -1 if failed
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionState(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_get_session_state(env, obj, session_id, chip_id),
        function_name!(),
    ) {
        // SessionState does not overflow i8
        Some(s) => s as i8,
        None => -1,
    }
}

fn native_get_session_state(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<SessionState> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.session_get_state(session_id as u32).map_err(|e| e.into())
}

fn parse_app_config_tlv_vec(no_of_params: i32, mut byte_array: &[u8]) -> Result<Vec<AppConfigTlv>> {
    let mut parsed_tlvs_len = 0;
    let received_tlvs_len = byte_array.len();
    let mut tlvs = Vec::<AppConfigTlv>::new();
    for _ in 0..no_of_params {
        // The tlv consists of the type of payload in 1 byte, the length of payload as u8
        // in 1 byte, and the payload.
        const TLV_HEADER_SIZE: usize = 2;
        let tlv = AppConfigTlv::parse(byte_array).map_err(|_| UwbCoreError::BadParameters)?;
        byte_array =
            byte_array.get(tlv.v.len() + TLV_HEADER_SIZE..).ok_or(UwbCoreError::BadParameters)?;
        parsed_tlvs_len += tlv.v.len() + TLV_HEADER_SIZE;
        tlvs.push(tlv);
    }
    if parsed_tlvs_len != received_tlvs_len {
        return Err(Error::UwbCoreError(UwbCoreError::BadParameters));
    };
    Ok(tlvs)
}

fn create_set_config_response(response: SetAppConfigResponse, env: JNIEnv) -> Result<jbyteArray> {
    let uwb_config_status_class = env.find_class(CONFIG_STATUS_DATA_CLASS)?;
    let mut buf = Vec::<u8>::new();
    for config_status in &response.config_status {
        buf.push(config_status.cfg_id as u8);
        buf.push(config_status.status as u8);
    }
    let config_status_jbytearray = env.byte_array_from_slice(&buf)?;
    let config_status_jobject = env.new_object(
        uwb_config_status_class,
        "(II[B)V",
        &[
            JValue::Int(response.status as i32),
            JValue::Int(response.config_status.len() as i32),
            JValue::Object(JObject::from(config_status_jbytearray)),
        ],
    )?;
    Ok(*config_status_jobject)
}

/// Set app configurations on a single UWB device. Return null JObject if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    _app_config_param_len: jint, // TODO(ningyuan): Obsolete parameter
    app_config_params: jbyteArray,
    chip_id: JString,
) -> jbyteArray {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_set_app_configurations(
            env,
            obj,
            session_id,
            no_of_params,
            app_config_params,
            chip_id,
        ),
        function_name!(),
    ) {
        Some(config_response) => create_set_config_response(config_response, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_set_app_configurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    app_config_params: jbyteArray,
    chip_id: JString,
) -> Result<SetAppConfigResponse> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    let config_byte_array = env.convert_byte_array(app_config_params)?;
    let tlvs = parse_app_config_tlv_vec(no_of_params, &config_byte_array)?;
    uci_manager.session_set_app_config(session_id as u32, tlvs).map_err(|e| e.into())
}

fn create_get_config_response(tlvs: Vec<AppConfigTlv>, env: JNIEnv) -> Result<jbyteArray> {
    let tlv_data_class = env.find_class(TLV_DATA_CLASS)?;
    let mut buf = Vec::<u8>::new();
    for tlv in &tlvs {
        buf.push(tlv.cfg_id as u8);
        buf.push(tlv.v.len() as u8);
        buf.extend(&tlv.v);
    }
    let tlvs_jbytearray = env.byte_array_from_slice(&buf)?;
    let tlvs_jobject = env.new_object(
        tlv_data_class,
        "(II[B)V",
        &[
            JValue::Int(StatusCode::UciStatusOk as i32),
            JValue::Int(tlvs.len() as i32),
            JValue::Object(JObject::from(tlvs_jbytearray)),
        ],
    )?;
    Ok(*tlvs_jobject)
}

/// Get app configurations on a single UWB device. Return null JObject if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    _no_of_params: jint,
    _app_config_param_len: jint,
    app_config_params: jbyteArray,
    chip_id: JString,
) -> jbyteArray {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_get_app_configurations(env, obj, session_id, app_config_params, chip_id),
        function_name!(),
    ) {
        Some(v) => create_get_config_response(v, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_get_app_configurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    app_config_params: jbyteArray,
    chip_id: JString,
) -> Result<Vec<AppConfigTlv>> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    let app_config_bytearray = env.convert_byte_array(app_config_params)?;
    uci_manager
        .session_get_app_config(
            session_id as u32,
            app_config_bytearray
                .into_iter()
                .map(AppConfigTlvType::from_u8)
                .collect::<Option<Vec<_>>>()
                .ok_or(UwbCoreError::BadParameters)?,
        )
        .map_err(|e| e.into())
}

fn create_cap_response(tlvs: Vec<CapTlv>, env: JNIEnv) -> Result<jbyteArray> {
    let tlv_data_class = env.find_class(TLV_DATA_CLASS)?;
    let mut buf = Vec::<u8>::new();
    for tlv in &tlvs {
        buf.push(tlv.t as u8);
        buf.push(tlv.v.len() as u8);
        buf.extend(&tlv.v);
    }
    let tlvs_jbytearray = env.byte_array_from_slice(&buf)?;
    let tlvs_jobject = env.new_object(
        tlv_data_class,
        "(II[B)V",
        &[
            JValue::Int(StatusCode::UciStatusOk as i32),
            JValue::Int(tlvs.len() as i32),
            JValue::Object(JObject::from(tlvs_jbytearray)),
        ],
    )?;
    Ok(*tlvs_jobject)
}

/// Get capability info on a single UWB device. Return null JObject if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetCapsInfo(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jbyteArray {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_get_caps_info(env, obj, chip_id), function_name!()) {
        Some(v) => create_cap_response(v, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_get_caps_info(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<Vec<CapTlv>> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.core_get_caps_info().map_err(|e| e.into())
}

/// Update multicast list on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    action: jbyte,
    no_of_controlee: jbyte,
    addresses: jshortArray,
    sub_session_ids: jintArray,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(
        native_controller_multicast_list_update(
            env,
            obj,
            session_id,
            action,
            no_of_controlee,
            addresses,
            sub_session_ids,
            chip_id,
        ),
        function_name!(),
    )
}

// Function is used only once that copies arguments from JNI
#[allow(clippy::too_many_arguments)]
fn native_controller_multicast_list_update(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    action: jbyte,
    no_of_controlee: jbyte,
    addresses: jshortArray,
    sub_session_ids: jintArray,
    chip_id: JString,
) -> Result<()> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it goes
    // out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    let mut address_list = vec![
        0i16;
        env.get_array_length(addresses)?.try_into().map_err(|_| {
            Error::UwbCoreError(UwbCoreError::BadParameters)
        })?
    ];
    env.get_short_array_region(addresses, 0, &mut address_list)?;
    let mut sub_session_id_list = vec![
        0i32;
        env.get_array_length(sub_session_ids)?.try_into().map_err(
            |_| Error::UwbCoreError(UwbCoreError::BadParameters)
        )?
    ];
    env.get_int_array_region(sub_session_ids, 0, &mut sub_session_id_list)?;
    if address_list.len() != sub_session_id_list.len()
        || address_list.len() != no_of_controlee as usize
    {
        return Err(Error::UwbCoreError(UwbCoreError::BadParameters));
    }
    let controlee_list = zip(address_list, sub_session_id_list)
        .map(|(a, s)| Controlee { short_address: a as u16, subsession_id: s as u32 })
        .collect::<Vec<Controlee>>();
    uci_manager
        .session_update_controller_multicast_list(
            session_id as u32,
            UpdateMulticastListAction::from_u8(action as u8)
                .ok_or(Error::UwbCoreError(UwbCoreError::BadParameters))?,
            controlee_list,
        )
        .map_err(|e| e.into())
}

/// Set country code on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetCountryCode(
    env: JNIEnv,
    obj: JObject,
    country_code: jbyteArray,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_set_country_code(env, obj, country_code, chip_id), function_name!())
}

fn native_set_country_code(
    env: JNIEnv,
    obj: JObject,
    country_code: jbyteArray,
    chip_id: JString,
) -> Result<()> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it goes
    // out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    let country_code = env.convert_byte_array(country_code)?;
    debug!("Country code: {:?}", country_code);
    if country_code.len() != 2 {
        return Err(Error::UwbCoreError(UwbCoreError::BadParameters));
    }
    uci_manager
        .android_set_country_code(
            CountryCode::new(&[country_code[0], country_code[1]])
                .ok_or(Error::UwbCoreError(UwbCoreError::BadParameters))?,
        )
        .map_err(|e| e.into())
}

fn create_vendor_response(msg: RawVendorMessage, env: JNIEnv) -> Result<jobject> {
    let vendor_response_class = env.find_class(VENDOR_RESPONSE_CLASS)?;
    match env.new_object(
        vendor_response_class,
        "(BII[B)V",
        &[
            JValue::Byte(StatusCode::UciStatusOk as i8),
            JValue::Int(msg.gid as i32),
            JValue::Int(msg.oid as i32),
            JValue::Object(JObject::from(env.byte_array_from_slice(&msg.payload)?)),
        ],
    ) {
        Ok(obj) => Ok(*obj),
        Err(e) => Err(e.into()),
    }
}

fn create_invalid_vendor_response(env: JNIEnv) -> Result<jobject> {
    let vendor_response_class = env.find_class(VENDOR_RESPONSE_CLASS)?;
    match env.new_object(
        vendor_response_class,
        "(BII[B)V",
        &[
            JValue::Byte(StatusCode::UciStatusFailed as i8),
            JValue::Int(-1),
            JValue::Int(-1),
            JValue::Object(JObject::null()),
        ],
    ) {
        Ok(obj) => Ok(*obj),
        Err(e) => Err(e.into()),
    }
}

/// Send Raw vendor command on a single UWB device. Returns an invalid response if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSendRawVendorCmd(
    env: JNIEnv,
    obj: JObject,
    gid: jint,
    oid: jint,
    payload_jarray: jbyteArray,
    chip_id: JString,
) -> jobject {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_send_raw_vendor_cmd(env, obj, gid, oid, payload_jarray, chip_id),
        function_name!(),
    ) {
        // Note: unwrap() here is not desirable, but unavoidable given non-null object is returned
        // even for failing cases.
        Some(msg) => create_vendor_response(msg, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or_else(|_| create_invalid_vendor_response(env).unwrap()),
        None => create_invalid_vendor_response(env).unwrap(),
    }
}

fn native_send_raw_vendor_cmd(
    env: JNIEnv,
    obj: JObject,
    gid: jint,
    oid: jint,
    payload_jarray: jbyteArray,
    chip_id: JString,
) -> Result<RawVendorMessage> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    let payload = env.convert_byte_array(payload_jarray)?;
    uci_manager.raw_vendor_cmd(gid as u32, oid as u32, payload).map_err(|e| e.into())
}

fn create_power_stats(power_stats: PowerStats, env: JNIEnv) -> Result<jobject> {
    let power_stats_class = env.find_class(POWER_STATS_CLASS)?;
    match env.new_object(
        power_stats_class,
        "(IIII)V",
        &[
            JValue::Int(power_stats.idle_time_ms as i32),
            JValue::Int(power_stats.tx_time_ms as i32),
            JValue::Int(power_stats.rx_time_ms as i32),
            JValue::Int(power_stats.total_wake_count as i32),
        ],
    ) {
        Ok(o) => Ok(*o),
        Err(e) => Err(e.into()),
    }
}

/// Get UWB power stats on a single UWB device. Returns a null object if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetPowerStats(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jobject {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_get_power_stats(env, obj, chip_id), function_name!()) {
        Some(ps) => create_power_stats(ps, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_get_power_stats(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<PowerStats> {
    // Safety: Java side owns Dispatcher by pointer, and borrows to this function until it
    // goes out of scope.
    let uci_manager = unsafe { get_uci_manager(env, obj, chip_id) }?;
    uci_manager.android_get_power_stats().map_err(|e| e.into())
}

/// Get the class loader object. Has to be called from a JNIEnv where the local java classes are
/// loaded. Results in a global reference to the class loader object that can be used to look for
/// classes in other native thread.
fn get_class_loader_obj(env: &JNIEnv) -> Result<GlobalRef> {
    let ranging_data_class = env.find_class(&UWB_RANGING_DATA_CLASS)?;
    let ranging_data_class_class = env.get_object_class(ranging_data_class)?;
    let get_class_loader_method =
        env.get_method_id(ranging_data_class_class, "getClassLoader", "()Ljava/lang/ClassLoader;")?;
    let class_loader = env.call_method_unchecked(
        ranging_data_class,
        get_class_loader_method,
        JavaType::Object("java/lang/ClassLoader".into()),
        &[JValue::Void],
    )?;
    let class_loader_jobject = class_loader.l()?;
    Ok(env.new_global_ref(class_loader_jobject)?)
}

/// Create the dispatcher. Returns pointer to Dispatcher casted as jlong that owns the dispatcher.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDispatcherNew(
    env: JNIEnv,
    obj: JObject,
    chip_ids_jarray: jobjectArray,
) -> jlong {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_dispatcher_new(env, obj, chip_ids_jarray), function_name!()) {
        Some(ptr) => ptr as jlong,
        None => *JObject::null() as jlong,
    }
}

fn native_dispatcher_new(
    env: JNIEnv,
    obj: JObject,
    chip_ids_jarray: jobjectArray,
) -> Result<*mut Dispatcher> {
    let chip_ids_len: i32 = env.get_array_length(chip_ids_jarray)?;
    let chip_ids = (0..chip_ids_len)
        .map(|i| env.get_string(env.get_object_array_element(chip_ids_jarray, i)?.into()))
        .collect::<std::result::Result<Vec<_>, JNIError>>()?;
    let chip_ids = chip_ids.into_iter().map(String::from).collect::<Vec<String>>();
    let class_loader_obj = get_class_loader_obj(&env)?;
    Dispatcher::new_as_ptr(
        unique_jvm::get_static_ref().ok_or(UwbCoreError::Unknown)?,
        class_loader_obj,
        env.new_global_ref(obj)?,
        &chip_ids,
    )
    .map_err(|e| e.into())
}

/// Destroys the dispatcher.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDispatcherDestroy(
    env: JNIEnv,
    obj: JObject,
) {
    debug!("{}: enter", function_name!());
    if option_result_helper(native_dispatcher_destroy(env, obj), function_name!()).is_some() {
        debug!("The dispatcher is successfully destroyed.");
    }
}

fn native_dispatcher_destroy(env: JNIEnv, obj: JObject) -> Result<()> {
    let dispatcher_ptr_long = env.get_field(obj, "mDispatcherPointer", "J")?.j()?;
    // Safety: Java side owns Dispatcher through the pointer, and asks it to be destroyed
    unsafe {
        Dispatcher::destroy_ptr(dispatcher_ptr_long as *mut Dispatcher);
    }
    Ok(())
}

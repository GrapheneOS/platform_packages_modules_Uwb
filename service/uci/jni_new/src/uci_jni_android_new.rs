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

//! Implementation of JNI functions.

use crate::dispatcher::Dispatcher;
use crate::error::{Error, Result};
use crate::helper::{boolean_result_helper, byte_result_helper, option_result_helper};
use crate::jclass_name::{
    CONFIG_STATUS_DATA_CLASS, POWER_STATS_CLASS, TLV_DATA_CLASS, UWB_RANGING_DATA_CLASS,
    VENDOR_RESPONSE_CLASS,
};
use crate::unique_jvm;

use std::convert::TryInto;
use std::iter::zip;

use jni::errors::Error as JNIError;
use jni::objects::{GlobalRef, JObject, JString, JValue};
use jni::signature::JavaType;
use jni::sys::{
    jboolean, jbyte, jbyteArray, jint, jintArray, jlong, jobject, jobjectArray, jshortArray,
};
use jni::JNIEnv;
use log::{debug, error};
use num_traits::cast::FromPrimitive;
use uwb_core::error::Error as UwbCoreError;
use uwb_core::params::{CountryCode, RawVendorMessage, SetAppConfigResponse};
use uwb_core::uci::uci_manager_sync::UciManagerSync;
use uwb_uci_packets::{
    AppConfigTlv, AppConfigTlvType, CapTlv, Controlee, PowerStats, ResetConfig, SessionState,
    SessionType, StatusCode, UpdateMulticastListAction,
};

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

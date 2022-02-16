//! jni for uwb native stack
use android_logger::FilterBuilder;
use jni::objects::{JObject, JValue};
use jni::sys::{jboolean, jbyte, jbyteArray, jint, jintArray, jlong, jobject, jshortArray};
use jni::JNIEnv;
use log::{error, info, LevelFilter};
use num_traits::ToPrimitive;
use uwb_uci_packets::{
    GetCapsInfoRspPacket, Packet, SessionGetAppConfigRspPacket, SessionSetAppConfigRspPacket,
    StatusCode, UciResponseChild, UciResponsePacket, UciVendor_9_ResponseChild,
    UciVendor_A_ResponseChild, UciVendor_B_ResponseChild, UciVendor_E_ResponseChild,
    UciVendor_F_ResponseChild,
};
use uwb_uci_rust::error::UwbErr;
use uwb_uci_rust::event_manager::EventManagerImpl as EventManager;
use uwb_uci_rust::uci::{uci_hrcv::UciResponse, Dispatcher, JNICommand};

const STATUS_OK: i8 = 0;
const STATUS_FAILED: i8 = 2;

/// Initialize UWB
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeInit(
    _env: JNIEnv,
    _obj: JObject,
) -> jboolean {
    let crates_log_lvl_filter = FilterBuilder::new()
        .filter(None, LevelFilter::Trace) // default log level
        .filter(Some("jni"), LevelFilter::Info) // reduced log level for jni crate
        .build();
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("uwb")
            .with_min_level(log::Level::Trace)
            .with_filter(crates_log_lvl_filter),
    );
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeInit: enter");
    true as jboolean
}

/// Get max session number
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetMaxSessionNumber(
    _env: JNIEnv,
    _obj: JObject,
) -> jint {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetMaxSessionNumber: enter");
    5
}

/// Turn on UWB. initialize the GKI module and HAL module for UWB device.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoInitialize(
    env: JNIEnv,
    obj: JObject,
) -> jboolean {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoInitialize: enter");
    boolean_result_helper(do_initialize(env, obj), "DoInitialize")
}

/// Turn off UWB. Deinitilize the GKI and HAL module, power of the UWB device.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoDeinitialize(
    env: JNIEnv,
    obj: JObject,
) -> jboolean {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoDeinitialize: enter");
    boolean_result_helper(do_deinitialize(env, obj), "DoDeinitialize")
}

/// get nanos
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos(
    _env: JNIEnv,
    _obj: JObject,
) -> jlong {
    info!(
        "Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos: enter"
    );
    0
}

/// retrieve the UWB device specific information etc.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSpecificationInfo(
    env: JNIEnv,
    obj: JObject,
) -> jobject {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSpecificationInfo: enter");
    let uwb_specification_info_class =
        env.find_class("com/android/server/uwb/info/UwbSpecificationInfo").unwrap();
    match get_specification_info(env, obj) {
        Ok(para) => {
            let specification_info =
                env.new_object(uwb_specification_info_class, "(IIIIIIIIIIIIIIII)V", &para).unwrap();
            *specification_info
        }
        Err(e) => {
            error!("Get specification info failed with: {:?}", e);
            *JObject::null()
        }
    }
}

/// reset the device
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDeviceReset(
    env: JNIEnv,
    obj: JObject,
    reset_config: jbyte,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeDeviceReset: enter");
    byte_result_helper(reset_device(env, obj, reset_config as u8), "ResetDevice")
}

/// init the session
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionInit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    session_type: jbyte,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionInit: enter");
    byte_result_helper(session_init(env, obj, session_id as u32, session_type as u8), "SessionInit")
}

/// deinit the session
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionDeInit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionDeInit: enter");
    byte_result_helper(session_deinit(env, obj, session_id as u32), "SessionDeInit")
}

/// get session count
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionCount(
    env: JNIEnv,
    obj: JObject,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionCount: enter");
    match get_session_count(env, obj) {
        Ok(count) => count,
        Err(e) => {
            error!("GetSessionCount failed with {:?}", e);
            -1
        }
    }
}

///  start the ranging
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStart(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStart: enter");
    byte_result_helper(ranging_start(env, obj, session_id as u32), "RangingStart")
}

/// stop the ranging
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStop(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStop: enter");
    byte_result_helper(ranging_stop(env, obj, session_id as u32), "RangingStop")
}

/// get the session state
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionState(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionState: enter");
    match get_session_state(env, obj, session_id as u32) {
        Ok(state) => state,
        Err(e) => {
            error!("GetSessionState failed with {:?}", e);
            -1
        }
    }
}

/// set app configurations
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    app_config_param_len: jint,
    app_config_params: jbyteArray,
) -> jbyteArray {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetAppConfigurations: enter");
    match set_app_configurations(
        env,
        obj,
        session_id as u32,
        no_of_params as u32,
        app_config_param_len as u32,
        app_config_params,
    ) {
        Ok(data) => {
            let uwb_config_status_class =
                env.find_class("com/android/server/uwb/data/UwbConfigStatusData").unwrap();
            let mut buf: Vec<u8> = Vec::new();
            for iter in data.get_cfg_status() {
                buf.push(iter.cfg_id as u8);
                buf.push(iter.status as u8);
            }
            let cfg_jbytearray = env.byte_array_from_slice(&buf).unwrap();
            let uwb_config_status_object = env.new_object(
                uwb_config_status_class,
                "(II[B)V",
                &[
                    JValue::Int(data.get_status().to_i32().unwrap()),
                    JValue::Int(data.get_cfg_status().len().to_i32().unwrap()),
                    JValue::Object(JObject::from(cfg_jbytearray)),
                ],
            );
            *uwb_config_status_object.unwrap()
        }
        Err(e) => {
            error!("SetAppConfig failed with: {:?}", e);
            *JObject::null()
        }
    }
}

/// get app configurations
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    app_config_param_len: jint,
    app_config_params: jbyteArray,
) -> jbyteArray {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetAppConfigurations: enter");
    match get_app_configurations(
        env,
        obj,
        session_id as u32,
        no_of_params as u32,
        app_config_param_len as u32,
        app_config_params,
    ) {
        Ok(data) => {
            let uwb_tlv_info_class =
                env.find_class("com/android/server/uwb/data/UwbTlvData").unwrap();
            let mut buf: Vec<u8> = Vec::new();
            for tlv in data.get_tlvs() {
                buf.push(tlv.cfg_id as u8);
                buf.push(tlv.v.len() as u8);
                buf.extend(&tlv.v);
            }
            let tlv_jbytearray = env.byte_array_from_slice(&buf).unwrap();
            let uwb_tlv_info_object = env.new_object(
                uwb_tlv_info_class,
                "(II[B)V",
                &[
                    JValue::Int(data.get_status().to_i32().unwrap()),
                    JValue::Int(data.get_tlvs().len().to_i32().unwrap()),
                    JValue::Object(JObject::from(tlv_jbytearray)),
                ],
            );
            *uwb_tlv_info_object.unwrap()
        }
        Err(e) => {
            error!("GetAppConfig failed with: {:?}", e);
            *JObject::null()
        }
    }
}

/// get capability info
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetCapsInfo(
    env: JNIEnv,
    obj: JObject,
) -> jbyteArray {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetCapsInfo: enter");
    match get_caps_info(env, obj) {
        Ok(data) => {
            let uwb_tlv_info_class =
                env.find_class("com/android/server/uwb/data/UwbTlvData").unwrap();
            let mut buf: Vec<u8> = Vec::new();
            for tlv in data.get_tlvs() {
                buf.push(tlv.t as u8);
                buf.push(tlv.v.len() as u8);
                buf.extend(&tlv.v);
            }
            let tlv_jbytearray = env.byte_array_from_slice(&buf).unwrap();
            let uwb_tlv_info_object = env.new_object(
                uwb_tlv_info_class,
                "(II[B)V",
                &[
                    JValue::Int(data.get_status().to_i32().unwrap()),
                    JValue::Int(data.get_tlvs().len().to_i32().unwrap()),
                    JValue::Object(JObject::from(tlv_jbytearray)),
                ],
            );
            *uwb_tlv_info_object.unwrap()
        }
        Err(e) => {
            error!("GetCapsInfo failed with: {:?}", e);
            *JObject::null()
        }
    }
}

/// update multicast list
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    action: jbyte,
    no_of_controlee: jbyte,
    addresses: jshortArray,
    sub_session_ids: jintArray,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate: enter");
    byte_result_helper(
        multicast_list_update(
            env,
            obj,
            session_id as u32,
            action as u8,
            no_of_controlee as u8,
            addresses,
            sub_session_ids,
        ),
        "ControllerMulticastListUpdate",
    )
}

/// set country code
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetCountryCode(
    env: JNIEnv,
    obj: JObject,
    country_code: jbyteArray,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetCountryCode: enter");
    byte_result_helper(set_country_code(env, obj, country_code), "SetCountryCode")
}

/// set country code
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSendRawVendorCmd(
    env: JNIEnv,
    obj: JObject,
    gid: jint,
    oid: jint,
    payload: jbyteArray,
) -> jobject {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeRawVendor: enter");
    let uwb_vendor_uci_response_class =
        env.find_class("com/android/server/uwb/data/UwbVendorUciResponse").unwrap();
    match send_raw_vendor_cmd(
        env,
        obj,
        gid.try_into().expect("invalid gid"),
        oid.try_into().expect("invalid oid"),
        payload,
    ) {
        Ok((gid, oid, payload)) => *env
            .new_object(
                uwb_vendor_uci_response_class,
                "(BIIB])V",
                &[
                    JValue::Byte(STATUS_OK),
                    JValue::Int(gid.to_i32().unwrap()),
                    JValue::Int(oid.to_i32().unwrap()),
                    JValue::Object(JObject::from(
                        env.byte_array_from_slice(payload.as_ref()).unwrap(),
                    )),
                ],
            )
            .unwrap(),
        Err(e) => {
            error!("send raw uci cmd failed with: {:?}", e);
            *env.new_object(
                uwb_vendor_uci_response_class,
                "(BIIB])V",
                &[
                    JValue::Byte(STATUS_FAILED),
                    JValue::Int(-1),
                    JValue::Int(-1),
                    JValue::Object(JObject::null()),
                ],
            )
            .unwrap()
        }
    }
}

/// retrieve the UWB power stats
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetPowerStats(
    env: JNIEnv,
    obj: JObject,
) -> jobject {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetPowerStats: enter");
    let uwb_power_stats_class =
        env.find_class("com/android/server/uwb/info/UwbPowerStats").unwrap();
    match get_power_stats(env, obj) {
        Ok(para) => {
            let power_stats = env.new_object(uwb_power_stats_class, "(IIII)V", &para).unwrap();
            *power_stats
        }
        Err(e) => {
            error!("Get power stats failed with: {:?}", e);
            *JObject::null()
        }
    }
}

fn boolean_result_helper(result: Result<(), UwbErr>, function_name: &str) -> jboolean {
    match result {
        Ok(()) => true as jboolean,
        Err(err) => {
            error!("{} failed with: {:?}", function_name, err);
            false as jboolean
        }
    }
}

fn byte_result_helper(result: Result<(), UwbErr>, function_name: &str) -> jbyte {
    match result {
        Ok(()) => STATUS_OK,
        Err(err) => {
            error!("{} failed with: {:?}", function_name, err);
            STATUS_FAILED
        }
    }
}

fn do_initialize(env: JNIEnv, obj: JObject) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    dispatcher.send_jni_command(JNICommand::Enable)?;
    match uwa_get_device_info(dispatcher) {
        Ok(res) => {
            if let UciResponse::GetDeviceInfoRsp(device_info) = res {
                dispatcher.device_info = Some(device_info);
            }
        }
        Err(e) => {
            error!("GetDeviceInfo failed with: {:?}", e);
            return Err(UwbErr::failed());
        }
    }
    Ok(())
}

fn do_deinitialize(env: JNIEnv, obj: JObject) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    dispatcher.send_jni_command(JNICommand::Disable(true))?;
    dispatcher.send_jni_command(JNICommand::Exit)?;
    Ok(())
}

fn get_specification_info<'a>(env: JNIEnv, obj: JObject) -> Result<[JValue<'a>; 16], UwbErr> {
    let mut para = [JValue::Int(0); 16];
    let dispatcher = get_dispatcher(env, obj)?;
    if dispatcher.device_info.is_none() {
        error!("Fail to get specification info.");
        return Err(UwbErr::failed());
    }
    if let Some(data) = &dispatcher.device_info {
        para = [
            JValue::Int((data.get_uci_version() & 0xFF).into()),
            JValue::Int(((data.get_uci_version() >> 8) & 0xF).into()),
            JValue::Int(((data.get_uci_version() >> 12) & 0xF).into()),
            JValue::Int((data.get_mac_version() & 0xFF).into()),
            JValue::Int(((data.get_mac_version() >> 8) & 0xF).into()),
            JValue::Int(((data.get_mac_version() >> 12) & 0xF).into()),
            JValue::Int((data.get_phy_version() & 0xFF).into()),
            JValue::Int(((data.get_phy_version() >> 8) & 0xF).into()),
            JValue::Int(((data.get_phy_version() >> 12) & 0xF).into()),
            JValue::Int((data.get_uci_test_version() & 0xFF).into()),
            JValue::Int(((data.get_uci_test_version() >> 8) & 0xF).into()),
            JValue::Int(((data.get_uci_test_version() >> 12) & 0xF).into()),
            JValue::Int(1), // fira_major_version
            JValue::Int(0), // fira_minor_version
            JValue::Int(1), // ccc_major_version
            JValue::Int(0), // ccc_minor_version
        ];
    }
    Ok(para)
}

fn session_init(
    env: JNIEnv,
    obj: JObject,
    session_id: u32,
    session_type: u8,
) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher
        .block_on_jni_command(JNICommand::UciSessionInit(session_id, session_type))?
    {
        UciResponse::SessionInitRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn session_deinit(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciSessionDeinit(session_id))? {
        UciResponse::SessionDeinitRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn get_session_count(env: JNIEnv, obj: JObject) -> Result<jbyte, UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(JNICommand::UciSessionGetCount)? {
        UciResponse::SessionGetCountRsp(data) => Ok(data.get_session_count() as jbyte),
        _ => Err(UwbErr::failed()),
    }
}

fn ranging_start(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciStartRange(session_id))? {
        UciResponse::RangeStartRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn ranging_stop(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciStopRange(session_id))? {
        UciResponse::RangeStopRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn get_session_state(env: JNIEnv, obj: JObject, session_id: u32) -> Result<jbyte, UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(JNICommand::UciGetSessionState(session_id))? {
        UciResponse::SessionGetStateRsp(data) => Ok(data.get_session_state() as jbyte),
        _ => Err(UwbErr::failed()),
    }
}

fn set_app_configurations(
    env: JNIEnv,
    obj: JObject,
    session_id: u32,
    no_of_params: u32,
    app_config_param_len: u32,
    app_config_params: jintArray,
) -> Result<SessionSetAppConfigRspPacket, UwbErr> {
    let app_configs = env.convert_byte_array(app_config_params)?;
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(JNICommand::UciSetAppConfig {
        session_id,
        no_of_params,
        app_config_param_len,
        app_configs,
    })? {
        UciResponse::SessionSetAppConfigRsp(data) => Ok(data),
        _ => Err(UwbErr::failed()),
    }
}

fn get_app_configurations(
    env: JNIEnv,
    obj: JObject,
    session_id: u32,
    no_of_params: u32,
    app_config_param_len: u32,
    app_config_params: jintArray,
) -> Result<SessionGetAppConfigRspPacket, UwbErr> {
    let app_configs = env.convert_byte_array(app_config_params)?;
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(JNICommand::UciGetAppConfig {
        session_id,
        no_of_params,
        app_config_param_len,
        app_configs,
    })? {
        UciResponse::SessionGetAppConfigRsp(data) => Ok(data),
        _ => Err(UwbErr::failed()),
    }
}

fn get_caps_info(env: JNIEnv, obj: JObject) -> Result<GetCapsInfoRspPacket, UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(JNICommand::UciGetCapsInfo)? {
        UciResponse::GetCapsInfoRsp(data) => Ok(data),
        _ => Err(UwbErr::failed()),
    }
}

fn multicast_list_update(
    env: JNIEnv,
    obj: JObject,
    session_id: u32,
    action: u8,
    no_of_controlee: u8,
    addresses: jshortArray,
    sub_session_ids: jintArray,
) -> Result<(), UwbErr> {
    let mut address_list = vec![0i16; env.get_array_length(addresses)?.try_into().unwrap()];
    env.get_short_array_region(addresses, 0, &mut address_list)?;
    let mut sub_session_id_list =
        vec![0i32; env.get_array_length(sub_session_ids)?.try_into().unwrap()];
    env.get_int_array_region(sub_session_ids, 0, &mut sub_session_id_list)?;
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciSessionUpdateMulticastList {
        session_id,
        action,
        no_of_controlee,
        address_list: address_list.to_vec(),
        sub_session_id_list: sub_session_id_list.to_vec(),
    })? {
        UciResponse::SessionUpdateControllerMulticastListRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn set_country_code(env: JNIEnv, obj: JObject, country_code: jbyteArray) -> Result<(), UwbErr> {
    let code = env.convert_byte_array(country_code)?;
    if code.len() != 2 {
        return Err(UwbErr::failed());
    }
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciSetCountryCode { code })? {
        UciResponse::AndroidSetCountryCodeRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn get_vendor_uci_payload(data: UciResponsePacket) -> Result<Vec<u8>, UwbErr> {
    match data.specialize() {
        UciResponseChild::UciVendor_9_Response(evt) => match evt.specialize() {
            UciVendor_9_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_9_ResponseChild::None => Ok(Vec::new()),
        },
        UciResponseChild::UciVendor_A_Response(evt) => match evt.specialize() {
            UciVendor_A_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_A_ResponseChild::None => Ok(Vec::new()),
        },
        UciResponseChild::UciVendor_B_Response(evt) => match evt.specialize() {
            UciVendor_B_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_B_ResponseChild::None => Ok(Vec::new()),
        },
        UciResponseChild::UciVendor_E_Response(evt) => match evt.specialize() {
            UciVendor_E_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_E_ResponseChild::None => Ok(Vec::new()),
        },
        UciResponseChild::UciVendor_F_Response(evt) => match evt.specialize() {
            UciVendor_F_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_F_ResponseChild::None => Ok(Vec::new()),
        },
        _ => {
            error!("Invalid vendor response with gid {:?}", data.get_group_id());
            Err(UwbErr::Specialize(data.to_vec()))
        }
    }
}

fn send_raw_vendor_cmd(
    env: JNIEnv,
    obj: JObject,
    gid: u32,
    oid: u32,
    payload: jbyteArray,
) -> Result<(i32, i32, Vec<u8>), UwbErr> {
    let payload = env.convert_byte_array(payload)?;
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(JNICommand::UciRawVendorCmd { gid, oid, payload })? {
        UciResponse::RawVendorRsp(response) => Ok((
            response.get_group_id().to_i32().unwrap(),
            response.get_opcode().to_i32().unwrap(),
            get_vendor_uci_payload(response)?,
        )),
        _ => Err(UwbErr::failed()),
    }
}

fn status_code_to_res(status: StatusCode) -> Result<(), UwbErr> {
    match status {
        StatusCode::UciStatusOk => Ok(()),
        _ => Err(UwbErr::failed()),
    }
}

fn get_dispatcher<'a>(env: JNIEnv, obj: JObject) -> Result<&'a mut Dispatcher, UwbErr> {
    let dispatcher_ptr_value = env.get_field(obj, "mDispatcherPointer", "J")?;
    let dispatcher_ptr = dispatcher_ptr_value.j()?;
    if dispatcher_ptr == 0i64 {
        error!("The dispatcher is not initialized.");
        return Err(UwbErr::NoneDispatcher);
    }
    // Safety: dispatcher pointer must not be a null pointer and it must point to a valid dispatcher object.
    // This can be ensured because the dispatcher is created in an earlier stage and
    // won't be deleted before calling doDeinitialize.
    unsafe { Ok(&mut *(dispatcher_ptr as *mut Dispatcher)) }
}

/// create a dispatcher instance
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDispatcherNew(
    env: JNIEnv,
    obj: JObject,
) -> jlong {
    let eventmanager = match EventManager::new(env, obj) {
        Ok(evtmgr) => evtmgr,
        Err(err) => {
            error!("Fail to create event manager{:?}", err);
            return *JObject::null() as jlong;
        }
    };
    match Dispatcher::new(eventmanager) {
        Ok(dispatcher) => Box::into_raw(Box::new(dispatcher)) as jlong,
        Err(err) => {
            error!("Fail to create dispatcher {:?}", err);
            *JObject::null() as jlong
        }
    }
}

/// destroy the dispatcher instance
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDispatcherDestroy(
    env: JNIEnv,
    obj: JObject,
) {
    let dispatcher_ptr_value = match env.get_field(obj, "mDispatcherPointer", "J") {
        Ok(value) => value,
        Err(err) => {
            error!("Failed to get the pointer with: {:?}", err);
            return;
        }
    };
    let dispatcher_ptr = match dispatcher_ptr_value.j() {
        Ok(value) => value,
        Err(err) => {
            error!("Failed to get the pointer with: {:?}", err);
            return;
        }
    };
    // Safety: dispatcher pointer must not be a null pointer and must point to a valid dispatcher object.
    // This can be ensured because the dispatcher is created in an earlier stage and
    // won't be deleted before calling this destroy function.
    // This function will early return if the instance is already destroyed.
    let _boxed_dispatcher = unsafe { Box::from_raw(dispatcher_ptr as *mut Dispatcher) };
    info!("The dispatcher successfully destroyed.");
}

fn get_power_stats<'a>(env: JNIEnv, obj: JObject) -> Result<[JValue<'a>; 4], UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(JNICommand::UciGetPowerStats)? {
        UciResponse::AndroidGetPowerStatsRsp(data) => Ok([
            JValue::Int(data.get_stats().idle_time_ms as i32),
            JValue::Int(data.get_stats().tx_time_ms as i32),
            JValue::Int(data.get_stats().rx_time_ms as i32),
            JValue::Int(data.get_stats().total_wake_count as i32),
        ]),
        _ => Err(UwbErr::failed()),
    }
}

fn uwa_get_device_info(dispatcher: &Dispatcher) -> Result<UciResponse, UwbErr> {
    let res = dispatcher.block_on_jni_command(JNICommand::UciGetDeviceInfo)?;
    Ok(res)
}

fn reset_device(env: JNIEnv, obj: JObject, reset_config: u8) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciDeviceReset { reset_config })? {
        UciResponse::DeviceResetRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

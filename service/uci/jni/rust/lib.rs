//! jni for uwb native stack
use android_logger::FilterBuilder;
use jni::JNIEnv;
use jni::objects::{JObject, JValue};
use jni::sys::{jboolean, jbyte, jbyteArray, jint, jintArray, jlong, jobject};
use log::{error, info, warn, LevelFilter};
use uwb_uci_rust::event_manager::EventManager;
use uwb_uci_rust::error::UwbErr;
use uwb_uci_rust::uci::{BlockingJNICommand, Dispatcher, JNICommand, uci_hrcv::UciResponse};
use uwb_uci_packets::StatusCode;

const STATUS_OK: i8 = 0;
const STATUS_FAILED: i8 = 2;

/// Initialize UWB
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeInit(_env: JNIEnv, _obj: JObject) -> jboolean {
    let crates_log_lvl_filter = FilterBuilder::new()
            .filter(None, LevelFilter::Trace) // default log level
            .filter(Some("jni"), LevelFilter::Info) // reduced log level for jni crate
            .build();
    android_logger::init_once(
        android_logger::Config::default().with_tag("uwb").with_min_level(log::Level::Trace).with_filter(crates_log_lvl_filter),
    );
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeInit: enter");
    true as jboolean
}

/// Get max session number
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetMaxSessionNumber(_env: JNIEnv, _obj: JObject) -> jint {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetMaxSessionNumber: enter");
    5
}

/// Turn on UWB. initialize the GKI module and HAL module for UWB device.
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDoInitialize(env: JNIEnv, obj: JObject) -> jboolean {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeDoInitialize: enter");
    boolean_result_helper(do_initialize(env, obj), "DoInitialize")
}

/// Turn off UWB. Deinitilize the GKI and HAL module, power of the UWB device.
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDoDeinitialize(env: JNIEnv, obj: JObject) -> jboolean {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeDoDeinitialize: enter");
    boolean_result_helper(do_deinitialize(env, obj), "DoDeinitialize")
}

/// get nanos
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos(_env: JNIEnv, _obj: JObject) -> jlong {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos: enter");
    0
}

/// retrieve the UWB device specific information etc.
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetSpecificationInfo(env: JNIEnv, obj: JObject) -> jobject {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetSpecificationInfo: enter");
    let uwb_specification_info_class = env.find_class("com/android/uwb/info/UwbSpecificationInfo").unwrap();
    match get_specification_info(env, obj) {
        Ok(para) => {
            let specification_info = env.new_object(uwb_specification_info_class, "(IIIIIIIIIIIIIIII)V", &para).unwrap();
            *specification_info
        },
        Err(e) => {
            error!("Get specification info failed with: {:?}", e);
            *JObject::null()
        }
    }
}

/// reset the device
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeResetDevice(_env: JNIEnv, _obj: JObject, _reset_config: jbyte) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeResetDevice: enter");
    // TODO: implement this function
    0
}

/// init the session
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeSessionInit(env: JNIEnv, obj: JObject, session_id: jint, session_type: jbyte) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeSessionInit: enter");
    byte_result_helper(session_init(env, obj, session_id as u32, session_type as u8), "SessionInit")
}

/// deinit the session
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeSessionDeInit(env: JNIEnv, obj: JObject, session_id: jint) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeSessionDeInit: enter");
    byte_result_helper(session_deinit(env, obj, session_id as u32), "SessionDeInit")
}

/// get session count
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetSessionCount(env: JNIEnv, obj: JObject) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetSessionCount: enter");
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
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeRangingStart(env: JNIEnv, obj: JObject, session_id: jint) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeRangingStart: enter");
    byte_result_helper(ranging_start(env, obj, session_id as u32), "RangingStart")
}

/// stop the ranging
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeRangingStop(env: JNIEnv, obj: JObject, session_id: jint) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeRangingStop: enter");
    byte_result_helper(ranging_stop(env, obj, session_id as u32), "RangingStop")
}

/// get the session state
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetSessionState(env: JNIEnv, obj: JObject, session_id: jint) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetSessionState: enter");
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
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeSetAppConfigurations(env: JNIEnv, _obj: JObject, _session_id: jint, _no_of_params: jint, _app_config_param_len: jint, _app_config_params: jbyteArray) -> jbyteArray {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeSetAppConfigurations: enter");
    // TODO: implement this function
    let buf = [1; 10];
    env.byte_array_from_slice(&buf).unwrap()
}

/// get app configurations
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetAppConfigurations(env: JNIEnv, _obj: JObject, _session_id: jint, _no_of_params: jint, _app_config_param_len: jint, _app_config_params: jbyteArray) -> jbyteArray {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetAppConfigurations: enter");
    // TODO: implement this function
    let buf = [1; 10];
    env.byte_array_from_slice(&buf).unwrap()
}

/// update multicast list
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate(env: JNIEnv, obj: JObject, session_id: jint, action: jbyte, no_of_controlee: jbyte, address: jbyteArray, sub_session_id: jintArray) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate: enter");
    byte_result_helper(multicast_list_update(env, obj, session_id as u32, action as u8, no_of_controlee as u8, address, sub_session_id), "ControllerMulticastListUpdate")
}

/// set country code
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeSetCountryCode(env: JNIEnv, obj: JObject, country_code: jbyteArray) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeSetCountryCode: enter");
    byte_result_helper(set_country_code(env, obj, country_code), "SetCountryCode")
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
    dispatcher.send_jni_command(JNICommand::UwaEnable)?;
    uwa_init(); // todo: implement this
    clear_all_session_context(); // todo: implement this
    uwa_enable()?; // todo: implement this, and add a lock here
    match uwa_get_device_info(dispatcher) {
        Ok(res) => {
            if let UciResponse::GetDeviceInfoRsp(device_info) = res {
                dispatcher.device_info = Some(device_info);
            }
        },
        Err(e) => {
            warn!("Failed to get device info with: {:?}", e);
            return Err(UwbErr::failed());
        },
    }
    match set_core_device_configurations() {
        Ok(()) => {
            info!("set_core_device_configurations is success");
            return Ok(());
        },
        _ => info!("set_core_device_configurations is failed"),
    };
    match uwa_disable(false) {
        Ok(()) => info!("UWA_disable(false) success."),
        _ => warn!("UWA_disable(false) is failed."),
    };
    Err(UwbErr::failed())
}

fn do_deinitialize(env: JNIEnv, obj: JObject) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    dispatcher.send_jni_command(JNICommand::UwaDisable(true))?;
    dispatcher.send_jni_command(JNICommand::Exit)?;
    Ok(())
}

fn get_specification_info<'a>(env: JNIEnv, obj: JObject) -> Result<[JValue<'a>; 16], UwbErr> {
    let mut para = [JValue::Int(0); 16];
    let dispatcher = get_dispatcher(env, obj)?;
    if dispatcher.device_info.is_none() {
        warn!("Fail to get specification info.");
        return Err(UwbErr::failed());
    }
    if let Some(data) = &dispatcher.device_info {
        para = [
            JValue::Int((data.uci_version & 0xFF).into()),
            JValue::Int(((data.uci_version >> 8) & 0xF).into()),
            JValue::Int(((data.uci_version >> 12) & 0xF).into()),
            JValue::Int((data.mac_version & 0xFF).into()),
            JValue::Int(((data.mac_version >> 8) & 0xF).into()),
            JValue::Int(((data.mac_version >> 12) & 0xF).into()),
            JValue::Int((data.phy_version & 0xFF).into()),
            JValue::Int(((data.phy_version >> 8) & 0xF).into()),
            JValue::Int(((data.phy_version >> 12) & 0xF).into()),
            JValue::Int((data.uci_test_version & 0xFF).into()),
            JValue::Int(((data.uci_test_version >> 8) & 0xF).into()),
            JValue::Int(((data.uci_test_version >> 12) & 0xF).into()),
            JValue::Int(1), // fira_major_version
            JValue::Int(0), // fira_minor_version
            JValue::Int(1), // ccc_major_version
            JValue::Int(0) // ccc_minor_version
        ];
    }
    Ok(para)
}

fn session_init(env: JNIEnv, obj: JObject, session_id: u32, session_type: u8) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(BlockingJNICommand::UwaSessionInit(session_id, session_type))? {
        UciResponse::SessionInitRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.status)
}

fn session_deinit(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(BlockingJNICommand::UwaSessionDeinit(session_id))? {
        UciResponse::SessionDeinitRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.status)
}

fn get_session_count(env: JNIEnv, obj: JObject) -> Result<jbyte, UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(BlockingJNICommand::UwaSessionGetCount)? {
        UciResponse::SessionGetCountRsp(data) => {
            Ok(data.session_count as jbyte)
        },
        _ => Err(UwbErr::failed()),
    }
}

fn ranging_start(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(BlockingJNICommand::UwaStartRange(session_id))? {
        UciResponse::RangeStartRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.status)
}

fn ranging_stop(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(BlockingJNICommand::UwaStopRange(session_id))? {
        UciResponse::RangeStopRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.status)
}

fn get_session_state(env: JNIEnv, obj: JObject, session_id: u32) -> Result<jbyte, UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    match dispatcher.block_on_jni_command(BlockingJNICommand::UwaGetSessionState(session_id))? {
        UciResponse::SessionGetStateRsp(data) => {
            Ok(data.session_state as jbyte)
        },
        _ => Err(UwbErr::failed()),
    }
}

fn multicast_list_update(env: JNIEnv, obj: JObject, session_id: u32, action: u8, no_of_controlee: u8, address: jbyteArray, sub_session_id: jintArray) -> Result<(), UwbErr> {
    let address_list = env.convert_byte_array(address)?;
    let mut sub_session_id_list = vec![0i32; env.get_array_length(sub_session_id)?.try_into().unwrap()];
    env.get_int_array_region(sub_session_id, 0, &mut sub_session_id_list)?;
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(BlockingJNICommand::UwaSessionUpdateMulticastList{session_id, action, no_of_controlee, address_list, sub_session_id_list: sub_session_id_list.to_vec()})? {
        UciResponse::SessionUpdateControllerMulticastListRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.status)
}

fn set_country_code(env: JNIEnv, obj: JObject, country_code: jbyteArray) -> Result<(), UwbErr> {
    let code = env.convert_byte_array(country_code)?;
    if code.len() != 2 {
        return Err(UwbErr::failed());
    }
    let dispatcher = get_dispatcher(env, obj)?;
    let res = match dispatcher.block_on_jni_command(BlockingJNICommand::UwaSetCountryCode { code })? {
        UciResponse::AndroidSetCountryCodeRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.status)
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
        warn!("The dispatcher is not initialized.");
        return Err(UwbErr::NoneDispatcher);
    }
    // Safety: dispatcher pointer must not be a null pointer and it must point to a valid dispatcher object.
    // This can be ensured because the dispatcher is created in an earlier stage and
    // won't be deleted before calling doDeinitialize.
    unsafe { Ok(&mut *(dispatcher_ptr as *mut Dispatcher)) }
}

/// create a dispatcher instance
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDispatcherNew(env: JNIEnv, obj: JObject) -> jlong {
    let eventmanager = EventManager::new(env, obj).expect("Failed to create event manager");
    let dispatcher = match Dispatcher::new(eventmanager) {
        Ok(dispatcher) => dispatcher,
        Err(_err) => panic!("Fail to create dispatcher"),
    };
    Box::into_raw(Box::new(dispatcher)) as jlong
}

/// destroy the dispatcher instance
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDispatcherDestroy(env: JNIEnv, obj: JObject) {
    let dispatcher_ptr_value = match env.get_field(obj, "mDispatcherPointer", "J") {
        Ok(value) => value,
        Err(err) => {
            error!("Failed to get the pointer with: {:?}", err);
            return;
        }
    };
    let dispatcher_ptr = dispatcher_ptr_value.j().expect("Failed to get the pointer!");
    // Safety: dispatcher pointer must not be a null pointer and must point to a valid dispatcher object.
    // This can be ensured because the dispatcher is created in an earlier stage and
    // won't be deleted before calling this destroy function.
    // This function will early return if the instance is already destroyed.
    let _boxed_dispatcher = unsafe { Box::from_raw(dispatcher_ptr as *mut Dispatcher) };
    info!("The dispatcher successfully destroyed.");
}

// TODO: Implement these functions
fn uwa_init() {

}

fn uwa_get_device_info(dispatcher: &Dispatcher) -> Result<UciResponse, UwbErr> {
    let res = dispatcher.block_on_jni_command(BlockingJNICommand::GetDeviceInfo)?;
    Ok(res)
}

fn uwa_enable() -> Result<(), UwbErr> {
    Ok(())
}

fn clear_all_session_context() {

}

fn uwa_disable(_para: bool) -> Result<(), UwbErr> {
    Err(UwbErr::refused())
}

fn set_core_device_configurations() -> Result<(), UwbErr> {
    Ok(())
}

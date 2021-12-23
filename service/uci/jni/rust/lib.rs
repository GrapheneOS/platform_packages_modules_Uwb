//! jni for uwb native stack
use jni::JNIEnv;
use jni::objects::{JObject, JValue};
use jni::sys::{jboolean, jbyte, jbyteArray, jint, jintArray, jlong, jobject};
use log::{error, info, warn};
use uwb_uci_rust::adaptation::{THalUwbEntry, UwbAdaptation, UwbErr};
use uwb_uci_rust::uci::{Dispatcher, JNICommand};

const STATUS_OK: i8 = 0;
const STATUS_FAILED: i8 = 2;

/// Initialize UWB
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeInit(_env: JNIEnv, _obj: JObject) -> jboolean {
    logger::init(
        logger::Config::default().with_tag_on_device("uwb").with_min_level(log::Level::Trace),
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
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetSpecificationInfo(env: JNIEnv, _obj: JObject) -> jobject {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetSpecificationInfo: enter");
    // TODO: implement this function
    let uwb_specification_info_class = env.find_class("com/android/uwb/info/UwbSpecificationInfo").unwrap();
    let specification_info = env.new_object(uwb_specification_info_class, "(IIIIIIIIIIII)V", &[JValue::Int(1); 12]).unwrap();
    *specification_info
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
    byte_result_helper(get_session_count(env, obj), "GetSessionCount")
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
    byte_result_helper(get_session_state(env, obj, session_id as u32), "GetSessionState")
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
    byte_result_helper(multicast_list_update(env, obj, session_id as u32, action as u8, no_of_controlee as u8, address, &sub_session_id), "ControllerMulticastListUpdate")
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
    dispatch_command(env, obj, JNICommand::UwaEnable)?;
    let mut uwb_adaptation = UwbAdaptation::new(THalUwbEntry::default(), None);
    uwb_adaptation.initialize();
    let hal_func_entries = uwb_adaptation.get_hal_entry_funcs();
    uwa_init(hal_func_entries); // todo: implement this
    clear_all_session_context(); // todo: implement this
    uwa_enable()?; // todo: implement this, and add a lock here
    uwb_adaptation.core_initialization()?;
    uwa_get_device_info()?;
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
    uwb_adaptation.finalize(false);
    Err(UwbErr::Failed)
}

fn do_deinitialize(env: JNIEnv, obj: JObject) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    dispatcher.send_jni_command(JNICommand::UwaDisable(true)).map_err(|_| UwbErr::Undefined)?;
    dispatcher.send_jni_command(JNICommand::Exit).map_err(|_| UwbErr::Undefined)?;
    Ok(())
}

fn session_init(env: JNIEnv, obj: JObject, session_id: u32, session_type: u8) -> Result<(), UwbErr> {
    dispatch_command(env, obj, JNICommand::UwaSessionInit(session_id, session_type))
}

fn session_deinit(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    dispatch_command(env, obj, JNICommand::UwaSessionDeinit(session_id))
}

fn get_session_count(env: JNIEnv, obj: JObject) -> Result<(), UwbErr> {
    dispatch_command(env, obj, JNICommand::UwaSessionGetCount)
}

fn ranging_start(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    dispatch_command(env, obj, JNICommand::UwaStartRange(session_id))
}

fn ranging_stop(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    dispatch_command(env, obj, JNICommand::UwaStopRange(session_id))
}

fn get_session_state(env: JNIEnv, obj: JObject, session_id: u32) -> Result<(), UwbErr> {
    dispatch_command(env, obj, JNICommand::UwaGetSessionState(session_id))
}

fn multicast_list_update(env: JNIEnv, obj: JObject, session_id: u32, action: u8, no_of_controlee: u8, address: jbyteArray, sub_session_id: &jintArray) -> Result<(), UwbErr> {
    let address_list = env.convert_byte_array(address).map_err(|_| UwbErr::Undefined)?;
    let sub_session_id_list: &mut [i32] = &mut [];
    env.get_int_array_region(*sub_session_id, 0, sub_session_id_list).map_err(|_| UwbErr::Undefined)?;
    dispatch_command(env, obj, JNICommand::UwaSessionUpdateMulticastList{session_id, action, no_of_controlee, address_list, sub_session_id_list: sub_session_id_list.to_vec()})
}

fn set_country_code(env: JNIEnv, obj: JObject, country_code: jbyteArray) -> Result<(), UwbErr> {
    let code = env.convert_byte_array(country_code).map_err(|_| UwbErr::Undefined)?;
    dispatch_command(env, obj, JNICommand::UwaSetCountryCode{code})
}

fn dispatch_command(env: JNIEnv, obj: JObject, command: JNICommand) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    dispatcher.send_jni_command(command).map_err(|_| UwbErr::Undefined)?;
    Ok(())
}

fn get_dispatcher<'a>(env: JNIEnv, obj: JObject) -> Result<&'a mut Dispatcher, UwbErr> {
    let dispatcher_ptr_value = env.get_field(obj, "mDispatcherPointer", "J").map_err(|_| UwbErr::Undefined)?;
    let dispatcher_ptr = dispatcher_ptr_value.j().map_err(|_| UwbErr::Undefined)?;
    // Safety: dispatcher pointer must not be a null pointer and it must point to a valid dispatcher object.
    // This can be ensured because the dispatcher is created in an earlier stage and
    // won't be deleted before calling doDeinitialize.
    unsafe { Ok(&mut *(dispatcher_ptr as *mut Dispatcher)) }
}

/// create a dispatcher instance
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDispatcherNew(_env: JNIEnv, _obj: JObject) -> jlong {
    let dispatcher = match Dispatcher::new() {
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
fn uwa_init(_hal_func_entries: THalUwbEntry) {

}

fn uwa_get_device_info() -> Result<(), UwbErr> {
    Ok(())
}

fn uwa_enable() -> Result<(), UwbErr> {
    Ok(())
}

fn clear_all_session_context() {

}

fn uwa_disable(_para: bool) -> Result<(), UwbErr> {
    Err(UwbErr::Refused)
}

fn set_core_device_configurations() -> Result<(), UwbErr> {
    Err(UwbErr::Failed)
}

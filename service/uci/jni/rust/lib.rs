//! jni for uwb native stack
use jni::JNIEnv;
use jni::objects::{JClass, JObject, JValue};
use jni::sys::{jboolean, jbyte, jbyteArray, jint, jintArray, jlong, jobject};
use log::{error, info, warn};
use uwb_uci_rust::adaptation::{THalUwbEntry, UwbAdaptation, UwbErr};
use uwb_uci_rust::uci::{Dispatcher, JNICommand};

/// Initialize UWB
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeInit(_env: JNIEnv, _class: JClass) -> jboolean {
    logger::init(
        logger::Config::default().with_tag_on_device("uwb").with_min_level(log::Level::Trace),
    );
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeInit: enter");
    true as jboolean
}

/// Get max session number
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetMaxSessionNumber(_env: JNIEnv, _class: JClass) -> jint {
    5
}

/// Turn on UWB. initialize the GKI module and HAL module for UWB device.
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDoInitialize(env: JNIEnv, obj: JObject) -> jboolean {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeDoInitialize: enter");
    match do_initialize(env, obj) {
        Ok(()) => true as jboolean,
        Err(err) => {
            error!("Initialize failed with: {:?}", err);
            false as jboolean
        }
    }
}

/// Turn off UWB. Deinitilize the GKI and HAL module, power of the UWB device.
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDoDeinitialize(_env: JNIEnv, _class: JClass) -> jboolean {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeDoDeinitialize: enter");
    // TODO: implement this function
    true as jboolean
}

/// get nanos
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos(_env: JNIEnv, _class: JClass) -> jlong {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos: enter");
    0
}

/// retrieve the UWB device specific information etc.
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetSpecificationInfo(env: JNIEnv, _class: JClass) -> jobject {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetSpecificationInfo: enter");
    // TODO: implement this function
    let uwb_specification_info_class = env.find_class("com/android/uwb/info/UwbSpecificationInfo").unwrap();
    let specification_info = env.new_object(uwb_specification_info_class, "(IIIIIIIIIIII)V", &[JValue::Int(1); 12]).unwrap();
    *specification_info
}

/// reset the device
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeResetDevice(_env: JNIEnv, _class: JClass, _reset_config: jbyte) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeResetDevice: enter");
    // TODO: implement this function
    0
}

/// init the session
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeSessionInit(_env: JNIEnv, _class: JClass, _session_id: jint, _session_type: jbyte) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeSessionInit: enter");
    // TODO: implement this function
    0
}

/// deinit the session
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeSessionDeInit(_env: JNIEnv, _class: JClass, _session_id: jint) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeSessionDeInit: enter");
    // TODO: implement this function
    0
}

/// get session count
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetSessionCount(_env: JNIEnv, _class: JClass) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetSessionCount: enter");
    // TODO: implement this function
    0
}

///  start the ranging
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeRangingStart(_env: JNIEnv, _class: JClass, _session_id: jint) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeRangingStart: enter");
    // TODO: implement this function
    0
}

/// stop the ranging
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeRangingStop(_env: JNIEnv, _class: JClass, _session_id: jint) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeRangingStop: enter");
    // TODO: implement this function
    0
}

/// get the session state
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeGetSessionState(_env: JNIEnv, _class: JClass, _session_id: jint) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeGetSessionState: enter");
    // TODO: implement this function
    0
}

/// set app configurations
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeSetAppConfigurations(env: JNIEnv, _class: JClass, _session_id: jint, _no_of_params: jint, _app_config_param_len: jint, _app_config_params: jbyteArray) -> jbyteArray {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeSetAppConfigurations: enter");
    // TODO: implement this function
    let buf = [1; 10];
    env.byte_array_from_slice(&buf).unwrap()
}

/// update multicast list
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate(_env: JNIEnv, _class: JClass, _session_id: jint, _action: jbyte, _no_of_controlee: jbyte, _address: jbyteArray, _sub_session_id: jintArray) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate: enter");
    // TODO: implement this function
    0
}

/// set country code
#[no_mangle]
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeSetCountryCode(_env: JNIEnv, _class: JClass, _country_code: jbyteArray) -> jbyte {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeSetCountryCode: enter");
    // TODO: implement this function
    0
}

fn do_initialize(env: JNIEnv, obj: JObject) -> Result<(), UwbErr> {
    let dispatcher = get_dispatcher(env, obj)?;
    dispatcher.send_jni_command(JNICommand::UwaDmApiEnable).map_err(|_| UwbErr::Undefined)?;
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
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDispatcherNew(_env: JNIEnv, _class: JClass) -> jlong {
    let dispatcher = match Dispatcher::new() {
        Ok(dispatcher) => dispatcher,
        Err(_err) => panic!("Fail to create dispatcher"),
    };
    Box::into_raw(Box::new(dispatcher)) as jlong
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

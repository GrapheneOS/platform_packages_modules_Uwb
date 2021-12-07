//! jni for uwb native stack
use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jboolean, jint};
use log::{error, info, warn};
use uwb_uci_rust::adaptation::{THalUwbEntry, UwbAdaptation, UwbErr};

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
pub extern "system" fn Java_com_android_uwb_jni_NativeUwbManager_nativeDoInitialize(_env: JNIEnv, _class: JClass) -> jboolean {
    info!("Java_com_android_uwb_jni_NativeUwbManager_nativeDoInitialize: enter");
    match do_initialize() {
        Ok(()) => true as jboolean,
        Err(err) => {
            error!("Initialize failed with: {:?}", err);
            false as jboolean
        }
    }
}

fn do_initialize() -> Result<(), UwbErr> {
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
            info!("set_core_device_configurations is SUCCESS");
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

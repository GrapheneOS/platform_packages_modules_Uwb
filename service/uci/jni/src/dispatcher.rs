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

//! Implementation of Dispatcher and related methods.

use crate::error::{Error, Result};
use crate::notification_manager_android::NotificationManagerAndroidBuilder;

use std::collections::HashMap;
use std::ops::Deref;
use std::sync::Arc;

use jni::objects::{GlobalRef, JObject, JString};
use jni::{JNIEnv, JavaVM, MonitorGuard};
use tokio::runtime::{Builder as RuntimeBuilder, Runtime};
use uci_hal_android::uci_hal_android::UciHalAndroid;
use uwb_core::error::{Error as UwbCoreError, Result as UwbCoreResult};
use uwb_core::uci::pcapng_uci_logger_factory::PcapngUciLoggerFactoryBuilder;
use uwb_core::uci::uci_logger::UciLoggerMode;
use uwb_core::uci::uci_logger_factory::UciLoggerFactory;
use uwb_core::uci::uci_manager_sync::UciManagerSync;

/// Dispatcher is managed by Java side. Construction and Destruction are provoked by JNI function
/// nativeDispatcherNew and nativeDispatcherDestroy respectively.
/// Destruction does NOT wait until the spawned threads are closed.
pub(crate) struct Dispatcher {
    pub manager_map: HashMap<String, UciManagerSync>,
    _runtime: Runtime,
}
impl Dispatcher {
    /// Constructs Dispatcher.
    pub fn new<T: AsRef<str>>(
        vm: &'static Arc<JavaVM>,
        class_loader_obj: GlobalRef,
        callback_obj: GlobalRef,
        chip_ids: &[T],
    ) -> UwbCoreResult<Dispatcher> {
        let runtime = RuntimeBuilder::new_multi_thread()
            .thread_name("UwbService")
            .enable_all()
            .build()
            .map_err(|_| UwbCoreError::Unknown)?;
        let mut manager_map = HashMap::<String, UciManagerSync>::new();
        let mut log_file_factory = PcapngUciLoggerFactoryBuilder::new()
            .log_path("/data/misc/apexdata/com.android.uwb/log".into())
            .filename_prefix("uwb_uci".to_owned())
            .runtime_handle(runtime.handle().to_owned())
            .build()
            .ok_or(UwbCoreError::Unknown)?;
        for chip_id in chip_ids {
            let logger =
                log_file_factory.build_logger(chip_id.as_ref()).ok_or(UwbCoreError::Unknown)?;
            let manager = UciManagerSync::new(
                UciHalAndroid::new(chip_id.as_ref()),
                NotificationManagerAndroidBuilder {
                    chip_id: chip_id.as_ref().to_owned(),
                    vm,
                    class_loader_obj: class_loader_obj.clone(),
                    callback_obj: callback_obj.clone(),
                },
                logger,
                runtime.handle().to_owned(),
            )?;
            manager_map.insert(chip_id.as_ref().to_string(), manager);
        }
        Ok(Self { manager_map, _runtime: runtime })
    }

    /// Sets log mode for all chips.
    pub fn set_logger_mode(&self, logger_mode: UciLoggerMode) -> UwbCoreResult<()> {
        for (_, manager) in self.manager_map.iter() {
            manager.set_logger_mode(logger_mode.clone())?;
        }
        Ok(())
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

    /// Gets reference to Dispatcher.
    ///
    /// # Safety
    /// Must be called from a Java object holding a valid or null mDispatcherPointer.
    pub unsafe fn get_dispatcher<'a>(
        env: JNIEnv<'a>,
        obj: JObject<'a>,
    ) -> Result<GuardedDispatcher<'a>> {
        let guard = env.lock_obj(obj)?;
        let dispatcher_ptr_value = env.get_field(obj, "mDispatcherPointer", "J")?.j()?;
        if dispatcher_ptr_value == 0 {
            return Err(Error::UwbCoreError(UwbCoreError::BadParameters));
        }
        let dispatcher_ptr = dispatcher_ptr_value as *const Dispatcher;
        Ok(GuardedDispatcher { _guard: guard, dispatcher: &*dispatcher_ptr })
    }

    /// Gets reference to UciManagerSync with chip_id.
    ///
    /// # Safety
    /// Must be called from a Java object holding a valid or null mDispatcherPointer.
    pub unsafe fn get_uci_manager<'a>(
        env: JNIEnv<'a>,
        obj: JObject<'a>,
        chip_id: JString,
    ) -> Result<GuardedUciManager<'a>> {
        // Safety: get_dispatcher and get_uci_manager has the same assumption.
        let guarded_dispatcher = Self::get_dispatcher(env, obj)?;
        let chip_id_str = String::from(env.get_string(chip_id)?);
        guarded_dispatcher.into_guarded_uci_manager(&chip_id_str)
    }
}

/// Lifetimed reference to UciManagerSync that locks Java object while reference is alive.
pub(crate) struct GuardedUciManager<'a> {
    _guard: MonitorGuard<'a>,
    uci_manager: &'a UciManagerSync,
}

impl<'a> Deref for GuardedUciManager<'a> {
    type Target = UciManagerSync;
    fn deref(&self) -> &Self::Target {
        self.uci_manager
    }
}

/// Lifetimed reference to Dispatcher that locks Java object while reference is alive.
pub(crate) struct GuardedDispatcher<'a> {
    _guard: MonitorGuard<'a>,
    dispatcher: &'a Dispatcher,
}

impl<'a> GuardedDispatcher<'a> {
    pub fn into_guarded_uci_manager(self, chip_id: &str) -> Result<GuardedUciManager<'a>> {
        let uci_manager = self
            .dispatcher
            .manager_map
            .get(chip_id)
            .ok_or(Error::UwbCoreError(UwbCoreError::BadParameters))?;
        Ok(GuardedUciManager { _guard: self._guard, uci_manager })
    }
}

impl<'a> Deref for GuardedDispatcher<'a> {
    type Target = Dispatcher;
    fn deref(&self) -> &Self::Target {
        self.dispatcher
    }
}

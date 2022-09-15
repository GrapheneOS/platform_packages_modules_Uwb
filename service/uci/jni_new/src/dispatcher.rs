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

//! Implementation of Dispatcher.

use crate::notification_manager_android::NotificationManagerAndroidBuilder;

use std::collections::HashMap;
use std::sync::Arc;

use jni::objects::GlobalRef;
use jni::JavaVM;
use uci_hal_android::uci_hal_android::UciHalAndroid;
use uwb_core::error::Result as UwbCoreResult;
use uwb_core::uci::uci_manager_sync::UciManagerSync;

/// Dispatcher is managed by Java side. Construction and Destruction are provoked by JNI function
/// nativeDispatcherNew and nativeDispatcherDestroy respectively.
/// Destruction does NOT wait until the spawned threads are closed.
pub(crate) struct Dispatcher {
    pub manager_map: HashMap<String, UciManagerSync>,
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

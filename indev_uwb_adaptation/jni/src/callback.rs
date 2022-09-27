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

//! Defines the callback structure that knows how to communicate with Java UWB service
//! when certain events happen such as device reset, data or notification received
//! and status changes.
use uwb_core::params::uci_packets::{DeviceState, ReasonCode, SessionId, SessionState};
use uwb_core::service::{UwbServiceCallback, UwbServiceCallbackBuilder};
use uwb_core::uci::SessionRangeData;

pub struct UwbServiceCallbackBuilderImpl {}
impl UwbServiceCallbackBuilder<UwbServiceCallbackImpl> for UwbServiceCallbackBuilderImpl {
    fn build(self) -> Option<UwbServiceCallbackImpl> {
        Some(UwbServiceCallbackImpl {})
    }
}

// TODO(b/244785972): implement this with caching
pub struct UwbServiceCallbackImpl {}
impl UwbServiceCallback for UwbServiceCallbackImpl {
    fn on_service_reset(&mut self, success: bool) {
        todo!(); // call java
    }

    fn on_uci_device_status_changed(&mut self, state: DeviceState) {
        todo!(); // call java
    }

    fn on_session_state_changed(
        &mut self,
        session_id: SessionId,
        session_state: SessionState,
        reason_code: ReasonCode,
    ) {
        todo!(); // call java
    }

    fn on_range_data_received(&mut self, session_id: SessionId, range_data: SessionRangeData) {
        todo!(); // call java
    }

    fn on_vendor_notification_received(&mut self, gid: u32, oid: u32, payload: Vec<u8>) {
        todo!(); // call java
    }
}

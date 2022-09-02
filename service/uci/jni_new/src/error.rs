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

//! Error type.

use jni::errors::Error as JNIError;
use uwb_core::error::Error as UwbCoreError;

#[derive(Debug, thiserror::Error)]
pub(crate) enum Error {
    #[error("UwbCore error: {0:?}")]
    UwbCoreError(#[from] UwbCoreError),
    #[error("JNI error: {0:?}")]
    JNIError(#[from] JNIError),
}
pub(crate) type Result<T> = std::result::Result<T, Error>;

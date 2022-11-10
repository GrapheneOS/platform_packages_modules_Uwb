/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Copyright 2021-2022 NXP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _UWBAPI_INTERNAL_H_
#define _UWBAPI_INTERNAL_H_

#include <nativehelper/ScopedLocalRef.h>

#include "UwbJniTypes.h"
#include "UwbJniUtil.h"
#include "uwa_api.h"

namespace android {

#define UWB_CMD_TIMEOUT 4000 // JNI API wait timout

/* extern declarations */
extern bool uwb_debug_enabled;
extern bool gIsUwaEnabled;

#define TDOA_TX_TIMESTAMP_OFFSET 0x00FF
#define TDOA_TX_TIMESTAMP_OFFSET_MASK 0x06
#define TDOA_RX_TIMESTAMP_OFFSET 0x00FF
#define TDOA_RX_TIMESTAMP_OFFSET_MASK 0x18
#define TDOA_ANCHOR_LOC_OFFSET 0x00FF
#define TDOA_ANCHOR_LOC_OFFSET_MASK 0x60
#define TDOA_ACTIVE_RR_OFFSET 0x0FF0
#define TDOA_ACTIVE_RR_OFFSET_MASK 0x0780

#define TDOA_TX_TIMESTAMP_40BITS 0
#define TDOA_TX_TIMESTAMP_64BITS 2
#define TDOA_RX_TIMESTAMP_40BITS 0
#define TDOA_RX_TIMESTAMP_64BITS 8
#define TDOA_ANCHOR_LOC_NOT_INCLUDED 0
#define TDOA_ANCHOR_LOC_IN_RELATIVE_SYSTEM 0x40
#define TDOA_ANCHOR_LOC_IN_WGS84_SYSTEM 0x20
#define MAC_SHORT_ADD_LEN 2
#define MAC_EXT_ADD_LEN 8
#define TDOA_TIMESTAMP_LEN_40BITS 5
#define TDOA_TIMESTAMP_LEN_64BITS 8
#define TDOA_ANCHOR_LOC_LEN_10BYTES 10
#define TDOA_ANCHOR_LOC_LEN_12BYTES 12
#define TDOA_ACTIVE_RR_INDEX_POSITION 7

#define TDOA_SESSION_ID_LEN 4
#define TDOA_PARAM_LEN_1_BYTE 1

void clearRfTestContext();
void uwaRfTestDeviceManagementCallback(uint8_t dmEvent,
                                       tUWA_DM_TEST_CBACK_DATA *eventData);
} // namespace android
#endif

/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Copyright 20212022 NXP.
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

#include "UwbJniInternal.h"
#include "UwbEventManager.h"
#include "JniLog.h"
#include "ScopedJniEnv.h"
#include "SyncEvent.h"
#include "UwbAdaptation.h"
#include "uwb_config.h"
#include "uwb_hal_int.h"

namespace android {

const char *RANGING_DATA_CLASS_NAME = "com/android/server/uwb/data/UwbRangingData";
const char *RANGING_MEASURES_CLASS_NAME =
    "com/android/server/uwb/data/UwbTwoWayMeasurement";
/* ranging tdoa measures and multicast list update ntf events are implemented as
   per Fira specification.
       TODO support for these class to be added in service.*/
const char *MULTICAST_UPDATE_LIST_DATA_CLASS_NAME =
    "com/android/server/uwb/data/UwbMulticastListUpdateStatus";
const char *RANGING_OWR_AOA_MEASURES_CLASS_NAME =
    "com/android/server/uwb/data/UwbOwrAoaMeasurement";
const char *RANGING_DLTDOA_MEASURES_CLASS_NAME =
    "com/android/server/uwb/data/UwbDownLinkTDoAMeasurement";

UwbEventManager UwbEventManager::mObjUwbManager;

UwbEventManager &UwbEventManager::getInstance() { return mObjUwbManager; }

UwbEventManager::UwbEventManager() {
  mVm = NULL;
  mClass = NULL;
  mObject = NULL;
  mRangeDataClass = NULL;
  mRangingTwoWayMeasuresClass = NULL;
  mRangingOwrAoaMeasuresClass = NULL;
  mRangeTdoaMeasuresClass = NULL;
  mMulticastUpdateListDataClass = NULL;
  mOnDeviceStateNotificationReceived = NULL;
  mOnRangeDataNotificationReceived = NULL;
  mOnSessionStatusNotificationReceived = NULL;
  mOnCoreGenericErrorNotificationReceived = NULL;
  mOnMulticastListUpdateNotificationReceived = NULL;
  mOnBlinkDataTxNotificationReceived = NULL;
  mOnRawUciNotificationReceived = NULL;
  mOnVendorUciNotificationReceived = NULL;
  mOnVendorDeviceInfo = NULL;
  mRangeDlTdoaMeasuresClass = NULL;
}

void UwbEventManager::onRangeDataNotificationReceived(
    tUWA_RANGE_DATA_NTF *ranging_ntf_data) {
  static const char fn[] = "onRangeDataNotificationReceived";
  UNUSED(fn);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", fn);
    return;
  }

  jobject rangeDataObject = NULL;
  jbyteArray vendorSpecificData = NULL;
  if (ranging_ntf_data->vendor_specific_ntf.len > 0) {
    vendorSpecificData =
        env->NewByteArray(ranging_ntf_data->vendor_specific_ntf.len);
    env->SetByteArrayRegion(
        vendorSpecificData, 0, ranging_ntf_data->vendor_specific_ntf.len,
        (jbyte *)ranging_ntf_data->vendor_specific_ntf.data);
  } else {
    JNI_TRACE_I("%s: No Vendor specific ntf data!", fn);
  }
  if (ranging_ntf_data->ranging_measure_type == MEASUREMENT_TYPE_TWOWAY) {
    JNI_TRACE_I("%s: ranging_measure_type = MEASUREMENT_TYPE_TWOWAY", fn);
    jmethodID rngMeasuresCtor;
    jmethodID rngDataCtorTwm;
    jobjectArray rangeMeasuresArray;
    rangeMeasuresArray =
        env->NewObjectArray(ranging_ntf_data->no_of_measurements,
                            mRangingTwoWayMeasuresClass, NULL);

    /* Copy the data from structure to Java Object */
    for (int i = 0; i < ranging_ntf_data->no_of_measurements; i++) {
      jbyteArray macAddress;
      jbyteArray rfu;

      if (ranging_ntf_data->mac_addr_mode_indicator == SHORT_MAC_ADDRESS) {
        macAddress = env->NewByteArray(2);
        env->SetByteArrayRegion(
            macAddress, 0, 2,
            (jbyte *)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                .mac_addr);
        rfu = env->NewByteArray(12);
        env->SetByteArrayRegion(
            rfu, 0, 12,
            (jbyte *)ranging_ntf_data->ranging_measures.twr_range_measr[i].rfu);
      } else {
        macAddress = env->NewByteArray(8);
        env->SetByteArrayRegion(
            macAddress, 0, 8,
            (jbyte *)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                .mac_addr);
        rfu = env->NewByteArray(6);
        env->SetByteArrayRegion(
            rfu, 0, 6,
            (jbyte *)ranging_ntf_data->ranging_measures.twr_range_measr[i].rfu);
      }
      rngMeasuresCtor = env->GetMethodID(mRangingTwoWayMeasuresClass, "<init>",
                                         "([BIIIIIIIIIIIII)V");

      env->SetObjectArrayElement(
          rangeMeasuresArray, i,
          env->NewObject(
              mRangingTwoWayMeasuresClass, rngMeasuresCtor, macAddress,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].status,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].nLos,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .distance,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .aoa_azimuth,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .aoa_azimuth_FOM,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .aoa_elevation,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .aoa_elevation_FOM,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .aoa_dest_azimuth,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .aoa_dest_azimuth_FOM,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .aoa_dest_elevation,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .aoa_dest_elevation_FOM,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i]
                  .slot_index,
              (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].rssi,
              rfu));
    }

    rngDataCtorTwm = env->GetMethodID(
        mRangeDataClass, "<init>",
        "(JJIJIII[Lcom/android/server/uwb/data/UwbTwoWayMeasurement;[B)V");
    rangeDataObject = env->NewObject(
        mRangeDataClass, rngDataCtorTwm, (long)ranging_ntf_data->seq_counter,
        (long)ranging_ntf_data->session_id,
        (int)ranging_ntf_data->rcr_indication,
        (long)ranging_ntf_data->curr_range_interval,
        ranging_ntf_data->ranging_measure_type,
        ranging_ntf_data->mac_addr_mode_indicator,
        (int)ranging_ntf_data->no_of_measurements, rangeMeasuresArray,
        vendorSpecificData == NULL ? nullptr : vendorSpecificData);
  } else if (ranging_ntf_data->ranging_measure_type ==
             MEASUREMENT_TYPE_DLTDOA) {
    JNI_TRACE_I("%s: ranging_measure_type = MEASUREMENT_TYPE_DLTDOA", fn);
    jmethodID rngDlTdoaMeasuresCtor;
    jobjectArray rangeDlTdoaMeasuresArray;
    rangeDlTdoaMeasuresArray = env->NewObjectArray(
        ranging_ntf_data->no_of_measurements, mRangeDlTdoaMeasuresClass, NULL);
    for (int i = 0; i < ranging_ntf_data->no_of_measurements; i++) {
      jbyteArray dlTdoaMacAddress = NULL;
      jbyteArray dlTdoaTxTimeStamp = NULL;
      jbyteArray dlTdoaRxTimeStamp = NULL;
      jbyteArray dlTdoaAnchorLocation = NULL;
      jbyteArray dlTdoaActiveRangingRound = NULL;
      uint16_t txTimeStampValue = 0;
      uint16_t rxTimeStampValue = 0;
      uint16_t anchorLocationValue = 0;
      uint16_t activeRangingRoundValue = 0;

      /* Copy the data from structure to Java Object */
      if (ranging_ntf_data->mac_addr_mode_indicator == SHORT_MAC_ADDRESS) {
        dlTdoaMacAddress = env->NewByteArray(MAC_SHORT_ADD_LEN);
        env->SetByteArrayRegion(
            dlTdoaMacAddress, 0, MAC_SHORT_ADD_LEN,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .mac_addr);
      } else {
        dlTdoaMacAddress = env->NewByteArray(MAC_EXT_ADD_LEN);
        env->SetByteArrayRegion(
            dlTdoaMacAddress, 0, MAC_EXT_ADD_LEN,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .mac_addr);
      }

      txTimeStampValue =
          ((ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .message_control &
            TDOA_TX_TIMESTAMP_OFFSET) &
           (TDOA_TX_TIMESTAMP_OFFSET_MASK));
      if (txTimeStampValue == TDOA_TX_TIMESTAMP_40BITS) {
        dlTdoaTxTimeStamp = env->NewByteArray(TDOA_TIMESTAMP_LEN_40BITS);
        env->SetByteArrayRegion(
            dlTdoaTxTimeStamp, 0, TDOA_TIMESTAMP_LEN_40BITS,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .txTimeStamp);
      } else if (txTimeStampValue == TDOA_TX_TIMESTAMP_64BITS) {
        dlTdoaTxTimeStamp = env->NewByteArray(TDOA_TIMESTAMP_LEN_64BITS);
        env->SetByteArrayRegion(
            dlTdoaTxTimeStamp, 0, TDOA_TIMESTAMP_LEN_64BITS,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .txTimeStamp);
      } else {
        JNI_TRACE_E("%s: Invalid dlTdoaTxTimeStamp", fn);
      }

      rxTimeStampValue =
          ((ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .message_control &
            TDOA_RX_TIMESTAMP_OFFSET) &
           (TDOA_RX_TIMESTAMP_OFFSET_MASK));
      if (rxTimeStampValue == TDOA_RX_TIMESTAMP_40BITS) {
        dlTdoaRxTimeStamp = env->NewByteArray(TDOA_TIMESTAMP_LEN_40BITS);
        env->SetByteArrayRegion(
            dlTdoaRxTimeStamp, 0, TDOA_TIMESTAMP_LEN_40BITS,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .rxTimeStamp);
      } else if (rxTimeStampValue == TDOA_RX_TIMESTAMP_64BITS) {
        dlTdoaRxTimeStamp = env->NewByteArray(TDOA_TIMESTAMP_LEN_64BITS);
        env->SetByteArrayRegion(
            dlTdoaRxTimeStamp, 0, TDOA_TIMESTAMP_LEN_64BITS,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .rxTimeStamp);
      } else {
        JNI_TRACE_E("%s: Invalid dlTdoaRxTimeStamp", fn);
      }

      anchorLocationValue =
          ((ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .message_control &
            TDOA_ANCHOR_LOC_OFFSET) &
           (TDOA_ANCHOR_LOC_OFFSET_MASK));
      if (anchorLocationValue == TDOA_ANCHOR_LOC_NOT_INCLUDED) {
        JNI_TRACE_I("%s: AnchorLocation not included", fn);
      } else if (anchorLocationValue == TDOA_ANCHOR_LOC_IN_RELATIVE_SYSTEM) {
        dlTdoaAnchorLocation = env->NewByteArray(TDOA_ANCHOR_LOC_LEN_10BYTES);
        env->SetByteArrayRegion(
            dlTdoaAnchorLocation, 0, TDOA_ANCHOR_LOC_LEN_10BYTES,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .anchor_location);
      } else if (anchorLocationValue == TDOA_ANCHOR_LOC_IN_WGS84_SYSTEM) {
        dlTdoaAnchorLocation = env->NewByteArray(TDOA_ANCHOR_LOC_LEN_12BYTES);
        env->SetByteArrayRegion(
            dlTdoaAnchorLocation, 0, TDOA_ANCHOR_LOC_LEN_12BYTES,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .anchor_location);
      } else {
        JNI_TRACE_E("%s: Invalid dlTdoaAnchorLocation", fn);
      }

      activeRangingRoundValue =
          ((ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .message_control &
            TDOA_ACTIVE_RR_OFFSET) &
           (TDOA_ACTIVE_RR_OFFSET_MASK)) >>
          TDOA_ACTIVE_RR_INDEX_POSITION;
      if (activeRangingRoundValue != 0) {
        dlTdoaActiveRangingRound = env->NewByteArray(activeRangingRoundValue);
        env->SetByteArrayRegion(
            dlTdoaActiveRangingRound, 0, activeRangingRoundValue,
            (jbyte *)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                .active_ranging_round);
      } else {
        JNI_TRACE_I("%s: dlTdoaActiveRangingRound not included", fn);
      }
      rngDlTdoaMeasuresCtor = env->GetMethodID(
          mRangeDlTdoaMeasuresClass, "<init>", "([BIIIIIIIIII[B[BIIJJI[B[B)V");
      env->SetObjectArrayElement(
          rangeDlTdoaMeasuresArray, i,
          env->NewObject(
              mRangeDlTdoaMeasuresClass, rngDlTdoaMeasuresCtor,
              dlTdoaMacAddress,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .status,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .message_type,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .message_control,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .block_index,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .round_index,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .nLos,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .aoa_azimuth,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .aoa_azimuth_FOM,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .aoa_elevation,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .aoa_elevation_FOM,
              dlTdoaTxTimeStamp, dlTdoaRxTimeStamp,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .cfo_anchor,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i].cfo,
              (long)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .initiator_reply_time,
              (long)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .responder_reply_time,
              (int)ranging_ntf_data->ranging_measures.dltdoa_range_measr[i]
                  .initiator_responder_TOF,
              dlTdoaAnchorLocation, dlTdoaActiveRangingRound));
    }
    jmethodID rngDataCtorDlTdoa =
        env->GetMethodID(mRangeDataClass, "<init>",
                         "(JJIJBBI[Lcom/android/server/uwb/data/"
                         "UwbDownLinkTDoAMeasurement;[B)V");
    rangeDataObject = env->NewObject(
        mRangeDataClass, rngDataCtorDlTdoa, (long)ranging_ntf_data->seq_counter,
        (long)ranging_ntf_data->session_id,
        (int)ranging_ntf_data->rcr_indication,
        (long)ranging_ntf_data->curr_range_interval,
        ranging_ntf_data->ranging_measure_type,
        ranging_ntf_data->mac_addr_mode_indicator,
        (int)ranging_ntf_data->no_of_measurements, rangeDlTdoaMeasuresArray,
        vendorSpecificData == NULL ? nullptr : vendorSpecificData);
  } else if (ranging_ntf_data->ranging_measure_type ==
             MEASUREMENT_TYPE_OWR_WITH_AOA) {
    JNI_TRACE_I("%s: ranging_measure_type = MEASUREMENT_TYPE_OWR_WITH_AOA", fn);
    jmethodID rngOwrAoaMeasuresCtor, rngDataCtorOwrAoa;
    jobject rangeOwrAoaMeasures;

    jbyteArray owrAoaMacAddress = NULL;
    if (ranging_ntf_data->mac_addr_mode_indicator == SHORT_MAC_ADDRESS) {
      owrAoaMacAddress = env->NewByteArray(2);
      env->SetByteArrayRegion(owrAoaMacAddress, 0, 2,
                              (jbyte *)ranging_ntf_data->ranging_measures
                                  .owr_with_aoa_range_measr.mac_addr);
    } else {
      owrAoaMacAddress = env->NewByteArray(8);
      env->SetByteArrayRegion(owrAoaMacAddress, 0, 8,
                              (jbyte *)ranging_ntf_data->ranging_measures
                                  .owr_with_aoa_range_measr.mac_addr);
    }
    rngOwrAoaMeasuresCtor = env->GetMethodID(mRangingOwrAoaMeasuresClass,
                                             "<init>", "([BIIIIIIII)V");
    rangeOwrAoaMeasures = env->NewObject(
        mRangingOwrAoaMeasuresClass, rngOwrAoaMeasuresCtor, owrAoaMacAddress,
        (int)ranging_ntf_data->ranging_measures.owr_with_aoa_range_measr.status,
        (int)ranging_ntf_data->ranging_measures.owr_with_aoa_range_measr.nLos,
        (int)ranging_ntf_data->ranging_measures.owr_with_aoa_range_measr
            .frame_seq_num,
        (int)ranging_ntf_data->ranging_measures.owr_with_aoa_range_measr
            .block_index,
        (int)ranging_ntf_data->ranging_measures.owr_with_aoa_range_measr
            .aoa_azimuth,
        (int)ranging_ntf_data->ranging_measures.owr_with_aoa_range_measr
            .aoa_azimuth_FOM,
        (int)ranging_ntf_data->ranging_measures.owr_with_aoa_range_measr
            .aoa_elevation,
        (int)ranging_ntf_data->ranging_measures.owr_with_aoa_range_measr
            .aoa_elevation_FOM);

    rngDataCtorOwrAoa = env->GetMethodID(
        mRangeDataClass, "<init>",
        "(JJIJIIILcom/android/server/uwb/data/UwbOwrAoaMeasurement;[B)V");
    rangeDataObject = env->NewObject(
        mRangeDataClass, rngDataCtorOwrAoa, (long)ranging_ntf_data->seq_counter,
        (long)ranging_ntf_data->session_id,
        (int)ranging_ntf_data->rcr_indication,
        (long)ranging_ntf_data->curr_range_interval,
        ranging_ntf_data->ranging_measure_type,
        ranging_ntf_data->mac_addr_mode_indicator,
        (int)ranging_ntf_data->no_of_measurements, rangeOwrAoaMeasures,
        vendorSpecificData == NULL ? nullptr : vendorSpecificData);
  }

  if ((mOnRangeDataNotificationReceived != NULL) && (rangeDataObject != NULL)) {
    env->CallVoidMethod(mObject, mOnRangeDataNotificationReceived,
                        rangeDataObject);
    if (env->ExceptionCheck()) {
      env->ExceptionDescribe();
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to send range data", fn);
    }
  } else {
    JNI_TRACE_E("%s: rangeDataNtf MID is NULL", fn);
  }
  JNI_TRACE_I("%s: exit", fn);
}

void UwbEventManager::onRawUciNotificationReceived(uint8_t *data,
                                                   uint16_t length) {
  JNI_TRACE_I("%s: Enter", __func__);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", __func__);
    return;
  }

  if (length == 0 || data == NULL) {
    JNI_TRACE_E(
        "%s: length is zero or data is NULL, skip sending notifications",
        __func__);
    return;
  }

  jbyteArray dataArray = env->NewByteArray(length);
  env->SetByteArrayRegion(dataArray, 0, length, (jbyte *)data);

  if (mOnRawUciNotificationReceived != NULL) {
    env->CallVoidMethod(mObject, mOnRawUciNotificationReceived, dataArray);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to send notification", __func__);
    }
  } else {
    JNI_TRACE_E("%s: onRawUciNotificationReceived MID is NULL", __func__);
  }
  JNI_TRACE_I("%s: exit", __func__);
}

void UwbEventManager::onSessionStatusNotificationReceived(uint32_t sessionId,
                                                          uint8_t state,
                                                          uint8_t reasonCode) {
  static const char fn[] = "notifySessionStateNotification";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter; session ID=%x, State = %x reasonCode = %x", fn,
              sessionId, state, reasonCode);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", fn);
    return;
  }

  if (mOnSessionStatusNotificationReceived != NULL) {
    env->CallVoidMethod(mObject, mOnSessionStatusNotificationReceived,
                        (long)sessionId, (int)state, (int)reasonCode);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to notify", fn);
    }
  } else {
    JNI_TRACE_E("%s: sessionStatusNtf MID is null ", fn);
  }
  JNI_TRACE_I("%s: exit", fn);
}

void UwbEventManager::onDeviceStateNotificationReceived(uint8_t state) {
  static const char fn[] = "notifyDeviceStateNotification";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter:  State = %x", fn, state);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", fn);
    return;
  }

  if (mOnDeviceStateNotificationReceived != NULL) {
    env->CallVoidMethod(mObject, mOnDeviceStateNotificationReceived,
                        (int)state);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to notify", fn);
    }
  } else {
    JNI_TRACE_E("%s: deviceStatusNtf MID is null ", fn);
  }
  JNI_TRACE_I("%s: exit", fn);
}

void UwbEventManager::onCoreGenericErrorNotificationReceived(uint8_t state) {
  static const char fn[] = "notifyCoreGenericErrorNotification";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter:  State = %x", fn, state);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", fn);
    return;
  }

  if (mOnCoreGenericErrorNotificationReceived != NULL) {
    env->CallVoidMethod(mObject, mOnCoreGenericErrorNotificationReceived,
                        (int)state);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to notify", fn);
    }
  } else {
    JNI_TRACE_E("%s: genericErrorStatusNtf MID is null ", fn);
  }

  JNI_TRACE_I("%s: exit", fn);
}

void UwbEventManager::onMulticastListUpdateNotificationReceived(
    tUWA_SESSION_UPDATE_MULTICAST_LIST_NTF *multicast_list_ntf) {
  static const char fn[] = "onMulticastListUpdateNotificationReceived";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter;", fn);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", fn);
    return;
  }

  if (multicast_list_ntf == NULL) {
    JNI_TRACE_E("%s: multicast_list_ntf is null", fn);
    return;
  }

  if (multicast_list_ntf->no_of_controlees > MAX_NUM_CONTROLLEES) {
    JNI_TRACE_E("%s: no Of Controlees %d exceeded max allowed: %d ", fn,
                multicast_list_ntf->no_of_controlees, MAX_NUM_CONTROLLEES);
    return;
  }

  jintArray controleeMacAddressArray =
      env->NewIntArray(multicast_list_ntf->no_of_controlees);
  jlongArray subSessionIdArray =
      env->NewLongArray(multicast_list_ntf->no_of_controlees);
  jintArray statusArray =
      env->NewIntArray(multicast_list_ntf->no_of_controlees);

  if (multicast_list_ntf->no_of_controlees > 0) {
    uint32_t controleeMacAddressList[multicast_list_ntf->no_of_controlees];
    uint32_t statusList[multicast_list_ntf->no_of_controlees];
    uint64_t subSessionIdList[multicast_list_ntf->no_of_controlees];
    for (int i = 0; i < multicast_list_ntf->no_of_controlees; i++) {
      controleeMacAddressList[i] =
          multicast_list_ntf->controlee_mac_address_list[i];
      statusList[i] = multicast_list_ntf->status_list[i];
    }
    for (int i = 0; i < multicast_list_ntf->no_of_controlees; i++) {
      subSessionIdList[i] = multicast_list_ntf->subsession_id_list[i];
    }
    env->SetIntArrayRegion(controleeMacAddressArray, 0,
                           multicast_list_ntf->no_of_controlees,
                           (jint *)controleeMacAddressList);
    env->SetLongArrayRegion(subSessionIdArray, 0,
                            multicast_list_ntf->no_of_controlees,
                            (jlong *)subSessionIdList);
    env->SetIntArrayRegion(statusArray, 0, multicast_list_ntf->no_of_controlees,
                           (jint *)statusList);
  }
  jmethodID multicastUpdateListDataCtor =
      env->GetMethodID(mMulticastUpdateListDataClass, "<init>", "(JII[I[J[I)V");
  jobject multicastUpdateListDataObject =
      env->NewObject(mMulticastUpdateListDataClass, multicastUpdateListDataCtor,
                     (long)multicast_list_ntf->session_id,
                     (int)multicast_list_ntf->remaining_list,
                     (int)multicast_list_ntf->no_of_controlees,
                     controleeMacAddressArray, subSessionIdArray, statusArray);

  if (mOnMulticastListUpdateNotificationReceived != NULL) {
    env->CallVoidMethod(mObject, mOnMulticastListUpdateNotificationReceived,
                        multicastUpdateListDataObject);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to send Multicast update list ntf", fn);
    }
  } else {
    JNI_TRACE_E("%s: MulticastUpdateListNtf MID is null ", fn);
  }
  JNI_TRACE_I("%s: exit", fn);
}

void UwbEventManager::onBlinkDataTxNotificationReceived(uint8_t status) {
  static const char fn[] = "onBlinkDataTxNotificationReceived";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter:  State = %x", fn, status);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", fn);
    return;
  }

  if (mOnBlinkDataTxNotificationReceived != NULL) {
    env->CallVoidMethod(mObject, mOnBlinkDataTxNotificationReceived,
                        (int)status);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to notify", fn);
    }
  } else {
    JNI_TRACE_E("%s: BlikDataTxNtf MID is null ", fn);
  }
  JNI_TRACE_I("%s: exit", fn);
}

void UwbEventManager::onVendorUciNotificationReceived(uint8_t gid, uint8_t oid, uint8_t* data, uint16_t length) {
  static const char fn[] = "onVendorUciNotificationReceived";
  UNUSED(fn);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
    JNI_TRACE_E("%s: jni env is null", fn);
    return;
  }

  jbyteArray dataArray = env->NewByteArray(length);
  env->SetByteArrayRegion(dataArray, 0, length, (jbyte*)data);

  if (mOnVendorUciNotificationReceived != NULL) {
    env->CallVoidMethod(mObject, mOnVendorUciNotificationReceived, (int)gid, (int)oid, dataArray);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to send notification", __func__);
    }
  } else {
    JNI_TRACE_E("%s: onVendorUciNotificationReceived MID is NULL", __func__);
  }
  JNI_TRACE_I("%s: exit", __func__);
}

void UwbEventManager::onVendorDeviceInfo(uint8_t* data, uint8_t length) {
  static const char fn[] = "onVendorDeviceInfo";
  UNUSED(fn);
  if((length <= 0) || (data == NULL)) {
        JNI_TRACE_E("%s: data len is Zero or vendorDevice info  is NULL", fn);
        return;
  }

  ScopedJniEnv env(mVm);

  jbyteArray dataArray = env->NewByteArray(length);
  env->SetByteArrayRegion(dataArray, 0, length, (jbyte*)data);
  if (mOnVendorDeviceInfo != NULL) {
    env->CallVoidMethod(mObject, mOnVendorDeviceInfo, dataArray);
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      JNI_TRACE_E("%s: fail to vendor info", __func__);
    }
  } else {
    JNI_TRACE_E("%s: onVendorDeviceInfo MID is NULL", __func__);
  }
  JNI_TRACE_I("%s: exit", __func__);
}

void UwbEventManager::doLoadSymbols(JNIEnv *env, jobject thiz) {
  static const char fn[] = "UwbEventManager::doLoadSymbols";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter", fn);
  env->GetJavaVM(&mVm);

  jclass clazz = env->GetObjectClass(thiz);
  if (clazz != NULL) {
    mClass = (jclass)env->NewGlobalRef(clazz);
    // The reference is only used as a proxy for callbacks.
    mObject = env->NewGlobalRef(thiz);

    mOnDeviceStateNotificationReceived =
        env->GetMethodID(clazz, "onDeviceStatusNotificationReceived", "(I)V");
    mOnRangeDataNotificationReceived =
        env->GetMethodID(clazz, "onRangeDataNotificationReceived",
                         "(Lcom/android/server/uwb/data/UwbRangingData;)V");
    mOnSessionStatusNotificationReceived = env->GetMethodID(
        clazz, "onSessionStatusNotificationReceived", "(JII)V");
    mOnCoreGenericErrorNotificationReceived = env->GetMethodID(
        clazz, "onCoreGenericErrorNotificationReceived", "(I)V");
    mOnDataReceived = env->GetMethodID(clazz, "onDataReceived", "(JIJ[BII[B)V");
    // TDB, this should be reworked
    mOnMulticastListUpdateNotificationReceived = env->GetMethodID(
        clazz, "onMulticastListUpdateNotificationReceived",
        "(Lcom/android/server/uwb/data/UwbMulticastListUpdateStatus;)V");
    mOnRawUciNotificationReceived = env->GetMethodID(clazz,
            "onRawUciNotificationReceived", "([B)V");
    mOnVendorUciNotificationReceived = env->GetMethodID(clazz,
            "onVendorUciNotificationReceived", "(II[B)V");
    mOnVendorDeviceInfo = env->GetMethodID(clazz,
            "onVendorDeviceInfo", "([B)V");

    uwb_jni_cache_jclass(env, RANGING_DATA_CLASS_NAME, &mRangeDataClass);
    uwb_jni_cache_jclass(env, RANGING_MEASURES_CLASS_NAME,
                         &mRangingTwoWayMeasuresClass);
    uwb_jni_cache_jclass(env, RANGING_DLTDOA_MEASURES_CLASS_NAME,
                         &mRangeDlTdoaMeasuresClass);
    uwb_jni_cache_jclass(env, RANGING_OWR_AOA_MEASURES_CLASS_NAME,
                         &mRangingOwrAoaMeasuresClass);
    uwb_jni_cache_jclass(env, MULTICAST_UPDATE_LIST_DATA_CLASS_NAME,
                         &mMulticastUpdateListDataClass);
  }
  JNI_TRACE_I("%s: exit", fn);
}
} // namespace android

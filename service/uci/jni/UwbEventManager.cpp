/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Copyright 2021 NXP.
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
#include "UwbAdaptation.h"
#include "SyncEvent.h"
#include "uwb_config.h"
#include "uwb_hal_int.h"
#include "JniLog.h"
#include "ScopedJniEnv.h"

namespace android {

const char* RANGING_DATA_CLASS_NAME = "com/android/uwb/data/UwbRangingData";
const char* RANGING_MEASURES_CLASS_NAME = "com/android/uwb/data/UwbTwoWayMeasurement";
/* ranging tdoa measures and multicast list update ntf events are implemented as per Fira specification.
       TODO support for these class to be added in service.*/
const char* RANGING_TDoA_MEASURES_CLASS_NAME = "com/android/uwb/data/UwbTDoAMeasurement";
const char* MULTICAST_UPDATE_LIST_DATA_CLASS_NAME = "com/android/uwb/data/UwbMulticastListUpdateStatus";

UwbEventManager UwbEventManager::mObjUwbManager;

UwbEventManager& UwbEventManager::getInstance() {
    return mObjUwbManager;
}

UwbEventManager::UwbEventManager() {
  mVm = NULL;
  mClass = NULL;
  mObject = NULL;
  mRangeDataClass = NULL;
  mRangingTwoWayMeasuresClass = NULL;
  mRangeTdoaMeasuresClass = NULL;
  mMulticastUpdateListDataClass = NULL;
  mOnDeviceStateNotificationReceived = NULL;
  mOnRangeDataNotificationReceived = NULL;
  mOnSessionStatusNotificationReceived = NULL;
  mOnCoreGenericErrorNotificationReceived = NULL;
  mOnMulticastListUpdateNotificationReceived = NULL;
  mOnBlinkDataTxNotificationReceived = NULL;
  mOnRawUciNotificationReceived = NULL;
}

void UwbEventManager::onRangeDataNotificationReceived(tUWA_RANGE_DATA_NTF* ranging_ntf_data) {
  static const char fn[] = "onRangeDataNotificationReceived";
  UNUSED(fn);

    ScopedJniEnv env(mVm);
    if (env == NULL) {
        JNI_TRACE_E("%s: jni env is null", fn);
        return;
    }

    jobject rangeDataObject;

    if(ranging_ntf_data->ranging_measure_type == MEASUREMENT_TYPE_TWOWAY) {
        JNI_TRACE_I("%s: ranging_measure_type = MEASUREMENT_TYPE_TWOWAY", fn);
        jmethodID rngMeasuresCtor;
        jmethodID rngDataCtorTwm;
        jobjectArray rangeMeasuresArray;
        rangeMeasuresArray = env->NewObjectArray(ranging_ntf_data->no_of_measurements,
                                                        mRangingTwoWayMeasuresClass, NULL);

        /* Copy the data from structure to Java Object */
        for(int i = 0; i < ranging_ntf_data->no_of_measurements; i++) {
            jbyteArray macAddress;
            jbyteArray rfu;

            if(ranging_ntf_data->mac_addr_mode_indicator == SHORT_MAC_ADDRESS){
                macAddress = env->NewByteArray(2);
                env->SetByteArrayRegion (macAddress, 0, 2, (jbyte *)ranging_ntf_data->ranging_measures.twr_range_measr[i].mac_addr);
                rfu = env->NewByteArray(12);
                env->SetByteArrayRegion (rfu, 0, 12, (jbyte *)ranging_ntf_data->ranging_measures.twr_range_measr[i].rfu);
            } else {
                macAddress = env->NewByteArray(8);
                env->SetByteArrayRegion (macAddress, 0, 8, (jbyte *)ranging_ntf_data->ranging_measures.twr_range_measr[i].mac_addr);
                rfu = env->NewByteArray(6);
                env->SetByteArrayRegion (rfu, 0, 6, (jbyte *)ranging_ntf_data->ranging_measures.twr_range_measr[i].rfu);
            }
            rngMeasuresCtor = env->GetMethodID(mRangingTwoWayMeasuresClass, "<init>", "([BIIIIIIIIIIII)V");

            env->SetObjectArrayElement(rangeMeasuresArray,
                                     i,
                                     env->NewObject(mRangingTwoWayMeasuresClass,
                                                    rngMeasuresCtor,
                                                    macAddress,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].status,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].nLos,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].distance,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].aoa_azimuth,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].aoa_azimuth_FOM,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].aoa_elevation,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].aoa_elevation_FOM,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].aoa_dest_azimuth,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].aoa_dest_azimuth_FOM,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].aoa_dest_elevation,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].aoa_dest_elevation_FOM,
                                                    (int)ranging_ntf_data->ranging_measures.twr_range_measr[i].slot_index,
                                                    rfu));
        }

        rngDataCtorTwm = env->GetMethodID(mRangeDataClass, "<init>", "(JJIJIII[Lcom/android/uwb/data/UwbTwoWayMeasurement;)V");
        rangeDataObject = env->NewObject(mRangeDataClass,
                                        rngDataCtorTwm,
                                        (long)ranging_ntf_data->seq_counter,
                                        (long)ranging_ntf_data->session_id,
                                        (int)ranging_ntf_data->rcr_indication,
                                        (long)ranging_ntf_data->curr_range_interval,
                                        ranging_ntf_data->ranging_measure_type,
                                        ranging_ntf_data->mac_addr_mode_indicator,
                                        (int)ranging_ntf_data->no_of_measurements,
                                        rangeMeasuresArray);

    } else {
        JNI_TRACE_I("%s: ranging_measure_type = MEASUREMENT_TYPE_ONEWAY", fn);
        jmethodID rngTdoaMeasuresCtor, rngDataCtorTdoa;
        jobjectArray rangeTdoaMeasuresArray;

        rangeTdoaMeasuresArray = env->NewObjectArray(ranging_ntf_data->no_of_measurements,
                                                         mRangeTdoaMeasuresClass, NULL);

        for(int i = 0; i < ranging_ntf_data->no_of_measurements; i++) {
          jbyteArray tdoaMacAddress = NULL;
          jbyteArray deviceInfoArray = NULL;
          jbyteArray blinkPayloadData = NULL;
          jbyteArray rfu = NULL;
          /* Copy the data from structure to Java Object */
          if(ranging_ntf_data->mac_addr_mode_indicator == SHORT_MAC_ADDRESS){
              tdoaMacAddress = env->NewByteArray(2);
              env->SetByteArrayRegion (tdoaMacAddress, 0, 2, (jbyte *)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].mac_addr);
              rfu = env->NewByteArray(12);
              env->SetByteArrayRegion (rfu, 0, 12, (jbyte *)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].rfu);
          } else{
              tdoaMacAddress = env->NewByteArray(8);
              env->SetByteArrayRegion (tdoaMacAddress, 0, 8, (jbyte *)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].mac_addr);
              rfu = env->NewByteArray(6);
              env->SetByteArrayRegion (rfu, 0, 6, (jbyte *)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].rfu);
          }
          if(ranging_ntf_data->ranging_measures.tdoa_range_measr[i].device_info_size > 0) {
             deviceInfoArray = env->NewByteArray(ranging_ntf_data->ranging_measures.tdoa_range_measr[i].device_info_size);
             if(deviceInfoArray != NULL){
                env->SetByteArrayRegion (deviceInfoArray, 0, ranging_ntf_data->ranging_measures.tdoa_range_measr[i].device_info_size, (jbyte *)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].device_info);
             }
          }
          if(ranging_ntf_data->ranging_measures.tdoa_range_measr[i].blink_payload_size > 0) {
             blinkPayloadData = env->NewByteArray(ranging_ntf_data->ranging_measures.tdoa_range_measr[i].blink_payload_size);
             if(blinkPayloadData != NULL) {
                env->SetByteArrayRegion (blinkPayloadData, 0, ranging_ntf_data->ranging_measures.tdoa_range_measr[i].blink_payload_size, (jbyte *)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].blink_payload_data);
             }
          }
          rngTdoaMeasuresCtor = env->GetMethodID(mRangeTdoaMeasuresClass, "<init>", "([BIIIIIIJJ[B[B[B)V");

          env->SetObjectArrayElement(rangeTdoaMeasuresArray,
                                         i, env->NewObject(mRangeTdoaMeasuresClass,
                                         rngTdoaMeasuresCtor,
                                         tdoaMacAddress,
                                         (int)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].frame_type,
                                         (int)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].nLos,
                                         (int)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].aoa_azimuth,
                                         (int)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].aoa_azimuth_FOM,
                                         (int)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].aoa_elevation,
                                         (int)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].aoa_elevation_FOM,
                                         (long)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].timeStamp,
                                         (long)ranging_ntf_data->ranging_measures.tdoa_range_measr[i].blink_frame_number,
                                         rfu,
                                         deviceInfoArray,
                                         blinkPayloadData));

        }


          rngDataCtorTdoa = env->GetMethodID(mRangeDataClass, "<init>", "(JJIJIII[Lcom/android/uwb/data/UwbTDoAMeasurement;)V");
          rangeDataObject = env->NewObject(mRangeDataClass,
                                           rngDataCtorTdoa,
                                           (long)ranging_ntf_data->seq_counter,
                                           (long)ranging_ntf_data->session_id,
                                           (int)ranging_ntf_data->rcr_indication,
                                           (long)ranging_ntf_data->curr_range_interval,
                                           ranging_ntf_data->ranging_measure_type,
                                           ranging_ntf_data->mac_addr_mode_indicator,
                                           (int)ranging_ntf_data->no_of_measurements,
                                           rangeTdoaMeasuresArray);
    }

    if(mOnRangeDataNotificationReceived != NULL) {
        env->CallVoidMethod(mObject, mOnRangeDataNotificationReceived, rangeDataObject);
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

void UwbEventManager::onRawUciNotificationReceived(uint8_t* data, uint16_t length) {
    JNI_TRACE_I("%s: Enter", __func__);

    ScopedJniEnv env(mVm);
    if (env == NULL) {
        JNI_TRACE_E("%s: jni env is null", __func__);
        return;
    }

    if(length == 0 || data == NULL) {
        JNI_TRACE_E("%s: length is zero or data is NULL, skip sending notifications", __func__);
        return;
    }

    jbyteArray dataArray = env->NewByteArray(length);
    env->SetByteArrayRegion (dataArray, 0, length, (jbyte *)data);

    if(mOnRawUciNotificationReceived != NULL) {
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

void UwbEventManager::onSessionStatusNotificationReceived(uint32_t sessionId, uint8_t state, uint8_t reasonCode) {
    static const char fn[] = "notifySessionStateNotification";
    UNUSED(fn);
    JNI_TRACE_I("%s: enter; session ID=%x, State = %x reasonCode = %x", fn, sessionId, state, reasonCode);

    ScopedJniEnv env(mVm);
    if (env == NULL) {
        JNI_TRACE_E("%s: jni env is null", fn);
        return;
    }

    if(mOnSessionStatusNotificationReceived != NULL) {
        env->CallVoidMethod(mObject,
                          mOnSessionStatusNotificationReceived, (long)sessionId, (int)state, (int)reasonCode);
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

    if(mOnDeviceStateNotificationReceived != NULL) {
      env->CallVoidMethod(mObject,
                        mOnDeviceStateNotificationReceived, (int)state);
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

    if(mOnCoreGenericErrorNotificationReceived != NULL) {
        env->CallVoidMethod(mObject,
                          mOnCoreGenericErrorNotificationReceived, (int)state);
        if (env->ExceptionCheck()) {
          env->ExceptionClear();
          JNI_TRACE_E("%s: fail to notify", fn);
        }
    } else {
        JNI_TRACE_E("%s: genericErrorStatusNtf MID is null ", fn);
    }

    JNI_TRACE_I("%s: exit", fn);
}

void UwbEventManager::onMulticastListUpdateNotificationReceived(tUWA_SESSION_UPDATE_MULTICAST_LIST_NTF *multicast_list_ntf) {
  static const char fn[] = "onMulticastListUpdateNotificationReceived";
  UNUSED(fn);
  JNI_TRACE_I("%s: enter;",fn);

  ScopedJniEnv env(mVm);
  if (env == NULL) {
      JNI_TRACE_E("%s: jni env is null", fn);
      return;
  }

  if (multicast_list_ntf == NULL) {
    JNI_TRACE_E("%s: multicast_list_ntf is null", fn);
    return;
  }

  jlongArray subSessionIdArray = env->NewLongArray(multicast_list_ntf->no_of_controlees);
  jintArray statusArray = env->NewIntArray(multicast_list_ntf->no_of_controlees);

  if(multicast_list_ntf->no_of_controlees > 0) {
      uint32_t statusList[multicast_list_ntf->no_of_controlees];
      uint64_t subSessionIdList[multicast_list_ntf->no_of_controlees];
      for(int i=0; i<multicast_list_ntf->no_of_controlees; i++) {
          statusList[i] = multicast_list_ntf->status_list[i];
      }
      for(int i=0; i<multicast_list_ntf->no_of_controlees; i++) {
          subSessionIdList[i] = multicast_list_ntf->subsession_id_list[i];
      }
      env->SetLongArrayRegion(subSessionIdArray, 0, multicast_list_ntf->no_of_controlees, (jlong*)subSessionIdList);
      env->SetIntArrayRegion(statusArray, 0, multicast_list_ntf->no_of_controlees, (jint*)statusList);
  }

  jmethodID multicastUpdateListDataCtor = env->GetMethodID(mMulticastUpdateListDataClass, "<init>", "(JII[J[I)V");
  jobject multicastUpdateListDataObject = env->NewObject(mMulticastUpdateListDataClass,
                                                         multicastUpdateListDataCtor,
                                                         (long)multicast_list_ntf->session_id,
                                                         (int)multicast_list_ntf->remaining_list,
                                                         (int)multicast_list_ntf->no_of_controlees,
                                                         subSessionIdArray,
                                                         statusArray);

  if(mOnMulticastListUpdateNotificationReceived != NULL) {
      env->CallVoidMethod(mObject,
                          mOnMulticastListUpdateNotificationReceived,
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

  if(mOnBlinkDataTxNotificationReceived != NULL) {
    env->CallVoidMethod(mObject,
                        mOnBlinkDataTxNotificationReceived,
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

void UwbEventManager::doLoadSymbols(JNIEnv* env, jobject thiz) {
    static const char fn[] = "UwbEventManager::doLoadSymbols";
    UNUSED(fn);
    JNI_TRACE_I("%s: enter", fn);
    env->GetJavaVM(&mVm);

    jclass clazz = env->GetObjectClass(thiz);
    if (clazz != NULL) {
        mClass = (jclass) env->NewGlobalRef(clazz);
        // The reference is only used as a proxy for callbacks.
        mObject = env->NewGlobalRef(thiz);

        mOnDeviceStateNotificationReceived = env->GetMethodID(clazz, "onDeviceStatusNotificationReceived", "(I)V");
        mOnRangeDataNotificationReceived = env->GetMethodID(clazz, "onRangeDataNotificationReceived", "(Lcom/android/uwb/data/UwbRangingData;)V");
        mOnSessionStatusNotificationReceived = env->GetMethodID(clazz, "onSessionStatusNotificationReceived", "(JII)V");
        mOnCoreGenericErrorNotificationReceived = env->GetMethodID(clazz, "onCoreGenericErrorNotificationReceived", "(I)V");

	// TDB, this shoud be reworked
        mOnMulticastListUpdateNotificationReceived = env->GetMethodID(clazz, "onMulticastListUpdateNotificationReceived", "(Lcom/android/uwb/data/UwbMulticastListUpdateStatus;)V");
//        mOnRawUciNotificationReceived = env->GetMethodID(clazz, "onRawUciNotificationReceived", "([B)V");

        uwb_jni_cache_jclass(env, RANGING_DATA_CLASS_NAME, &mRangeDataClass);
        uwb_jni_cache_jclass(env, RANGING_MEASURES_CLASS_NAME, &mRangingTwoWayMeasuresClass);
        uwb_jni_cache_jclass(env, RANGING_TDoA_MEASURES_CLASS_NAME, &mRangeTdoaMeasuresClass);
        uwb_jni_cache_jclass(env, MULTICAST_UPDATE_LIST_DATA_CLASS_NAME, &mMulticastUpdateListDataClass);
    }
    JNI_TRACE_I("%s: exit", fn);
}
}

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

use log::error;
use pdl_runtime::Packet;
use std::convert::{TryFrom, TryInto};

use crate::error::{Error, Result};
use crate::params::uci_packets::{
    AndroidRadarConfigResponse, AppConfigTlv, CapTlv, CoreSetConfigResponse,
    CreateLogicalLinkResponse, DeviceConfigTlv, GetDeviceInfoResponse, GetLogicalLinkParamResponse,
    PowerStats, RadarConfigTlv, RawUciMessage, RfTestConfigResponse, SessionHandle, SessionState,
    SessionUpdateControllerMulticastListRspV1Payload,
    SessionUpdateControllerMulticastListRspV2Payload, SessionUpdateControllerMulticastResponse,
    SessionUpdateDtTagRangingRoundsResponse, SetAppConfigResponse, StatusCode, UCIMajorVersion,
    UciControlPacket,
};
use crate::uci::error::status_code_to_result;

#[derive(Debug, Clone, PartialEq)]
pub(super) enum UciResponse {
    SetLoggerMode,
    SetNotification,
    OpenHal,
    CloseHal,
    DeviceReset(Result<()>),
    CoreGetDeviceInfo(Result<GetDeviceInfoResponse>),
    CoreGetCapsInfo(Result<Vec<CapTlv>>),
    CoreSetConfig(CoreSetConfigResponse),
    CoreGetConfig(Result<Vec<DeviceConfigTlv>>),
    CoreQueryTimeStamp(Result<u64>),
    SessionInit(Result<Option<SessionHandle>>),
    SessionDeinit(Result<()>),
    SessionSetAppConfig(SetAppConfigResponse),
    SessionGetAppConfig(Result<Vec<AppConfigTlv>>),
    SessionGetCount(Result<u8>),
    SessionGetState(Result<SessionState>),
    SessionUpdateControllerMulticastList(Result<SessionUpdateControllerMulticastResponse>),
    SessionUpdateDtTagRangingRounds(Result<SessionUpdateDtTagRangingRoundsResponse>),
    SessionQueryMaxDataSize(Result<u16>),
    SessionStart(Result<()>),
    SessionStop(Result<()>),
    SessionGetRangingCount(Result<usize>),
    AndroidSetCountryCode(Result<()>),
    AndroidGetPowerStats(Result<PowerStats>),
    AndroidSetRadarConfig(AndroidRadarConfigResponse),
    AndroidGetRadarConfig(Result<Vec<RadarConfigTlv>>),
    RawUciCmd(Result<RawUciMessage>),
    SendUciData(Result<()>),
    SessionSetHybridControllerConfig(Result<()>),
    SessionSetHybridControleeConfig(Result<()>),
    SessionDataTransferPhaseConfig(Result<()>),
    SessionSetRfTestConfig(RfTestConfigResponse),
    RfTest(Result<()>),
    CreateLogicalLink(Result<CreateLogicalLinkResponse>),
    GetLogicalLinkParams(Result<GetLogicalLinkParamResponse>),
    CloseLogicalLink(Result<()>),
}

impl UciResponse {
    pub fn need_retry(&self) -> bool {
        match self {
            Self::SetNotification | Self::OpenHal | Self::CloseHal | Self::SetLoggerMode => false,
            Self::DeviceReset(result) => Self::matches_result_retry(result),
            Self::CoreGetDeviceInfo(result) => Self::matches_result_retry(result),
            Self::CoreGetCapsInfo(result) => Self::matches_result_retry(result),
            Self::CoreGetConfig(result) => Self::matches_result_retry(result),
            Self::CoreQueryTimeStamp(result) => Self::matches_result_retry(result),
            Self::SessionInit(result) => Self::matches_result_retry(result),
            Self::SessionDeinit(result) => Self::matches_result_retry(result),
            Self::SessionGetAppConfig(result) => Self::matches_result_retry(result),
            Self::SessionGetCount(result) => Self::matches_result_retry(result),
            Self::SessionGetState(result) => Self::matches_result_retry(result),
            Self::SessionUpdateControllerMulticastList(result) => {
                Self::matches_result_retry(result)
            }
            Self::SessionUpdateDtTagRangingRounds(result) => Self::matches_result_retry(result),
            Self::SessionStart(result) => Self::matches_result_retry(result),
            Self::SessionStop(result) => Self::matches_result_retry(result),
            Self::SessionGetRangingCount(result) => Self::matches_result_retry(result),
            Self::AndroidSetCountryCode(result) => Self::matches_result_retry(result),
            Self::AndroidGetPowerStats(result) => Self::matches_result_retry(result),
            Self::AndroidGetRadarConfig(result) => Self::matches_result_retry(result),
            Self::AndroidSetRadarConfig(resp) => Self::matches_status_retry(&resp.status),
            Self::RawUciCmd(result) => Self::matches_result_retry(result),
            Self::SessionSetHybridControllerConfig(result) => Self::matches_result_retry(result),
            Self::SessionSetHybridControleeConfig(result) => Self::matches_result_retry(result),
            Self::SessionDataTransferPhaseConfig(result) => Self::matches_result_retry(result),
            Self::CoreSetConfig(resp) => Self::matches_status_retry(&resp.status),
            Self::SessionSetAppConfig(resp) => Self::matches_status_retry(&resp.status),

            Self::SessionQueryMaxDataSize(result) => Self::matches_result_retry(result),
            Self::SessionSetRfTestConfig(resp) => Self::matches_status_retry(&resp.status),
            Self::RfTest(result) => Self::matches_result_retry(result),
            // TODO(b/273376343): Implement retry logic for Data packet send.
            Self::SendUciData(_result) => false,
            Self::CreateLogicalLink(result) => Self::matches_result_retry(result),
            Self::GetLogicalLinkParams(result) => Self::matches_result_retry(result),
            Self::CloseLogicalLink(result) => Self::matches_result_retry(result),
        }
    }

    fn matches_result_retry<T>(result: &Result<T>) -> bool {
        matches!(result, Err(Error::CommandRetry))
    }
    fn matches_status_retry(status: &StatusCode) -> bool {
        matches!(status, StatusCode::UciStatusCommandRetry)
    }
}

impl TryFrom<(uwb_uci_packets::UciResponse, UCIMajorVersion, bool)> for UciResponse {
    type Error = Error;
    fn try_from(
        pair: (uwb_uci_packets::UciResponse, UCIMajorVersion, bool),
    ) -> std::result::Result<Self, Self::Error> {
        let evt = pair.0;
        let uci_fira_major_ver = pair.1;
        let is_multicast_list_rsp_v2_supported = pair.2;
        use uwb_uci_packets::UciResponseChild;
        match evt.specialize() {
            Ok(UciResponseChild::CoreResponse(evt)) => evt.try_into(),
            Ok(UciResponseChild::SessionConfigResponse(evt)) => {
                (evt, uci_fira_major_ver, is_multicast_list_rsp_v2_supported).try_into()
            }
            Ok(UciResponseChild::SessionControlResponse(evt)) => evt.try_into(),
            Ok(UciResponseChild::AndroidResponse(evt)) => evt.try_into(),
            Ok(UciResponseChild::TestResponse(evt)) => evt.try_into(),
            Ok(UciResponseChild::UciVendor_9_Response(evt)) => raw_response(evt.try_into()?),
            Ok(UciResponseChild::UciVendor_A_Response(evt)) => raw_response(evt.try_into()?),
            Ok(UciResponseChild::UciVendor_B_Response(evt)) => raw_response(evt.try_into()?),
            Ok(UciResponseChild::UciVendor_E_Response(evt)) => raw_response(evt.try_into()?),
            Ok(UciResponseChild::UciVendor_F_Response(evt)) => raw_response(evt.try_into()?),
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<uwb_uci_packets::CoreResponse> for UciResponse {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::CoreResponse) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::CoreResponseChild;
        match evt.specialize() {
            Ok(CoreResponseChild::GetDeviceInfoRsp(evt)) => {
                Ok(UciResponse::CoreGetDeviceInfo(status_code_to_result(evt.status()).map(|_| {
                    GetDeviceInfoResponse {
                        status: evt.status(),
                        uci_version: evt.uci_version(),
                        mac_version: evt.mac_version(),
                        phy_version: evt.phy_version(),
                        uci_test_version: evt.uci_test_version(),
                        vendor_spec_info: evt.vendor_spec_info().clone(),
                    }
                })))
            }
            Ok(CoreResponseChild::GetCapsInfoRsp(evt)) => Ok(UciResponse::CoreGetCapsInfo(
                status_code_to_result(evt.status()).map(|_| evt.tlvs().clone()),
            )),
            Ok(CoreResponseChild::DeviceResetRsp(evt)) => {
                Ok(UciResponse::DeviceReset(status_code_to_result(evt.status())))
            }
            Ok(CoreResponseChild::SetConfigRsp(evt)) => {
                Ok(UciResponse::CoreSetConfig(CoreSetConfigResponse {
                    status: evt.status(),
                    config_status: evt.cfg_status().clone(),
                }))
            }

            Ok(CoreResponseChild::GetConfigRsp(evt)) => Ok(UciResponse::CoreGetConfig(
                status_code_to_result(evt.status()).map(|_| evt.tlvs().clone()),
            )),
            Ok(CoreResponseChild::CoreQueryTimeStampRsp(evt)) => {
                Ok(UciResponse::CoreQueryTimeStamp(
                    status_code_to_result(evt.status()).map(|_| evt.timeStamp()),
                ))
            }
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<(uwb_uci_packets::SessionConfigResponse, UCIMajorVersion, bool)> for UciResponse {
    type Error = Error;
    fn try_from(
        pair: (uwb_uci_packets::SessionConfigResponse, UCIMajorVersion, bool),
    ) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::SessionConfigResponseChild;
        let evt = pair.0;
        let uci_fira_major_ver = pair.1;
        let is_multicast_list_rsp_v2_supported = pair.2;
        match evt.specialize() {
            Ok(SessionConfigResponseChild::SessionInitRsp(evt)) => {
                Ok(UciResponse::SessionInit(status_code_to_result(evt.status()).map(|_| None)))
            }
            Ok(SessionConfigResponseChild::SessionInitRsp_V2(evt)) => Ok(UciResponse::SessionInit(
                status_code_to_result(evt.status()).map(|_| Some(evt.session_handle())),
            )),
            Ok(SessionConfigResponseChild::SessionDeinitRsp(evt)) => {
                Ok(UciResponse::SessionDeinit(status_code_to_result(evt.status())))
            }
            Ok(SessionConfigResponseChild::SessionGetCountRsp(evt)) => {
                Ok(UciResponse::SessionGetCount(
                    status_code_to_result(evt.status()).map(|_| evt.session_count()),
                ))
            }
            Ok(SessionConfigResponseChild::SessionGetStateRsp(evt)) => {
                Ok(UciResponse::SessionGetState(
                    status_code_to_result(evt.status()).map(|_| evt.session_state()),
                ))
            }
            Ok(SessionConfigResponseChild::SessionUpdateControllerMulticastListRsp(evt))
                if uci_fira_major_ver == UCIMajorVersion::V1
                    || !is_multicast_list_rsp_v2_supported =>
            {
                error!(
                    "Tryfrom: SessionConfigResponse:: SessionUpdateControllerMulticastListRspV1 "
                );
                let payload = evt.payload();
                let (multicast_update_list_rsp_payload_v1, _) =
                    SessionUpdateControllerMulticastListRspV1Payload::decode(payload).map_err(
                        |e| {
                            error!(
                                "Failed to parse Multicast list rsp v1 {:?}, payload: {:?}",
                                e, &payload
                            );
                            Error::BadParameters
                        },
                    )?;

                Ok(UciResponse::SessionUpdateControllerMulticastList(Ok(
                    SessionUpdateControllerMulticastResponse {
                        status: multicast_update_list_rsp_payload_v1.status,
                        status_list: vec![],
                    },
                )))
            }
            Ok(SessionConfigResponseChild::SessionUpdateControllerMulticastListRsp(evt))
                if uci_fira_major_ver >= UCIMajorVersion::V2 =>
            {
                error!(
                    "Tryfrom: SessionConfigResponse:: SessionUpdateControllerMulticastListRspV2 "
                );
                let payload = evt.payload();
                let (multicast_update_list_rsp_payload_v2, _) =
                    SessionUpdateControllerMulticastListRspV2Payload::decode(payload).map_err(
                        |e| {
                            error!(
                                "Failed to parse Multicast list rsp v2 {:?}, payload: {:?}",
                                e, &payload
                            );
                            Error::BadParameters
                        },
                    )?;
                Ok(UciResponse::SessionUpdateControllerMulticastList(Ok(
                    SessionUpdateControllerMulticastResponse {
                        status: multicast_update_list_rsp_payload_v2.status,
                        status_list: multicast_update_list_rsp_payload_v2.controlee_status,
                    },
                )))
            }
            Ok(SessionConfigResponseChild::SessionUpdateDtTagRangingRoundsRsp(evt)) => {
                Ok(UciResponse::SessionUpdateDtTagRangingRounds(Ok(
                    SessionUpdateDtTagRangingRoundsResponse {
                        status: evt.status(),
                        ranging_round_indexes: evt.ranging_round_indexes().to_vec(),
                    },
                )))
            }
            Ok(SessionConfigResponseChild::SessionSetAppConfigRsp(evt)) => {
                Ok(UciResponse::SessionSetAppConfig(SetAppConfigResponse {
                    status: evt.status(),
                    config_status: evt.cfg_status().clone(),
                }))
            }
            Ok(SessionConfigResponseChild::SessionGetAppConfigRsp(evt)) => {
                Ok(UciResponse::SessionGetAppConfig(
                    status_code_to_result(evt.status())
                        .map(|_| evt.tlvs().clone().into_iter().map(|tlv| tlv.into()).collect()),
                ))
            }
            Ok(SessionConfigResponseChild::SessionQueryMaxDataSizeRsp(evt)) => {
                Ok(UciResponse::SessionQueryMaxDataSize(
                    status_code_to_result(evt.status()).map(|_| evt.max_data_size()),
                ))
            }
            Ok(SessionConfigResponseChild::SessionSetHybridControllerConfigRsp(evt)) => Ok(
                UciResponse::SessionSetHybridControllerConfig(status_code_to_result(evt.status())),
            ),
            Ok(SessionConfigResponseChild::SessionSetHybridControleeConfigRsp(evt)) => Ok(
                UciResponse::SessionSetHybridControleeConfig(status_code_to_result(evt.status())),
            ),
            Ok(SessionConfigResponseChild::SessionDataTransferPhaseConfigRsp(evt)) => {
                Ok(UciResponse::SessionDataTransferPhaseConfig(status_code_to_result(evt.status())))
            }
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<uwb_uci_packets::SessionControlResponse> for UciResponse {
    type Error = Error;
    fn try_from(
        evt: uwb_uci_packets::SessionControlResponse,
    ) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::SessionControlResponseChild;
        match evt.specialize() {
            Ok(SessionControlResponseChild::SessionStartRsp(evt)) => {
                Ok(UciResponse::SessionStart(status_code_to_result(evt.status())))
            }
            Ok(SessionControlResponseChild::SessionStopRsp(evt)) => {
                Ok(UciResponse::SessionStop(status_code_to_result(evt.status())))
            }
            Ok(SessionControlResponseChild::SessionGetRangingCountRsp(evt)) => {
                Ok(UciResponse::SessionGetRangingCount(
                    status_code_to_result(evt.status()).map(|_| evt.count() as usize),
                ))
            }
            Ok(SessionControlResponseChild::CreateLogicalLinkRsp(evt)) => {
                Ok(UciResponse::CreateLogicalLink(Ok(CreateLogicalLinkResponse {
                    status: evt.status(),
                    connect_id: evt.connect_id(),
                })))
            }
            Ok(SessionControlResponseChild::CloseLogicalLinkRsp(evt)) => {
                Ok(UciResponse::CloseLogicalLink(status_code_to_result(evt.status())))
            }
            Ok(SessionControlResponseChild::GetLogicalLinkParamsRsp(evt)) => {
                Ok(UciResponse::GetLogicalLinkParams(status_code_to_result(evt.status()).map(
                    |_| GetLogicalLinkParamResponse {
                        status: evt.status(),
                        control_field: evt.control_field(),
                        logical_link_params: evt.logical_link_params().to_vec(),
                    },
                )))
            }
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<uwb_uci_packets::AndroidResponse> for UciResponse {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::AndroidResponse) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::AndroidResponseChild;
        match evt.specialize() {
            Ok(AndroidResponseChild::AndroidSetCountryCodeRsp(evt)) => {
                Ok(UciResponse::AndroidSetCountryCode(status_code_to_result(evt.status())))
            }
            Ok(AndroidResponseChild::AndroidGetPowerStatsRsp(evt)) => {
                Ok(UciResponse::AndroidGetPowerStats(
                    status_code_to_result(evt.stats().status).map(|_| evt.stats().clone()),
                ))
            }
            Ok(AndroidResponseChild::AndroidSetRadarConfigRsp(evt)) => {
                Ok(UciResponse::AndroidSetRadarConfig(AndroidRadarConfigResponse {
                    status: evt.status(),
                    config_status: evt.cfg_status().clone(),
                }))
            }
            Ok(AndroidResponseChild::AndroidGetRadarConfigRsp(evt)) => {
                Ok(UciResponse::AndroidGetRadarConfig(
                    status_code_to_result(evt.status()).map(|_| evt.tlvs().clone()),
                ))
            }
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<uwb_uci_packets::TestResponse> for UciResponse {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::TestResponse) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::TestResponseChild;
        match evt.specialize() {
            Ok(TestResponseChild::SessionSetRfTestConfigRsp(evt)) => {
                Ok(UciResponse::SessionSetRfTestConfig(RfTestConfigResponse {
                    status: evt.status(),
                    config_status: evt.cfg_status().clone(),
                }))
            }
            Ok(TestResponseChild::TestPeriodicTxRsp(evt)) => {
                Ok(UciResponse::RfTest(status_code_to_result(evt.status())))
            }
            Ok(TestResponseChild::TestPerRxRsp(evt)) => {
                Ok(UciResponse::RfTest(status_code_to_result(evt.status())))
            }
            Ok(TestResponseChild::TestLoopbackRsp(evt)) => {
                Ok(UciResponse::RfTest(status_code_to_result(evt.status())))
            }
            Ok(TestResponseChild::TestRxRsp(evt)) => {
                Ok(UciResponse::RfTest(status_code_to_result(evt.status())))
            }
            Ok(TestResponseChild::TestSrRxRsp(evt)) => {
                Ok(UciResponse::RfTest(status_code_to_result(evt.status())))
            }
            Ok(TestResponseChild::TestSsTwrRsp(evt)) => {
                Ok(UciResponse::RfTest(status_code_to_result(evt.status())))
            }
            Ok(TestResponseChild::StopRfTestRsp(evt)) => {
                Ok(UciResponse::RfTest(status_code_to_result(evt.status())))
            }
            _ => Err(Error::Unknown),
        }
    }
}

fn raw_response(evt: uwb_uci_packets::UciResponse) -> Result<UciResponse> {
    let gid: u32 = evt.group_id().into();
    let oid: u32 = evt.opcode().into();
    let packet: UciControlPacket = evt.try_into()?;
    Ok(UciResponse::RawUciCmd(Ok(RawUciMessage { gid, oid, payload: packet.to_raw_payload() })))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_uci_response_casting_from_uci_vendor_response_packet() {
        let mut uci_vendor_rsp_packet =
            uwb_uci_packets::UciResponse::try_from(uwb_uci_packets::UciVendor_9_Response {
                opcode: 0x00,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            })
            .unwrap();
        let uci_fira_major_version = UCIMajorVersion::V1;
        let mut uci_response = UciResponse::try_from((
            uci_vendor_rsp_packet.clone(),
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        assert_eq!(
            uci_response,
            UciResponse::RawUciCmd(Ok(RawUciMessage {
                gid: 0x9,
                oid: 0x0,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            }))
        );

        uci_vendor_rsp_packet =
            uwb_uci_packets::UciResponse::try_from(uwb_uci_packets::UciVendor_A_Response {
                opcode: 0x00,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            })
            .unwrap();
        uci_response = UciResponse::try_from((
            uci_vendor_rsp_packet.clone(),
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        assert_eq!(
            uci_response,
            UciResponse::RawUciCmd(Ok(RawUciMessage {
                gid: 0xA,
                oid: 0x0,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            }))
        );

        uci_vendor_rsp_packet =
            uwb_uci_packets::UciResponse::try_from(uwb_uci_packets::UciVendor_B_Response {
                opcode: 0x00,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            })
            .unwrap();
        uci_response = UciResponse::try_from((
            uci_vendor_rsp_packet.clone(),
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        assert_eq!(
            uci_response,
            UciResponse::RawUciCmd(Ok(RawUciMessage {
                gid: 0xB,
                oid: 0x0,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            }))
        );

        uci_vendor_rsp_packet =
            uwb_uci_packets::UciResponse::try_from(uwb_uci_packets::UciVendor_E_Response {
                opcode: 0x00,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            })
            .unwrap();
        uci_response = UciResponse::try_from((
            uci_vendor_rsp_packet.clone(),
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        assert_eq!(
            uci_response,
            UciResponse::RawUciCmd(Ok(RawUciMessage {
                gid: 0xE,
                oid: 0x0,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            }))
        );

        uci_vendor_rsp_packet =
            uwb_uci_packets::UciResponse::try_from(uwb_uci_packets::UciVendor_F_Response {
                opcode: 0x00,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            })
            .unwrap();
        uci_response = UciResponse::try_from((
            uci_vendor_rsp_packet.clone(),
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        assert_eq!(
            uci_response,
            UciResponse::RawUciCmd(Ok(RawUciMessage {
                gid: 0xF,
                oid: 0x0,
                payload: vec![0x0, 0x1, 0x2, 0x3],
            }))
        );
    }
}

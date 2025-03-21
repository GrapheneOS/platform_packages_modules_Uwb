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

use std::convert::{TryFrom, TryInto};

use log::{debug, error};
use pdl_runtime::Packet;
use uwb_uci_packets::{
    parse_diagnostics_ntf, radar_bytes_per_sample_value, RadarDataRcv, RadarSweepDataRaw,
    UCI_PACKET_HEADER_LEN, UCI_RADAR_SEQUENCE_NUMBER_LEN, UCI_RADAR_TIMESTAMP_LEN,
    UCI_RADAR_VENDOR_DATA_LEN_LEN,
};

use crate::error::{Error, Result};
use crate::params::fira_app_config_params::UwbAddress;
use crate::params::uci_packets::{
    BitsPerSample, ControleeStatusV1, ControleeStatusV2, CreditAvailability, DataRcvStatusCode,
    DataTransferNtfStatusCode, DataTransferPhaseConfigUpdateStatusCode, DeviceState,
    ExtendedAddressDlTdoaRangingMeasurement, ExtendedAddressOwrAoaRangingMeasurement,
    ExtendedAddressTwoWayRangingMeasurement, RadarDataType, RangingMeasurementType, RawUciMessage,
    SessionId, SessionState, SessionToken, SessionUpdateControllerMulticastListNtfV1Payload,
    SessionUpdateControllerMulticastListNtfV2Payload, ShortAddressDlTdoaRangingMeasurement,
    ShortAddressOwrAoaRangingMeasurement, ShortAddressTwoWayRangingMeasurement, StatusCode,
    UCIMajorVersion,
};

/// enum of all UCI notifications with structured fields.
#[derive(Debug, Clone, PartialEq)]
pub enum UciNotification {
    /// CoreNotification equivalent.
    Core(CoreNotification),
    /// SessionNotification equivalent.
    Session(SessionNotification),
    /// UciVendor_X_Notification equivalent.
    Vendor(RawUciMessage),
    /// RfTestNotification equivalent
    RfTest(RfTestNotification),
}

/// UCI CoreNotification.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CoreNotification {
    /// DeviceStatusNtf equivalent.
    DeviceStatus(DeviceState),
    /// GenericErrorPacket equivalent.
    GenericError(StatusCode),
}

/// UCI SessionNotification.
#[derive(Debug, Clone, PartialEq)]
pub enum SessionNotification {
    /// SessionStatusNtf equivalent.
    Status {
        /// SessionId : u32
        session_id: SessionId,
        /// SessionToken : u32
        session_token: SessionToken,
        /// uwb_uci_packets::SessionState.
        session_state: SessionState,
        /// uwb_uci_packets::Reasoncode.
        reason_code: u8,
    },
    /// SessionUpdateControllerMulticastListNtfV1 equivalent.
    UpdateControllerMulticastListV1 {
        /// SessionToken : u32
        session_token: SessionToken,
        /// count of controlees: u8
        remaining_multicast_list_size: usize,
        /// list of controlees.
        status_list: Vec<ControleeStatusV1>,
    },
    /// SessionUpdateControllerMulticastListNtfV2 equivalent.
    UpdateControllerMulticastListV2 {
        /// SessionToken : u32
        session_token: SessionToken,
        /// list of controlees.
        status_list: Vec<ControleeStatusV2>,
    },
    /// (Short/Extended)Mac()SessionInfoNtf equivalent
    SessionInfo(SessionRangeData),
    /// DataCreditNtf equivalent.
    DataCredit {
        /// SessionToken : u32
        session_token: SessionToken,
        /// Credit Availability (for sending Data packets on UWB Session)
        credit_availability: CreditAvailability,
    },
    /// DataTransferStatusNtf equivalent.
    DataTransferStatus {
        /// SessionToken : u32
        session_token: SessionToken,
        /// Sequence Number: u16
        uci_sequence_number: u16,
        /// Data Transfer Status Code
        status: DataTransferNtfStatusCode,
        /// Transmission count
        tx_count: u8,
    },
    /// SessionDataTransferPhaseConfigNtf equivalent.
    DataTransferPhaseConfig {
        /// SessionToken : u32
        session_token: SessionToken,
        /// status
        status: DataTransferPhaseConfigUpdateStatusCode,
    },
}

/// UCI RfTest Notification.
#[derive(Debug, Clone, PartialEq)]
pub enum RfTestNotification {
    ///TestPeriodicTxNtf equivalent
    TestPeriodicTxNtf {
        /// Status
        status: StatusCode,
        /// The raw data of the notification message.
        /// It's not at FiRa specification, only used by vendor's extension.
        raw_notification_data: Vec<u8>,
    },
    /// TestPerRxNtf equivalent
    TestPerRxNtf(RfTestPerRxData),
}

/// The session range data.
#[derive(Debug, Clone, PartialEq)]
pub struct SessionRangeData {
    /// The sequence counter that starts with 0 when the session is started.
    pub sequence_number: u32,

    /// The identifier of the session.
    pub session_token: SessionToken,

    /// The current ranging interval setting in the unit of ms.
    pub current_ranging_interval_ms: u32,

    /// The ranging measurement type.
    pub ranging_measurement_type: RangingMeasurementType,

    /// Hus primary session Session Id
    pub hus_primary_session_id: SessionToken,

    /// The ranging measurement data.
    pub ranging_measurements: RangingMeasurements,

    /// Indication that a RCR was sent/received in the current ranging round.
    pub rcr_indicator: u8,

    /// The raw data of the notification message.
    /// (b/243555651): It's not at FiRa specification, only used by vendor's extension.
    pub raw_ranging_data: Vec<u8>,
}

/// PER RX NTF Data
#[derive(Debug, Clone, PartialEq)]
pub struct RfTestPerRxData {
    /// Status
    pub status: StatusCode,

    /// Number of RX attempts.
    pub attempts: u32,

    /// Number of times signal was detected.
    pub acq_detect: u32,

    /// Number of times signal was rejected.
    pub acq_reject: u32,

    /// Number of times RX did not go beyound ACQ stage.
    pub rx_fail: u32,

    /// Number of times sync CIR ready event was received.
    pub sync_cir_ready: u32,

    /// Number of times RX was stuck at either ACQ detect or sync CIR ready.
    pub sfd_fail: u32,

    /// Number of times SFD was found.
    pub sfd_found: u32,

    /// Number of times PHR decode failed.
    pub phr_dec_error: u32,

    /// Number of times PHR bits in error.
    pub phr_bit_error: u32,

    /// Number of times payload decode failed.
    pub psdu_dec_error: u32,

    /// Number of times payload bits in error.
    pub psdu_bit_error: u32,

    /// Number of times STS detection was successful.
    pub sts_found: u32,

    /// Number of times end of frame event was triggered.
    pub eof: u32,

    /// The raw data of the notification message.
    /// It's not at FiRa specification, only used by vendor's extension.
    pub raw_notification_data: Vec<u8>,
}

/// The ranging measurements.
#[derive(Debug, Clone, PartialEq)]
pub enum RangingMeasurements {
    /// A Two-Way measurement with short address.
    ShortAddressTwoWay(Vec<ShortAddressTwoWayRangingMeasurement>),

    /// A Two-Way measurement with extended address.
    ExtendedAddressTwoWay(Vec<ExtendedAddressTwoWayRangingMeasurement>),

    /// Dl-TDoA measurement with short address.
    ShortAddressDltdoa(Vec<ShortAddressDlTdoaRangingMeasurement>),

    /// Dl-TDoA measurement with extended address.
    ExtendedAddressDltdoa(Vec<ExtendedAddressDlTdoaRangingMeasurement>),

    /// OWR for AoA measurement with short address.
    ShortAddressOwrAoa(ShortAddressOwrAoaRangingMeasurement),

    /// OWR for AoA measurement with extended address.
    ExtendedAddressOwrAoa(ExtendedAddressOwrAoaRangingMeasurement),
}

/// The DATA_RCV packet
#[derive(Debug, Clone, std::cmp::PartialEq)]
pub struct DataRcvNotification {
    /// The identifier of the session on which data transfer is happening.
    pub session_token: SessionToken,

    /// The status of the data rx.
    pub status: StatusCode,

    /// The sequence number of the data packet.
    pub uci_sequence_num: u16,

    /// MacAddress of the sender of the application data.
    pub source_address: UwbAddress,

    /// Application Payload Data
    pub payload: Vec<u8>,
}

/// The Radar sweep data struct
#[derive(Debug, Clone, std::cmp::PartialEq)]
pub struct RadarSweepData {
    /// Counter of a single radar sweep per receiver. Starting
    /// with 0 when the radar session is started.
    pub sequence_number: u32,

    /// Timestamp when this radar sweep is received. Unit is
    /// based on the PRF.
    pub timestamp: u32,

    /// The radar vendor specific data.
    pub vendor_specific_data: Vec<u8>,

    /// The radar sample data.
    pub sample_data: Vec<u8>,
}

/// The RADAR_DATA_RCV packet
#[derive(Debug, Clone, std::cmp::PartialEq)]
pub struct RadarDataRcvNotification {
    /// The identifier of the session on which radar data transfer is happening.
    pub session_token: SessionToken,

    /// The status of the radar data rx.
    pub status: DataRcvStatusCode,

    /// The radar data type.
    pub radar_data_type: RadarDataType,

    /// The number of sweeps.
    pub number_of_sweeps: u8,

    /// Number of samples captured for each radar sweep.
    pub samples_per_sweep: u8,

    /// Bits per sample in the radar sweep.
    pub bits_per_sample: BitsPerSample,

    /// Defines the start offset with respect to 0cm distance. Unit in samples.
    pub sweep_offset: u16,

    /// Radar sweep data.
    pub sweep_data: Vec<RadarSweepData>,
}

impl From<&uwb_uci_packets::RadarSweepDataRaw> for RadarSweepData {
    fn from(evt: &uwb_uci_packets::RadarSweepDataRaw) -> Self {
        Self {
            sequence_number: evt.sequence_number,
            timestamp: evt.timestamp,
            vendor_specific_data: evt.vendor_specific_data.clone(),
            sample_data: evt.sample_data.clone(),
        }
    }
}

impl TryFrom<uwb_uci_packets::UciDataPacket> for RadarDataRcvNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::UciDataPacket) -> std::result::Result<Self, Self::Error> {
        match evt.specialize() {
            uwb_uci_packets::UciDataPacketChild::RadarDataRcv(evt) => parse_radar_data(evt),
            _ => Err(Error::Unknown),
        }
    }
}

fn parse_radar_data(data: RadarDataRcv) -> Result<RadarDataRcvNotification> {
    let session_token = data.get_session_handle();
    let status = data.get_status();
    let radar_data_type = data.get_radar_data_type();
    let number_of_sweeps = data.get_number_of_sweeps();
    let samples_per_sweep = data.get_samples_per_sweep();
    let bits_per_sample = data.get_bits_per_sample();
    let bytes_per_sample_value = radar_bytes_per_sample_value(bits_per_sample);
    let sweep_offset = data.get_sweep_offset();

    Ok(RadarDataRcvNotification {
        session_token,
        status,
        radar_data_type,
        number_of_sweeps,
        samples_per_sweep,
        bits_per_sample,
        sweep_offset,
        sweep_data: parse_radar_sweep_data(
            number_of_sweeps,
            samples_per_sweep,
            bytes_per_sample_value,
            data.get_sweep_data().clone(),
        )?,
    })
}

fn parse_radar_sweep_data(
    number_of_sweeps: u8,
    samples_per_sweep: u8,
    bytes_per_sample_value: u8,
    data: Vec<u8>,
) -> Result<Vec<RadarSweepData>> {
    let mut radar_sweep_data: Vec<RadarSweepData> = Vec::new();
    let mut sweep_data_cursor = 0;
    for _ in 0..number_of_sweeps {
        let vendor_data_len_index =
            sweep_data_cursor + UCI_RADAR_SEQUENCE_NUMBER_LEN + UCI_RADAR_TIMESTAMP_LEN;
        if data.len() <= vendor_data_len_index {
            error!("Invalid radar sweep data length for vendor, data: {:?}", &data);
            return Err(Error::BadParameters);
        }
        let vendor_specific_data_len = data[vendor_data_len_index] as usize;
        let sweep_data_len = UCI_RADAR_SEQUENCE_NUMBER_LEN
            + UCI_RADAR_TIMESTAMP_LEN
            + UCI_RADAR_VENDOR_DATA_LEN_LEN
            + vendor_specific_data_len
            + samples_per_sweep as usize * bytes_per_sample_value as usize;
        if data.len() < sweep_data_cursor + sweep_data_len {
            error!("Invalid radar sweep data length, data: {:?}", &data);
            return Err(Error::BadParameters);
        }
        radar_sweep_data.push(
            (&RadarSweepDataRaw::parse(
                &data[sweep_data_cursor..sweep_data_cursor + sweep_data_len],
            )
            .map_err(|e| {
                error!("Failed to parse raw Radar Sweep Data {:?}, data: {:?}", e, &data);
                Error::BadParameters
            })?)
                .into(),
        );

        sweep_data_cursor += sweep_data_len;
    }

    Ok(radar_sweep_data)
}

impl TryFrom<uwb_uci_packets::UciDataPacket> for DataRcvNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::UciDataPacket) -> std::result::Result<Self, Self::Error> {
        match evt.specialize() {
            uwb_uci_packets::UciDataPacketChild::UciDataRcv(evt) => Ok(DataRcvNotification {
                session_token: evt.get_session_token(),
                status: evt.get_status(),
                uci_sequence_num: evt.get_uci_sequence_number(),
                source_address: UwbAddress::Extended(evt.get_source_mac_address().to_le_bytes()),
                payload: evt.get_data().to_vec(),
            }),
            _ => Err(Error::Unknown),
        }
    }
}

impl UciNotification {
    pub(crate) fn need_retry(&self) -> bool {
        matches!(
            self,
            Self::Core(CoreNotification::GenericError(StatusCode::UciStatusCommandRetry))
        )
    }
}

impl TryFrom<(uwb_uci_packets::UciNotification, UCIMajorVersion, bool)> for UciNotification {
    type Error = Error;
    fn try_from(
        pair: (uwb_uci_packets::UciNotification, UCIMajorVersion, bool),
    ) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::UciNotificationChild;
        let evt = pair.0;
        let uci_fira_major_ver = pair.1;
        let is_multicast_list_ntf_v2_supported = pair.2;

        match evt.specialize() {
            UciNotificationChild::CoreNotification(evt) => Ok(Self::Core(evt.try_into()?)),
            UciNotificationChild::SessionConfigNotification(evt) => Ok(Self::Session(
                (evt, uci_fira_major_ver, is_multicast_list_ntf_v2_supported).try_into()?,
            )),
            UciNotificationChild::SessionControlNotification(evt) => {
                Ok(Self::Session(evt.try_into()?))
            }
            UciNotificationChild::AndroidNotification(evt) => evt.try_into(),
            UciNotificationChild::UciVendor_9_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::UciVendor_A_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::UciVendor_B_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::UciVendor_E_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::UciVendor_F_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::TestNotification(evt) => Ok(Self::RfTest(evt.try_into()?)),
            _ => {
                error!("Unknown UciNotification: {:?}", evt);
                Err(Error::Unknown)
            }
        }
    }
}

impl TryFrom<uwb_uci_packets::CoreNotification> for CoreNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::CoreNotification) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::CoreNotificationChild;
        match evt.specialize() {
            CoreNotificationChild::DeviceStatusNtf(evt) => {
                Ok(Self::DeviceStatus(evt.get_device_state()))
            }
            CoreNotificationChild::GenericError(evt) => Ok(Self::GenericError(evt.get_status())),
            _ => {
                error!("Unknown CoreNotification: {:?}", evt);
                Err(Error::Unknown)
            }
        }
    }
}

impl TryFrom<(uwb_uci_packets::SessionConfigNotification, UCIMajorVersion, bool)>
    for SessionNotification
{
    type Error = Error;
    fn try_from(
        pair: (uwb_uci_packets::SessionConfigNotification, UCIMajorVersion, bool),
    ) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::SessionConfigNotificationChild;
        let evt = pair.0;
        let uci_fira_major_ver = pair.1;
        let is_multicast_list_ntf_v2_supported = pair.2;
        match evt.specialize() {
            SessionConfigNotificationChild::SessionStatusNtf(evt) => Ok(Self::Status {
                //no sessionId recieved, assign from sessionIdToToken map in uci_manager
                session_id: 0,
                session_token: evt.get_session_token(),
                session_state: evt.get_session_state(),
                reason_code: evt.get_reason_code(),
            }),
            SessionConfigNotificationChild::SessionUpdateControllerMulticastListNtf(evt)
                if uci_fira_major_ver == UCIMajorVersion::V1
                    || !is_multicast_list_ntf_v2_supported =>
            {
                let payload = evt.get_payload();
                let multicast_update_list_payload_v1 =
                    SessionUpdateControllerMulticastListNtfV1Payload::parse(payload).map_err(
                        |e| {
                            error!(
                                "Failed to parse Multicast list ntf v1 {:?}, payload: {:?}",
                                e, &payload
                            );
                            Error::BadParameters
                        },
                    )?;
                Ok(Self::UpdateControllerMulticastListV1 {
                    session_token: evt.get_session_token(),
                    remaining_multicast_list_size: multicast_update_list_payload_v1
                        .remaining_multicast_list_size
                        as usize,
                    status_list: multicast_update_list_payload_v1.controlee_status,
                })
            }
            SessionConfigNotificationChild::SessionUpdateControllerMulticastListNtf(evt)
                if uci_fira_major_ver >= UCIMajorVersion::V2 =>
            {
                let payload = evt.get_payload();
                let multicast_update_list_payload_v2 =
                    SessionUpdateControllerMulticastListNtfV2Payload::parse(payload).map_err(
                        |e| {
                            error!(
                                "Failed to parse Multicast list ntf v2 {:?}, payload: {:?}",
                                e, &payload
                            );
                            Error::BadParameters
                        },
                    )?;
                Ok(Self::UpdateControllerMulticastListV2 {
                    session_token: evt.get_session_token(),
                    status_list: multicast_update_list_payload_v2.controlee_status,
                })
            }
            SessionConfigNotificationChild::SessionDataTransferPhaseConfigNtf(evt) => {
                Ok(Self::DataTransferPhaseConfig {
                    session_token: evt.get_session_token(),
                    status: evt.get_status(),
                })
            }
            _ => {
                error!("Unknown SessionConfigNotification: {:?}", evt);
                Err(Error::Unknown)
            }
        }
    }
}

impl TryFrom<uwb_uci_packets::SessionControlNotification> for SessionNotification {
    type Error = Error;
    fn try_from(
        evt: uwb_uci_packets::SessionControlNotification,
    ) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::SessionControlNotificationChild;
        match evt.specialize() {
            SessionControlNotificationChild::SessionInfoNtf(evt) => evt.try_into(),
            SessionControlNotificationChild::DataCreditNtf(evt) => Ok(Self::DataCredit {
                session_token: evt.get_session_token(),
                credit_availability: evt.get_credit_availability(),
            }),
            SessionControlNotificationChild::DataTransferStatusNtf(evt) => {
                Ok(Self::DataTransferStatus {
                    session_token: evt.get_session_token(),
                    uci_sequence_number: evt.get_uci_sequence_number(),
                    status: evt.get_status(),
                    tx_count: evt.get_tx_count(),
                })
            }
            _ => {
                error!("Unknown SessionControlNotification: {:?}", evt);
                Err(Error::Unknown)
            }
        }
    }
}

impl TryFrom<uwb_uci_packets::SessionInfoNtf> for SessionNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::SessionInfoNtf) -> std::result::Result<Self, Self::Error> {
        let raw_ranging_data = evt.encode_to_bytes().unwrap()[UCI_PACKET_HEADER_LEN..].to_vec();
        use uwb_uci_packets::SessionInfoNtfChild;
        let ranging_measurements = match evt.specialize() {
            SessionInfoNtfChild::ShortMacTwoWaySessionInfoNtf(evt) => {
                RangingMeasurements::ShortAddressTwoWay(
                    evt.get_two_way_ranging_measurements().clone(),
                )
            }
            SessionInfoNtfChild::ExtendedMacTwoWaySessionInfoNtf(evt) => {
                RangingMeasurements::ExtendedAddressTwoWay(
                    evt.get_two_way_ranging_measurements().clone(),
                )
            }
            SessionInfoNtfChild::ShortMacOwrAoaSessionInfoNtf(evt) => {
                if evt.get_owr_aoa_ranging_measurements().clone().len() == 1 {
                    RangingMeasurements::ShortAddressOwrAoa(
                        match evt.get_owr_aoa_ranging_measurements().clone().pop() {
                            Some(r) => r,
                            None => {
                                error!(
                                    "Unable to parse ShortAddress OwrAoA measurement: {:?}",
                                    evt
                                );
                                return Err(Error::BadParameters);
                            }
                        },
                    )
                } else {
                    error!("Wrong count of OwrAoA ranging measurements {:?}", evt);
                    return Err(Error::BadParameters);
                }
            }
            SessionInfoNtfChild::ExtendedMacOwrAoaSessionInfoNtf(evt) => {
                if evt.get_owr_aoa_ranging_measurements().clone().len() == 1 {
                    RangingMeasurements::ExtendedAddressOwrAoa(
                        match evt.get_owr_aoa_ranging_measurements().clone().pop() {
                            Some(r) => r,
                            None => {
                                error!(
                                    "Unable to parse ExtendedAddress OwrAoA measurement: {:?}",
                                    evt
                                );
                                return Err(Error::BadParameters);
                            }
                        },
                    )
                } else {
                    error!("Wrong count of OwrAoA ranging measurements {:?}", evt);
                    return Err(Error::BadParameters);
                }
            }
            SessionInfoNtfChild::ShortMacDlTDoASessionInfoNtf(evt) => {
                match ShortAddressDlTdoaRangingMeasurement::parse(
                    evt.get_dl_tdoa_measurements(),
                    evt.get_no_of_ranging_measurements(),
                ) {
                    Some(v) => {
                        if v.len() == evt.get_no_of_ranging_measurements().into() {
                            RangingMeasurements::ShortAddressDltdoa(v)
                        } else {
                            error!("Wrong count of ranging measurements {:?}", evt);
                            return Err(Error::BadParameters);
                        }
                    }
                    None => return Err(Error::BadParameters),
                }
            }
            SessionInfoNtfChild::ExtendedMacDlTDoASessionInfoNtf(evt) => {
                match ExtendedAddressDlTdoaRangingMeasurement::parse(
                    evt.get_dl_tdoa_measurements(),
                    evt.get_no_of_ranging_measurements(),
                ) {
                    Some(v) => {
                        if v.len() == evt.get_no_of_ranging_measurements().into() {
                            RangingMeasurements::ExtendedAddressDltdoa(v)
                        } else {
                            error!("Wrong count of ranging measurements {:?}", evt);
                            return Err(Error::BadParameters);
                        }
                    }
                    None => return Err(Error::BadParameters),
                }
            }
            _ => {
                error!("Unknown SessionInfoNtf: {:?}", evt);
                return Err(Error::Unknown);
            }
        };
        Ok(Self::SessionInfo(SessionRangeData {
            sequence_number: evt.get_sequence_number(),
            session_token: evt.get_session_token(),
            current_ranging_interval_ms: evt.get_current_ranging_interval(),
            ranging_measurement_type: evt.get_ranging_measurement_type(),
            hus_primary_session_id: evt.get_hus_primary_session_id(),
            ranging_measurements,
            rcr_indicator: evt.get_rcr_indicator(),
            raw_ranging_data,
        }))
    }
}

impl TryFrom<uwb_uci_packets::AndroidNotification> for UciNotification {
    type Error = Error;
    fn try_from(
        evt: uwb_uci_packets::AndroidNotification,
    ) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::AndroidNotificationChild;

        // (b/241336806): Currently we don't process the diagnostic packet, just log it only.
        if let AndroidNotificationChild::AndroidRangeDiagnosticsNtf(ntf) = evt.specialize() {
            debug!("Received diagnostic packet: {:?}", parse_diagnostics_ntf(ntf));
        } else {
            error!("Received unknown AndroidNotification: {:?}", evt);
        }
        Err(Error::Unknown)
    }
}

fn vendor_notification(evt: uwb_uci_packets::UciNotification) -> Result<UciNotification> {
    Ok(UciNotification::Vendor(RawUciMessage {
        gid: evt.get_group_id().into(),
        oid: evt.get_opcode().into(),
        payload: get_vendor_uci_payload(evt)?,
    }))
}

impl TryFrom<uwb_uci_packets::TestNotification> for RfTestNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::TestNotification) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::TestNotificationChild;
        let raw_ntf_data = evt.clone().encode_to_bytes().unwrap()[UCI_PACKET_HEADER_LEN..].to_vec();
        match evt.specialize() {
            TestNotificationChild::TestPeriodicTxNtf(evt) => Ok(Self::TestPeriodicTxNtf {
                status: evt.get_status(),
                raw_notification_data: raw_ntf_data,
            }),
            TestNotificationChild::TestPerRxNtf(evt) => Ok(Self::TestPerRxNtf (RfTestPerRxData {
                status: evt.get_status(),
                attempts: evt.get_attempts(),
                acq_detect: evt.get_acq_detect(),
                acq_reject: evt.get_acq_reject(),
                rx_fail: evt.get_rx_fail(),
                sync_cir_ready: evt.get_sync_cir_ready(),
                sfd_fail: evt.get_sfd_fail(),
                sfd_found: evt.get_sfd_found(),
                phr_dec_error: evt.get_phr_dec_error(),
                phr_bit_error: evt.get_phr_bit_error(),
                psdu_dec_error: evt.get_psdu_dec_error(),
                psdu_bit_error: evt.get_psdu_bit_error(),
                sts_found: evt.get_sts_found(),
                eof: evt.get_eof(),
                raw_notification_data: raw_ntf_data,
            })),
            _ => {
                error!("Unknown RfTestNotification: {:?}", evt);
                Err(Error::Unknown)
            }
        }
    }
}

fn get_vendor_uci_payload(evt: uwb_uci_packets::UciNotification) -> Result<Vec<u8>> {
    match evt.specialize() {
        uwb_uci_packets::UciNotificationChild::UciVendor_9_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_9_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_9_NotificationChild::None => Ok(Vec::new()),
            }
        }
        uwb_uci_packets::UciNotificationChild::UciVendor_A_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_A_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_A_NotificationChild::None => Ok(Vec::new()),
            }
        }
        uwb_uci_packets::UciNotificationChild::UciVendor_B_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_B_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_B_NotificationChild::None => Ok(Vec::new()),
            }
        }
        uwb_uci_packets::UciNotificationChild::UciVendor_E_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_E_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_E_NotificationChild::None => Ok(Vec::new()),
            }
        }
        uwb_uci_packets::UciNotificationChild::UciVendor_F_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_F_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_F_NotificationChild::None => Ok(Vec::new()),
            }
        }
        _ => {
            error!("Unknown UciVendor packet: {:?}", evt);
            Err(Error::Unknown)
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use bytes::{BufMut, BytesMut};

    #[test]
    fn test_ranging_measurements_trait() {
        let empty_short_ranging_measurements = RangingMeasurements::ShortAddressTwoWay(vec![]);
        assert_eq!(empty_short_ranging_measurements, empty_short_ranging_measurements);
        let extended_ranging_measurements = RangingMeasurements::ExtendedAddressTwoWay(vec![
            ExtendedAddressTwoWayRangingMeasurement {
                mac_address: 0x1234_5678_90ab,
                status: StatusCode::UciStatusOk,
                nlos: 0,
                distance: 4,
                aoa_azimuth: 5,
                aoa_azimuth_fom: 6,
                aoa_elevation: 7,
                aoa_elevation_fom: 8,
                aoa_destination_azimuth: 9,
                aoa_destination_azimuth_fom: 10,
                aoa_destination_elevation: 11,
                aoa_destination_elevation_fom: 12,
                slot_index: 0,
                rssi: u8::MAX,
            },
        ]);
        assert_eq!(extended_ranging_measurements, extended_ranging_measurements.clone());
        let empty_extended_ranging_measurements =
            RangingMeasurements::ExtendedAddressTwoWay(vec![]);
        assert_eq!(empty_short_ranging_measurements, empty_short_ranging_measurements);
        //short and extended measurements are unequal even if both are empty:
        assert_ne!(empty_short_ranging_measurements, empty_extended_ranging_measurements);
    }
    #[test]
    fn test_core_notification_casting_from_generic_error() {
        let generic_error_packet = uwb_uci_packets::GenericErrorBuilder {
            status: uwb_uci_packets::StatusCode::UciStatusRejected,
        }
        .build();
        let core_notification =
            uwb_uci_packets::CoreNotification::try_from(generic_error_packet).unwrap();
        let core_notification = CoreNotification::try_from(core_notification).unwrap();
        let uci_notification_from_generic_error = UciNotification::Core(core_notification);
        assert_eq!(
            uci_notification_from_generic_error,
            UciNotification::Core(CoreNotification::GenericError(
                uwb_uci_packets::StatusCode::UciStatusRejected
            ))
        );
    }
    #[test]
    fn test_core_notification_casting_from_device_status_ntf() {
        let device_status_ntf_packet = uwb_uci_packets::DeviceStatusNtfBuilder {
            device_state: uwb_uci_packets::DeviceState::DeviceStateActive,
        }
        .build();
        let core_notification =
            uwb_uci_packets::CoreNotification::try_from(device_status_ntf_packet).unwrap();
        let uci_notification = CoreNotification::try_from(core_notification).unwrap();
        let uci_notification_from_device_status_ntf = UciNotification::Core(uci_notification);
        assert_eq!(
            uci_notification_from_device_status_ntf,
            UciNotification::Core(CoreNotification::DeviceStatus(
                uwb_uci_packets::DeviceState::DeviceStateActive
            ))
        );
    }

    #[test]
    fn test_session_notification_casting_from_extended_mac_two_way_session_info_ntf() {
        let extended_measurement = uwb_uci_packets::ExtendedAddressTwoWayRangingMeasurement {
            mac_address: 0x1234_5678_90ab,
            status: StatusCode::UciStatusOk,
            nlos: 0,
            distance: 4,
            aoa_azimuth: 5,
            aoa_azimuth_fom: 6,
            aoa_elevation: 7,
            aoa_elevation_fom: 8,
            aoa_destination_azimuth: 9,
            aoa_destination_azimuth_fom: 10,
            aoa_destination_elevation: 11,
            aoa_destination_elevation_fom: 12,
            slot_index: 0,
            rssi: u8::MAX,
        };
        let extended_two_way_session_info_ntf =
            uwb_uci_packets::ExtendedMacTwoWaySessionInfoNtfBuilder {
                sequence_number: 0x10,
                session_token: 0x11,
                rcr_indicator: 0x12,
                current_ranging_interval: 0x13,
                hus_primary_session_id: 0x00,
                two_way_ranging_measurements: vec![extended_measurement.clone()],
                vendor_data: vec![],
            }
            .build();
        let raw_ranging_data = extended_two_way_session_info_ntf.encode_to_bytes().unwrap()
            [UCI_PACKET_HEADER_LEN..]
            .to_vec();
        let range_notification =
            uwb_uci_packets::SessionInfoNtf::try_from(extended_two_way_session_info_ntf).unwrap();
        let session_notification = SessionNotification::try_from(range_notification).unwrap();
        let uci_notification_from_extended_two_way_session_info_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_extended_two_way_session_info_ntf,
            UciNotification::Session(SessionNotification::SessionInfo(SessionRangeData {
                sequence_number: 0x10,
                session_token: 0x11,
                ranging_measurement_type: uwb_uci_packets::RangingMeasurementType::TwoWay,
                hus_primary_session_id: 0x00,
                current_ranging_interval_ms: 0x13,
                ranging_measurements: RangingMeasurements::ExtendedAddressTwoWay(vec![
                    extended_measurement
                ]),
                rcr_indicator: 0x12,
                raw_ranging_data,
            }))
        );
    }

    #[test]
    fn test_session_notification_casting_from_short_mac_two_way_session_info_ntf() {
        let short_measurement = uwb_uci_packets::ShortAddressTwoWayRangingMeasurement {
            mac_address: 0x1234,
            status: StatusCode::UciStatusOk,
            nlos: 0,
            distance: 4,
            aoa_azimuth: 5,
            aoa_azimuth_fom: 6,
            aoa_elevation: 7,
            aoa_elevation_fom: 8,
            aoa_destination_azimuth: 9,
            aoa_destination_azimuth_fom: 10,
            aoa_destination_elevation: 11,
            aoa_destination_elevation_fom: 12,
            slot_index: 0,
            rssi: u8::MAX,
        };
        let short_two_way_session_info_ntf = uwb_uci_packets::ShortMacTwoWaySessionInfoNtfBuilder {
            sequence_number: 0x10,
            session_token: 0x11,
            rcr_indicator: 0x12,
            current_ranging_interval: 0x13,
            hus_primary_session_id: 0x00,
            two_way_ranging_measurements: vec![short_measurement.clone()],
            vendor_data: vec![0x02, 0x01],
        }
        .build();
        let raw_ranging_data = short_two_way_session_info_ntf.encode_to_bytes().unwrap()
            [UCI_PACKET_HEADER_LEN..]
            .to_vec();
        let range_notification =
            uwb_uci_packets::SessionInfoNtf::try_from(short_two_way_session_info_ntf).unwrap();
        let session_notification = SessionNotification::try_from(range_notification).unwrap();
        let uci_notification_from_short_two_way_session_info_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_short_two_way_session_info_ntf,
            UciNotification::Session(SessionNotification::SessionInfo(SessionRangeData {
                sequence_number: 0x10,
                session_token: 0x11,
                ranging_measurement_type: uwb_uci_packets::RangingMeasurementType::TwoWay,
                hus_primary_session_id: 0x00,
                current_ranging_interval_ms: 0x13,
                ranging_measurements: RangingMeasurements::ShortAddressTwoWay(vec![
                    short_measurement
                ]),
                rcr_indicator: 0x12,
                raw_ranging_data,
            }))
        );
    }

    #[test]
    fn test_session_notification_casting_from_extended_mac_owr_aoa_session_info_ntf() {
        let extended_measurement = uwb_uci_packets::ExtendedAddressOwrAoaRangingMeasurement {
            mac_address: 0x1234_5678_90ab,
            status: StatusCode::UciStatusOk,
            nlos: 0,
            frame_sequence_number: 1,
            block_index: 1,
            aoa_azimuth: 5,
            aoa_azimuth_fom: 6,
            aoa_elevation: 7,
            aoa_elevation_fom: 8,
        };
        let extended_owr_aoa_session_info_ntf =
            uwb_uci_packets::ExtendedMacOwrAoaSessionInfoNtfBuilder {
                sequence_number: 0x10,
                session_token: 0x11,
                rcr_indicator: 0x12,
                current_ranging_interval: 0x13,
                hus_primary_session_id: 0x00,
                owr_aoa_ranging_measurements: vec![extended_measurement.clone()],
                vendor_data: vec![],
            }
            .build();
        let raw_ranging_data = extended_owr_aoa_session_info_ntf.encode_to_bytes().unwrap()
            [UCI_PACKET_HEADER_LEN..]
            .to_vec();
        let range_notification =
            uwb_uci_packets::SessionInfoNtf::try_from(extended_owr_aoa_session_info_ntf).unwrap();
        let session_notification = SessionNotification::try_from(range_notification).unwrap();
        let uci_notification_from_extended_owr_aoa_session_info_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_extended_owr_aoa_session_info_ntf,
            UciNotification::Session(SessionNotification::SessionInfo(SessionRangeData {
                sequence_number: 0x10,
                session_token: 0x11,
                ranging_measurement_type: uwb_uci_packets::RangingMeasurementType::OwrAoa,
                hus_primary_session_id: 0x00,
                current_ranging_interval_ms: 0x13,
                ranging_measurements: RangingMeasurements::ExtendedAddressOwrAoa(
                    extended_measurement
                ),
                rcr_indicator: 0x12,
                raw_ranging_data,
            }))
        );
    }

    #[test]
    fn test_session_notification_casting_from_short_mac_owr_aoa_session_info_ntf() {
        let short_measurement = uwb_uci_packets::ShortAddressOwrAoaRangingMeasurement {
            mac_address: 0x1234,
            status: StatusCode::UciStatusOk,
            nlos: 0,
            frame_sequence_number: 1,
            block_index: 1,
            aoa_azimuth: 5,
            aoa_azimuth_fom: 6,
            aoa_elevation: 7,
            aoa_elevation_fom: 8,
        };
        let short_owr_aoa_session_info_ntf = uwb_uci_packets::ShortMacOwrAoaSessionInfoNtfBuilder {
            sequence_number: 0x10,
            session_token: 0x11,
            rcr_indicator: 0x12,
            current_ranging_interval: 0x13,
            hus_primary_session_id: 0x00,
            owr_aoa_ranging_measurements: vec![short_measurement.clone()],
            vendor_data: vec![],
        }
        .build();
        let raw_ranging_data = short_owr_aoa_session_info_ntf.encode_to_bytes().unwrap()
            [UCI_PACKET_HEADER_LEN..]
            .to_vec();
        let range_notification =
            uwb_uci_packets::SessionInfoNtf::try_from(short_owr_aoa_session_info_ntf).unwrap();
        let session_notification = SessionNotification::try_from(range_notification).unwrap();
        let uci_notification_from_short_owr_aoa_session_info_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_short_owr_aoa_session_info_ntf,
            UciNotification::Session(SessionNotification::SessionInfo(SessionRangeData {
                sequence_number: 0x10,
                session_token: 0x11,
                ranging_measurement_type: uwb_uci_packets::RangingMeasurementType::OwrAoa,
                hus_primary_session_id: 0x00,
                current_ranging_interval_ms: 0x13,
                ranging_measurements: RangingMeasurements::ShortAddressOwrAoa(short_measurement),
                rcr_indicator: 0x12,
                raw_ranging_data,
            }))
        );
    }

    #[test]
    fn test_session_notification_casting_from_session_status_ntf() {
        let session_status_ntf = uwb_uci_packets::SessionStatusNtfBuilder {
            session_token: 0x20,
            session_state: uwb_uci_packets::SessionState::SessionStateActive,
            reason_code: uwb_uci_packets::ReasonCode::StateChangeWithSessionManagementCommands
                .into(),
        }
        .build();
        let session_notification_packet =
            uwb_uci_packets::SessionConfigNotification::try_from(session_status_ntf).unwrap();
        let uci_fira_major_version = UCIMajorVersion::V1;
        let session_notification = SessionNotification::try_from((
            session_notification_packet,
            uci_fira_major_version,
            false,
        ))
        .unwrap();
        let uci_notification_from_session_status_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_session_status_ntf,
            UciNotification::Session(SessionNotification::Status {
                session_id: 0x0,
                session_token: 0x20,
                session_state: uwb_uci_packets::SessionState::SessionStateActive,
                reason_code: uwb_uci_packets::ReasonCode::StateChangeWithSessionManagementCommands
                    .into(),
            })
        );
    }

    fn write_multicast_ntf_v1_payload(
        payload: &SessionUpdateControllerMulticastListNtfV1Payload,
        buffer: &mut BytesMut,
    ) {
        buffer.put_u8(payload.remaining_multicast_list_size);
        buffer.put_u8(payload.controlee_status.len() as u8);
        for elem in &payload.controlee_status {
            write_v1_controlee_status(elem, buffer);
        }
    }

    fn write_v1_controlee_status(status: &ControleeStatusV1, buffer: &mut BytesMut) {
        for elem in &status.mac_address {
            buffer.put_u8(*elem);
        }
        buffer.put_u32_le(status.subsession_id);
        buffer.put_u8(u8::from(status.status));
    }

    fn write_multicast_ntf_v2_payload(
        payload: &SessionUpdateControllerMulticastListNtfV2Payload,
        buffer: &mut BytesMut,
    ) {
        buffer.put_u8(payload.controlee_status.len() as u8);
        for elem in &payload.controlee_status {
            write_v2_controlee_status(elem, buffer);
        }
    }

    fn write_v2_controlee_status(status: &ControleeStatusV2, buffer: &mut BytesMut) {
        for elem in &status.mac_address {
            buffer.put_u8(*elem);
        }
        buffer.put_u8(u8::from(status.status));
    }

    #[test]
    fn test_session_notification_casting_from_session_update_controller_multicast_list_ntf_v1_packet(
    ) {
        let controlee_status_v1 = uwb_uci_packets::ControleeStatusV1 {
            mac_address: [0x0c, 0xa8],
            subsession_id: 0x30,
            status: uwb_uci_packets::MulticastUpdateStatusCode::StatusOkMulticastListUpdate,
        };
        let another_controlee_status_v1 = uwb_uci_packets::ControleeStatusV1 {
            mac_address: [0x0c, 0xa9],
            subsession_id: 0x31,
            status: uwb_uci_packets::MulticastUpdateStatusCode::StatusErrorKeyFetchFail,
        };
        let payload = uwb_uci_packets::SessionUpdateControllerMulticastListNtfV1Payload {
            remaining_multicast_list_size: 0x2,
            controlee_status: vec![
                controlee_status_v1.clone(),
                another_controlee_status_v1.clone(),
            ],
        };
        let mut buf = BytesMut::new();
        write_multicast_ntf_v1_payload(&payload, &mut buf);
        let session_update_controller_multicast_list_ntf_v1 =
            uwb_uci_packets::SessionUpdateControllerMulticastListNtfBuilder {
                session_token: 0x32,
                payload: Some(buf.freeze()),
            }
            .build();
        let session_notification_packet = uwb_uci_packets::SessionConfigNotification::try_from(
            session_update_controller_multicast_list_ntf_v1,
        )
        .unwrap();
        let uci_fira_major_version = UCIMajorVersion::V1;
        let session_notification = SessionNotification::try_from((
            session_notification_packet,
            uci_fira_major_version,
            false,
        ))
        .unwrap();
        let uci_notification_from_session_update_controller_multicast_list_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_session_update_controller_multicast_list_ntf,
            UciNotification::Session(SessionNotification::UpdateControllerMulticastListV1 {
                session_token: 0x32,
                remaining_multicast_list_size: 0x2,
                status_list: vec![controlee_status_v1, another_controlee_status_v1],
            })
        );
    }

    #[test]
    fn test_cast_failed_from_session_update_controller_multicast_list_ntf_v1_packet_v2_payload() {
        let controlee_status_v2 = uwb_uci_packets::ControleeStatusV2 {
            mac_address: [0x0c, 0xa8],
            status: uwb_uci_packets::MulticastUpdateStatusCode::StatusOkMulticastListUpdate,
        };
        let another_controlee_status_v2 = uwb_uci_packets::ControleeStatusV2 {
            mac_address: [0x0c, 0xa9],
            status: uwb_uci_packets::MulticastUpdateStatusCode::StatusErrorKeyFetchFail,
        };
        let payload = uwb_uci_packets::SessionUpdateControllerMulticastListNtfV2Payload {
            controlee_status: vec![controlee_status_v2, another_controlee_status_v2],
        };
        let mut buf = BytesMut::new();
        write_multicast_ntf_v2_payload(&payload, &mut buf);
        let session_update_controller_multicast_list_ntf_v1 =
            uwb_uci_packets::SessionUpdateControllerMulticastListNtfBuilder {
                session_token: 0x32,
                payload: Some(buf.freeze()),
            }
            .build();
        let session_notification_packet = uwb_uci_packets::SessionConfigNotification::try_from(
            session_update_controller_multicast_list_ntf_v1,
        )
        .unwrap();
        let uci_fira_major_version = UCIMajorVersion::V1;
        let session_notification = SessionNotification::try_from((
            session_notification_packet,
            uci_fira_major_version,
            false,
        ));
        assert_eq!(session_notification, Err(Error::BadParameters));
    }

    #[test]
    fn test_cast_failed_from_session_update_controller_multicast_list_ntf_v2_packet_v1_payload() {
        let controlee_status_v1 = uwb_uci_packets::ControleeStatusV1 {
            mac_address: [0x0c, 0xa8],
            subsession_id: 0x30,
            status: uwb_uci_packets::MulticastUpdateStatusCode::StatusOkMulticastListUpdate,
        };
        let payload = uwb_uci_packets::SessionUpdateControllerMulticastListNtfV1Payload {
            remaining_multicast_list_size: 0x4,
            controlee_status: vec![controlee_status_v1],
        };
        let mut buf = BytesMut::new();
        write_multicast_ntf_v1_payload(&payload, &mut buf);
        let session_update_controller_multicast_list_ntf_v1 =
            uwb_uci_packets::SessionUpdateControllerMulticastListNtfBuilder {
                session_token: 0x32,
                payload: Some(buf.freeze()),
            }
            .build();
        let session_notification_packet = uwb_uci_packets::SessionConfigNotification::try_from(
            session_update_controller_multicast_list_ntf_v1,
        )
        .unwrap();
        let uci_fira_major_version = UCIMajorVersion::V2;
        let session_notification = SessionNotification::try_from((
            session_notification_packet,
            uci_fira_major_version,
            true,
        ));
        assert_eq!(session_notification, Err(Error::BadParameters));
    }

    #[test]
    fn test_session_notification_casting_from_session_update_controller_multicast_list_ntf_v2_packet(
    ) {
        let controlee_status_v2 = uwb_uci_packets::ControleeStatusV2 {
            mac_address: [0x0c, 0xa8],
            status: uwb_uci_packets::MulticastUpdateStatusCode::StatusOkMulticastListUpdate,
        };
        let another_controlee_status_v2 = uwb_uci_packets::ControleeStatusV2 {
            mac_address: [0x0c, 0xa9],
            status: uwb_uci_packets::MulticastUpdateStatusCode::StatusErrorKeyFetchFail,
        };
        let payload = uwb_uci_packets::SessionUpdateControllerMulticastListNtfV2Payload {
            controlee_status: vec![
                controlee_status_v2.clone(),
                another_controlee_status_v2.clone(),
            ],
        };
        let mut buf = BytesMut::new();
        write_multicast_ntf_v2_payload(&payload, &mut buf);
        let session_update_controller_multicast_list_ntf_v2 =
            uwb_uci_packets::SessionUpdateControllerMulticastListNtfBuilder {
                session_token: 0x32,
                payload: Some(buf.freeze()),
            }
            .build();
        let session_notification_packet = uwb_uci_packets::SessionConfigNotification::try_from(
            session_update_controller_multicast_list_ntf_v2,
        )
        .unwrap();
        let uci_fira_major_version = UCIMajorVersion::V2;
        let session_notification = SessionNotification::try_from((
            session_notification_packet,
            uci_fira_major_version,
            true,
        ))
        .unwrap();
        let uci_notification_from_session_update_controller_multicast_list_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_session_update_controller_multicast_list_ntf,
            UciNotification::Session(SessionNotification::UpdateControllerMulticastListV2 {
                session_token: 0x32,
                status_list: vec![controlee_status_v2, another_controlee_status_v2],
            })
        );
    }

    #[test]
    fn test_session_notification_casting_from_session_data_transfer_phase_config_ntf_packet() {
        let session_data_transfer_phase_config_ntf =
            uwb_uci_packets::SessionDataTransferPhaseConfigNtfBuilder {
                session_token: 0x32,
                status: DataTransferPhaseConfigUpdateStatusCode::UciDtpcmConfigSuccessStatusOk,
            }
            .build();
        let session_notification_packet = uwb_uci_packets::SessionConfigNotification::try_from(
            session_data_transfer_phase_config_ntf,
        )
        .unwrap();
        let uci_fira_major_version = UCIMajorVersion::V1;
        let session_notification = SessionNotification::try_from((
            session_notification_packet,
            uci_fira_major_version,
            false,
        ))
        .unwrap();
        let uci_notification_from_session_data_transfer_phase_config_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_session_data_transfer_phase_config_ntf,
            UciNotification::Session(SessionNotification::DataTransferPhaseConfig {
                session_token: 0x32,
                status: DataTransferPhaseConfigUpdateStatusCode::UciDtpcmConfigSuccessStatusOk
            })
        );
    }

    #[test]
    fn test_session_notification_casting_from_short_mac_dl_tdoa_session_info_ntf_packet() {
        let dl_tdoa_measurements = vec![
            0x0a, 0x01, 0x33, 0x05, // 2(Mac address), Status, Message Type
            0x53, 0x05, 0x02, 0x05, // 2(Message control), 2(Block Index)
            0x07, 0x09, 0x0a, 0x01, // Round Index, NLoS, 2(AoA Azimuth)
            0x02, 0x05, 0x07, 0x09, // AoA Azimuth FOM, 2(AoA Elevation), AoA Elevation FOM
            0x0a, 0x01, 0x02, 0x05, // RSSI, 3(Tx Timestamp..)
            0x07, 0x09, 0x0a, 0x01, // 4(Tx Timestamp..)
            0x02, 0x05, 0x07, 0x09, // Tx Timestamp, 3(Rx Timestamp..)
            0x05, 0x07, 0x09, 0x0a, // 2(Rx Timestamp), 2(Anchor Cfo)
            0x01, 0x02, 0x05, 0x07, // 2(Cfo), 2(Initiator Reply Time..)
            0x09, 0x05, 0x07, 0x09, // 2(Initiator Reply Time), 2(Responder Reply Time..)
            0x0a, 0x01, 0x02, 0x05, // 2(Responder Reply Time), 2(Initiator-Responder ToF)
            0x07, 0x09, 0x07, 0x09, // 4(Anchor Location..)
            0x05, 0x07, 0x09, 0x0a, // 4(Anchor Location..)
            0x01, 0x02, 0x05, 0x07, // 2(Anchor Location..), 2(Active Ranging Rounds..)
            0x09, 0x0a, 0x01, 0x02, // 4(Active Ranging Rounds..)
            0x05, 0x07, 0x09, 0x05, // 4(Active Ranging Rounds)
        ];
        let short_mac_dl_tdoa_session_info_ntf =
            uwb_uci_packets::ShortMacDlTDoASessionInfoNtfBuilder {
                current_ranging_interval: 0x13,
                hus_primary_session_id: 0x00,
                dl_tdoa_measurements: dl_tdoa_measurements.clone(),
                no_of_ranging_measurements: 1,
                rcr_indicator: 0x12,
                sequence_number: 0x10,
                session_token: 0x11,
            }
            .build();
        let raw_ranging_data = short_mac_dl_tdoa_session_info_ntf.encode_to_bytes().unwrap()
            [UCI_PACKET_HEADER_LEN..]
            .to_vec();
        let short_measurement =
            ShortAddressDlTdoaRangingMeasurement::parse(&dl_tdoa_measurements, 1).unwrap();
        let range_notification_packet =
            uwb_uci_packets::SessionInfoNtf::try_from(short_mac_dl_tdoa_session_info_ntf).unwrap();
        let session_notification =
            SessionNotification::try_from(range_notification_packet).unwrap();
        let uci_notification_from_short_mac_dl_tdoa_session_info_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_short_mac_dl_tdoa_session_info_ntf,
            UciNotification::Session(SessionNotification::SessionInfo(SessionRangeData {
                sequence_number: 0x10,
                session_token: 0x11,
                ranging_measurement_type: uwb_uci_packets::RangingMeasurementType::DlTdoa,
                current_ranging_interval_ms: 0x13,
                hus_primary_session_id: 0x00,
                ranging_measurements: RangingMeasurements::ShortAddressDltdoa(short_measurement),
                rcr_indicator: 0x12,
                raw_ranging_data,
            }))
        );
    }

    #[test]
    fn test_session_notification_casting_from_extended_mac_dltdoa_session_info_ntf_packet() {
        let dl_tdoa_measurements = vec![
            // All Fields in Little Endian (LE)
            0x0a, 0x01, 0x33, 0x05, // 4(Mac address..)
            0x33, 0x05, 0x02, 0x05, // 4(Mac address)
            0x07, 0x09, 0x0a, 0x01, // Status, Message Type, 2(Message control),
            0x02, 0x05, 0x07, 0x09, // 2(Block Index), Round Index, NLoS,
            0x0a, 0x01, 0x02, 0x05, // 2(AoA Azimuth), AoA Azimuth FOM, 1(AoA Elevation..)
            0x07, 0x09, 0x0a, // 1(AoA Elevation), AoA Elevation FOM, RSSI,
            0x01, 0x02, 0x05, 0x07, // 4(Tx Timestamp..)
            0x09, 0x05, 0x07, 0x09, // 4(Tx Timestamp),
            0x0a, 0x01, 0x02, 0x05, // 4(Rx Timestamp..)
            0x07, 0x09, 0x05, 0x07, // 4(Rx Timestamp)
            0x09, 0x0a, 0x01, 0x02, // 2(Anchor Cfo), 2(Cfo),
            0x05, 0x07, 0x09, 0x05, // 4(Initiator Reply Time)
            0x07, 0x09, 0x0a, 0x01, // 4(Responder Reply Time),
            0x02, 0x05, 0x02, 0x05, // 2(Initiator-Responder ToF), 2(Active Ranging Rounds)
        ];
        let extended_mac_dl_tdoa_session_info_ntf =
            uwb_uci_packets::ExtendedMacDlTDoASessionInfoNtfBuilder {
                current_ranging_interval: 0x13,
                hus_primary_session_id: 0x00,
                dl_tdoa_measurements: dl_tdoa_measurements.clone(),
                no_of_ranging_measurements: 1,
                rcr_indicator: 0x12,
                sequence_number: 0x10,
                session_token: 0x11,
            }
            .build();
        let raw_ranging_data = extended_mac_dl_tdoa_session_info_ntf.encode_to_bytes().unwrap()
            [UCI_PACKET_HEADER_LEN..]
            .to_vec();
        let short_measurement =
            ExtendedAddressDlTdoaRangingMeasurement::parse(&dl_tdoa_measurements, 1).unwrap();
        let range_notification_packet =
            uwb_uci_packets::SessionInfoNtf::try_from(extended_mac_dl_tdoa_session_info_ntf)
                .unwrap();
        let session_notification =
            SessionNotification::try_from(range_notification_packet).unwrap();
        let uci_notification_from_extended_mac_dl_tdoa_session_info_ntf =
            UciNotification::Session(session_notification);
        assert_eq!(
            uci_notification_from_extended_mac_dl_tdoa_session_info_ntf,
            UciNotification::Session(SessionNotification::SessionInfo(SessionRangeData {
                sequence_number: 0x10,
                session_token: 0x11,
                ranging_measurement_type: uwb_uci_packets::RangingMeasurementType::DlTdoa,
                current_ranging_interval_ms: 0x13,
                hus_primary_session_id: 0x00,
                ranging_measurements: RangingMeasurements::ExtendedAddressDltdoa(short_measurement),
                rcr_indicator: 0x12,
                raw_ranging_data,
            }))
        );
    }

    #[test]
    #[allow(non_snake_case)] //override snake case for vendor_A
    fn test_vendor_notification_casting() {
        let vendor_9_empty_notification: uwb_uci_packets::UciNotification =
            uwb_uci_packets::UciVendor_9_NotificationBuilder { opcode: 0x40, payload: None }
                .build()
                .into();
        let vendor_A_nonempty_notification: uwb_uci_packets::UciNotification =
            uwb_uci_packets::UciVendor_A_NotificationBuilder {
                opcode: 0x41,
                payload: Some(bytes::Bytes::from_static(b"Placeholder notification.")),
            }
            .build()
            .into();
        let vendor_B_nonempty_notification: uwb_uci_packets::UciNotification =
            uwb_uci_packets::UciVendor_B_NotificationBuilder {
                opcode: 0x41,
                payload: Some(bytes::Bytes::from_static(b"Placeholder notification.")),
            }
            .build()
            .into();
        let vendor_E_nonempty_notification: uwb_uci_packets::UciNotification =
            uwb_uci_packets::UciVendor_E_NotificationBuilder {
                opcode: 0x41,
                payload: Some(bytes::Bytes::from_static(b"Placeholder notification.")),
            }
            .build()
            .into();
        let vendor_F_nonempty_notification: uwb_uci_packets::UciNotification =
            uwb_uci_packets::UciVendor_F_NotificationBuilder {
                opcode: 0x41,
                payload: Some(bytes::Bytes::from_static(b"Placeholder notification.")),
            }
            .build()
            .into();
        let uci_fira_major_version = UCIMajorVersion::V1;
        let uci_notification_from_vendor_9 = UciNotification::try_from((
            vendor_9_empty_notification,
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        let uci_notification_from_vendor_A = UciNotification::try_from((
            vendor_A_nonempty_notification,
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        let uci_notification_from_vendor_B = UciNotification::try_from((
            vendor_B_nonempty_notification,
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        let uci_notification_from_vendor_E = UciNotification::try_from((
            vendor_E_nonempty_notification,
            uci_fira_major_version.clone(),
            false,
        ))
        .unwrap();
        let uci_notification_from_vendor_F = UciNotification::try_from((
            vendor_F_nonempty_notification,
            uci_fira_major_version,
            false,
        ))
        .unwrap();
        assert_eq!(
            uci_notification_from_vendor_9,
            UciNotification::Vendor(RawUciMessage {
                gid: 0x9, // per enum GroupId in uci_packets.pdl
                oid: 0x40,
                payload: vec![],
            })
        );
        assert_eq!(
            uci_notification_from_vendor_A,
            UciNotification::Vendor(RawUciMessage {
                gid: 0xa,
                oid: 0x41,
                payload: b"Placeholder notification.".to_owned().into(),
            })
        );
        assert_eq!(
            uci_notification_from_vendor_B,
            UciNotification::Vendor(RawUciMessage {
                gid: 0xb,
                oid: 0x41,
                payload: b"Placeholder notification.".to_owned().into(),
            })
        );
        assert_eq!(
            uci_notification_from_vendor_E,
            UciNotification::Vendor(RawUciMessage {
                gid: 0xe,
                oid: 0x41,
                payload: b"Placeholder notification.".to_owned().into(),
            })
        );
        assert_eq!(
            uci_notification_from_vendor_F,
            UciNotification::Vendor(RawUciMessage {
                gid: 0xf,
                oid: 0x41,
                payload: b"Placeholder notification.".to_owned().into(),
            })
        );
    }

    #[test]
    fn test_rf_test_notification_casting_from_rf_periodic_tx_ntf() {
        let test_periodic_tx_ntf_packet = uwb_uci_packets::TestPeriodicTxNtfBuilder {
            status: uwb_uci_packets::StatusCode::UciStatusOk,
            vendor_data: vec![],
        }
        .build();
        let raw_notification_data = test_periodic_tx_ntf_packet.clone().encode_to_bytes().unwrap()
            [UCI_PACKET_HEADER_LEN..]
            .to_vec();
        let rf_test_notification =
            uwb_uci_packets::TestNotification::try_from(test_periodic_tx_ntf_packet).unwrap();
        let uci_notification = RfTestNotification::try_from(rf_test_notification).unwrap();
        let uci_notification_from_periodic_tx_ntf = UciNotification::RfTest(uci_notification);
        let status = uwb_uci_packets::StatusCode::UciStatusOk;
        assert_eq!(
            uci_notification_from_periodic_tx_ntf,
            UciNotification::RfTest(RfTestNotification::TestPeriodicTxNtf {
                status,
                raw_notification_data
            })
        );
    }

    #[test]
    fn test_rf_test_notification_casting_from_rf_per_rx_ntf() {
        let test_per_rx_ntf_packet = uwb_uci_packets::TestPerRxNtfBuilder {
            status: uwb_uci_packets::StatusCode::UciStatusOk,
            attempts: 1,
            acq_detect: 2,
            acq_reject: 3,
            rx_fail: 4,
            sync_cir_ready: 5,
            sfd_fail: 6,
            sfd_found: 7,
            phr_dec_error: 8,
            phr_bit_error: 9,
            psdu_dec_error: 10,
            psdu_bit_error: 11,
            sts_found: 12,
            eof: 13,
            vendor_data: vec![],
        }
            .build();
        let raw_notification_data = test_per_rx_ntf_packet.clone().encode_to_bytes().unwrap()
            [UCI_PACKET_HEADER_LEN..]
            .to_vec();
        let rf_test_notification =
            uwb_uci_packets::TestNotification::try_from(test_per_rx_ntf_packet).unwrap();
        let uci_notification = RfTestNotification::try_from(rf_test_notification).unwrap();
        let uci_notification_from_per_rx_ntf = UciNotification::RfTest(uci_notification);
        let status = uwb_uci_packets::StatusCode::UciStatusOk;
        let attempts = 1;
        let acq_detect = 2;
        let acq_reject = 3;
        let rx_fail = 4;
        let sync_cir_ready = 5;
        let sfd_fail = 6;
        let sfd_found = 7;
        let phr_dec_error = 8;
        let phr_bit_error = 9;
        let psdu_dec_error = 10;
        let psdu_bit_error = 11;
        let sts_found = 12;
        let eof = 13;
        assert_eq!(
            uci_notification_from_per_rx_ntf,
            UciNotification::RfTest(RfTestNotification::TestPerRxNtf(RfTestPerRxData {
                status,
                attempts,
                acq_detect,
                acq_reject,
                rx_fail,
                sync_cir_ready,
                sfd_fail,
                sfd_found,
                phr_dec_error,
                phr_bit_error,
                psdu_dec_error,
                psdu_bit_error,
                sts_found,
                eof,
                raw_notification_data
            }))
        );
    }
}

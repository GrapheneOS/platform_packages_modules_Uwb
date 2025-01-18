from abc import ABC, abstractmethod
import dataclasses
from enum import IntEnum
from typing import Tuple, List, Optional
from lib.cs import CsRangingParams
from lib.rtt import RttRangingParams
from lib.uwb import UwbRangingParams
from lib.rssi import BleRssiRangingParams


class DeviceRole(IntEnum):
  RESPONDER = 0
  INITIATOR = 1


class RangingSessionType(IntEnum):
  RAW = 0
  OOB = 1

class SecurityLevel(IntEnum):
  BASIC = 0
  SECURE = 1

class RangingMode(IntEnum):
  AUTO = 0
  HIGH_ACCURACY = 1
  HIGH_ACCURACY_PREFERRED = 2
  FUSED = 3

@dataclasses.dataclass(kw_only=True, frozen=True)
class DeviceParams:
  peer_id: str
  uwb_params: Optional[UwbRangingParams] = None
  cs_params: Optional[CsRangingParams] = None
  rtt_params: Optional[RttRangingParams] = None
  rssi_params: Optional[BleRssiRangingParams] = None


@dataclasses.dataclass(kw_only=True, frozen=True)
class RangingParams(ABC):
  session_type: RangingSessionType

@dataclasses.dataclass(kw_only=True, frozen=True)
class OobInitiatorRangingParams(RangingParams):
  session_type: RangingSessionType = RangingSessionType.OOB
  ranging_interval_ms: Tuple[int, int] = (100, 5000)
  security_level: SecurityLevel = SecurityLevel.BASIC
  ranging_mode: RangingMode = RangingMode.AUTO
  peer_ids: List[str]

@dataclasses.dataclass(kw_only=True, frozen=True)
class OobResponderRangingParams(RangingParams):
  session_type: RangingSessionType = RangingSessionType.OOB
  peer_id: str

@dataclasses.dataclass(kw_only=True, frozen=True)
class RawInitiatorRangingParams(RangingParams):
  session_type: RangingSessionType = RangingSessionType.RAW
  peer_params: List[DeviceParams]


@dataclasses.dataclass(kw_only=True, frozen=True)
class RawResponderRangingParams(RangingParams):
  session_type: RangingSessionType = RangingSessionType.RAW
  peer_params: DeviceParams


@dataclasses.dataclass(kw_only=True, frozen=True)
class SensorFusionParams:
  is_sensor_fusion_enabled: bool = True


@dataclasses.dataclass(kw_only=True, frozen=True)
class RangingPreference:
  device_role: DeviceRole
  ranging_params: RangingParams
  sensor_fusion_params: SensorFusionParams = dataclasses.field(
      default_factory=SensorFusionParams
  )
  enable_range_data_notifications: bool = True

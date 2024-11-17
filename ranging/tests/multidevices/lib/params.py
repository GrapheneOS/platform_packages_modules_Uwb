from abc import ABC, abstractmethod
import dataclasses
from enum import IntEnum
from typing import List, Optional
from lib.rtt import RttRangingParams
from lib.uwb import UwbRangingParams


class DeviceRole(IntEnum):
  RESPONDER = 0
  INITIATOR = 1


class RangingSessionType(IntEnum):
  RAW = 0
  OOB = 1


@dataclasses.dataclass(kw_only=True, frozen=True)
class DeviceParams:
  peer_id: str
  uwb_params: Optional[UwbRangingParams] = None
  cs_params = None
  rtt_params: Optional[RttRangingParams] = None


@dataclasses.dataclass(kw_only=True, frozen=True)
class RangingParams(ABC):
  session_type: RangingSessionType


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

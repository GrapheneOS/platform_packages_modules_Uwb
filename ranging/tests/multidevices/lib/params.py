from abc import ABC, abstractmethod
import dataclasses
import enum
from types import MappingProxyType
from typing import Tuple, List

from lib.cs import CsRangingParams
from lib.rtt import RttRangingParams
from lib.uwb import UwbRangingParams
from lib.rssi import BleRssiRangingParams
from lib.wifipd import WifiPdRangingParams

ADVERTISE_SETTINGS = MappingProxyType(
    {
        "AdvertiseMode": "ADVERTISE_MODE_LOW_LATENCY",
        "TxPowerLevel": "ADVERTISE_TX_POWER_HIGH",
        "Connectable": True,
        "Timeout": 0,
    }
)

ADVERTISE_DATA = MappingProxyType(
    {"IncludeDeviceName": True, "IncludeTxPowerLevel": False}
)

SCAN_SETTINGS = MappingProxyType({"ScanMode": "SCAN_MODE_LOW_LATENCY", "Legacy": False})


@enum.unique
class DeviceRole(enum.IntEnum):
  RESPONDER = 0
  INITIATOR = 1

@enum.unique
class MotionState(enum.IntEnum):
  MOTION_NOT_DETECTED = 0
  MOTION_SLIGHT = 1
  MOTION_MODERATE = 2
  MOTION_LARGE = 3

@enum.unique
class RangingSessionType(enum.IntEnum):
  RAW = 0
  OOB = 1


@enum.unique
class RangingTechnology(enum.IntEnum):
  UWB = 0
  BLE_CS = 1
  WIFI_RTT = 2
  BLE_RSSI = 3
  WIFI_PD = 5


@enum.unique
class SecurityLevel(enum.IntEnum):
  BASIC = 0
  SECURE = 1


@enum.unique
class RangingMode(enum.IntEnum):
  AUTO = 0
  HIGH_ACCURACY = 1
  HIGH_ACCURACY_PREFERRED = 2
  FUSED = 3


@dataclasses.dataclass(kw_only=True, frozen=True)
class DeviceParams:
  peer_id: str
  uwb_params: UwbRangingParams | None = None
  cs_params: CsRangingParams | None = None
  rtt_params: RttRangingParams | None = None
  rssi_params: BleRssiRangingParams | None = None
  wifi_pd_params: WifiPdRangingParams | None = None


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
  measurement_limit: int = 0

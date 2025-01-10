"""Class for UWB ranging parameters for testing."""

import dataclasses
from enum import IntEnum
from typing import List, Optional


class ConfigId(IntEnum):
  UNICAST_DS_TWR = 1
  MULTICAST_DS_TWR = 2
  PROVISIONED_UNICAST_DS_TWR = 3
  PROVISIONED_MULTICAST_DS_TWR = 4
  PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR = 5
  PROVISIONED_UNICAST_DS_TWR_VERY_FAST = 6


class RangingUpdateRate(IntEnum):
  AUTOMATIC = 1
  INFREQUENT = 2
  FREQUENT = 3


class SlotDuration(IntEnum):
  MILLIS_1 = 1
  MILLIS_2 = 2


@dataclasses.dataclass(kw_only=True)
class UwbRangingParams:
  """Class for Uwb ranging parameters."""

  session_id: int
  sub_session_id: int = 0
  config_id: ConfigId
  device_address: List[int]
  session_key_info: List[int] = dataclasses.field(
      default_factory=lambda: [1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1]
  )
  sub_session_key_info: Optional[List[int]] = None
  peer_address: List[int]
  ranging_update_rate: RangingUpdateRate = RangingUpdateRate.AUTOMATIC
  slot_duration_ms: SlotDuration = SlotDuration.MILLIS_2
  is_aoa_disabled: bool = False

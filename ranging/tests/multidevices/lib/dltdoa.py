"""Class for UWB ranging parameters for testing."""

import dataclasses
from enum import IntEnum
from typing import List

from lib.uwb import SlotDuration


class MeasurementVersion(IntEnum):
  VERSION_1 = 1
  VERSION_2 = 2


@dataclasses.dataclass(kw_only=True)
class DlTdoaRangingParams:
  """Class for Uwb DL-TDoA ranging parameters."""

# adb shell cmd uwb start-dl-tdoa-ranging-session \
#     --session-id 305419896 \
#     --channel-number 9 \
#     --slots-per-ranging-round 10 \
#     --slot-duration-rstu 2400 \
#     --ranging-interval-ms 1000 \
#     --preamble-code-index 12 \
#     --sts-iv 334455667788 \
#     --vendor-id 2211 \
#     --device-address 258 \
#     --number-of-ranging-rounds 4 \
#     --ranging-round-indexes 0,1,2,3

  session_id: int = 305419896
  device_address: List[int] = dataclasses.field(
      default_factory=lambda: [0x01, 0x02]
  )
  session_key_info: List[int] = dataclasses.field(
      default_factory=lambda: [0x22, 0x11, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88]
  )
  channel_number: int = 9
  preamble_code_index: int = 12
  ranging_interval_ms: int = 1000
  slot_duration: SlotDuration = SlotDuration.MILLIS_2
  slot_per_ranging_round: int = 10
  ranging_round_indexes: List[int] = dataclasses.field(
      default_factory=lambda: [0, 1, 2, 3]
  )
  measurement_version: MeasurementVersion = MeasurementVersion.VERSION_1

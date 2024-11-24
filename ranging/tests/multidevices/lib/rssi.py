"""Class for Ble Rssi ranging parameters for testing."""

import dataclasses
from enum import IntEnum
from typing import List


class RangingUpdateRate(IntEnum):
    AUTOMATIC = 1
    INFREQUENT = 2
    FREQUENT = 3


@dataclasses.dataclass(kw_only=True)
class BleRssiRangingParams:
  """Class for Uwb ranging parameters."""

  peer_address: List[int]
  ranging_update_rate: RangingUpdateRate = RangingUpdateRate.AUTOMATIC
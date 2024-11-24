"""Class for CS ranging parameters for testing."""

import dataclasses
from enum import IntEnum
from typing import List

class RangingUpdateRate(IntEnum):
  AUTOMATIC = 1
  INFREQUENT = 2
  FREQUENT = 3

class SecurityLevel(IntEnum):
  LEVEL_1 = 1
  LEVEL_4 = 4

@dataclasses.dataclass(kw_only=True)
class CsRangingParams:
  """Class for CS ranging parameters."""

  peer_address: List[int]
  ranging_update_rate: RangingUpdateRate = RangingUpdateRate.AUTOMATIC
  security_level: SecurityLevel = SecurityLevel.LEVEL_1

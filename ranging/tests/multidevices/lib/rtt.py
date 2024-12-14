"""Class for Rtt ranging parameters for testing."""

import dataclasses
from enum import IntEnum


class RangingUpdateRate(IntEnum):
  AUTOMATIC = 1
  INFREQUENT = 2
  FREQUENT = 3


@dataclasses.dataclass(kw_only=True)
class RttRangingParams:
  """Class for Uwb ranging parameters."""

  service_name: str
  ranging_update_rate: RangingUpdateRate = RangingUpdateRate.AUTOMATIC
  enable_periodic_ranging_hw_feature: bool = False

"""Class for Wifi-PD ranging parameters for testing."""

import dataclasses
from enum import IntEnum


class RangingUpdateRate(IntEnum):
  AUTOMATIC = 1
  INFREQUENT = 2
  FREQUENT = 3

class PasnMode(IntEnum):
  UNAUTHENTICATED = 0
  AUTHENTICATED = 1

class PreambleType(IntEnum):
  LEGACY = 0
  HT = 1
  VHT = 2
  HE = 3
  EHT = 4

class ChannelWidth(IntEnum):
  MHZ_20 = 0
  MHZ_40 = 1
  MHZ_80 = 2
  MHZ_160 = 3
  MHZ_80_PLUS_80 = 4
  MHZ_320 = 5

@dataclasses.dataclass(kw_only=True)
class WifiPdRangingParams:
  """Class for Wifi PD ranging parameters."""

  peer_address: str
  ranging_update_rate: RangingUpdateRate = RangingUpdateRate.AUTOMATIC
  discovery_channel_frequency_mhz: int = 2437
  pasn_mode: PasnMode = PasnMode.UNAUTHENTICATED
  device_ik: bytes | None = None
  password: str | None = None
  preamble_type: PreambleType = PreambleType.LEGACY
  is_responder_80211az_ntb_supported: bool = True
  channel_width: ChannelWidth = ChannelWidth.MHZ_20

def get_best_wifi_pd_params(initiator_caps, responder_caps, responder_mac_address):
    """Pick best parameters based on capabilities."""
    common_pasn_modes = set(initiator_caps['supported_pasn_modes']) & set(
        responder_caps['supported_pasn_modes']
    )
    pasn_mode = PasnMode.UNAUTHENTICATED

    preamble_type = min(
        initiator_caps['max_preamble'], responder_caps['max_preamble']
    )

    channel_width = min(
        initiator_caps['max_channel_width'], responder_caps['max_channel_width']
    )

    common_channels = set(
        initiator_caps['supported_discovery_channel_frequencies_mhz']
    ) & set(responder_caps['supported_discovery_channel_frequencies_mhz'])
    discovery_channel = next(iter(common_channels)) if common_channels else 2437

    return WifiPdRangingParams(
        peer_address=responder_mac_address,
        discovery_channel_frequency_mhz=discovery_channel,
        pasn_mode=pasn_mode,
        preamble_type=preamble_type,
        channel_width=channel_width,
        is_responder_80211az_ntb_supported=responder_caps[
            'is_80211az_ntb_supported'
        ],
    )

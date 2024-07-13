"""Class for UWB ranging parameters for testing."""

import dataclasses
from enum import IntEnum
from typing import Any, Dict, List, Optional


class Constants:
  """Class for ranging parameter constants."""

  class DeviceType(IntEnum):
    CONTROLEE = 0
    CONTROLLER = 1

  class ConfigType(IntEnum):
    UNICAST_DS_TWR = 1
    MULTICAST_DS_TWR = 2
    PROVISIONED_UNICAST_DS_TWR = 4
    PROVISIONED_MULTICAST_DS_TWR = 5
    PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR = 7

  class RangingUpdateRate(IntEnum):
    AUTOMATIC = 1
    INFREQUENT = 2
    FREQUENT = 3

  class SlotDuration(IntEnum):
    MILLIS_1 = 1
    MILLIS_2 = 2

  class RangeDataConfigType(IntEnum):
    """Distance-based notifications are not supported in tests-- only accepted values are ENABLE or DISABLE."""

    DISABLE = 0
    ENABLE = 1


# TODO(b/349419138): Dead code
@dataclasses.dataclass
class UwbRangingReconfigureParams:
  """Class for UWB ranging reconfigure parameters.

  Attributes:
    action: Type of reconfigure action.
    address_list: new address list.
    block_stride_length: block stride length
    sub_session_id_list: provisioned sts sub session id list.
    sub_session_key_list: provisioned sts sub session key list.
  """

  action: Optional[int] = None
  address_list: Optional[List[List[int]]] = None
  block_stride_length: Optional[int] = None
  sub_session_id_list: Optional[List[int]] = None
  sub_session_key_list: Optional[List[int]] = None

  def to_dict(self) -> Dict[str, Any]:
    """Returns UWB ranging reconfigure parameters in dictionary for sl4a.

    Returns:
      UWB ranging reconfigure parameters in dictionary.
    """
    reconfigure_params = {}
    if self.address_list is not None:
      reconfigure_params["action"] = self.action
      reconfigure_params["addressList"] = self.address_list
      if self.sub_session_id_list is not None:
        reconfigure_params["subSessionIdList"] = self.sub_session_id_list
      if self.sub_session_key_list is not None:
        reconfigure_params["subSessionKeyList"] = self.sub_session_key_list
    elif self.block_stride_length is not None:
      reconfigure_params["blockStrideLength"] = self.block_stride_length
    return reconfigure_params


# TODO(b/349419138): Dead code
@dataclasses.dataclass
class UwbRangingControleeParams:
  """Class for UWB ranging controlee parameters.

  Attributes:
    action: Type of reconfigure action.
    address_list: new address list.
    sub_session_id_list: provisioned sts sub session id list.
    sub_session_key_list: provisioned sts sub session key list.
  """

  action: Optional[int] = None
  address_list: Optional[List[List[int]]] = None
  sub_session_id_list: Optional[List[int]] = None
  sub_session_key_list: Optional[List[int]] = None

  def to_dict(self) -> Dict[str, Any]:
    """Returns UWB ranging controlee parameters in dictionary for sl4a.

    Returns:
      UWB ranging controlee parameters in dictionary.
    """
    controlee_params = {}
    if self.action is not None:
      controlee_params["action"] = self.action
    if self.address_list is not None:
      controlee_params["addressList"] = self.address_list
    if self.sub_session_id_list is not None:
      controlee_params["subSessionIdList"] = self.sub_session_id_list
    if self.sub_session_key_list is not None:
      controlee_params["subSessionKeyList"] = self.sub_session_key_list
    return controlee_params


@dataclasses.dataclass(kw_only=True)
class UwbRangingParams:
  """Class for Uwb ranging parameters."""

  config_type: Constants.ConfigType
  session_id: int
  sub_session_id: int = 0
  session_key_info: List[int] = dataclasses.field(
      default_factory=lambda: [1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1]
  )
  sub_session_key_info: Optional[List[int]] = None
  peer_addresses: List[List[int]]
  update_rate_type: Constants.RangingUpdateRate = (
      Constants.RangingUpdateRate.AUTOMATIC
  )
  range_data_config_type: Constants.RangeDataConfigType = (
      Constants.RangeDataConfigType.ENABLE
  )
  slot_duration_millis: Constants.SlotDuration = Constants.SlotDuration.MILLIS_2
  is_aoa_disabled: bool = False
  device_address: List[int]
  device_type: Constants.DeviceType

  def to_dict(self) -> Dict[str, Any]:
    """Returns UWB ranging parameters in dictionary for sl4a.

    Returns:
      UWB ranging parameters in dictionary.
    """
    dict = {
        "configType": self.config_type,
        "sessionId": self.session_id,
        "subSessionId": self.sub_session_id,
        "sessionKeyInfo": self.session_key_info,
        "peerAddresses": self.peer_addresses,
        "updateRateType": self.update_rate_type,
        "rangeDataConfigType": self.range_data_config_type,
        "slotDurationMillis": self.slot_duration_millis,
        "isAoaDisabled": self.is_aoa_disabled,
        "deviceAddress": self.device_address,
        "deviceType": self.device_type,
    }

    if self.sub_session_key_info is not None:
      dict["subSessionKeyInfo"] = self.sub_session_key_info

    return dict

  def update(self, **kwargs: Any):
    """Updates the UWB parameters with the new values.

    Args:
      **kwargs: uwb attributes with new values.
    """
    for key, value in kwargs.items():
      if hasattr(self, key):
        setattr(self, key, value)

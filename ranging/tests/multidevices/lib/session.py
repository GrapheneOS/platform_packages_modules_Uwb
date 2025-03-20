from typing import Dict, List
from lib.params import *
from lib.ranging_decorator import *
from mobly import asserts
from mobly.controllers.android_device_lib.callback_handler_v2 import CallbackHandlerV2


class RangingSession:
  handle: str
  initiator_id: str
  responder_ids: Set[str]
  devices: Dict[str, RangingDecorator]
  preferences: Dict[str, RangingPreference]

  def __init__(self):
    self.handle = str(uuid4())
    self.responder_ids = set()
    self.devices = {}
    self.preferences = {}

  def set_initiator(
      self, initiator: RangingDecorator, preference: RangingPreference
  ):
    asserts.assert_true(
        preference.device_role == DeviceRole.INITIATOR,
        "Expected preference with initiator role",
    )

    self.initiator_id = initiator.id
    self.devices[initiator.id] = initiator
    self.preferences[initiator.id] = preference
    return self

  def add_responder(
      self, responder: RangingDecorator, preference: RangingPreference
  ):
    asserts.assert_true(
        preference.device_role == DeviceRole.RESPONDER,
        "Expected preference with responder role",
    )

    self.responder_ids.add(responder.id)
    self.devices[responder.id] = responder
    self.preferences[responder.id] = preference
    return self

  def start_and_assert_opened(self, start_responders: bool = True, check_responders: bool = True):
    self.devices[self.initiator_id].start_ranging(self.handle, self.preferences[self.initiator_id])

    if start_responders:
      for id in self.responder_ids:
        self.devices[id].start_ranging(self.handle, self.preferences[id])

    if self._is_using_oob():
      self._handle_oob_start_ranging()

    self.devices[self.initiator_id].assert_ranging_event_received(self.handle, Event.OPENED)

    if check_responders:
      for id in self.responder_ids:
        self.devices[id].assert_ranging_event_received(self.handle, Event.OPENED)

    return self

  def assert_received_data(self, technologies: Set[RangingTechnology] = None, check_responders: bool = True):
    if technologies is None:
      self._assert_received_data_using_any_technologies(check_responders)
    else:
      self._assert_received_data_using_technologies(technologies, check_responders)

    return self

  def stop_and_assert_closed(self, stop_responders: bool = True, check_responders: bool = True):
    self.devices[self.initiator_id].stop_ranging(self.handle)

    if self._is_using_oob():
      self._handle_oob_stop_ranging()

    if stop_responders:
      for id in self.responder_ids:
        self.devices[id].stop_ranging(self.handle)


    self.devices[self.initiator_id].assert_closed(self.handle)
    if check_responders:
      for responder_id in self.responder_ids:
        self.devices[responder_id].assert_closed(self.handle)

  def _assert_received_data_using_any_technologies(self, check_responders: bool = True):
    for responder_id in self.responder_ids:
      self.devices[self.initiator_id].verify_received_data_from_peer(
          self.handle, responder_id
      )
      if check_responders:
        self.devices[responder_id].verify_received_data_from_peer(
            self.handle, self.initiator_id
        )

  def _assert_received_data_using_technologies(
      self, technologies: Set[RangingTechnology] = None, check_responders: bool = True
  ):
    for responder_id in self.responder_ids:
      self.devices[
          self.initiator_id
      ].verify_received_data_from_peer_using_technologies(
          self.handle, responder_id, technologies
      )
      if check_responders:
        self.devices[
            responder_id
        ].verify_received_data_from_peer_using_technologies(
            self.handle, self.initiator_id, technologies
        )

  def _is_using_oob(self):
    return (
        self.preferences[self.initiator_id].ranging_params.session_type
        == RangingSessionType.OOB
    )

  def _handle_oob_start_ranging(self):
    self._oob_initiator_broadcast_to_responders(
        Event.OOB_SEND_CAPABILITIES_REQUEST
    )
    self._oob_responders_all_send_to_initiator(
        Event.OOB_SEND_CAPABILITIES_RESPONSE
    )
    self._oob_initiator_broadcast_to_responders(
        Event.OOB_SEND_SET_CONFIGURATION
    )

  def _handle_oob_stop_ranging(self):
    self._oob_initiator_broadcast_to_responders(Event.OOB_SEND_STOP_RANGING)

  def _oob_initiator_broadcast_to_responders(self, event: Event):
    responders_yet_to_receive_message = copy.deepcopy(self.responder_ids)

    for _ in self.responder_ids:
      event = self.devices[self.initiator_id].assert_ranging_event_received(
          self.handle, event
      )
      receiver_id = event.data["peer_id"]
      oob_message = event.data["data"]

      responders_yet_to_receive_message.remove(receiver_id)
      self.devices[receiver_id].ad.ranging.handleOobDataReceived(
          self.handle, self.initiator_id, oob_message
      )

    asserts.assert_equal(
        0,
        len(responders_yet_to_receive_message),
        f"Some responders did not receive message: {event}"
        f" {responders_yet_to_receive_message}",
    )

  def _oob_responders_all_send_to_initiator(self, event: Event):
    for responder_id in self.responder_ids:

      event = self.devices[responder_id].assert_ranging_event_received(
          self.handle, event
      )

      receiver_id = event.data["peer_id"]
      oob_message = event.data["data"]
      asserts.assert_equal(
          self.initiator_id,
          receiver_id,
          f"Responder sent {event} to another responder with id {receiver_id}",
      )
      self.devices[receiver_id].ad.ranging.handleOobDataReceived(
          self.handle, responder_id, oob_message
      )

  def _log_to_all(self, message):
    for device in self.devices.values():
      device.ad.ranging.logInfo(message)

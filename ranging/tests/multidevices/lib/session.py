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

  def start_and_assert_opened(self):
    ids = self.responder_ids.union({self.initiator_id})
    for id in ids:
      self.devices[id].start_ranging(self.handle, self.preferences[id])

    if self._is_using_oob():
      self._do_oob_exchange_and_assert_successful()

    for id in ids:
      self.devices[id].assert_ranging_event_received(self.handle, Event.OPENED)

    return self

  def assert_exchanged_data(self, technologies: Set[RangingTechnology] = None):
    if technologies is None:
      self._assert_exchanged_data_using_any_technologies()
    else:
      self._assert_exchanged_data_using_technologies(technologies)

    return self

  def stop_and_assert_closed(self):
    for id in self.responder_ids.union({self.initiator_id}):
      self.devices[id].stop_ranging_and_assert_closed(self.handle)

  def _assert_exchanged_data_using_any_technologies(self):
    for responder_id in self.responder_ids:
      self.devices[self.initiator_id].verify_received_data_from_peer(
          self.handle, responder_id
      )
      self.devices[responder_id].verify_received_data_from_peer(
          self.handle, self.initiator_id
      )

  def _assert_exchanged_data_using_technologies(
      self, technologies: Set[RangingTechnology] = None
  ):
    for responder_id in self.responder_ids:
      self.devices[
          self.initiator_id
      ].verify_received_data_from_peer_using_technologies(
          self.handle, responder_id, technologies
      )
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

  def _do_oob_exchange_and_assert_successful(self):
    self._handle_capabilities_request_messages()
    self._handle_capabilities_response_messages()
    self._handle_set_configuration_messages()

  def _handle_capabilities_request_messages(self):
    """The initiator should send a capabilities request to each responder"""
    responders_yet_to_receive_request = copy.deepcopy(self.responder_ids)

    for _ in self.responder_ids:
      event = self.devices[self.initiator_id].assert_ranging_event_received(
          self.handle, Event.OOB_SEND_CAPABILITIES_REQUEST
      )

      receiver_id = event.data["peer_id"]
      oob_message = event.data["data"]

      responders_yet_to_receive_request.remove(receiver_id)
      self.devices[receiver_id].ad.ranging.handleOobDataReceived(
          self.handle, self.initiator_id, oob_message
      )

    asserts.assert_equal(
        0,
        len(responders_yet_to_receive_request),
        "Some responders did not receive a capabilities request:"
        f" {responders_yet_to_receive_request}",
    )

  def _handle_capabilities_response_messages(self):
    """Every responder should send a capabilities response to the initiator"""
    for responder_id in self.responder_ids:

      event = self.devices[responder_id].assert_ranging_event_received(
          self.handle, Event.OOB_SEND_CAPABILITIES_RESPONSE
      )

      receiver_id = event.data["peer_id"]
      oob_message = event.data["data"]
      asserts.assert_equal(
          self.initiator_id,
          receiver_id,
          "Responder sent capabilities response to another responder with id"
          f" {receiver_id}",
      )
      self.devices[receiver_id].ad.ranging.handleOobDataReceived(
          self.handle, responder_id, oob_message
      )

  def _handle_set_configuration_messages(self):
    """The initiator should send a set configuration message to each responder"""
    responders_yet_to_receive_message = copy.deepcopy(self.responder_ids)

    for _ in self.responder_ids:
      event = self.devices[self.initiator_id].assert_ranging_event_received(
          self.handle, Event.OOB_SEND_SET_CONFIGURATION
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
        "Some responders did not receive a set configuration message:"
        f" {responders_yet_to_receive_message}",
    )

  def _log_to_all(self, message):
    for device in self.devices.values():
      device.ad.ranging.logInfo(message)

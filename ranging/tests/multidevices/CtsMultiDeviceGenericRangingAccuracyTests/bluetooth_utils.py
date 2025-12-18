"""Utils for Bluetooth."""

import logging
import uuid

from mobly import asserts
from mobly.controllers import android_device


def ble_bond(
    initiator: android_device.AndroidDevice,
    responder: android_device.AndroidDevice,
) -> tuple[str, str]:
  """Bonds the initiator and responder via BLE."""
  service_uuid = str(uuid.uuid4())

  # Start and advertise regular server
  responder.bluetooth.createAndAdvertiseServer(service_uuid)
  oob_data = responder.bluetooth.generateServerLocalOobData()
  asserts.assert_true(oob_data, f"{responder} OOB data not generated")

  # Connect to the advertisement using OOB data generated on responder.
  responder_bt_addr = initiator.bluetooth.createBondOob(service_uuid, oob_data)
  asserts.assert_true(responder_bt_addr, f"{initiator} Server not bonded")
  connected_devices = responder.bluetooth.getConnectedDevices()
  asserts.assert_true(
      connected_devices, f"{responder} No clients found connected to server"
  )
  initiator_bt_addr = connected_devices[0]
  return initiator_bt_addr, responder_bt_addr


def ble_unbond(
    initiator: android_device.AndroidDevice,
    responder: android_device.AndroidDevice,
    initiator_bt_addr: str,
    responder_bt_addr: str,
) -> None:
  """Unbonds the initiator and responder via BLE."""
  if not initiator.bluetooth.removeBond(responder_bt_addr):
    initiator.log.error("Server not unbonded %s", responder_bt_addr)
  if not responder.bluetooth.removeBond(initiator_bt_addr):
    responder.log.error("Client not unbonded %s", initiator_bt_addr)

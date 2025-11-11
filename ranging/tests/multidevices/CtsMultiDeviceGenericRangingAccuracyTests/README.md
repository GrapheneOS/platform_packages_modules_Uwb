# CTS Multi-Device Ranging Accuracy Tests

## Overview

This test module (`CtsMultiDeviceGenericRangingAccuracyTests`) validates the accuracy of Android's ranging APIs across multiple technologies:
* **UWB** (Ultra-Wideband)
* **BLE_CS** (Bluetooth Channel Sounding)
* **WIFI_RTT** (Wi-Fi Round-Trip-Time)

The test compares the measured distance between two devices (Initiator and Responder) against a real-world physical distance.

## ⚠️ How to Run

1.  **Physical Setup**: Place the test devices (initiator and responder) **exactly 1 meter apart**. Use a tape measure to measure the distance precisely.

2.  **Execute Test**: Run the test using `atest`:
    ```bash
    atest CtsMultiDeviceGenericRangingAccuracyTests
    ```

---

## Android CDD Ranging Requirements

This test module validates critical presence and ranging requirements defined by the Android Compatibility Definition Document (CDD).

Refer to the [Android CDD](https://source.android.com/docs/core/connect/presence-requirements#wifi-nan-reqs) for the specific requirements, particularly **Section 7.4.2.5 Wi-Fi Aware and RTT**.
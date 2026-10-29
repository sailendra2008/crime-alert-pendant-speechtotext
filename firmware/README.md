# ESP32-C3 Super Mini — Crime Alert Pendant Firmware

This firmware turns an **ESP32-C3 Super Mini** board into a wireless BLE Crime Alert Pendant. When the push button connected to **GPIO 4** is clicked, it immediately dispatches an SOS signal via Bluetooth Low Energy to the Android mobile app, triggering the app's SOS countdown and emergency dispatch.

---

## 1. Hardware Pinout & Wiring

### Wiring Diagram
```
ESP32-C3 Super Mini                     Push Button (Momentary)
  +---------------+                         +----------+
  |               |                         |          |
  |         GPIO4 |------------------------>|  Pin 1   |
  |               |                         |          |
  |           GND |------------------------>|  Pin 2   |
  |               |                         +----------+
  +---------------+
```
*(No external resistor needed! The firmware uses the internal ESP32 `INPUT_PULLUP` resistor. When the button is pressed, GPIO4 connects to GND and registers a click).*

### Status LED Indicators (Onboard LED on GPIO 8)
* **Slow Blink (every ~0.7s):** Pendant is powered on and advertising over Bluetooth (waiting for the mobile app to connect).
* **Solid Blue:** Connected to the Android mobile app.
* **Rapid Multi-Strobe Flash:** SOS button on GPIO4 was clicked and transmitted!

---

## 2. Arduino IDE Setup & Flashing Instructions

1. **Install Arduino IDE** (v2.x recommended from [arduino.cc](https://www.arduino.cc/en/software)).
2. **Add ESP32 Board URL**:
   * Open Arduino IDE, go to **File** -> **Preferences**.
   * In **Additional boards manager URLs**, add:
     ```
     https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
     ```
   * Click **OK**.
3. **Install the ESP32 Platform**:
   * Go to **Tools** -> **Board** -> **Boards Manager...**
   * Search for `esp32` by Espressif Systems and click **Install**.
4. **Open the Sketch**:
   * Open `firmware/esp32_c3_pendant/esp32_c3_pendant.ino`.
5. **Configure Board Settings (Tools menu)**:
   * **Board:** `ESP32C3 Dev Module`
   * **USB CDC On Boot:** `Enabled` ⚠️ *(CRITICAL for ESP32-C3 Super Mini USB Serial to work!)*
   * **CPU Frequency:** `160MHz (WiFi/BT)`
   * **Flash Frequency:** `80MHz`
   * **Flash Mode:** `QIO`
   * **Flash Size:** `4MB (32Mb)`
   * **Partition Scheme:** `Default 4MB with spiffs`
   * **Upload Speed:** `921600` (or `460800` if flashing fails)
   * **Port:** Select the COM port corresponding to your plugged-in ESP32-C3 Super Mini.
6. **Upload**:
   * Click the **Upload** arrow button (or press `Ctrl + U`).
   * *Tip:* If the board doesn't automatically enter download mode, hold down the `BOOT` button (GPIO 9) on the Super Mini, plug the USB-C cable in, click upload, and release after upload begins.
7. **Verify via Serial Monitor**:
   * Open **Tools** -> **Serial Monitor** at **115200 baud**.
   * You will see:
     ```
     ==================================================
        CRIME ALERT PENDANT - ESP32-C3 SUPER MINI     
     ==================================================
      Button Pin : GPIO 4 (INPUT_PULLUP -> Active LOW)
      Status LED : GPIO 8 (Onboard LED)
     ==================================================
     [BLE] Initializing CrimeAlertPendant Bluetooth...
     [BLE] Advertising started as 'CrimeAlertPendant'.
     [BLE] Waiting for Android App connection...
     ```
   * Press your button on GPIO4 to verify that the message `[ALERT] GPIO4 BUTTON PRESSED!` prints out!

---

## 3. BLE Protocol Specifications

* **Device Advertising Name:** `CrimeAlertPendant`
* **Primary Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
* **SOS Characteristic UUID:** `beb5483e-36e1-4688-b7f5-ea07361b26a8`
  * **Properties:** `READ`, `NOTIFY`, `INDICATE`
  * **Payload:**
    * `0x01` = SOS Clicked (Triggers SOS countdown on Android App)
    * `0x00` = Idle / Normal
* **Diagnostics Characteristic UUID:** `beb5483f-36e1-4688-b7f5-ea07361b26a8`
  * **Payload:** Periodic heartbeat string (e.g. `ALIVE|GPIO4_UP|#pressCount`)

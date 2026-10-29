/*
 * ==============================================================================
 * Project: ESP32-C3 Super Mini - Crime Alert Pendant
 * Target:  ESP32-C3 Super Mini (or any ESP32-C3 module)
 * Feature: Push button on GPIO4 triggers SOS alert on connected Android App via BLE
 * Author:  Pair-programmed for Real-Time Crime Monitoring System
 * ==============================================================================
 *
 * WIRING:
 * ------------------------------------------------------------------------------
 * Push Button Pin 1  -----> GPIO 4 (ESP32-C3)
 * Push Button Pin 2  -----> GND    (ESP32-C3)
 * (Internal INPUT_PULLUP is enabled, so no external resistor is required!)
 *
 * ONBOARD LED (ESP32-C3 Super Mini):
 * ------------------------------------------------------------------------------
 * Onboard LED is on GPIO 8 (Active LOW on most Super Mini boards).
 * - Blinks 1s interval while waiting for Bluetooth connection.
 * - Solid ON when connected to the Android App.
 * - Rapid strobe when GPIO4 SOS button is pressed!
 *
 * ARDUINO IDE SETUP:
 * ------------------------------------------------------------------------------
 * 1. Install ESP32 board package:
 *    File -> Preferences -> Additional Board Manager URLs:
 *    https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
 * 2. Tools -> Board -> "ESP32C3 Dev Module"
 * 3. Tools -> USB CDC On Boot -> "Enabled" (IMPORTANT for Serial output on ESP32-C3 Super Mini)
 * 4. Tools -> Upload Speed -> 921600 (or 460800)
 * 5. Tools -> Port -> Select your COM port
 * ==============================================================================
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ---------------------------------
// PIN DEFINITIONS
// ---------------------------------
#define BUTTON_PIN   4    // User's push button connected to GPIO4
#define ONBOARD_LED  8    // Onboard blue LED on ESP32-C3 Super Mini (Active LOW)

// ---------------------------------
// BLE UUIDS (Matches Android App)
// ---------------------------------
#define SERVICE_UUID               "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define SOS_CHARACTERISTIC_UUID    "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define STATUS_CHARACTERISTIC_UUID "beb5483f-36e1-4688-b7f5-ea07361b26a8"

// ---------------------------------
// GLOBAL OBJECTS & STATES
// ---------------------------------
BLEServer* pServer = nullptr;
BLECharacteristic* pSosCharacteristic = nullptr;
BLECharacteristic* pStatusCharacteristic = nullptr;

bool deviceConnected = false;
bool oldDeviceConnected = false;

// Button debouncing & state tracking
int lastButtonState = HIGH;
int buttonState = HIGH;
unsigned long lastDebounceTime = 0;
const unsigned long debounceDelay = 50; // 50ms debounce window
unsigned long pressStartTime = 0;
bool sosSentForCurrentPress = false;

// LED & Status Timing
unsigned long lastLedBlink = 0;
bool ledState = false;
unsigned long lastHeartbeat = 0;
uint32_t pressCounter = 0;

// Helper to set LED (considering active LOW on Super Mini)
void setLed(bool on) {
  // Most ESP32-C3 Super Mini onboard LEDs are active LOW
  digitalWrite(ONBOARD_LED, on ? LOW : HIGH);
}

// ---------------------------------
// BLE SERVER CALLBACKS
// ---------------------------------
class PendantServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        deviceConnected = true;
        Serial.println(F(">>> [BLE] Android Device Connected!"));
        setLed(true); // Solid LED when connected
    }

    void onDisconnect(BLEServer* pServer) override {
        deviceConnected = false;
        Serial.println(F(">>> [BLE] Device Disconnected! Restarting advertising..."));
    }
};

void setup() {
    // Initialize USB Serial
    Serial.begin(115200);
    delay(1000); // Allow CDC USB to enumerate

    Serial.println();
    Serial.println(F("=================================================="));
    Serial.println(F("   CRIME ALERT PENDANT - ESP32-C3 SUPER MINI     "));
    Serial.println(F("=================================================="));
    Serial.printf(" Button Pin : GPIO %d (INPUT_PULLUP -> Active LOW)\n", BUTTON_PIN);
    Serial.printf(" Status LED : GPIO %d (Onboard LED)\n", ONBOARD_LED);
    Serial.println(F("=================================================="));

    // Configure GPIO Pins
    pinMode(BUTTON_PIN, INPUT_PULLUP);
    pinMode(ONBOARD_LED, OUTPUT);
    setLed(false);

    // Initial hardware test flash
    for (int i = 0; i < 3; i++) {
        setLed(true);
        delay(80);
        setLed(false);
        delay(80);
    }

    // ---------------------------------
    // BLE INITIALIZATION
    // ---------------------------------
    Serial.println(F("[BLE] Initializing CrimeAlertPendant Bluetooth..."));
    BLEDevice::init("CrimeAlertPendant");

    // Set high TX power for good range
    BLEDevice::setPower(ESP_PWR_LVL_P9);

    // Create BLE Server
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new PendantServerCallbacks());

    // Create Primary Safety Service
    BLEService* pService = pServer->createService(SERVICE_UUID);

    // Create SOS Characteristic (Notify & Read)
    pSosCharacteristic = pService->createCharacteristic(
        SOS_CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ   |
        BLECharacteristic::PROPERTY_NOTIFY |
        BLECharacteristic::PROPERTY_INDICATE
    );
    pSosCharacteristic->addDescriptor(new BLE2902());
    uint8_t initialSosValue = 0x00;
    pSosCharacteristic->setValue(&initialSosValue, 1);

    // Create Status / Diagnostics Characteristic
    pStatusCharacteristic = pService->createCharacteristic(
        STATUS_CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pStatusCharacteristic->addDescriptor(new BLE2902());
    pStatusCharacteristic->setValue("PENDANT_READY");

    // Start Service
    pService->start();

    // Configure Advertising
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06); // Functions that help with iPhone connections
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();

    Serial.println(F("[BLE] Advertising started as 'CrimeAlertPendant'."));
    Serial.println(F("[BLE] Waiting for Android App connection..."));
    Serial.println(F(">>> Press button on GPIO4 to trigger SOS at any time."));
}

void loop() {
    // ---------------------------------
    // 1. BUTTON POLLING & DEBOUNCING
    // ---------------------------------
    int reading = digitalRead(BUTTON_PIN);

    // Reset debounce timer if reading changed
    if (reading != lastButtonState) {
        lastDebounceTime = millis();
    }

    if ((millis() - lastDebounceTime) > debounceDelay) {
        // Debounced state settled
        if (reading != buttonState) {
            buttonState = reading;

            // When button is pressed (LOW because of INPUT_PULLUP)
            if (buttonState == LOW) {
                pressCounter++;
                pressStartTime = millis();
                sosSentForCurrentPress = true;

                Serial.println();
                Serial.println(F("**************************************************"));
                Serial.printf(" [ALERT] GPIO4 BUTTON PRESSED! Event #%u\n", pressCounter);
                Serial.println(F("**************************************************"));

                // Notify connected Android App
                if (deviceConnected && pSosCharacteristic != nullptr) {
                    uint8_t sosPayload = 0x01; // 1 = Trigger SOS
                    pSosCharacteristic->setValue(&sosPayload, 1);
                    pSosCharacteristic->notify();
                    Serial.println(F(">>> [BLE] SOS Notification dispatched to Mobile App!"));
                } else {
                    Serial.println(F(">>> [WARN] Button pressed, but no BLE central connected yet!"));
                }

                // Rapid visual flash on pendant LED
                for (int i = 0; i < 5; i++) {
                    setLed(true);
                    delay(30);
                    setLed(false);
                    delay(30);
                }
            } else {
                // Button released (HIGH)
                unsigned long duration = millis() - pressStartTime;
                Serial.printf(" [INFO] Button released. Held for %lu ms\n", duration);
                sosSentForCurrentPress = false;

                // Send 0x00 to clear or reset characteristic state
                if (deviceConnected && pSosCharacteristic != nullptr) {
                    uint8_t idlePayload = 0x00;
                    pSosCharacteristic->setValue(&idlePayload, 1);
                }
            }
        }
    }
    lastButtonState = reading;

    // ---------------------------------
    // 2. LED STATUS INDICATOR
    // ---------------------------------
    if (!deviceConnected) {
        // Slow blink while disconnected / searching
        if (millis() - lastLedBlink >= 700) {
            lastLedBlink = millis();
            ledState = !ledState;
            setLed(ledState);
        }
    } else {
        // Keep solid ON when connected (unless momentarily flashing for press)
        if (!sosSentForCurrentPress) {
            setLed(true);
        }
    }

    // ---------------------------------
    // 3. AUTO-RESTART ADVERTISING ON DISCONNECT
    // ---------------------------------
    if (!deviceConnected && oldDeviceConnected) {
        delay(500); // Give the BLE stack a moment
        pServer->startAdvertising();
        Serial.println(F("[BLE] Auto-restarted advertising for Android App."));
        oldDeviceConnected = deviceConnected;
    }

    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }

    // ---------------------------------
    // 4. PERIODIC HEARTBEAT DIAGNOSTIC (Every 10 seconds)
    // ---------------------------------
    if (deviceConnected && (millis() - lastHeartbeat >= 10000)) {
        lastHeartbeat = millis();
        if (pStatusCharacteristic != nullptr) {
            char statusBuffer[32];
            snprintf(statusBuffer, sizeof(statusBuffer), "ALIVE|GPIO4_UP|#%u", pressCounter);
            pStatusCharacteristic->setValue(statusBuffer);
            pStatusCharacteristic->notify();
        }
    }

    delay(10); // Small loop yield for RTOS task watchdog
}

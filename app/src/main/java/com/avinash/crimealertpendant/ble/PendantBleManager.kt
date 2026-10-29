package com.avinash.crimealertpendant.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class Connecting(val deviceName: String) : BleConnectionState()
    data class Connected(val deviceName: String, val address: String) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

data class SosTriggerEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "ESP32_GPIO4",
    val value: Int = 1
)

class PendantBleManager(private val context: Context) {

    companion object {
        private const val TAG = "PendantBleManager"

        // Matches ESP32-C3 Super Mini Firmware UUIDs
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val SOS_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val STATUS_CHAR_UUID: UUID = UUID.fromString("beb5483f-36e1-4688-b7f5-ea07361b26a8")
        val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_PERIOD_MS = 12000L
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _sosTriggerFlow = MutableSharedFlow<SosTriggerEvent>(extraBufferCapacity = 10)
    val sosTriggerFlow = _sosTriggerFlow.asSharedFlow()

    private val _logMessages = MutableStateFlow<List<String>>(listOf("System initialized. Ready to connect ESP32-C3."))
    val logMessages = _logMessages.asStateFlow()

    var targetDeviceName: String = "CrimeAlertPendant"

    fun log(msg: String) {
        Log.d(TAG, msg)
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val current = _logMessages.value.toMutableList()
        current.add(0, "[$time] $msg")
        if (current.size > 50) current.removeAt(current.lastIndex)
        _logMessages.value = current
    }

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) {
            log("Scan failed: Missing Bluetooth permissions.")
            _connectionState.value = BleConnectionState.Error("Missing Bluetooth permissions")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            log("Scan failed: Bluetooth is turned off.")
            _connectionState.value = BleConnectionState.Error("Please enable Bluetooth")
            return
        }

        if (isScanning) return

        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.Scanning
        isScanning = true
        log("Started scanning for ESP32-C3 ($targetDeviceName)...")

        val filters = listOf(
            ScanFilter.Builder().setDeviceName(targetDeviceName).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Fallback broad scan if targeted filter returns empty in noisy environments
        bleScanner?.startScan(null, settings, scanCallback)

        mainHandler.postDelayed({
            if (isScanning) {
                stopScan()
                if (_connectionState.value is BleConnectionState.Scanning) {
                    _connectionState.value = BleConnectionState.Disconnected
                    log("Scan completed.")
                }
            }
        }, SCAN_PERIOD_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
            isScanning = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name ?: ""
                val currentList = _discoveredDevices.value
                if (!currentList.any { it.address == device.address }) {
                    _discoveredDevices.value = currentList + device
                    log("Discovered BLE device: ${name.ifEmpty { "Unknown" }} (${device.address})")
                }

                // Auto-connect if matches target device name or service UUID
                if (name.equals(targetDeviceName, ignoreCase = true) && _connectionState.value == BleConnectionState.Scanning) {
                    log("Found matching pendant: $name! Auto-connecting...")
                    stopScan()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            log("BLE scan failed with error code: $errorCode")
            _connectionState.value = BleConnectionState.Error("Scan failed: error $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        val deviceName = device.name ?: targetDeviceName
        _connectionState.value = BleConnectionState.Connecting(deviceName)
        log("Connecting to $deviceName (${device.address})...")

        bluetoothGatt?.close()
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        log("Disconnecting from pendant...")
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.Disconnected
    }

    // Manual test trigger from app
    fun simulateGpio4SosClick() {
        log("Simulating GPIO4 SOS Click from app interface...")
        scope.launch {
            _sosTriggerFlow.emit(SosTriggerEvent(source = "Simulated ESP32 GPIO4", value = 1))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val device = gatt?.device
            val name = device?.name ?: targetDeviceName
            val address = device?.address ?: ""

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connected to GATT Server: $name! Discovering services...")
                    _connectionState.value = BleConnectionState.Connected(name, address)
                    // Request MTU or start discovering services
                    mainHandler.postDelayed({
                        gatt?.discoverServices()
                    }, 300)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("Pendant disconnected.")
                    _connectionState.value = BleConnectionState.Disconnected
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
                else -> {
                    log("Connection state changed: $newState (status: $status)")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                log("Services discovered successfully.")
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val sosChar = service.getCharacteristic(SOS_CHAR_UUID)
                    if (sosChar != null) {
                        subscribeToNotifications(gatt, sosChar)
                    } else {
                        log("Warning: SOS Characteristic not found in service.")
                    }
                } else {
                    log("Target Safety Service not found on device. Checking all services...")
                    for (s in gatt.services) {
                        val c = s.getCharacteristic(SOS_CHAR_UUID)
                        if (c != null) {
                            subscribeToNotifications(gatt, c)
                            break
                        }
                    }
                }
            } else {
                log("Service discovery failed with status: $status")
            }
        }

        @SuppressLint("MissingPermission")
        private fun subscribeToNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val success = gatt.setCharacteristicNotification(characteristic, true)
            if (success) {
                val descriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                    log("Subscribed to SOS Notifications on GPIO4 Characteristic!")
                } else {
                    log("CCCD Descriptor not found on characteristic.")
                }
            } else {
                log("Failed to enable characteristic notification.")
            }
        }

        // Android 13+ Callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicUpdate(characteristic.uuid, value)
        }

        // Legacy Callback for Android 12 and below
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic?.value ?: ByteArray(0)
            if (characteristic != null) {
                handleCharacteristicUpdate(characteristic.uuid, value)
            }
        }

        private fun handleCharacteristicUpdate(uuid: UUID, value: ByteArray) {
            if (uuid == SOS_CHAR_UUID) {
                val byteVal = if (value.isNotEmpty()) value[0].toInt() else 0
                val stringVal = String(value)

                log("Incoming Notification on SOS Characteristic: byte=$byteVal, string='$stringVal'")

                // GPIO4 button pressed generates value 0x01 or "SOS"
                if (byteVal == 1 || stringVal.contains("SOS", ignoreCase = true)) {
                    log("🚨 ALERT DETECTED: ESP32 GPIO4 Button Clicked!")
                    scope.launch {
                        _sosTriggerFlow.emit(
                            SosTriggerEvent(
                                timestamp = System.currentTimeMillis(),
                                source = "ESP32-C3 GPIO4 Pendant",
                                value = byteVal
                            )
                        )
                    }
                }
            } else if (uuid == STATUS_CHAR_UUID) {
                val statusMsg = String(value)
                log("Pendant Heartbeat: $statusMsg")
            }
        }
    }
}

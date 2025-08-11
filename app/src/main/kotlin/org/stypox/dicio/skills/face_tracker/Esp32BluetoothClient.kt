package org.stypox.dicio.skills.face_tracker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Клиент для подключения к устройству на базе ESP32 (например, M5Stack)
 * по классическому Bluetooth и отправки ему углов yaw/pitch.
 * Соединение устанавливается асинхронно при вызове [connect].
 */
class Esp32BluetoothClient(
    private val context: Context, // контекст нужен для проверки разрешений
    private val deviceName: String = "M5Stack" // имя целевого устройства
) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    @Volatile private var socket: BluetoothSocket? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    /**
     * Пытается подключиться к уже спаренному устройству. Если это не удаётся,
     * вызывается [onFail], чтобы, например, показать окно поиска устройств.
     */
    fun connect(onFail: () -> Unit = {}) {
        thread {
            try {
                val bt = adapter ?: return@thread.also { onFail() }
                if (!bt.isEnabled) return@thread.also { onFail() }

                if (!hasConnectPermission()) {
                    Log.w(TAG, "Нет разрешения BLUETOOTH_CONNECT")
                    return@thread.also { onFail() }
                }

                // Ищем среди уже спаренных устройств подходящее по имени
                val device = bt.bondedDevices.firstOrNull {
                    it.name?.contains(deviceName, ignoreCase = true) == true
                }
                if (device == null) {
                    onFail()
                    return@thread
                }

                bt.cancelDiscovery() // останавливаем возможное сканирование

                // Используем стандартный UUID SPP
                val uuid = device.uuids?.firstOrNull()?.uuid ?: UUID.fromString(SPP_UUID)
                socket = device.createRfcommSocketToServiceRecord(uuid).apply { connect() }
            } catch (e: IOException) {
                Log.e(TAG, "Не удалось подключиться к $deviceName", e)
                onFail()
            } catch (e: SecurityException) {
                Log.e(TAG, "Отсутствует разрешение BLUETOOTH_CONNECT", e)
                onFail()
            }
        }
    }

    /** Подключение к конкретному устройству, выбранному пользователем. */
    fun connectTo(device: BluetoothDevice) {
        thread {
            try {
                val bt = adapter ?: return@thread
                if (!bt.isEnabled) return@thread
                if (!hasConnectPermission()) {
                    Log.w(TAG, "Нет разрешения BLUETOOTH_CONNECT")
                    return@thread
                }
                bt.cancelDiscovery()
                val uuid = device.uuids?.firstOrNull()?.uuid ?: UUID.fromString(SPP_UUID)
                socket = device.createRfcommSocketToServiceRecord(uuid).apply { connect() }
            } catch (e: IOException) {
                Log.e(TAG, "Не удалось подключиться к ${device.name}", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Отсутствует разрешение BLUETOOTH_CONNECT", e)
            }
        }
    }

    /** Начинаем сканирование устройств и передаём найденные в [onDevice]. */
    fun startDiscovery(onDevice: (BluetoothDevice) -> Unit) {
        val bt = adapter ?: return
        if (!bt.isEnabled) return
        if (!hasScanPermission()) {
            Log.w(TAG, "Нет разрешения BLUETOOTH_SCAN")
            return
        }
        cancelDiscovery()
        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) onDevice(device)
            }
        }
        context.registerReceiver(discoveryReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        bt.startDiscovery()
    }

    /** Останавливаем поиск устройств. */
    fun cancelDiscovery() {
        val receiver = discoveryReceiver
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // receiver уже удалён
            }
            discoveryReceiver = null
        }
        adapter?.cancelDiscovery()
    }

    /** Отправка рассчитанных углов yaw/pitch в градусах. */
    fun sendAngles(yaw: Float, pitch: Float) {
        try {
            val message = "%+.1f,%+.1f\n".format(yaw, pitch)
            socket?.outputStream?.write(message.toByteArray())
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при отправке данных", e)
        }
    }

    /** Закрываем соединение и останавливаем сканирование устройств. */
    fun close() {
        cancelDiscovery()
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при закрытии сокета", e)
        }
    }

    private fun hasConnectPermission() =
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission() =
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "Esp32BluetoothClient"
        // Стандартный UUID для профиля Serial Port Profile
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    }
}


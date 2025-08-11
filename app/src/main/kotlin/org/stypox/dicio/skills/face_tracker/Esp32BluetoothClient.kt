package org.stypox.dicio.skills.face_tracker

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Клиент для подключения к устройству на базе ESP32 (например, M5Stack)
 * по классическому Bluetooth и отправки ему углов yaw/pitch.
 * Соединение устанавливается асинхронно при вызове [connect].
 */
class Esp32BluetoothClient(
    private val deviceName: String = "M5Stack" // имя целевого устройства
) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    @Volatile private var socket: BluetoothSocket? = null

    /** Ищет спаренное устройство и подключается к нему в отдельном потоке. */
    fun connect() {
        thread {
            try {
                val bt = adapter ?: return@thread
                if (!bt.isEnabled) return@thread

                // Ищем среди уже спаренных устройств подходящее по имени
                val device = bt.bondedDevices.firstOrNull {
                    it.name?.contains(deviceName, ignoreCase = true) == true
                } ?: return@thread

                // Перед подключением останавливаем возможное сканирование
                bt.cancelDiscovery()

                // Используем стандартный UUID SPP
                val uuid = device.uuids?.firstOrNull()?.uuid ?: UUID.fromString(SPP_UUID)
                socket = device.createRfcommSocketToServiceRecord(uuid).apply { connect() }
            } catch (e: IOException) {
                Log.e(TAG, "Не удалось подключиться к $deviceName", e)
            }
        }
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

    /** Закрываем соединение с устройством. */
    fun close() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при закрытии сокета", e)
        }
    }

    companion object {
        private const val TAG = "Esp32BluetoothClient"
        // Стандартный UUID для профиля Serial Port Profile
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    }
}


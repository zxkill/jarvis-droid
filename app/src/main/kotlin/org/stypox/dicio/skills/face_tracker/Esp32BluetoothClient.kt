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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Клиент для подключения к устройству на базе ESP32 (например, M5Stack)
 * по классическому Bluetooth и отправки ему углов yaw/pitch.
 * Соединение устанавливается асинхронно при вызове [connect].
 */
class Esp32BluetoothClient(
    private val context: Context, // контекст нужен для проверки разрешений
    private val deviceName: String = "M5Stack", // имя целевого устройства
    private val devicePin: String = "1234", // PIN‑код, установленный на ESP32
    private val uuid: UUID = UUID.fromString(SPP_UUID) // UUID сервиса SPP
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
                var device = bt.bondedDevices.firstOrNull {
                    it.name?.contains(deviceName, ignoreCase = true) == true
                }

                // Если устройство не найдено, пытаемся обнаружить его сканированием
                if (device == null) {
                    if (!hasScanPermission()) {
                        Log.w(TAG, "Нет разрешения BLUETOOTH_SCAN")
                        onFail()
                        return@thread
                    }
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "Нет разрешения ACCESS_FINE_LOCATION")
                        onFail()
                        return@thread
                    }

                    val latch = CountDownLatch(1)
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            val found = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            if (found?.name?.contains(deviceName, ignoreCase = true) == true) {
                                device = found
                                latch.countDown()
                            }
                        }
                    }
                    context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                    bt.startDiscovery()
                    // Ждём до 10 секунд появления нужного устройства
                    latch.await(10, TimeUnit.SECONDS)
                    bt.cancelDiscovery()
                    try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
                    if (device == null) {
                        onFail()
                        return@thread
                    }
                }

                bt.cancelDiscovery() // останавливаем возможное сканирование

                // Если устройство ещё не спарено — выполняем спаривание с PIN 1234
                if (!pairDevice(device)) {
                    Log.e(TAG, "Не удалось спарить устройство $deviceName")
                    onFail()
                    return@thread
                }

                // Используем заранее известный UUID сервиса
                val uuid = this.uuid

                // Закрываем предыдущее соединение, если оно было
                try { socket?.close() } catch (_: IOException) {}

                // Сначала пробуем установить «безопасное» RFCOMM‑соединение.
                // Если оно не удаётся (например, устройство не спарено),
                // пробуем «небезопасный» вариант без предварительного паринга.
                // В крайнем случае используем скрытые методы порта 1
                socket = try {
                    device.createRfcommSocketToServiceRecord(uuid).apply { connect() }
                } catch (e: IOException) {
                    Log.w(TAG, "Secure RFCOMM не удался, пробуем insecure", e)
                    try {
                        device.createInsecureRfcommSocketToServiceRecord(uuid).apply { connect() }
                    } catch (e2: IOException) {
                        Log.w(TAG, "Insecure RFCOMM не удался, пробуем порт 1", e2)
                        try {
                            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            (m.invoke(device, 1) as BluetoothSocket).apply { connect() }
                        } catch (e3: Exception) {
                            Log.w(TAG, "Порт 1 secure не удался, пробуем insecure", e3)
                            try {
                                val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                                (m.invoke(device, 1) as BluetoothSocket).apply { connect() }
                            } catch (e4: Exception) {
                                Log.e(TAG, "Не удалось подключиться к $deviceName", e4)
                                null
                            }
                        }
                    }
                }
                if (socket == null) onFail()
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

                // При необходимости спариваем устройство с PIN 1234
                if (!pairDevice(device)) {
                    Log.e(TAG, "Не удалось спарить устройство ${device.name}")
                    return@thread
                }

                // Используем тот же UUID сервиса, что и при автоподключении
                val uuid = this.uuid

                // Закрываем предыдущее соединение, если оно было
                try { socket?.close() } catch (_: IOException) {}

                // Аналогично автоматическому подключению пробуем сначала
                // безопасное соединение, затем небезопасное.
                // В крайнем случае используем скрытые методы порта 1.
                socket = try {
                    device.createRfcommSocketToServiceRecord(uuid).apply { connect() }
                } catch (e: IOException) {
                    Log.w(TAG, "Secure RFCOMM не удался, пробуем insecure", e)
                    try {
                        device.createInsecureRfcommSocketToServiceRecord(uuid).apply { connect() }
                    } catch (e2: IOException) {
                        Log.w(TAG, "Insecure RFCOMM не удался, пробуем порт 1", e2)
                        try {
                            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            (m.invoke(device, 1) as BluetoothSocket).apply { connect() }
                        } catch (e3: Exception) {
                            Log.w(TAG, "Порт 1 secure не удался, пробуем insecure", e3)
                            try {
                                val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                                (m.invoke(device, 1) as BluetoothSocket).apply { connect() }
                            } catch (e4: Exception) {
                                Log.e(TAG, "Не удалось подключиться к ${device.name}", e4)
                                null
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Не удалось подключиться к ${device.name}", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Отсутствует разрешение BLUETOOTH_CONNECT", e)
            }
        }
    }

    /**
     * Начинаем сканирование устройств и передаём найденные в [onDevice].
     * Требуется разрешение BLUETOOTH_SCAN. Разрешение геолокации
     * (ACCESS_FINE_LOCATION) нужно только на Android до 11 включительно,
     * поскольку на Android 12+ мы объявляем флаг neverForLocation.
     */
    fun startDiscovery(onDevice: (BluetoothDevice) -> Unit) {
        val bt = adapter ?: return
        if (!bt.isEnabled) return
        if (!hasScanPermission()) {
            Log.w(TAG, "Нет разрешения BLUETOOTH_SCAN")
            return
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Нет разрешения ACCESS_FINE_LOCATION")
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

    /**
     * Возвращает множество уже спаренных устройств.
     * Список пуст, если нет разрешения BLUETOOTH_CONNECT.
     */
    fun getBondedDevices(): Set<BluetoothDevice> =
        if (hasConnectPermission()) adapter?.bondedDevices ?: emptySet() else emptySet()

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

    /**
     * Спаривание с устройством с использованием заранее известного PIN.
     * Возвращает *true*, если устройство успешно перешло в состояние BOND_BONDED.
     */
    private fun pairDevice(device: BluetoothDevice): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return true
        if (!hasConnectPermission()) return false

        return try {
            val bondLatch = CountDownLatch(1)

            // Отслеживаем изменение состояния спаривания
            val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            val bondReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val d = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val state = intent?.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR
                    )
                    if (d?.address == device.address &&
                        (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE)
                    ) {
                        bondLatch.countDown()
                    }
                }
            }
            context.registerReceiver(bondReceiver, bondFilter)

            // При запросе PIN автоматически отправляем наш код 1234
            val pairingFilter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST).apply {
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }
            val pairingReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val d = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (d?.address == device.address) {
                        try {
                            d.javaClass.getMethod("setPin", ByteArray::class.java)
                                .invoke(d, devicePin.toByteArray())
                            d.javaClass.getMethod(
                                "setPairingConfirmation",
                                Boolean::class.javaPrimitiveType
                            ).invoke(d, true)
                            abortBroadcast()
                        } catch (e: Exception) {
                            Log.e(TAG, "Не удалось установить PIN", e)
                        }
                    }
                }
            }
            context.registerReceiver(pairingReceiver, pairingFilter)

            // Запускаем процесс спаривания
            device.javaClass.getMethod("createBond").invoke(device)

            // Ждём завершения (максимум 20 секунд)
            bondLatch.await(20, TimeUnit.SECONDS)

            try { context.unregisterReceiver(pairingReceiver) } catch (_: IllegalArgumentException) {}
            try { context.unregisterReceiver(bondReceiver) } catch (_: IllegalArgumentException) {}

            device.bondState == BluetoothDevice.BOND_BONDED
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при спаривании", e)
            false
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


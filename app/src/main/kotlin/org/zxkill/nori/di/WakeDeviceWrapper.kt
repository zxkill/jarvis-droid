package org.zxkill.nori.di

import android.content.Context
import androidx.datastore.core.DataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.zxkill.nori.io.wake.WakeDevice
import org.zxkill.nori.io.wake.WakeState
import org.zxkill.nori.io.wake.oww.OpenWakeWordDevice
import org.zxkill.nori.settings.datastore.UserSettings
import org.zxkill.nori.settings.datastore.WakeDevice.UNRECOGNIZED
import org.zxkill.nori.settings.datastore.WakeDevice.WAKE_DEVICE_NOTHING
import org.zxkill.nori.settings.datastore.WakeDevice.WAKE_DEVICE_OWW
import org.zxkill.nori.settings.datastore.WakeDevice.WAKE_DEVICE_UNSET
import org.zxkill.nori.util.distinctUntilChangedBlockingFirst
import javax.inject.Singleton

// Обёртка над реализациями устройств прослушивания ключевого слова (wake word).
// Управляет выбором устройства и предоставляет общее API.
interface WakeDeviceWrapper {
    // Состояние текущего устройства (загружено, не загружено и т.д.).
    val state: StateFlow<WakeState?>
    // True, если текущая модель слушает фразу «Hey Nori».
    val isHeyNori: StateFlow<Boolean>

    // Скачивает необходимые файлы модели.
    fun download()
    // Передаёт очередной аудиофрейм и возвращает, найдено ли ключевое слово.
    fun processFrame(audio16bitPcm: ShortArray): Boolean
    // Возвращает размер аудиофрейма, который ожидает устройство.
    fun frameSize(): Int
    // Переинициализация для освобождения ресурсов.
    fun reinitializeToReleaseResources()
}

typealias DataStoreWakeDevice = org.zxkill.nori.settings.datastore.WakeDevice

class WakeDeviceWrapperImpl(
    @ApplicationContext private val appContext: Context,
    dataStore: DataStore<UserSettings>,
    private val okHttpClient: OkHttpClient,
) : WakeDeviceWrapper {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var currentSetting: DataStoreWakeDevice
    private var lastFrameHadWrongSize = false

    // null означает, что пользователь не выбрал устройство пробуждения
    private val _state: MutableStateFlow<WakeState?> = MutableStateFlow(null)
    override val state: StateFlow<WakeState?> = _state
    private val _isHeyNori: MutableStateFlow<Boolean>
    override val isHeyNori: StateFlow<Boolean>
    private val currentDevice: MutableStateFlow<WakeDevice?>

    init {
        // Блокирующее чтение настроек: DataStore доступен сразу, как и другие компоненты.
        val (firstWakeDeviceSetting, nextWakeDeviceFlow) = dataStore.data
            .map { it.wakeDevice }
            .distinctUntilChangedBlockingFirst()

        currentSetting = firstWakeDeviceSetting
        val firstWakeDevice = buildInputDevice(firstWakeDeviceSetting)
        currentDevice = MutableStateFlow(firstWakeDevice)
        _isHeyNori = MutableStateFlow(firstWakeDevice?.isHeyNori() ?: true)
        isHeyNori = _isHeyNori

        scope.launch {
            currentDevice.collectLatest { newWakeDevice ->
                _isHeyNori.emit(newWakeDevice?.isHeyNori() ?: true)
                if (newWakeDevice == null) {
                    _state.emit(null)
                } else {
                    newWakeDevice.state.collect { _state.emit(it) }
                }
            }
        }

        scope.launch {
            nextWakeDeviceFlow.collect(::changeWakeDeviceTo)
        }
    }

    // Переключает устройство на новое значение из настроек.
    private fun changeWakeDeviceTo(setting: DataStoreWakeDevice) {
        currentSetting = setting
        val newWakeDevice = buildInputDevice(setting)
        lastFrameHadWrongSize = false
        currentDevice.update { prevWakeDevice ->
            prevWakeDevice?.destroy()
            newWakeDevice
        }
    }

    // Создаёт реализацию устройства пробуждения в зависимости от настроек.
    private fun buildInputDevice(setting: DataStoreWakeDevice): WakeDevice? {
        return when (setting) {
            UNRECOGNIZED,
            WAKE_DEVICE_UNSET,
            WAKE_DEVICE_OWW -> OpenWakeWordDevice(appContext, okHttpClient)
            WAKE_DEVICE_NOTHING -> null
        }
    }

    // Запускает процесс скачивания модели (если она поддерживается).
    override fun download() {
        currentDevice.value?.download()
    }

    // Передаёт очередной аудиофрейм устройству. Проверяет, совпадает ли размер
    // с ожидаемым, чтобы избежать ошибок при переключении устройств.
    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        val device = currentDevice.value
            ?: throw IllegalArgumentException("No wake word device is enabled")

        if (audio16bitPcm.size != device.frameSize()) {
            if (lastFrameHadWrongSize) {
                // Одно неверное по размеру сообщение может появиться при смене устройства,
                // поэтому второе подряд считаем ошибкой и выбрасываем исключение.
                throw IllegalArgumentException("Wrong audio frame size: expected ${
                    device.frameSize()} samples but got ${audio16bitPcm.size}")
            }
            lastFrameHadWrongSize = true
            return false

        } else {
            // Обрабатываем фрейм, только если размер корректный.
            lastFrameHadWrongSize = false
            return device.processFrame(audio16bitPcm)
        }
    }

    // Возвращает ожидаемый размер аудиофрейма для текущего устройства.
    override fun frameSize(): Int {
        return currentDevice.value?.frameSize() ?: 0
    }

    // Переинициализация, чтобы освободить ресурсы, когда сервис работает в фоне.
    override fun reinitializeToReleaseResources() {
        changeWakeDeviceTo(currentSetting)
    }
}

@Module
@InstallIn(SingletonComponent::class)
class WakeDeviceWrapperModule {
    @Provides
    @Singleton
    fun provideWakeDeviceWrapper(
        @ApplicationContext appContext: Context,
        dataStore: DataStore<UserSettings>,
        okHttpClient: OkHttpClient,
    ): WakeDeviceWrapper {
        return WakeDeviceWrapperImpl(appContext, dataStore, okHttpClient)
    }
}

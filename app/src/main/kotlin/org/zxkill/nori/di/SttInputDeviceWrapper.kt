package org.zxkill.nori.di

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.datastore.core.DataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.zxkill.nori.R
import org.zxkill.nori.io.input.InputEvent
import org.zxkill.nori.io.input.SttInputDevice
import org.zxkill.nori.io.input.SttState
import org.zxkill.nori.io.input.external_popup.ExternalPopupInputDevice
import org.zxkill.nori.io.input.vosk.VoskInputDevice
import org.zxkill.nori.settings.datastore.InputDevice
import org.zxkill.nori.settings.datastore.InputDevice.INPUT_DEVICE_NOTHING
import org.zxkill.nori.settings.datastore.InputDevice.INPUT_DEVICE_EXTERNAL_POPUP
import org.zxkill.nori.settings.datastore.InputDevice.INPUT_DEVICE_UNSET
import org.zxkill.nori.settings.datastore.InputDevice.INPUT_DEVICE_VOSK
import org.zxkill.nori.settings.datastore.InputDevice.UNRECOGNIZED
import org.zxkill.nori.settings.datastore.SttPlaySound
import org.zxkill.nori.settings.datastore.UserSettings
import org.zxkill.nori.util.distinctUntilChangedBlockingFirst
import javax.inject.Singleton


// Обёртка вокруг конкретной реализации устройства ввода речи (STT).
// Позволяет переключать реализацию на лету и управлять общими задачами.
interface SttInputDeviceWrapper {
    // Текущее состояние устройства распознавания речи.
    val uiState: StateFlow<SttState?>

    // Пытается загрузить устройство. Если передан обработчик, то сразу начинает слушать.
    // playSound указывает, воспроизводить ли звук начала прослушивания.
    fun tryLoad(
        thenStartListeningEventListener: ((InputEvent) -> Unit)?,
        playSound: Boolean = true,
    ): Boolean

    // Останавливает прослушивание.
    fun stopListening()

    // Вызывается при клике на кнопку микрофона в интерфейсе.
    fun onClick(eventListener: (InputEvent) -> Unit)

    // Переинициализирует устройство, чтобы освободить ресурсы.
    fun reinitializeToReleaseResources()
}

class SttInputDeviceWrapperImpl(
    @ApplicationContext private val appContext: Context,
    dataStore: DataStore<UserSettings>,
    private val localeManager: LocaleManager,
    private val okHttpClient: OkHttpClient,
    private val activityForResultManager: ActivityForResultManager,
) : SttInputDeviceWrapper {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var inputDeviceSetting: InputDevice
    private var sttPlaySoundSetting: SttPlaySound
    private var sttInputDevice: SttInputDevice?

    // null означает, что пользователь не выбрал устройство распознавания речи
    private val _uiState: MutableStateFlow<SttState?> = MutableStateFlow(null)
    override val uiState: StateFlow<SttState?> = _uiState
    private var uiStateJob: Job? = null
    private var playListeningSoundNextTime = true
    private var playNoInputSoundNextTime = true

    init {
        // Выполняем блокирующее чтение, потому что DataStore доступен сразу.
        // LocaleManager тоже инициализируется синхронно из того же хранилища.
        val (firstSettings, nextSettingsFlow) = dataStore.data
            .map { Pair(it.inputDevice, it.sttPlaySound) }
            .distinctUntilChangedBlockingFirst()

        inputDeviceSetting = firstSettings.first
        sttPlaySoundSetting = firstSettings.second
        sttInputDevice = buildInputDevice(inputDeviceSetting)
        scope.launch {
            restartUiStateJob()
        }

        scope.launch {
            // Реагируем на изменения настроек: смену устройства или звуков.
            nextSettingsFlow.collect { (inputDevice, sttPlaySound) ->
                sttPlaySoundSetting = sttPlaySound
                if (inputDeviceSetting != inputDevice) {
                    changeInputDeviceTo(inputDevice)
                }
            }
        }
    }

    // Смена реализации устройства ввода согласно новой настройке.
    private suspend fun changeInputDeviceTo(setting: InputDevice) {
        val prevSttInputDevice = sttInputDevice
        inputDeviceSetting = setting
        sttInputDevice = buildInputDevice(setting)
        prevSttInputDevice?.destroy()
        restartUiStateJob()
    }

    // Создаёт конкретную реализацию STT на основе настройки пользователя.
    private fun buildInputDevice(setting: InputDevice): SttInputDevice? {
        return when (setting) {
            UNRECOGNIZED,
            INPUT_DEVICE_UNSET,
            INPUT_DEVICE_VOSK -> VoskInputDevice(appContext, okHttpClient, localeManager)
            INPUT_DEVICE_EXTERNAL_POPUP ->
                ExternalPopupInputDevice(appContext, activityForResultManager, localeManager)
            INPUT_DEVICE_NOTHING -> null
        }
    }

    // Перезапускает корутину, наблюдающую за состоянием STT устройства.
    private suspend fun restartUiStateJob() {
        uiStateJob?.cancel()
        val newSttInputDevice = sttInputDevice
        if (newSttInputDevice == null) {
            uiStateJob = null
            _uiState.emit(null)
        } else {
            uiStateJob = scope.launch {
                newSttInputDevice.uiState.collect { state ->
                    _uiState.emit(state)
                    if (state == SttState.Listening) {
                        if (playListeningSoundNextTime) {
                            playSound(R.raw.listening_sound)
                        }
                        // После каждого перехода в режим прослушивания
                        // сбрасываем флаг, чтобы звук не воспроизводился
                        // повторно без явного указания при следующем запуске.
                        playListeningSoundNextTime = false
                    }
                }
            }
        }
    }

    // Воспроизводит звуковой сигнал в соответствии с настройками пользователя.
    private fun playSound(resid: Int) {
        val attributes = AudioAttributes.Builder()
            .setUsage(
                when (sttPlaySoundSetting) {
                    SttPlaySound.UNRECOGNIZED,
                    SttPlaySound.STT_PLAY_SOUND_UNSET,
                    SttPlaySound.STT_PLAY_SOUND_NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
                    SttPlaySound.STT_PLAY_SOUND_ALARM -> AudioAttributes.USAGE_ALARM
                    SttPlaySound.STT_PLAY_SOUND_MEDIA -> AudioAttributes.USAGE_MEDIA
                    SttPlaySound.STT_PLAY_SOUND_NONE -> return // звук отключён
                }
            )
            .build()
        val mediaPlayer = MediaPlayer.create(appContext, resid, attributes, 0)
        mediaPlayer.setVolume(0.75f, 0.75f)
        mediaPlayer.start()
    }

    // Добавляет к слушателю событие проигрывания звука при отсутствии распознанной речи.
    private fun wrapEventListener(eventListener: (InputEvent) -> Unit): (InputEvent) -> Unit = {
        if (it is InputEvent.None && playNoInputSoundNextTime) {
            // Звук «ничего не услышано» должен проигрываться только один раз
            // после старта прослушивания. Сбрасываем флаг сразу, чтобы
            // последующие паузы не вызывали повторного сигнала.
            playNoInputSoundNextTime = false
            scope.launch {
                playSound(R.raw.listening_no_input_sound)
            }
        }
        eventListener(it)
    }

    // Загружает устройство и при необходимости начинает слушать пользователя.
    override fun tryLoad(
        thenStartListeningEventListener: ((InputEvent) -> Unit)?,
        playSound: Boolean,
    ): Boolean {
        val listener = if (thenStartListeningEventListener != null) {
            wrapEventListener(thenStartListeningEventListener)
        } else null

        val device = sttInputDevice ?: return false
        playListeningSoundNextTime = playSound
        playNoInputSoundNextTime = playSound
        val loaded = device.tryLoad(listener)
        if (!loaded && listener != null) {
            // Automatically trigger download and loading of the model if it is not ready yet.
            device.onClick(listener)
            return true
        }
        return loaded
    }

    // Останавливает прослушивание.
    override fun stopListening() {
        sttInputDevice?.stopListening()
    }

    // Вызывается при ручном нажатии на кнопку микрофона.
    override fun onClick(eventListener: (InputEvent) -> Unit) {
        sttInputDevice?.onClick(wrapEventListener(eventListener))
    }

    // Переинициализация нужна, когда сервисы работают в фоне и требуют очистки.
    override fun reinitializeToReleaseResources() {
        scope.launch { changeInputDeviceTo(inputDeviceSetting) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
class SttInputDeviceWrapperModule {
    @Provides
    @Singleton
    fun provideInputDeviceWrapper(
        @ApplicationContext appContext: Context,
        dataStore: DataStore<UserSettings>,
        localeManager: LocaleManager,
        okHttpClient: OkHttpClient,
        activityForResultManager: ActivityForResultManager,
    ): SttInputDeviceWrapper {
        return SttInputDeviceWrapperImpl(
            appContext, dataStore, localeManager, okHttpClient, activityForResultManager
        )
    }
}

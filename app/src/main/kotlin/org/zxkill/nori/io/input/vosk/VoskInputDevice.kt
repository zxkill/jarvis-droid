/*
 * Taken from /e/OS Assistant
 *
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.zxkill.nori.io.input.vosk

import android.content.Context
import android.util.Log
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.zxkill.nori.di.LocaleManager
import org.zxkill.nori.io.input.InputEvent
import org.zxkill.nori.io.input.SttInputDevice
import org.zxkill.nori.io.input.SttState
import org.zxkill.nori.io.input.vosk.VoskState.Downloaded
import org.zxkill.nori.io.input.vosk.VoskState.Downloading
import org.zxkill.nori.io.input.vosk.VoskState.ErrorDownloading
import org.zxkill.nori.io.input.vosk.VoskState.ErrorLoading
import org.zxkill.nori.io.input.vosk.VoskState.ErrorUnzipping
import org.zxkill.nori.io.input.vosk.VoskState.Listening
import org.zxkill.nori.io.input.vosk.VoskState.Loaded
import org.zxkill.nori.io.input.vosk.VoskState.Loading
import org.zxkill.nori.io.input.vosk.VoskState.NotAvailable
import org.zxkill.nori.io.input.vosk.VoskState.NotDownloaded
import org.zxkill.nori.io.input.vosk.VoskState.NotInitialized
import org.zxkill.nori.io.input.vosk.VoskState.NotLoaded
import org.zxkill.nori.io.input.vosk.VoskState.Unzipping
import org.zxkill.nori.ui.util.Progress
import org.zxkill.nori.util.FileToDownload
import org.zxkill.nori.util.LocaleUtils
import org.zxkill.nori.util.distinctUntilChangedBlockingFirst
import org.zxkill.nori.util.downloadBinaryFilesWithPartial
import org.zxkill.nori.util.extractZip
import org.vosk.BuildConfig
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Реализация устройства распознавания речи на базе библиотеки Vosk.
 * Класс отвечает за скачивание и распаковку языковых моделей,
 * их загрузку в память и управление состояниями распознавания.
 * Все взаимодействие с остальной частью приложения происходит через
 * интерфейс [SttInputDevice].
 */
class VoskInputDevice(
    @ApplicationContext appContext: Context,
    private val okHttpClient: OkHttpClient,
    localeManager: LocaleManager,
) : SttInputDevice {

    private val _state: MutableStateFlow<VoskState>
    private val _uiState: MutableStateFlow<SttState>
    override val uiState: StateFlow<SttState>

    private var operationsJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val filesDir: File = appContext.filesDir
    private val cacheDir: File = appContext.cacheDir
    private val modelZipFile: File get() = File(filesDir, "vosk-model.zip")
    private val sameModelUrlCheck: File get() = File(filesDir, "vosk-model-url")
    private val modelDirectory: File get() = File(filesDir, "vosk-model")
    private val modelExistFileCheck: File get() = File(modelDirectory, "ivector")


    init {
        // Инициализируемся синхронно: LocaleManager сразу предоставляет локаль,
        // поэтому можно безопасно блокировать поток. Если отложить запуск,
        // первый вызов tryLoad() из MainActivity может пройти впустую.
        val (firstLocale, nextLocaleFlow) = localeManager.locale
            .distinctUntilChangedBlockingFirst()

        val initialState = init(firstLocale)
        _state = MutableStateFlow(initialState)
        _uiState = MutableStateFlow(initialState.toUiState())
        uiState = _uiState

        scope.launch {
            _state.collect { _uiState.value = it.toUiState() }
        }

        scope.launch {
            // При каждой смене языка приложения переинициализируем устройство
            nextLocaleFlow.collect { reinit(it) }
        }
    }

    private fun init(locale: Locale): VoskState {
        // Выбираем ссылку на языковую модель Vosk в зависимости от локали
        val modelUrl = try {
            val localeResolutionResult = LocaleUtils.resolveSupportedLocale(
                LocaleListCompat.create(locale),
                MODEL_URLS.keys
            )
            MODEL_URLS[localeResolutionResult.supportedLocaleString]
        } catch (e: LocaleUtils.UnsupportedLocaleException) {
            null
        }

        // URL модели может измениться при смене языка приложения
        // или если выйдет обновление списка моделей
        val modelUrlChanged = try {
            sameModelUrlCheck.readText() != modelUrl
        } catch (e: IOException) {
            // Файл с предыдущим URL отсутствует — значит модель ещё не скачивалась
            true
        }

        return when {
            // modelUrl == null означает, что для текущей локали нет модели Vosk
            modelUrl == null -> NotAvailable
            // если адрес модели изменился, её нужно скачать заново
            modelUrlChanged -> NotDownloaded(modelUrl)
            // zip‑файл существует — скачивание завершилось, но распаковка прервалась
            // (zip удаляется после успешной распаковки), значит следующий шаг — распаковка
            modelZipFile.exists() -> Downloaded
            // zip отсутствует, но каталог модели есть — модель скачана и распакована,
            // можно переходить к загрузке в память
            modelExistFileCheck.isDirectory -> NotLoaded
            // если нет ни zip, ни каталога — модель ещё не скачивалась
            else -> NotDownloaded(modelUrl)
        }
    }

    private suspend fun reinit(locale: Locale) {
        // Останавливаем возможные фоновые процессы
        deinit()

        // Инициализируем заново и публикуем новое состояние
        val initialState = init(locale)
        _state.emit(initialState)
    }

    private suspend fun deinit() {
        val prevState = _state.getAndUpdate { NotInitialized }
        when (prevState) {
            // Прерываем операции скачивания
            is Downloading -> {
                operationsJob?.cancel()
                operationsJob?.join()
            }
            // Прерываем распаковку
            is Unzipping -> {
                operationsJob?.cancel()
                operationsJob?.join()
            }
            // Ждём окончания загрузки модели
            is Loading -> {
                operationsJob?.join()
                when (val s = _state.getAndUpdate { NotInitialized }) {
                    NotInitialized -> {}
                    is Loaded -> {
                        s.speechService.stop()
                        s.speechService.shutdown()
                    }
                    is Listening -> {
                        stopListening(s.speechService, s.eventListener, true)
                        s.speechService.shutdown()
                    }
                    else -> {
                        Log.w(TAG, "Неожиданное состояние после загрузки: $s")
                    }
                }
            }
            // Остановка сервиса, если он уже загружен
            is Loaded -> {
                prevState.speechService.stop()
                prevState.speechService.shutdown()
            }
            // Завершение прослушивания, если модель активна
            is Listening -> {
                stopListening(prevState.speechService, prevState.eventListener, true)
                prevState.speechService.shutdown()
            }

            // Пассивные состояния — ничего делать не нужно
            is NotInitialized,
            is NotAvailable,
            is NotDownloaded,
            is ErrorDownloading,
            is Downloaded,
            is ErrorUnzipping,
            is NotLoaded,
            is ErrorLoading -> {}
        }
    }

    /**
     * Загружает модель, если она скачана, но ещё не в памяти.
     * При переданном [thenStartListeningEventListener] сразу запускает прослушивание
     * и направляет события в этот обработчик.
     * Если модель уже загружена, просто начинает слушать.
     *
     * @param thenStartListeningEventListener обработчик событий распознавания;
     *   если `null`, устройство лишь подготовится к работе
     * @return `true`, если устройство начнёт слушать (или будет готово к этому),
     *   `false` — если требуется дополнительное действие пользователя
     */
    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        val s = _state.value
        if (s == NotLoaded || s is ErrorLoading) {
            load(thenStartListeningEventListener)
            return true
        } else if (thenStartListeningEventListener != null && s is Loaded) {
            startListening(s.speechService, thenStartListeningEventListener)
            return true
        } else {
            return false
        }
    }

    /**
     * Обрабатывает нажатие на кнопку микрофона.
     * В зависимости от текущего состояния запускает загрузку, распаковку,
     * загрузку модели в память или переключает режим прослушивания.
     *
     * @param eventListener используется только если по клику
     *   начинается прослушивание — тогда сюда приходят все события
     */
    override fun onClick(eventListener: (InputEvent) -> Unit) {
        // Состояние меняется только внутри фоновых задач Downloading/Unzipping/Loading.
        // Для случаев загрузки предусмотрены специальные меры (toggleThenStartListening и load),
        // чтобы не потерять клик пользователя при резкой смене состояния.
        when (val s = _state.value) {
            is NotInitialized -> {} // ждём завершения инициализации
            is NotAvailable -> {} // для языка нет модели
            is NotDownloaded -> download(s.modelUrl)
            is Downloading -> {} // ждём окончания скачивания
            is ErrorDownloading -> download(s.modelUrl) // повторяем попытку
            is Downloaded -> unzip()
            is Unzipping -> {} // ждём распаковки
            is ErrorUnzipping -> unzip() // повторяем попытку
            is NotLoaded -> load(eventListener)
            is Loading -> toggleThenStartListening(eventListener) // дождаться загрузки
            is ErrorLoading -> load(eventListener) // повторяем попытку
            is Loaded -> startListening(s.speechService, eventListener)
            is Listening -> stopListening(s.speechService, s.eventListener, true)
        }
    }

    /**
     * Останавливает прослушивание, если оно активно.
     */
    override fun stopListening() {
        when (val s = _state.value) {
            is Listening -> stopListening(s.speechService, s.eventListener, true)
            else -> {}
        }
    }

    /**
     * Скачивает zip-файл с моделью.
     * Состояние переводится в [Downloading] и регулярно обновляется прогрессом.
     * В конце переходит в [Downloaded] или [ErrorDownloading].
     */
    private fun download(modelUrl: String) {
        _state.value = Downloading(Progress.UNKNOWN)

        operationsJob = scope.launch(Dispatchers.IO) {
            try {
                downloadBinaryFilesWithPartial(
                    urlsFiles = listOf(FileToDownload(modelUrl, modelZipFile, sameModelUrlCheck)),
                    httpClient = okHttpClient,
                    cacheDir = cacheDir,
                ) { progress ->
                    _state.value = Downloading(progress)
                }

                // downloadBinaryFilesWithPartial переписывает файл проверки URL.
                // Это безопасно, потому что zip уже скачан и при повторном запуске
                // будет распакован корректный архив.

            } catch (e: IOException) {
                Log.e(TAG, "Can't download Vosk model", e)
                _state.value = ErrorDownloading(modelUrl, e)
                return@launch
            }

            _state.value = Unzipping(Progress.UNKNOWN)
            unzipImpl() // используем ту же задачу для распаковки
        }
    }

    /**
     * Переводит состояние в [Unzipping] и запускает распаковку в фоне.
     */
    private fun unzip() {
        _state.value = Unzipping(Progress.UNKNOWN)

        operationsJob = scope.launch {
            unzipImpl()
        }
    }

    /**
     * Распаковывает скачанный архив модели.
     * Прогресс распаковки публикуется через состояние [Unzipping].
     * После завершения zip удаляется, чтобы не занимать память.
     * В случае ошибки устанавливается [ErrorUnzipping].
     */
    private suspend fun unzipImpl() {
        try {
            // На всякий случай удаляем предыдущую модель, если она есть
            modelDirectory.deleteRecursively()
            extractZip(
                sourceZip = modelZipFile,
                destinationDirectory = modelDirectory,
            ) { progress ->
                _state.value = Unzipping(progress)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Can't unzip Vosk model", e)
            _state.value = ErrorUnzipping(e)
            return
        }

        // После успешной распаковки удаляем архив
        if (!modelZipFile.delete()) {
            Log.w(TAG, "Не удалось удалить zip модели: $modelZipFile")
        }

        _state.value = NotLoaded
    }

    /**
     * Загружает модель в память и переводит состояние в [Loading].
     * Если передан обработчик — после загрузки сразу начинает слушать.
     * В процессе может произойти смена состояния по нажатию пользователя,
     * поэтому проверяем актуальное значение [Loading.thenStartListening].
     */
    private fun load(thenStartListeningEventListener: ((InputEvent) -> Unit)?) {
        _state.value = Loading(thenStartListeningEventListener)

        operationsJob = scope.launch {
            val speechService: SpeechService
            try {
                LibVosk.setLogLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARNINGS)
                val model = Model(modelDirectory.absolutePath)
                val recognizer = Recognizer(model, SAMPLE_RATE)
                recognizer.setMaxAlternatives(ALTERNATIVE_COUNT)
                speechService = SpeechService(recognizer, SAMPLE_RATE)
            } catch (e: IOException) {
                Log.e(TAG, "Can't load Vosk model", e)
                _state.value = ErrorLoading(e)
                return@launch
            }

            if (!_state.compareAndSet(Loading(null), Loaded(speechService))) {
                val state = _state.value
                if (state is Loading && state.thenStartListening != null) {
                    // В момент между compareAndSet и чтением состояния мог произойти клик,
                    // поэтому проверяем актуальный обработчик и сразу запускаем прослушивание
                    startListening(speechService, state.thenStartListening)

                } else if (!_state.compareAndSet(Loading(null, true), Loaded(speechService))) {
                    // Если состояние уже поменялось (например, из-за переинициализации),
                    // просто освобождаем ресурсы без запуска сервиса
                    speechService.stop()
                    speechService.shutdown()
                }

            } // иначе состояние уже стало Loaded и дополнительных действий не требуется
        }
    }

    /**
     * Атомарно меняет флаг [Loading.thenStartListening].
     * Это нужно, чтобы клик пользователя не потерялся, если состояние
     * изменится прямо во время загрузки модели.
     *
     * @param eventListener используется, если загрузка уже завершена
     *   и нужно сразу начать слушать
     */
    /**
     * Переключает отложенный запуск прослушивания.
     * Если во время загрузки модели пользователь уже нажал на кнопку,
     * этот метод запоминает обработчик и запускает прослушивание
     * сразу после завершения загрузки.
     */
    private fun toggleThenStartListening(eventListener: (InputEvent) -> Unit) {
        if (
            !_state.compareAndSet(Loading(null), Loading(eventListener)) &&
            !_state.compareAndSet(Loading(eventListener), Loading(null))
        ) {
            // Такое возможно, если load() сменил состояние между проверками
            Log.w(TAG, "Не удалось переключить thenStartListening")
            when (val newValue = _state.value) {
                is Loaded -> startListening(newValue.speechService, eventListener)
                is Listening -> stopListening(newValue.speechService, newValue.eventListener, true)
                is ErrorLoading -> {} // игнорируем клик при ошибке загрузки
                // В остальных состояниях оказаться не должны, это ошибка логики
                else -> Log.e(TAG, "Состояние не Loading/Loaded/Listening")
            }
        }
    }

    /**
     * Запускает прослушивание и переводит состояние в [Listening].
     */
    private fun startListening(
        speechService: SpeechService,
        eventListener: (InputEvent) -> Unit,
    ) {
        _state.value = Listening(speechService, eventListener)
        speechService.startListening(VoskListener(this, eventListener, speechService))
    }

    /**
     * Останавливает прослушивание и возвращает состояние [Loaded].
     * `internal`, чтобы [VoskListener] мог завершать работу.
     */
    internal fun stopListening(
        speechService: SpeechService,
        eventListener: (InputEvent) -> Unit,
        sendNoneEvent: Boolean,
    ) {
        _state.value = Loaded(speechService)
        speechService.stop()
        if (sendNoneEvent) {
            eventListener(InputEvent.None)
        }
    }

    override suspend fun destroy() {
        deinit()
        // Отменяем все запущенные корутины
        scope.cancel()
    }

    companion object {
        private const val SAMPLE_RATE = 44100.0f
        private const val ALTERNATIVE_COUNT = 5
        private val TAG = VoskInputDevice::class.simpleName

        /**
         * Ссылки на компактные модели [Vosk](https://alphacephei.com/vosk/models)
         */
        val MODEL_URLS = mapOf(
            "en" to "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "en-in" to "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            "cn" to "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
            "ru" to "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
            "fr" to "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
            "de" to "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
            "es" to "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
            "pt" to "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
            "tr" to "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
            "vn" to "https://alphacephei.com/vosk/models/vosk-model-small-vn-0.4.zip",
            "it" to "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
            "nl" to "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip",
            "ca" to "https://alphacephei.com/vosk/models/vosk-model-small-ca-0.4.zip",
            "ar" to "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip",
            "ar-tn" to "https://alphacephei.com/vosk/models/vosk-model-small-ar-tn-0.1-linto.zip",
            "fa" to "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip",
            "ph" to "https://alphacephei.com/vosk/models/vosk-model-tl-ph-generic-0.6.zip",
            "uk" to "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-nano.zip",
            "kz" to "https://alphacephei.com/vosk/models/vosk-model-small-kz-0.15.zip",
            "sv" to "https://alphacephei.com/vosk/models/vosk-model-small-sv-rhasspy-0.15.zip",
            "ja" to "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
            "eo" to "https://alphacephei.com/vosk/models/vosk-model-small-eo-0.42.zip",
            "hi" to "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            "cs" to "https://alphacephei.com/vosk/models/vosk-model-small-cs-0.4-rhasspy.zip",
            "pl" to "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip",
            "uz" to "https://alphacephei.com/vosk/models/vosk-model-small-uz-0.22.zip",
            "ko" to "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
            "br" to "https://alphacephei.com/vosk/models/vosk-model-br-0.8.zip",
            "gu" to "https://alphacephei.com/vosk/models/vosk-model-small-gu-0.42.zip",
            "tg" to "https://alphacephei.com/vosk/models/vosk-model-small-tg-0.22.zip",
            "te" to "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip",
        )
    }
}

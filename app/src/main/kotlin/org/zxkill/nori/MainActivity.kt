package org.zxkill.nori

import android.Manifest
import android.content.Intent
import android.content.Intent.ACTION_ASSIST
import android.content.Intent.ACTION_VOICE_COMMAND
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.shreyaspatil.permissionFlow.PermissionFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.zxkill.nori.di.SttInputDeviceWrapper
import org.zxkill.nori.di.WakeDeviceWrapper
import org.zxkill.nori.eval.SkillEvaluator
import org.zxkill.nori.io.wake.WakeService
import org.zxkill.nori.io.wake.WakeState.Loaded
import org.zxkill.nori.io.wake.WakeState.Loading
import org.zxkill.nori.io.wake.WakeState.NotLoaded
import org.zxkill.nori.ui.nav.Navigation
import org.zxkill.nori.util.BaseActivity
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    // Объекты внедряются через Hilt и позволяют взаимодействовать с системой.
    @Inject
    lateinit var skillEvaluator: SkillEvaluator
    @Inject
    lateinit var sttInputDevice: SttInputDeviceWrapper
    @Inject
    lateinit var wakeDevice: WakeDeviceWrapper

    // Отслеживаем запущенные корутины, чтобы вовремя отменять их.
    private var sttPermissionJob: Job? = null
    private var wakeServiceJob: Job? = null

    // Время, после которого разрешено снова реагировать на интент помощника.
    private var nextAssistAllowed = Instant.MIN

    /**
     * Метод вызывается при получении интента помощника ([ACTION_ASSIST]).
     * Он загружает модель и модуль распознавания речи, но не чаще
     * чем раз в [INTENT_BACKOFF_MILLIS], так как система иногда
     * отправляет несколько одинаковых интентов подряд.
     */
    private fun onAssistIntentReceived() {
        val now = Instant.now()
        if (nextAssistAllowed < now) {
            nextAssistAllowed = now.plusMillis(INTENT_BACKOFF_MILLIS)
            Log.d(TAG, "Получен интент ассистента")
            // Запускаем распознавание и передаём события напрямую обработчику.
            // Лямбда должна быть передана первым параметром, поэтому используем скобки.
            sttInputDevice.tryLoad({ event ->
                skillEvaluator.processInputEvent(event)
            })
        } else {
            Log.w(TAG, "Повторный интент ассистента проигнорирован")
        }
    }

    // Обработка интента, который пришёл от распознавания ключевой фразы.
    // Если приложение запущено благодаря ключевой фразе, необходимо
    // включить экран и показать активити поверх блокировки.
    private fun handleWakeWordTurnOnScreen(intent: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            intent?.action == ACTION_WAKE_WORD
        ) {
            // Приложение было запущено по ключевой фразе, показываем экран.
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Уведомление о срабатывании ключевой фразы больше не нужно.
        WakeService.cancelTriggeredNotification(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleWakeWordTurnOnScreen(intent)
        if (isAssistIntent(intent)) {
            onAssistIntentReceived()
        }
    }

    override fun onStart() {
        isInForeground += 1
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        isInForeground -= 1

        // Как только активити сворачивается или скрывается,
        // убираем её с экрана блокировки.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Переводим приложение в альбомную ориентацию при запуске
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        isCreated += 1
        // Не даём экрану гаснуть, чтобы постоянно показывать информацию
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Скрываем системные панели максимально рано,
        // чтобы избежать их мерцания при запуске
        hideSystemBars()

        handleWakeWordTurnOnScreen(intent)
        if (isAssistIntent(intent)) {
            onAssistIntentReceived()
        } else if (intent.action != ACTION_WAKE_WORD) {
            // Загружаем устройство ввода, но не запускаем прослушивание.
            sttInputDevice.tryLoad(null)
        }

        // Запускаем сервис, который слушает ключевое слово.
        WakeService.start(this)
        wakeServiceJob?.cancel()
        wakeServiceJob = lifecycleScope.launch {
            wakeDevice.state
                .map { it == NotLoaded || it == Loading || it == Loaded }
                .combine(
                    PermissionFlow.getInstance().getMultiplePermissionState(Manifest.permission.RECORD_AUDIO)
                ) { wakeState, permGranted ->
                    wakeState && permGranted.allGranted
                }
                // Перезапускаем сервис только когда итоговое значение меняется.
                .distinctUntilChanged()
                .filter { it }
                .collect { WakeService.start(this@MainActivity) }
        }

        // Если загрузка STT не удалась из-за отсутствия разрешения,
        // после получения разрешения пробуем ещё раз.
        sttPermissionJob?.cancel()
        sttPermissionJob = lifecycleScope.launch {
            PermissionFlow.getInstance().getPermissionState(Manifest.permission.RECORD_AUDIO)
                .drop(1)
                .filter { it.isGranted }
                .collect { sttInputDevice.tryLoad(null) }
        }

        composeSetContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.safeDrawingPadding()
                ) {
                    Navigation()
                }
            }
        }

        // После установки контента повторно прячем панели –
        // некоторые прошивки могут снова их показать
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // При возвращении фокуса (например, после диалогов)
            // снова скрываем системные панели
            hideSystemBars()
        }
    }

    /**
     * Скрывает строку состояния и навигации и задаёт
     * поведение для их временного появления по жесту.
     */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        // Сервис ключевого слова продолжает работать в фоне,
        // поэтому освобождаем ресурсы, которые ему не нужны.
        sttInputDevice.reinitializeToReleaseResources()
        isCreated -= 1
        super.onDestroy()
    }

    companion object {
        private const val INTENT_BACKOFF_MILLIS = 100L
        private val TAG = MainActivity::class.simpleName
        const val ACTION_WAKE_WORD = "org.zxkill.nori.MainActivity.ACTION_WAKE_WORD"

        // Счётчики нужны для отслеживания жизненного цикла активити из других частей приложения.
        var isInForeground: Int = 0
            private set
        var isCreated: Int = 0
            private set

        // Проверяет, относится ли интент к ассистенту.
        private fun isAssistIntent(intent: Intent?): Boolean {
            return when (intent?.action) {
                ACTION_ASSIST, ACTION_VOICE_COMMAND -> true
                else -> false
            }
        }
    }
}

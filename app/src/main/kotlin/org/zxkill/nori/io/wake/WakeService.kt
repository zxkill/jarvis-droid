package org.zxkill.nori.io.wake

import android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
import android.Manifest.permission.RECORD_AUDIO
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import org.zxkill.nori.MainActivity
import org.zxkill.nori.MainActivity.Companion.ACTION_WAKE_WORD
import org.zxkill.nori.R
import org.zxkill.nori.di.SttInputDeviceWrapper
import org.zxkill.nori.eval.SkillEvaluator
import org.zxkill.nori.io.input.InputEvent
import org.zxkill.nori.io.input.SttState
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Service that continuously listens through the Vosk STT engine and waits for the
 * trigger word [TRIGGER_WORD]. When the word is detected, the rest of the utterance is
 * forwarded to [SkillEvaluator] as a command and the main activity is opened.
 */
@AndroidEntryPoint
class WakeService : Service() {

    private val listening = AtomicBoolean(false)
    // Флаг, показывающий, что ключевое слово уже было услышано и
    // следующая фраза должна трактоваться как команда.
    private var awaitingCommand = false

    @Inject
    lateinit var skillEvaluator: SkillEvaluator
    @Inject
    lateinit var sttInputDevice: SttInputDeviceWrapper

    private val handler = Handler(Looper.getMainLooper())
    private val releaseSttResourcesRunnable = Runnable {
        if (MainActivity.isCreated <= 0) {
            sttInputDevice.reinitializeToReleaseResources()
        }
    }

    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(this, NotificationManager::class.java)!!
        serviceStarted.set(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_WAKE_SERVICE) {
            listening.set(false)
            sttInputDevice.stopListening()
            return START_NOT_STICKY
        }

        if (listening.getAndSet(true)) {
            return START_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO) != PERMISSION_GRANTED) {
            stopWithMessage("Could not start WakeService: microphone permission not granted")
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(this, FOREGROUND_SERVICE_MICROPHONE) !=
            PERMISSION_GRANTED
        ) {
            stopWithMessage("Could not start WakeService: microphone foreground permission not granted")
            return START_NOT_STICKY
        }

        createForegroundNotification()
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        try {
            val willStart = sttInputDevice.tryLoad(::onInputEvent)
            if (!willStart) {
                listening.set(false)
                stopWithMessage("Could not start WakeService: STT device not ready")
            }
        } catch (e: Throwable) {
            listening.set(false)
            stopWithMessage("Could not start WakeService", e)
        }
    }

    private fun onInputEvent(event: InputEvent) {
        when (event) {
            is InputEvent.Partial -> {}
            is InputEvent.Error -> {
                Log.e(TAG, "Error during STT", event.throwable)
                // При ошибке сбрасываем состояние ожидания команды
                awaitingCommand = false
                restartListening(playSound = false)
            }
            InputEvent.None -> {
                // По таймауту или при отсутствии речи возвращаемся
                // к ожиданию ключевого слова
                awaitingCommand = false
                restartListening(playSound = false)
            }
            is InputEvent.Final -> {
                val triggered = handleFinalEvent(event)
                restartListening(playSound = triggered)
            }
        }
    }

    /**
     * Обработка окончательно распознанной фразы.
     * Возвращает `true`, если была выполнена команда.
     */
    private fun handleFinalEvent(event: InputEvent.Final): Boolean {
        val utterance = event.utterances.firstOrNull() ?: return false
        val text = utterance.first
        val confidence = utterance.second
        val lower = text.lowercase(Locale.getDefault())
        val triggerWord = TRIGGER_WORD.lowercase(Locale.getDefault())

        return if (awaitingCommand) {
            // Предыдущее распознавание было только ключевым словом,
            // значит текущий текст — это команда.
            awaitingCommand = false
            val command = text.trim()
            if (command.isNotEmpty()) {
                // Передаём команду обработчику без просьбы повторить
                skillEvaluator.processInputEvent(
                    InputEvent.Final(listOf(Pair(command, confidence))),
                    askToRepeat = false
                )
                openMainActivity()
                true
            } else {
                false
            }
        } else if (lower.startsWith(triggerWord)) {
            // В фразе присутствует ключевое слово.
            val command = text.substring(triggerWord.length).trim()
            if (command.isNotEmpty()) {
                // Ключевое слово и команда произнесены вместе.
                skillEvaluator.processInputEvent(
                    InputEvent.Final(listOf(Pair(command, confidence))),
                    askToRepeat = false
                )
                openMainActivity()
                true
            } else {
                // Было сказано только ключевое слово — ждём следующую фразу.
                awaitingCommand = true
                false
            }
        } else {
            false
        }
    }

    /**
     * Перезапускает прослушивание после обработки очередной фразы.
     *
     * Если устройство уже находится в состоянии прослушивания, мы не
     * останавливаем и не запускаем его заново, чтобы не пропустить
     * начало следующей команды.
     */
    private fun restartListening(playSound: Boolean = true) {
        if (listening.get()) {
            handler.removeCallbacks(releaseSttResourcesRunnable)
            handler.postDelayed(releaseSttResourcesRunnable, RELEASE_STT_RESOURCES_MILLIS)

            // Запускаем устройство заново только если оно не слушает
            val state = sttInputDevice.uiState.value
            if (state != SttState.Listening) {
                sttInputDevice.tryLoad(::onInputEvent, playSound)
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.setAction(ACTION_WAKE_WORD)
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || MainActivity.isInForeground > 0) {
            startActivity(intent)
        } else {
            val channel = NotificationChannel(
                TRIGGERED_NOTIFICATION_CHANNEL_ID,
                getString(R.string.wake_service_triggered_notification),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = getString(R.string.wake_service_triggered_notification_summary)
            notificationManager.createNotificationChannel(channel)

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(this, TRIGGERED_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hearing_white)
                .setContentTitle(getString(R.string.wake_service_triggered_notification))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        getString(R.string.wake_service_triggered_notification_summary)
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(pendingIntent, true)
                .build()

            notificationManager.cancel(TRIGGERED_NOTIFICATION_ID)
            notificationManager.notify(TRIGGERED_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        listening.set(false)
        sttInputDevice.reinitializeToReleaseResources()
        serviceStarted.set(false)
        super.onDestroy()
    }

    private fun stopWithMessage(message: String = "", throwable: Throwable? = null) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else if (message.isNotEmpty()) {
            Log.e(TAG, message)
        }
    }

    private fun createForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                getString(R.string.wake_service_label),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = getString(R.string.wake_service_foreground_notification_summary)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hearing_white)
            .setContentTitle(getString(R.string.nori_service_foreground_notification))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_stop_circle_white,
                    getString(R.string.stop),
                    PendingIntent.getService(
                        this,
                        0,
                        Intent(this, WakeService::class.java).apply { action = ACTION_STOP_WAKE_SERVICE },
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            )
            .build()

        try {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to start foreground service", e)
            stopSelf()
        }
    }

    companion object {
        @RequiresApi(Build.VERSION_CODES.R)
        fun createNotificationToStartLater(context: Context) {
            val notificationManager = getSystemService(context, NotificationManager::class.java)
                ?: return

            val channel = NotificationChannel(
                START_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.wake_service_start_notification),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            channel.description = context.getString(R.string.wake_service_start_notification_summary)
            notificationManager.createNotificationChannel(channel)

            val pendingIntent = PendingIntent.getForegroundService(
                context,
                0,
                Intent(context, WakeService::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, START_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hearing_white)
                .setContentTitle(context.getString(R.string.wake_service_start_notification))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        context.getString(R.string.wake_service_start_notification_summary)
                    )
                )
                .setOngoing(false)
                .setShowWhen(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(START_NOTIFICATION_ID, notification)
        }

        fun start(context: Context) {
            val permissions = mutableListOf(RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissions += FOREGROUND_SERVICE_MICROPHONE
            }
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(context, permission) != PERMISSION_GRANTED) {
                    Toast.makeText(
                        context,
                        R.string.grant_microphone_permission,
                        Toast.LENGTH_LONG,
                    ).show()
                    return
                }
            }

            val intent = Intent(context, WakeService::class.java)
            if (serviceStarted.get()) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, WakeService::class.java).apply { action = ACTION_STOP_WAKE_SERVICE }
                )
            } catch (_: IllegalStateException) {
                // Service not running
            }
        }

        fun isRunning(): Boolean = serviceStarted.get()

        fun cancelTriggeredNotification(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getSystemService(context, NotificationManager::class.java)
                    ?.cancel(TRIGGERED_NOTIFICATION_ID)
            }
        }

        private val serviceStarted = AtomicBoolean(false)

        private val TAG = WakeService::class.simpleName
        private const val FOREGROUND_NOTIFICATION_CHANNEL_ID =
            "org.zxkill.nori.io.wake.WakeService.FOREGROUND"
        private const val START_NOTIFICATION_CHANNEL_ID =
            "org.zxkill.nori.io.wake.WakeService.START"
        private const val TRIGGERED_NOTIFICATION_CHANNEL_ID =
            "org.zxkill.nori.io.wake.WakeService.TRIGGERED"
        private const val FOREGROUND_NOTIFICATION_ID = 19803672
        private const val START_NOTIFICATION_ID = 48019274
        private const val TRIGGERED_NOTIFICATION_ID = 601398647
        private const val ACTION_STOP_WAKE_SERVICE =
            "org.zxkill.nori.io.wake.WakeService.ACTION_STOP"
        private const val RELEASE_STT_RESOURCES_MILLIS = 1000L * 60 * 5 // 5 minutes
        // Ключевое слово, с которого должны начинаться все голосовые команды
        const val TRIGGER_WORD = "Норри"
    }
}


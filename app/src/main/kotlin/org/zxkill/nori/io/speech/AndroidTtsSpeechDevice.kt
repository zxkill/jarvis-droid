package org.zxkill.nori.io.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import org.nori.skill.context.SpeechOutputDevice
import org.zxkill.nori.R
import java.util.Locale

/**
 * Класс, реализующий устройство синтеза речи на базе стандартного Android TTS.
 * При инициализации создаётся экземпляр [TextToSpeech], который озвучивает
 * переданный текст и уведомляет о завершении произнесения фразы.
 */
class AndroidTtsSpeechDevice(private var context: Context, locale: Locale) : SpeechOutputDevice {
    /** Экземпляр движка синтеза речи. */
    private var textToSpeech: TextToSpeech? = null
    /** Флаг успешной инициализации TTS. */
    private var initializedCorrectly = false
    /** Очередь действий, которые нужно выполнить после окончания речи. */
    private val runnablesWhenFinished: MutableList<Runnable> = ArrayList()
    /** Последний использованный идентификатор озвучиваемой фразы. */
    private var lastUtteranceId = 0

    init {
        // Создаём и настраиваем движок TTS.
        textToSpeech = TextToSpeech(context) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.run {
                    val errorCode = setLanguage(locale)
                    if (errorCode >= 0) { // ошибки обозначаются отрицательными значениями
                        initializedCorrectly = true
                        setOnUtteranceProgressListener(object :
                            UtteranceProgressListener() {
                            override fun onStart(utteranceId: String) {}
                            override fun onDone(utteranceId: String) {
                                if ("nori_$lastUtteranceId" == utteranceId) {
                                    // Выполняем действия, запланированные после окончания речи
                                    for (runnable in runnablesWhenFinished) {
                                        runnable.run()
                                    }
                                    runnablesWhenFinished.clear()
                                }
                            }

                            @Deprecated("")
                            override fun onError(utteranceId: String) {
                            }
                        })
                    } else {
                        Log.e(TAG, "Unsupported language: $errorCode")
                        handleInitializationError(R.string.android_tts_unsupported_language)
                    }
                }
            } else {
                Log.e(TAG, "TTS error: $status")
                handleInitializationError(R.string.android_tts_error)
            }
        }
    }

    /**
     * Произнести указанную строку. Если TTS не инициализирован, просто выводим
     * текст на экран в виде Toast-сообщения.
     */
    override fun speak(speechOutput: String) {
        if (initializedCorrectly) {
            lastUtteranceId += 1
            textToSpeech?.speak(
                speechOutput, TextToSpeech.QUEUE_ADD, null,
                "nori_$lastUtteranceId",
            )
        } else {
            Toast.makeText(context, speechOutput, Toast.LENGTH_LONG).show()
        }
    }

    /** Остановить текущее произнесение. */
    override fun stopSpeaking() {
        textToSpeech?.stop()
    }

    /** Проверка, произносится ли сейчас речь. */
    override val isSpeaking: Boolean
        get() = textToSpeech?.isSpeaking == true

    /**
     * Запустить [runnable], когда произнесение завершится. Если речи нет,
     * выполняем действие сразу.
     */
    override fun runWhenFinishedSpeaking(runnable: Runnable) {
        if (isSpeaking) {
            runnablesWhenFinished.add(runnable)
        } else {
            runnable.run()
        }
    }

    /** Освободить ресурсы TTS. */
    override fun cleanup() {
        textToSpeech?.apply {
            shutdown()
            textToSpeech = null
        }
    }

    /**
     * Обработчик ошибок инициализации. Показывает сообщение пользователю и
     * освобождает ресурсы.
     */
    private fun handleInitializationError(@StringRes errorString: Int) {
        Toast.makeText(context, errorString, Toast.LENGTH_SHORT).show()
        cleanup()
    }

    companion object {
        /** Тэг для логов. */
        val TAG: String = AndroidTtsSpeechDevice::class.simpleName!!
    }
}

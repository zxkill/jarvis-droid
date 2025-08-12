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

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.zxkill.nori.io.input.InputEvent
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService


/**
 * Реализация [RecognitionListener], получающая события от Vosk [SpeechService]
 * и передающая их наружу через [eventListener].
 * Также взаимодействует с [VoskInputDevice], чтобы переключать состояния прослушивания.
 */
internal class VoskListener(
    private val voskInputDevice: VoskInputDevice,
    private val eventListener: (InputEvent) -> Unit,
    private val speechService: SpeechService,
) : RecognitionListener {

    /**
     * Вызывается при получении промежуточного результата распознавания
     */
    override fun onPartialResult(s: String) {
        Log.d(TAG, "onPartialResult called with s = $s")

        // Извлекаем частичный результат из JSON.
        // В отличие от onResult здесь приходит только одна, наиболее вероятная, гипотеза.
        var partialInput: String? = null
        try {
            partialInput = JSONObject(s).getString("partial")
        } catch (e: JSONException) {
            Log.e(TAG, "Can't obtain partial result from $s", e)
        }

        // Отправляем событие с промежуточным текстом
        partialInput?.also {
            if (it.isNotBlank()) {
                eventListener(InputEvent.Partial(it))
            }
        }
    }

    /**
     * Срабатывает после паузы в речи.
     *
     * Ранее здесь останавливалось прослушивание, что приводило к
     * кратковременным «провалам» и потере начала следующей команды.
     * Теперь мы лишь передаём распознанный результат наружу,
     * оставляя [SpeechService] активным.
     */
    @Suppress("ktlint:Style:NestedBlockDepth")
    override fun onResult(s: String) {
        Log.d(TAG, "onResult called with s = $s")

        // Собираем все возможные варианты, которые предложил движок STT
        val inputs = try {
            val jsonResult = JSONObject(s)
            utterancesFromJson(jsonResult)
        } catch (e: JSONException) {
            Log.e(TAG, "Can't obtain result from $s", e)
            eventListener(InputEvent.Error(e))
            return
        }

        // Отправляем итоговое событие
        if (inputs.isEmpty()) {
            // Пустой результат означает тишину или шум — просто уведомим слушателя,
            // но не будем перезапускать прослушивание.
            eventListener(InputEvent.None)
        } else {
            eventListener(InputEvent.Final(inputs))
        }
    }

    private fun utterancesFromJson(jsonResult: JSONObject): List<Pair<String, Float>> {
        return jsonResult.optJSONArray("alternatives")
            ?.takeIf { array -> array.length() > 0 }
            ?.let { array ->
                val maxConfidence = array.optJSONObject(0)?.optDouble("confidence")?.toFloat()
                    ?: return@let null

                return (0 until array.length())
                    .mapNotNull { i -> array.optJSONObject(i) }
                    .map {
                        Pair(
                            // Vosk возвращает строки с лидирующим пробелом — удаляем его
                            it.getString("text").removePrefix(" "),
                            // Коэффициенты уверенности Vosk не нормированы в диапазон 0..1
                            it.getDouble("confidence").toFloat() / maxConfidence
                        )
                    }
                    // Пустые строки — не результат; фильтруем, чтобы вместо пустого
                    // списка отправить событие InputEvent.None
                    .filter { it.first.isNotBlank() }
            }
            ?: (jsonResult.optString("text")
                .takeIf { it.isNotBlank() }
                ?.let { listOf(Pair(it.removePrefix(" "), 1.0f)) })
            ?: listOf()
    }

    /**
     * Вызывается после завершения потока. У нас это всегда происходит
     * после тишины, потому что stopListening вызывается в onResult
     */
    override fun onFinalResult(s: String) {
        Log.d(TAG, "onFinalResult called with s = $s")
    }
    
    /**
     * Срабатывает при возникновении ошибки
     */
    override fun onError(e: Exception) {
        // TODO установить состояние ошибки в VoskInputDevice
        Log.e(TAG, "Произошла ошибка распознавания", e)
        voskInputDevice.stopListening(speechService, eventListener, false)
        eventListener(InputEvent.Error(e))
    }

    /**
     * Вызывается при превышении времени ожидания
     */
    override fun onTimeout() {
        Log.d(TAG, "Превышено время ожидания")
        voskInputDevice.stopListening(speechService, eventListener, true)
    }

    companion object {
        private val TAG = VoskListener::class.simpleName
    }
}

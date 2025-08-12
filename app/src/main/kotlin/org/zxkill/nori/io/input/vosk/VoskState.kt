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

import org.zxkill.nori.io.input.InputEvent
import org.zxkill.nori.io.input.SttState
import org.zxkill.nori.ui.util.Progress
import org.vosk.android.SpeechService

/**
 * Описание внутренних состояний [VoskInputDevice].
 * Для каждого шага работы используется отдельный вариант, что позволяет
 * избегать множества `null`‑полей и чётко отслеживать прогресс.
 * Класс [SttState] зеркалирует эти состояния, но не содержит
 * специфичных для реализации объектов вроде [SpeechService].
 */
sealed interface VoskState {

    /**
     * Устройство ещё не инициализировано или только что деинициализировалось
     */
    data object NotInitialized : VoskState

    /**
     * Для текущей локали нет подходящей модели Vosk
     */
    data object NotAvailable : VoskState

    /**
     * Модель отсутствует на устройстве — её ещё не скачивали
     */
    data class NotDownloaded(
        val modelUrl: String
    ) : VoskState

    data class Downloading(
        val progress: Progress,
    ) : VoskState

    data class ErrorDownloading(
        val modelUrl: String,
        val throwable: Throwable
    ) : VoskState

    data object Downloaded : VoskState

    /**
     * Модель скачана, но находится в zip‑архиве и требует распаковки
     */
    data class Unzipping(
        val progress: Progress
    ) : VoskState

    data class ErrorUnzipping(
        val throwable: Throwable
    ) : VoskState

    /**
     * Модель распакована, но ещё не загружена в память
     */
    data object NotLoaded : VoskState

    /**
     * Модель загружается в память.
     * Если [thenStartListening] не `null`, после загрузки сразу начнётся прослушивание.
     * Поле [shouldEqualAnyLoading] помогает атомарно сравнивать объекты состояния.
     */
    data class Loading(
        val thenStartListening: ((InputEvent) -> Unit)?,
        val shouldEqualAnyLoading: Boolean = false,
    ) : VoskState {
        override fun equals(other: Any?): Boolean {
            if (other !is Loading)
                return false
            if (shouldEqualAnyLoading || other.shouldEqualAnyLoading)
                return true
            return (this.thenStartListening == null) == (other.thenStartListening == null)
        }

        override fun hashCode(): Int {
            return if (thenStartListening == null) 0 else 1;
        }
    }

    data class ErrorLoading(
        val throwable: Throwable
    ) : VoskState

    /**
     * Модель загружена в память ([SpeechService]) и готова к использованию
     */
    data class Loaded(
        internal val speechService: SpeechService
    ) : VoskState

    /**
     * Модель активна и слушает входящий звук
     */
    data class Listening(
        internal val speechService: SpeechService,
        internal val eventListener: (InputEvent) -> Unit,
    ) : VoskState

    /**
     * Преобразует [VoskState] в [SttState], удаляя поля, зависящие от реализации
     */
    fun toUiState(): SttState {
        return when (this) {
            NotInitialized -> SttState.NotInitialized
            NotAvailable -> SttState.NotAvailable
            is NotDownloaded -> SttState.NotDownloaded
            is Downloading -> SttState.Downloading(progress)
            is ErrorDownloading -> SttState.ErrorDownloading(throwable)
            Downloaded -> SttState.Downloaded
            is Unzipping -> SttState.Unzipping(progress)
            is ErrorUnzipping -> SttState.ErrorUnzipping(throwable)
            NotLoaded -> SttState.NotLoaded
            is Loading -> SttState.Loading(thenStartListening != null)
            is ErrorLoading -> SttState.ErrorLoading(throwable)
            is Loaded -> SttState.Loaded
            is Listening -> SttState.Listening
        }
    }
}

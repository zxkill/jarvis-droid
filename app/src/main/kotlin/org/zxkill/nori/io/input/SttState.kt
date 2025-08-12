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

package org.zxkill.nori.io.input

import org.zxkill.nori.ui.util.Progress

/**
 * Почти зеркальное отображение [org.zxkill.nori.io.input.vosk.VoskState],
 * но без полей, зависящих от конкретной реализации движка распознавания.
 * Благодаря этому UI-слой можно оставить без изменений при замене движка.
 */
sealed interface SttState {
    /**
     * У [org.zxkill.nori.io.input.vosk.VoskState] нет аналога.
     * Это состояние используется только на уровне UI,
     * когда у приложения нет разрешения на использование микрофона.
     */
    data object NoMicrophonePermission : SttState

    /**
     * Движок распознавания ещё не инициализирован (ожидается локаль)
     */
    data object NotInitialized : SttState

    /**
     * Движок не может быть использован, например, из-за отсутствия модели для текущего языка
     */
    data object NotAvailable : SttState

    /**
     * Модель отсутствует на устройстве
     */
    data object NotDownloaded : SttState

    data class Downloading(
        val progress: Progress,
    ) : SttState

    data class ErrorDownloading(
        val throwable: Throwable
    ) : SttState

    data object Downloaded : SttState

    /**
     * Модель скачана и распаковывается
     */
    data class Unzipping(
        val progress: Progress,
    ) : SttState

    data class ErrorUnzipping(
        val throwable: Throwable
    ) : SttState

    /**
     * Модель есть на диске, но ещё не загружена в память
     */
    data object NotLoaded : SttState

    /**
     * Модель загружается в память.
     * Флаг [thenStartListening] показывает, нужно ли сразу начать прослушивание после загрузки.
     */
    data class Loading(
        val thenStartListening: Boolean
    ) : SttState

    data class ErrorLoading(
        val throwable: Throwable
    ) : SttState

    /**
     * Модель загружена и готова начать прослушивание
     */
    data object Loaded : SttState

    /**
     * Активное прослушивание
     */
    data object Listening : SttState

    /**
     * Прослушивание выполняет внешнее приложение (например, через
     * `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`). Мы не знаем, слушает оно или ещё загружается,
     * поэтому в интерфейсе стоит показывать сообщение "Ожидание...".
     */
    data object WaitingForResult : SttState
}

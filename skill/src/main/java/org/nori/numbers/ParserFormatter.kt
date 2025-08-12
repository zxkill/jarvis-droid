package org.nori.numbers

import org.nori.numbers.unit.Duration
import org.nori.numbers.unit.Number
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.time.Duration as JavaDuration

/**
 * Упрощённая заглушка исходного класса ParserFormatter из библиотеки nori-numbers.
 * Класс оставляет только необходимый для проекта интерфейс и не выполняет
 * настоящего разбора текста или форматирования чисел.
 *
 * @param locale локаль, для которой предполагается выполнять операции.
 */
class ParserFormatter(private val locale: Locale) {
    /**
     * Пытается извлечь из строки длительность.
     * В заглушке всегда возвращает пару из `null` и `null`, тем самым
     * обозначая, что распознать длительность не удалось.
     */
    fun extractDuration(text: String): Pair<Duration?, String?> = Pair(null, null)

    /**
     * Форматирует переданную длительность в строку вида `HH:MM:SS`.
     * Вся логика делегирована функции [javaDurationToString].
     */
    fun niceDuration(duration: Duration): NiceString =
        NiceString(javaDurationToString(duration.toJavaDuration()))

    /**
     * Пытается распознать число в строке.
     * Возвращает пустой результат, поскольку разбор не поддерживается.
     */
    fun extractNumber(text: String): Extraction = Extraction(emptyList())

    /**
     * Проговаривает число, возвращая его строковое представление без
     * какого‑либо специального форматирования.
     */
    fun pronounceNumber(number: Double): NiceString = NiceString(number.toString())

    /**
     * Преобразует число в строку без дополнительного форматирования.
     */
    fun niceNumber(number: Double): NiceString = NiceString(number.toString())

    /**
     * Возвращает строковое представление даты в ISO‑формате.
     */
    fun niceDate(date: LocalDate): NiceString = NiceString(date.toString())

    /**
     * Возвращает строку, содержащую только год из переданной даты.
     */
    fun niceYear(date: LocalDate): NiceString = NiceString(date.year.toString())

    /**
     * Возвращает строковое представление времени.
     */
    fun niceTime(time: LocalTime): NiceString = NiceString(time.toString())

    /**
     * Небольшой вспомогательный класс для имитации объекта, который может
     * использоваться как обычная строка или как строка для озвучивания.
     */
    class NiceString(private val value: String) {

        /**
         * В заглушке флаг `speech` никак не используется, поэтому метод
         * просто возвращает текущий экземпляр.
         */
        fun speech(@Suppress("UNUSED_PARAMETER") speech: Boolean): NiceString = this

        /**
         * Аналогично [speech], параметр `use24` игнорируется.
         */
        fun use24Hour(@Suppress("UNUSED_PARAMETER") use24: Boolean): NiceString = this

        /** Возвращает сохранённую строку. */
        fun get(): String = value
    }

    /**
     * Результат попытки распознать число или длительность в тексте.
     *
     * @param mixedWithText список элементов, вперемешку с исходным текстом.
     */
    class Extraction(val mixedWithText: List<Any>) {

        /**
         * В заглушке метод ничего не делает и лишь возвращает текущий экземпляр.
         */
        fun preferOrdinal(@Suppress("UNUSED_PARAMETER") prefer: Boolean): Extraction = this
    }

    companion object {
        /**
         * Переводит [JavaDuration] в строку формата `HH:MM:SS`.
         */
        private fun javaDurationToString(d: JavaDuration): String {
            val seconds = d.seconds
            // Берём абсолютное значение для расчёта положительного формата
            val absSeconds = kotlin.math.abs(seconds)
            val positive = String.format(
                "%d:%02d:%02d",
                absSeconds / 3600,
                (absSeconds % 3600) / 60,
                absSeconds % 60
            )
            // Если длительность была отрицательной, добавляем знак минус
            return if (seconds < 0) "-$positive" else positive
        }
    }
}

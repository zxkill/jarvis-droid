package org.nori.numbers.unit

import java.time.Duration as JavaDuration

/**
 * Простейшая обёртка над [java.time.Duration], используемая заглушкой
 * [org.nori.numbers.ParserFormatter]. Никаких дополнительных вычислений не выполняется.
 *
 * @param javaDuration внутренняя длительность Java, которую мы храним.
 */
class Duration(private val javaDuration: JavaDuration) {

    /**
     * Возвращает сохранённый объект [JavaDuration] без изменений.
     */
    fun toJavaDuration(): JavaDuration = javaDuration
}

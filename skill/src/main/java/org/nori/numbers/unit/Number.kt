package org.nori.numbers.unit

/**
 * Минимальный числовой тип, обеспечивающий API, которого ждёт остальной код.
 * Фактически это простая обёртка над `Double`.
 *
 * @param value числовое значение, которое хранится внутри.
 */
class Number(private val value: Double) {

    /** Создаёт объект из целочисленного значения. */
    constructor(value: Long) : this(value.toDouble())

    /** Возвращает `true`, если число содержит дробную часть. */
    val isDecimal: Boolean get() = value % 1.0 != 0.0
    /** Возвращает `true`, если число целое. */
    val isInteger: Boolean get() = !isDecimal

    /** Возвращает значение в виде [Double]. */
    fun decimalValue(): Double = value
    /** Возвращает значение в виде [Long], отбрасывая дробную часть. */
    fun integerValue(): Long = value.toLong()
    /** Создаёт новое число, умноженное на [factor]. */
    fun multiply(factor: Int): Number = Number(value * factor)
    /** Проверяет, меньше ли число заданного [other]. */
    fun lessThan(other: Int): Boolean = value < other
}

package org.nori.skill.util

import java.text.Normalizer

/**
 * Приводит слово к форме NFKD и удаляет диакритические знаки,
 * чтобы сравнение строк выполнялось по базовым символам.
 */
fun nfkdNormalizeWord(word: String): String {
    // Нормализуем строку: буквы вроде "é" раскладываются на "e" + диакритику
    val normalized = Normalizer.normalize(word, Normalizer.Form.NFKD)
    // Удаляем все комбинируемые символы (диакритики) из полученной последовательности
    return normalized.replace("\\p{Mn}+".toRegex(), "")
}

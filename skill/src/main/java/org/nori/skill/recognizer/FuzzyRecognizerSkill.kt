package org.nori.skill.recognizer

import org.nori.skill.context.SkillContext
import org.nori.skill.skill.AlwaysWorstScore
import org.nori.skill.skill.FloatScore
import org.nori.skill.skill.Score
import org.nori.skill.skill.Skill
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.Specificity
import org.nori.skill.util.nfkdNormalizeWord
import kotlin.math.max

/**
 * Базовый класс для скиллов, использующих нечёткое совпадение
 * пользовательского ввода с набором примеров фраз.
 * Для каждого примера можно дополнительно указать регулярное
 * выражение, чтобы извлечь параметры из введённого текста.
 */
abstract class FuzzyRecognizerSkill<InputData>(
    correspondingSkillInfo: SkillInfo,
    specificity: Specificity,
) : Skill<InputData?>(correspondingSkillInfo, specificity) {

    /** Регулярные выражения для удаления пунктуации и уплотнения пробелов. */
    private val PUNCT_REGEX = "\\p{Punct}+".toRegex()
    private val WHITESPACE_REGEX = "\\s+".toRegex()

    /**
     * Описание одной возможной команды.
     *
     * - [examples] — набор примеров, с которыми сравнивается ввод пользователя.
     *   Можно указать несколько синонимов одной и той же команды.
     * - [regex] — необязательное регулярное выражение для извлечения данных
     *   (например, названия приложения или города). Если оно `null`, значит,
     *   что достаточно лишь приблизительного совпадения с одним из примеров.
     * - [builder] — функция, преобразующая результат совпадения (или `null`, если
     *   [regex] отсутствует) в объект данных, передаваемый скиллу.
     */
    data class Pattern<T>(
        val examples: List<String>,
        val regex: Regex? = null,
        val builder: (MatchResult?) -> T,
    )

    /** Список поддерживаемых шаблонов команд. */
    protected abstract val patterns: List<Pattern<InputData>>

    override fun score(ctx: SkillContext, input: String): Pair<Score, InputData?> {
        // Предварительно нормализуем ввод: приводим к нижнему регистру,
        // удаляем знаки пунктуации и лишние пробелы, а также вырезаем диакритику.
        val normalized = preprocess(input)
        var bestPattern: Pattern<InputData>? = null
        var bestScore = -1f
        var bestMatch: MatchResult? = null

        for (pattern in patterns) {
            val match = pattern.regex?.find(normalized)
            if (pattern.regex != null && match == null) {
                // Если регулярное выражение задано, но не совпало,
                // данный шаблон нам не подходит.
                continue
            }

            var score = if (match != null) 1f else -1f

            for (example in pattern.examples) {
                val normExample = preprocess(example)
                val distance = levenshteinDistance(normalized, normExample)
                val exampleScore = 1f - distance / max(normExample.length, normalized.length).toFloat()
                if (exampleScore > score) {
                    score = exampleScore
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestPattern = pattern
                bestMatch = match
            }
        }

        bestPattern?.let { pattern ->
            val data = pattern.builder(bestMatch)
            return Pair(FloatScore(bestScore), data)
        }

        return Pair(AlwaysWorstScore, null)
    }

    /**
     * Приводит строку к виду, удобному для сравнения: нижний регистр,
     * отсутствие пунктуации и диакритики, одиночные пробелы.
     */
    private fun preprocess(s: String): String {
        return nfkdNormalizeWord(s.lowercase())
            .replace(PUNCT_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    /**
     * Простая реализация расстояния Левенштейна для измерения схожести строк.
     * Реализована здесь, чтобы не добавлять внешние зависимости.
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[a.length][b.length]
    }
}

package org.dicio.skill.recognizer

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.AlwaysWorstScore
import org.dicio.skill.skill.FloatScore
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.Specificity
import kotlin.math.max

/**
 * Базовый класс для скиллов, использующих нечёткое совпадение
 * пользовательского ввода с набором регулярных выражений.
 * Каждое выражение задаёт пример команды и функцию, извлекающую
 * данные из совпавших групп.
 */
abstract class FuzzyRecognizerSkill<InputData>(
    correspondingSkillInfo: SkillInfo,
    specificity: Specificity,
) : Skill<InputData?>(correspondingSkillInfo, specificity) {

    /**
     * Описание одной возможной команды.
     * @param example строка-пример, с которой будет сравниваться ввод
     * @param regex регулярное выражение, проверяющее совпадение
     * @param builder функция, формирующая выходные данные из результата совпадения
     */
    data class Pattern<T>(
        val example: String,
        val regex: Regex,
        val builder: (MatchResult) -> T,
    )

    /** Список поддерживаемых шаблонов команд. */
    protected abstract val patterns: List<Pattern<InputData>>

    override fun score(ctx: SkillContext, input: String): Pair<Score, InputData?> {
        val normalized = input.lowercase()
        var bestData: InputData? = null
        var bestScore = -1f
        for (pattern in patterns) {
            val match = pattern.regex.find(normalized) ?: continue
            val distance = levenshteinDistance(normalized, pattern.example.lowercase())
            val score = 1f - distance / max(pattern.example.length, normalized.length).toFloat()
            if (score > bestScore) {
                bestScore = score
                bestData = pattern.builder(match)
            }
        }
        return if (bestData != null) {
            Pair(FloatScore(bestScore), bestData)
        } else {
            Pair(AlwaysWorstScore, null)
        }
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

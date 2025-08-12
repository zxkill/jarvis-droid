package org.zxkill.nori.skills.current_time

import org.nori.skill.context.SkillContext
import org.nori.skill.recognizer.FuzzyRecognizerSkill
import org.nori.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.nori.skill.skill.AutoRunnable
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Скилл озвучивает текущее время.
 * Распознаёт простые запросы вроде "который час" или "сколько времени".
 */
class CurrentTimeSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<Unit>(correspondingSkillInfo, Specificity.LOW), AutoRunnable {

    override val patterns = listOf(
        Pattern(
            examples = listOf(
                "который час",
                "сколько времени",
                "который сейчас час",
                "сколько времени сейчас"
            ),
            builder = { _ -> }
        )
    )

    // Автоматическое обновление раз в минуту
    override val autoUpdateIntervalMillis: Long = 60_000L

    override suspend fun generateOutput(ctx: SkillContext, inputData: Unit?): SkillOutput {
        return computeOutput(ctx)
    }

    override suspend fun autoOutput(ctx: SkillContext): SkillOutput {
        return computeOutput(ctx)
    }

    private fun computeOutput(ctx: SkillContext): SkillOutput {
        val now = LocalTime.now()
        val formatted = when {
            ctx.locale.language == "ru" -> formatRussianTime(now)
            ctx.parserFormatter != null -> ctx.parserFormatter!!
                .niceTime(now)
                .use24Hour(true)
                .get()
            else -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(ctx.locale)
                .format(now)
        }
        return CurrentTimeOutput(formatted)
    }

    /** Форматирование времени на русском языке без зависимости от внешних библиотек. */
    private fun formatRussianTime(time: LocalTime): String {
        val hours = time.hour
        val minutes = time.minute
        val hourText = numberToWords(hours, false)
        val minuteText = numberToWords(minutes, true)
        val hourWord = hourWord(hours)
        val minuteWord = minuteWord(minutes)
        return "$hourText $hourWord $minuteText $minuteWord"
    }

    /** Преобразует число в слова, учитывая род существительного. */
    private fun numberToWords(number: Int, feminine: Boolean): String {
        val unitsMasculine = arrayOf("ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
        val unitsFeminine = arrayOf("ноль", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
        val units = if (feminine) unitsFeminine else unitsMasculine
        val teens = arrayOf("десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать")
        val tens = arrayOf("", "десять", "двадцать", "тридцать", "сорок", "пятьдесят")

        return when {
            number < 10 -> units[number]
            number in 10..19 -> teens[number - 10]
            else -> {
                val tenPart = tens[number / 10]
                val unit = number % 10
                if (unit == 0) tenPart else "$tenPart ${units[unit]}"
            }
        }
    }

    /** Возвращает правильную форму слова "час". */
    private fun hourWord(hours: Int): String {
        val mod10 = hours % 10
        val mod100 = hours % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "час"
            mod10 in 2..4 && mod100 !in 12..14 -> "часа"
            else -> "часов"
        }
    }

    /** Возвращает правильную форму слова "минута". */
    private fun minuteWord(minutes: Int): String {
        val mod10 = minutes % 10
        val mod100 = minutes % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "минута"
            mod10 in 2..4 && mod100 !in 12..14 -> "минуты"
            else -> "минут"
        }
    }
}

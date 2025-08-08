package org.stypox.dicio.skills.current_time

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.CurrentTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CurrentTimeSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<CurrentTime>)
    : StandardRecognizerSkill<CurrentTime>(correspondingSkillInfo, data) {
    override suspend fun generateOutput(ctx: SkillContext, inputData: CurrentTime): SkillOutput {
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

    private fun formatRussianTime(time: LocalTime): String {
        val hours = time.hour
        val minutes = time.minute
        val hourText = numberToWords(hours, false)
        val minuteText = numberToWords(minutes, true)
        val hourWord = hourWord(hours)
        val minuteWord = minuteWord(minutes)
        return "$hourText $hourWord $minuteText $minuteWord"
    }

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

    private fun hourWord(hours: Int): String {
        val mod10 = hours % 10
        val mod100 = hours % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "час"
            mod10 in 2..4 && mod100 !in 12..14 -> "часа"
            else -> "часов"
        }
    }

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

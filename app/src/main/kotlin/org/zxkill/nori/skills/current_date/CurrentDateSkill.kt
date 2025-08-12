package org.zxkill.nori.skills.current_date

import org.nori.skill.context.SkillContext
import org.nori.skill.recognizer.FuzzyRecognizerSkill
import org.nori.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.nori.skill.skill.AutoRunnable
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/**
 * Скилл сообщает текущую дату или её части.
 * Вместо заранее скомпилированных предложений используется набор простых шаблонов.
 */
class CurrentDateSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<CurrentDateSkill.Command>(correspondingSkillInfo, Specificity.LOW),
    AutoRunnable {

    /** Возможные команды пользователя. */
    sealed class Command {
        object Day : Command()
        object Year : Command()
        object Month : Command()
    }

    // Список шаблонов распознавания. Явно указываем тип, чтобы все варианты
    // команды (день, месяц, год) интерпретировались как единый суперкласс
    // [Command]; иначе Kotlin выводит тип Pattern<*> и переопределение не
    // совпадает с ожиданиями базового класса.
    override val patterns: List<Pattern<Command>> = listOf(
        Pattern(
            examples = listOf("какое сегодня число", "какая сегодня дата"),
            builder = { _ -> Command.Day }
        ),
        Pattern(
            examples = listOf("какой сейчас год", "какой год сейчас"),
            builder = { _ -> Command.Year }
        ),
        Pattern(
            examples = listOf("какой месяц", "какой сейчас месяц"),
            builder = { _ -> Command.Month }
        ),
    )

    // Обновляем дату каждую минуту, чтобы смена суток отражалась на экране
    override val autoUpdateIntervalMillis: Long = 60_000L

    override suspend fun generateOutput(ctx: SkillContext, inputData: Command?): SkillOutput {
        val today = LocalDate.now()
        return when (inputData) {
            Command.Day -> {
                val formatted = formatDay(ctx, today)
                CurrentDateOutput(CurrentDateOutput.Type.DAY, formatted)
            }
            Command.Year -> {
                val formatted = formatYear(ctx, today)
                CurrentDateOutput(CurrentDateOutput.Type.YEAR, formatted)
            }
            Command.Month -> {
                val formatted = formatMonth(ctx, today)
                CurrentDateOutput(CurrentDateOutput.Type.MONTH, formatted)
            }
            null -> CurrentDateOutput(CurrentDateOutput.Type.DAY, formatDay(ctx, today))
        }
    }

    override suspend fun autoOutput(ctx: SkillContext): SkillOutput {
        val today = LocalDate.now()
        // Для компактного виджета используем числовой формат "09.08.2025"
        val formatted = today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        return CurrentDateOutput(CurrentDateOutput.Type.DAY, formatted)
    }

    /** Форматирование полной даты. */
    private fun formatDay(ctx: SkillContext, date: LocalDate): String {
        return when (ctx.locale.language) {
            // Для русского языка используем собственную реализацию
            "ru" -> formatRussianFullDate(date)
            // В остальных случаях используем парсер или стандартный формат
            else -> ctx.parserFormatter?.niceDate(date)?.get()
                ?: DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(ctx.locale)
                    .format(date)
        }
    }

    /** Форматирование года. */
    private fun formatYear(ctx: SkillContext, date: LocalDate): String {
        return when (ctx.locale.language) {
            "ru" -> russianYear(date.year, nominative = true)
            else -> ctx.parserFormatter?.niceYear(date)?.get() ?: date.year.toString()
        }
    }

    /** Форматирование месяца. */
    private fun formatMonth(ctx: SkillContext, date: LocalDate): String {
        return when (ctx.locale.language) {
            "ru" -> MONTHS_NOMINATIVE[date.monthValue]
            else -> date.month.getDisplayName(TextStyle.FULL, ctx.locale)
        }
    }

    /** Полный формат даты на русском языке. */
    private fun formatRussianFullDate(date: LocalDate): String {
        val weekday = WEEKDAYS[date.dayOfWeek.value]
        val day = ORDINALS_NEUTER[date.dayOfMonth]
        val month = MONTHS_GENITIVE[date.monthValue]
        val year = russianYear(date.year, nominative = false)
        return "$weekday $day $month $year года"
    }

    /**
     * Преобразует год в слова для русского языка.
     * Поддерживает особый случай для диапазона 2000-2099.
     */
    private fun russianYear(year: Int, nominative: Boolean): String {
        if (year in 2000..2099) {
            val remainder = year % 100
            val thousandPart = if (remainder == 0) {
                if (nominative) "двухтысячный" else "двухтысячного"
            } else {
                val ord = ordinalMasculine(remainder, nominative)
                "две тысячи $ord"
            }
            return thousandPart
        }
        return year.toString()
    }

    /** Возвращает порядковое числительное мужского рода. */
    private fun ordinalMasculine(number: Int, nominative: Boolean): String {
        val unitsNom = arrayOf("", "первый", "второй", "третий", "четвёртый", "пятый", "шестой", "седьмой", "восьмой", "девятый")
        val unitsGen = arrayOf("", "первого", "второго", "третьего", "четвёртого", "пятого", "шестого", "седьмого", "восьмого", "девятого")
        val teensNom = arrayOf("десятый", "одиннадцатый", "двенадцатый", "тринадцатый", "четырнадцатый", "пятнадцатый", "шестнадцатый", "семнадцатый", "восемнадцатый", "девятнадцатый")
        val teensGen = arrayOf("десятого", "одиннадцатого", "двенадцатого", "тринадцатого", "четырнадцатого", "пятнадцатого", "шестнадцатого", "семнадцатого", "восемнадцатого", "девятнадцатого")
        val tensCardinal = arrayOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")
        val tensOrdNom = arrayOf("", "", "двадцатый", "тридцатый", "сороковой", "пятидесятый", "шестидесятый", "семидесятый", "восьмидесятый", "девяностый")
        val tensOrdGen = arrayOf("", "", "двадцатого", "тридцатого", "сорокового", "пятидесятого", "шестидесятого", "семидесятого", "восьмидесятого", "девяностого")

        val units = if (nominative) unitsNom else unitsGen
        val teens = if (nominative) teensNom else teensGen
        val tensOrd = if (nominative) tensOrdNom else tensOrdGen

        return when {
            number < 10 -> units[number]
            number in 10..19 -> teens[number - 10]
            number % 10 == 0 -> tensOrd[number / 10]
            else -> tensCardinal[number / 10] + " " + units[number % 10]
        }
    }

    companion object {
        private val WEEKDAYS = arrayOf(
            "", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"
        )
        private val ORDINALS_NEUTER = arrayOf(
            "", "первое", "второе", "третье", "четвёртое", "пятое", "шестое", "седьмое", "восьмое", "девятое",
            "десятое", "одиннадцатое", "двенадцатое", "тринадцатое", "четырнадцатое", "пятнадцатое", "шестнадцатое", "семнадцатое", "восемнадцатое", "девятнадцатое",
            "двадцатое", "двадцать первое", "двадцать второе", "двадцать третье", "двадцать четвёртое", "двадцать пятое", "двадцать шестое", "двадцать седьмое", "двадцать восьмое", "двадцать девятое",
            "тридцатое", "тридцать первое"
        )
        private val MONTHS_NOMINATIVE = arrayOf(
            "", "январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
        )
        private val MONTHS_GENITIVE = arrayOf(
            "", "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"
        )
    }
}

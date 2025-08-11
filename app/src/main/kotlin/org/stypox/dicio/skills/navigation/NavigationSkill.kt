package org.stypox.dicio.skills.navigation

import android.content.Intent
import android.net.Uri
import org.dicio.numbers.unit.Number
import org.dicio.skill.context.SkillContext
import org.dicio.skill.recognizer.FuzzyRecognizerSkill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import java.util.Locale

/**
 * Скилл навигации: получает адрес от пользователя и открывает карту с построением маршрута.
 */
class NavigationSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<String>(correspondingSkillInfo, Specificity.LOW) {

    override val patterns = listOf(
        Pattern(
            example = "проложи маршрут до москвы",
            regex = Regex("(?:проложи|построй|покажи)\\s+(?:маршрут\\s+)?(?:до|к)\\s+(?<where>.+)"),
            builder = { it.groups["where"]!!.value }
        )
    )

    override suspend fun generateOutput(ctx: SkillContext, inputData: String?): SkillOutput {
        val placeToNavigate = inputData ?: return NavigationOutput(null)

        // Парсер чисел может вернуть нам числовые значения из строки адреса
        val npf = ctx.parserFormatter
        val cleanPlaceToNavigate = if (npf == null) {
            // Если парсер отсутствует, передаём адрес напрямую в приложение карт
            placeToNavigate.trim { it <= ' ' }
        } else {
            // Извлекаем числа и текст из адреса, чтобы убрать "лишние" слова
            val textWithNumbers: List<Any> = npf
                .extractNumber(placeToNavigate)
                .preferOrdinal(true)
                .mixedWithText

            // Собираем адрес обратно, заменяя распознанные числа их цифровым представлением
            val placeToNavigateSB = StringBuilder()
            for (currentItem in textWithNumbers) {
                if (currentItem is String) {
                    placeToNavigateSB.append(currentItem)
                } else if (currentItem is Number) {
                    if (currentItem.isInteger) {
                        placeToNavigateSB.append(currentItem.integerValue())
                    } else {
                        placeToNavigateSB.append(currentItem.decimalValue())
                    }
                }
            }
            placeToNavigateSB.toString().trim { it <= ' ' }
        }

        // Формируем URI для запуска приложения карт и передаём туда очищенный адрес
        val uriGeoSimple = String.format(Locale.getDefault(), "geo:0,0?q=%s", cleanPlaceToNavigate)
        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriGeoSimple))
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        ctx.android.startActivity(launchIntent)

        return NavigationOutput(cleanPlaceToNavigate)
    }
}

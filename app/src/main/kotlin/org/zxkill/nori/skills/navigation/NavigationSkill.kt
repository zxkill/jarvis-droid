package org.zxkill.nori.skills.navigation

import android.content.Intent
import android.net.Uri
import org.nori.numbers.unit.Number
import org.nori.skill.context.SkillContext
import org.nori.skill.recognizer.FuzzyRecognizerSkill
import org.nori.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity
import java.util.Locale

/**
 * Скилл навигации: получает адрес от пользователя и открывает карту с построением маршрута.
 */
class NavigationSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<String>(correspondingSkillInfo, Specificity.LOW) {

    override val patterns = listOf(
        Pattern(
            examples = listOf(
                "проложи маршрут до москвы",
                "построй маршрут к дому",
                "покажи дорогу до станции"
            ),
            regex = Regex("(?:проложи|построй|покажи)\\s+(?:маршрут\\s+)?(?:до|к)\\s+(?<where>.+)"),
            builder = { match -> match!!.groups["where"]!!.value }
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

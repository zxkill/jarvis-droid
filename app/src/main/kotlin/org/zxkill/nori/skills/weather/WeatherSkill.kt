package org.zxkill.nori.skills.weather

import android.util.Log
import kotlinx.coroutines.flow.first
import org.nori.skill.context.SkillContext
import org.nori.skill.recognizer.FuzzyRecognizerSkill
import org.nori.skill.recognizer.FuzzyRecognizerSkill.Pattern
import org.nori.skill.skill.AutoRunnable
import org.nori.skill.skill.SkillInfo
import org.nori.skill.skill.SkillOutput
import org.nori.skill.skill.Specificity
import org.zxkill.nori.skills.weather.WeatherInfo.weatherDataStore
import org.zxkill.nori.util.ConnectionUtils
import org.zxkill.nori.util.StringUtils
import java.io.FileNotFoundException
import java.util.Locale
import kotlin.math.roundToInt

/** Скилл получения текущей погоды для указанного города или текущих координат. */
class WeatherSkill(correspondingSkillInfo: SkillInfo) :
    FuzzyRecognizerSkill<String?>(correspondingSkillInfo, Specificity.LOW), AutoRunnable {

    // Явно указываем тип списка, чтобы Kotlin понимал,
    // что оба шаблона возвращают строку города либо null
    override val patterns: List<Pattern<String?>> = listOf(
        Pattern(
            examples = listOf(
                "какая погода",
                "какая сейчас погода",
                "погода сегодня"
            ),
            builder = { _ -> null }
        ),
        Pattern(
            examples = listOf(
                "какая погода в москве",
                "какая сейчас погода в париже",
                "погода в лондоне"
            ),
            // Поддерживаем указание города
            regex = Regex("какая(?:\\s+сейчас)?\\s+погода\\s+в\\s+(?<city>.+)"),
            builder = { match -> match?.groups["city"]?.value }
        )
    )

    // Погода меняется не так часто — обновляем информацию каждые 30 минут
    override val autoUpdateIntervalMillis: Long = 30 * 60 * 1000L

    private companion object {
        const val TAG = "WeatherSkill"
        private const val IP_INFO_URL = "https://ipinfo.io/json"
        private const val ICON_BASE_URL = "https://openweathermap.org/img/wn/"
        private const val ICON_FORMAT = "@2x.png"
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: String?): SkillOutput {
        val prefs = ctx.android.weatherDataStore.data.first()
        val city = getCity(prefs, inputData)
        val lang = ctx.locale.language.lowercase(Locale.getDefault())
        Log.d(TAG, "Запрос погоды. Город из запроса или настроек: $city")

        val weatherData = try {
            when {
                city != null -> WeatherCache.getWeather(city = city, lang = lang)
                else -> {
                    Log.d(TAG, "Город не указан, пробуем определить координаты устройства")
                    val coords = WeatherCache.getCoordinates(ctx.android)
                    if (coords != null) {
                        Log.d(TAG, "Координаты найдены: $coords")
                        WeatherCache.getWeather(coords = coords, lang = lang)
                    } else {
                        Log.d(TAG, "Координаты недоступны, определяем город по IP")
                        val ipCity = ConnectionUtils.getPageJson(IP_INFO_URL).getString("city")
                        WeatherCache.getWeather(city = ipCity, lang = lang)
                    }
                }
            }
        } catch (_: FileNotFoundException) {
            Log.w(TAG, "Не удалось найти город")
            return WeatherOutput.Failed(city = city ?: "")
        }

        val weatherObject = weatherData.getJSONArray("weather").getJSONObject(0)
        val mainObject = weatherData.getJSONObject("main")
        val windObject = weatherData.getJSONObject("wind")

        val tempUnit = ResolvedTemperatureUnit.from(prefs)
        val temp = mainObject.getDouble("temp")
        val tempConverted = tempUnit.convert(temp)
        val tempRounded = tempConverted.roundToInt()
        val result = WeatherOutput.Success(
            city = weatherData.getString("name"),
            description = weatherObject.getString("description")
                .apply { this[0].uppercaseChar() + this.substring(1) },
            iconUrl = ICON_BASE_URL + weatherObject.getString("icon") + ICON_FORMAT,
            temp = temp,
            tempMin = mainObject.getDouble("temp_min"),
            tempMax = mainObject.getDouble("temp_max"),
            tempString = ctx.parserFormatter
                ?.niceNumber(tempRounded.toDouble())?.speech(true)?.get()
                ?.replace(Regex("[.,]0+$"), "")
                ?: tempRounded.toString(),
            windSpeed = windObject.getDouble("speed"),
            temperatureUnit = tempUnit,
            lengthUnit = ResolvedLengthUnit.from(prefs),
        )
        Log.d(TAG, "Погода для города ${result.city} получена успешно")
        return result
    }

    override suspend fun autoOutput(ctx: SkillContext): SkillOutput {
        return generateOutput(ctx, null)
    }

    private fun getCity(prefs: SkillSettingsWeather, inputData: String?): String? {
        var city = inputData

        if (city.isNullOrEmpty()) {
            city = StringUtils.removePunctuation(prefs.defaultCity.trim { ch -> ch <= ' ' })
        }

        return city?.takeIf { it.isNotEmpty() }
    }
}

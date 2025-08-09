package org.stypox.dicio.skills.weather

import kotlinx.coroutines.flow.first
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Weather
import org.stypox.dicio.skills.weather.WeatherInfo.weatherDataStore
import org.stypox.dicio.util.ConnectionUtils
import org.stypox.dicio.util.StringUtils
import java.io.FileNotFoundException
import java.util.Locale
import kotlin.math.roundToInt

/** Скилл получения текущей погоды для указанного города. */
class WeatherSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Weather>) :
    StandardRecognizerSkill<Weather>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Weather): SkillOutput {
        val prefs = ctx.android.weatherDataStore.data.first()
        val city = getCity(prefs, inputData)
        val lang = ctx.locale.language.lowercase(Locale.getDefault())

        val weatherData = try {
            when {
                city != null -> WeatherCache.getWeather(city = city, lang = lang)
                else -> {
                    val coords = WeatherCache.getCoordinates(ctx.android)
                    if (coords != null) {
                        WeatherCache.getWeather(coords = coords, lang = lang)
                    } else {
                        val ipCity = ConnectionUtils.getPageJson(IP_INFO_URL).getString("city")
                        WeatherCache.getWeather(city = ipCity, lang = lang)
                    }
                }
            }
        } catch (_: FileNotFoundException) {
            return WeatherOutput.Failed(city = city ?: "")
        }

        val weatherObject = weatherData.getJSONArray("weather").getJSONObject(0)
        val mainObject = weatherData.getJSONObject("main")
        val windObject = weatherData.getJSONObject("wind")

        val tempUnit = ResolvedTemperatureUnit.from(prefs)
        val temp = mainObject.getDouble("temp")
        val tempConverted = tempUnit.convert(temp)
        return WeatherOutput.Success(
            city = weatherData.getString("name"),
            description = weatherObject.getString("description")
                .apply { this[0].uppercaseChar() + this.substring(1) },
            iconUrl = ICON_BASE_URL + weatherObject.getString("icon") + ICON_FORMAT,
            temp = temp,
            tempMin = mainObject.getDouble("temp_min"),
            tempMax = mainObject.getDouble("temp_max"),
            tempString = ctx.parserFormatter
                ?.niceNumber(tempConverted.roundToInt().toDouble())?.speech(true)?.get()
                ?: (tempConverted.roundToInt().toString()),
            windSpeed = windObject.getDouble("speed"),
            temperatureUnit = tempUnit,
            lengthUnit = ResolvedLengthUnit.from(prefs),
        )
    }

    private fun getCity(prefs: SkillSettingsWeather, inputData: Weather): String? {
        var city = when (inputData) {
            is Weather.Current -> inputData.where
        }

        if (city.isNullOrEmpty()) {
            city = StringUtils.removePunctuation(prefs.defaultCity.trim { ch -> ch <= ' ' })
        }

        return city?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val IP_INFO_URL = "https://ipinfo.io/json"
        private const val ICON_BASE_URL = "https://openweathermap.org/img/wn/"
        private const val ICON_FORMAT = "@2x.png"
    }
}

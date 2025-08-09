package org.stypox.dicio.skills.weather

import android.util.Log
import kotlinx.coroutines.flow.first
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.AutoRunnable
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Weather
import org.stypox.dicio.skills.weather.WeatherInfo.weatherDataStore
import org.stypox.dicio.util.ConnectionUtils
import org.stypox.dicio.util.StringUtils
import java.io.FileNotFoundException
import java.util.Locale
import kotlin.math.roundToInt

/** Скилл получения текущей погоды для указанного города или текущих координат. */
class WeatherSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Weather>) :
    StandardRecognizerSkill<Weather>(correspondingSkillInfo, data), AutoRunnable {

    // Погода меняется не так часто — обновляем информацию каждые 30 минут
    override val autoUpdateIntervalMillis: Long = 30 * 60 * 1000L

    private companion object {
        const val TAG = "WeatherSkill"
        private const val IP_INFO_URL = "https://ipinfo.io/json"
        private const val ICON_BASE_URL = "https://openweathermap.org/img/wn/"
        private const val ICON_FORMAT = "@2x.png"
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Weather): SkillOutput {
        // Загружаем настройки умения и определяем город и язык ответа
        val prefs = ctx.android.weatherDataStore.data.first()
        val city = getCity(prefs, inputData)
        val lang = ctx.locale.language.lowercase(Locale.getDefault())
        Log.d(TAG, "Запрос погоды. Город из запроса или настроек: $city")

        val weatherData = try {
            when {
                // Если город известен – берём данные по нему
                city != null -> WeatherCache.getWeather(city = city, lang = lang)
                else -> {
                    // Пытаемся определить координаты устройства
                    Log.d(TAG, "Город не указан, пробуем определить координаты устройства")
                    val coords = WeatherCache.getCoordinates(ctx.android)
                    if (coords != null) {
                        Log.d(TAG, "Координаты найдены: $coords")
                        WeatherCache.getWeather(coords = coords, lang = lang)
                    } else {
                        // Последняя попытка – определяем город по IP
                        Log.d(TAG, "Координаты недоступны, определяем город по IP")
                        val ipCity = ConnectionUtils.getPageJson(IP_INFO_URL).getString("city")
                        WeatherCache.getWeather(city = ipCity, lang = lang)
                    }
                }
            }
        } catch (_: FileNotFoundException) {
            Log.w(TAG, "Не удалось найти город", _)
            return WeatherOutput.Failed(city = city ?: "")
        }

        val weatherObject = weatherData.getJSONArray("weather").getJSONObject(0)
        val mainObject = weatherData.getJSONObject("main")
        val windObject = weatherData.getJSONObject("wind")

        val tempUnit = ResolvedTemperatureUnit.from(prefs)
        val temp = mainObject.getDouble("temp")
        val tempConverted = tempUnit.convert(temp)
        // Округляем температуру, чтобы в речи не звучало десятых долей
        val tempRounded = tempConverted.roundToInt()
        val result = WeatherOutput.Success(
            city = weatherData.getString("name"),
            description = weatherObject.getString("description")
                .apply { this[0].uppercaseChar() + this.substring(1) },
            iconUrl = ICON_BASE_URL + weatherObject.getString("icon") + ICON_FORMAT,
            temp = temp,
            tempMin = mainObject.getDouble("temp_min"),
            tempMax = mainObject.getDouble("temp_max"),
            // Убираем дробную часть из текстового представления температуры
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
        return generateOutput(ctx, Weather.Current(where = null))
    }

    private fun getCity(prefs: SkillSettingsWeather, inputData: Weather): String? {
        // Извлекаем город из пользовательского запроса
        var city = when (inputData) {
            is Weather.Current -> inputData.where
        }

        // Если пользователь ничего не сказал, используем город по умолчанию из настроек
        if (city.isNullOrEmpty()) {
            city = StringUtils.removePunctuation(prefs.defaultCity.trim { ch -> ch <= ' ' })
        }

        return city?.takeIf { it.isNotEmpty() }
    }
}

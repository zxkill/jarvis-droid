package org.zxkill.nori.skills.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.zxkill.nori.skills.weather.WeatherInfo.weatherDataStore
import org.zxkill.nori.util.ConnectionUtils
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Простое кэширование данных о погоде в памяти. Каждый элемент автоматически
 * обновляется каждые [REFRESH_MS] миллисекунд в фоне, поэтому ответы умения
 * выдаются мгновенно и без обращения к сети при каждом запросе.
 */
object WeatherCache {
    private const val TAG = "WeatherCache"

    // Обёртка над JSON‑ответом, хранящая время последнего обновления
    private data class Cached(val json: JSONObject, var timestamp: Long)

    private val cache = ConcurrentHashMap<String, Cached>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Период обновления данных: 30 минут
    private const val REFRESH_MS = 30 * 60 * 1000L

    suspend fun getWeather(
        city: String? = null,
        coords: Pair<Double, Double>? = null,
        lang: String,
    ): JSONObject {
        // Формируем ключ кэша: либо название города, либо координаты
        val key = city?.lowercase(Locale.getDefault()) ?: "${coords!!.first},${coords.second}"
        val now = System.currentTimeMillis()
        val cached = cache[key]
        // Если данные ещё свежие, возвращаем их
        if (cached != null && now - cached.timestamp < REFRESH_MS) {
            Log.d(TAG, "Возвращаем погоду из кэша для ключа $key")
            return cached.json
        }

        // Иначе запрашиваем погоду из сети и сохраняем в кэш
        Log.d(TAG, "Запрашиваем погоду из сети для ключа $key")
        val json = fetch(city, coords, lang)
        cache[key] = Cached(json, now)
        scheduleRefresh(key, city, coords, lang)
        return json
    }

    private fun scheduleRefresh(
        key: String,
        city: String?,
        coords: Pair<Double, Double>?,
        lang: String,
    ) {
        if (jobs.containsKey(key)) return
        Log.d(TAG, "Планируем периодическое обновление для ключа $key")
        jobs[key] = scope.launch {
            while (true) {
                delay(REFRESH_MS)
                try {
                    Log.d(TAG, "Обновляем данные погоды для ключа $key")
                    val json = fetch(city, coords, lang)
                    cache[key] = Cached(json, System.currentTimeMillis())
                } catch (e: Exception) {
                    // В случае ошибки оставляем старые данные
                    Log.w(TAG, "Не удалось обновить погоду для ключа $key", e)
                }
            }
        }
    }

    fun preload(context: Context) {
        val appContext = context.applicationContext
        val lang = Locale.getDefault().language.lowercase(Locale.getDefault())
        scope.launch {
            try {
                Log.d(TAG, "Предварительная загрузка данных о погоде")
                val prefs = appContext.weatherDataStore.data.first()
                val city = prefs.defaultCity.takeIf { it.isNotBlank() }
                val coords = if (city == null) getCoordinates(appContext) else null
                if (city != null || coords != null) {
                    getWeather(city, coords, lang)
                }
            } catch (e: Exception) {
                // Игнорируем ошибки во время прогрева кэша
                Log.w(TAG, "Ошибка при предварительной загрузке погоды", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getCoordinates(context: Context): Pair<Double, Double>? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = lm.getProviders(true)
        var best: Location? = null
        for (provider in providers) {
            val l = lm.getLastKnownLocation(provider) ?: continue
            if (best == null || l.accuracy < best!!.accuracy) {
                best = l
            }
        }
        val result = best?.let { it.latitude to it.longitude }
        Log.d(TAG, "Определены координаты устройства: $result")
        return result
    }

    private fun fetch(city: String?, coords: Pair<Double, Double>?, lang: String): JSONObject {
        val base = "$WEATHER_API_URL?APPID=$API_KEY&units=metric&lang=$lang"
        val url = if (coords != null) {
            "$base&lat=${coords.first}&lon=${coords.second}"
        } else {
            "$base&q=" + ConnectionUtils.urlEncode(city!!)
        }
        Log.d(TAG, "Запрос по URL: $url")
        return ConnectionUtils.getPageJson(url)
    }

    private const val WEATHER_API_URL = "https://api.openweathermap.org/data/2.5/weather"
    private const val API_KEY = "061f24cf3cde2f60644a8240302983f2"
}


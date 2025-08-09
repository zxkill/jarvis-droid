package org.stypox.dicio.skills.weather

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.stypox.dicio.util.ConnectionUtils
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory cache for weather data. Each entry is refreshed every [REFRESH_MS]
 * milliseconds in the background so that skill responses can be served instantly
 * without hitting the network on demand.
 */
object WeatherCache {
    private data class Cached(val json: JSONObject, var timestamp: Long)

    private val cache = ConcurrentHashMap<String, Cached>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO)

    private const val REFRESH_MS = 30 * 60 * 1000L // 30 minutes

    suspend fun getWeather(
        city: String? = null,
        coords: Pair<Double, Double>? = null,
        lang: String,
    ): JSONObject {
        val key = city?.lowercase(Locale.getDefault()) ?: "${coords!!.first},${coords.second}"
        val now = System.currentTimeMillis()
        val cached = cache[key]
        if (cached != null && now - cached.timestamp < REFRESH_MS) {
            return cached.json
        }
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
        jobs[key] = scope.launch {
            while (true) {
                delay(REFRESH_MS)
                try {
                    val json = fetch(city, coords, lang)
                    cache[key] = Cached(json, System.currentTimeMillis())
                } catch (_: Exception) {
                    // ignore and keep old cached value
                }
            }
        }
    }

    private fun fetch(city: String?, coords: Pair<Double, Double>?, lang: String): JSONObject {
        val base = "$WEATHER_API_URL?APPID=$API_KEY&units=metric&lang=$lang"
        val url = if (coords != null) {
            "$base&lat=${coords.first}&lon=${coords.second}"
        } else {
            "$base&q=" + ConnectionUtils.urlEncode(city!!)
        }
        return ConnectionUtils.getPageJson(url)
    }

    private const val WEATHER_API_URL = "https://api.openweathermap.org/data/2.5/weather"
    private const val API_KEY = "061f24cf3cde2f60644a8240302983f2"
}


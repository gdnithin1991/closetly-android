package com.smartwardrobe.app.data

import com.smartwardrobe.app.domain.WeatherMood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherResult(
    val summary: String,
    val mood: WeatherMood
)

class WeatherRepository {
    suspend fun fetch(city: String): Result<WeatherResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCity = city.trim()
            require(cleanCity.length >= 2) { "Enter a home city first." }
            val encoded = URLEncoder.encode(cleanCity, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val geo = JSONObject(httpGet(geoUrl))
            val first = geo.getJSONArray("results").getJSONObject(0)
            val latitude = first.getDouble("latitude")
            val longitude = first.getDouble("longitude")
            val place = first.getString("name")
            val country = first.optString("country", "")
            val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,precipitation,weather_code,relative_humidity_2m&timezone=auto"
            val forecast = JSONObject(httpGet(forecastUrl)).getJSONObject("current")
            val temp = forecast.getDouble("temperature_2m")
            val rain = forecast.optDouble("precipitation", 0.0)
            val humidity = forecast.optDouble("relative_humidity_2m", 0.0)
            val code = forecast.optInt("weather_code", 0)
            val mood = when {
                rain > 0.0 || code in 51..67 || code in 80..99 -> WeatherMood.RAIN
                temp < 18 -> WeatherMood.COLD
                temp >= 32 -> WeatherMood.HOT
                humidity >= 75 -> WeatherMood.HUMID
                else -> WeatherMood.CLEAR
            }
            val label = listOf(place, country).filter { it.isNotBlank() }.joinToString(", ")
            WeatherResult("$label - ${temp.toInt()}C - ${mood.name.lowercase()}", mood)
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 7000
        connection.readTimeout = 7000
        connection.requestMethod = "GET"
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}

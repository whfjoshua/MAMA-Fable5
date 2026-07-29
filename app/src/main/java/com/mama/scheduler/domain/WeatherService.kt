package com.mama.scheduler.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object WeatherService {
    private const val TAG = "WeatherService"
    private const val OPENWEATHERMAP_API_KEY = "YOUR_OPENWEATHERMAP_API_KEY" // replace with your key
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"

    data class WeatherInfo(
        val temperature: Double,
        val condition: String,
        val description: String,
        val icon: String,
        val humidity: Int,
        val windSpeed: Double,
        val isStormy: Boolean,
        val stormAlert: String?
    )

    suspend fun getWeatherForDate(
        dateMillis: Long,
        latitude: Double,
        longitude: Double
    ): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val hoursDiff = (dateMillis - System.currentTimeMillis()) / 3_600_000
            if (hoursDiff < 24) {
                getCurrentWeather(latitude, longitude)
            } else {
                getForecastWeather(latitude, longitude, dateMillis)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting weather: ${e.message}", e)
            null
        }
    }

    private fun getCurrentWeather(lat: Double, lon: Double): WeatherInfo? {
        val apiKey = weatherApiKey() ?: return mockWeather()
        return fetchWeather("$BASE_URL?lat=$lat&lon=$lon&appid=$apiKey&units=imperial")
    }

    private fun getForecastWeather(lat: Double, lon: Double, targetDate: Long): WeatherInfo? {
        val apiKey = weatherApiKey() ?: return mockWeather()
        val urlString =
            "https://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&appid=$apiKey&units=imperial"
        return try {
            val response = httpGet(urlString)
            val list = JSONObject(response).getJSONArray("list")
            var closest: JSONObject? = null
            var minDiff = Long.MAX_VALUE
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val diff = kotlin.math.abs(item.getLong("dt") * 1000 - targetDate)
                if (diff < minDiff) {
                    minDiff = diff
                    closest = item
                }
            }
            closest?.let { parseWeatherJson(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching forecast: ${e.message}", e)
            mockWeather()
        }
    }

    private fun fetchWeather(urlString: String): WeatherInfo? = try {
        parseWeatherJson(JSONObject(httpGet(urlString)))
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching weather: ${e.message}", e)
        mockWeather()
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        return InputStreamReader(connection.inputStream).use { it.readText() }
            .also { connection.disconnect() }
    }

    private fun parseWeatherJson(json: JSONObject): WeatherInfo {
        val main = json.getJSONObject("main")
        val wind = json.getJSONObject("wind")
        val weather = json.getJSONArray("weather").getJSONObject(0)

        val temp = main.getDouble("temp")
        val condition = weather.getString("main")
        val windSpeed = wind.getDouble("speed")

        val isStormy = condition.contains("Thunderstorm", ignoreCase = true) ||
            condition.contains("Storm", ignoreCase = true) || windSpeed > 25

        val stormAlert = when {
            condition.contains("Thunderstorm", true) -> "⛈️ Thunderstorm expected!"
            condition.contains("Storm", true) -> "🌪️ Storm expected!"
            windSpeed > 25 -> "💨 High winds expected!"
            temp < 32 -> "🥶 Freezing temperatures expected!"
            temp > 95 -> "🌡️ Extreme heat expected!"
            else -> null
        }

        return WeatherInfo(
            temperature = temp,
            condition = condition,
            description = weather.getString("description"),
            icon = weather.getString("icon"),
            humidity = main.getInt("humidity"),
            windSpeed = windSpeed,
            isStormy = isStormy,
            stormAlert = stormAlert
        )
    }

    private fun mockWeather() = WeatherInfo(
        temperature = 75.0,
        condition = "Clear",
        description = "clear sky",
        icon = "01d",
        humidity = 45,
        windSpeed = 10.0,
        isStormy = false,
        stormAlert = null
    )

    @Suppress("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}", e)
            null
        }
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun weatherApiKey(): String? =
        OPENWEATHERMAP_API_KEY.takeIf { it != "YOUR_OPENWEATHERMAP_API_KEY" }
}

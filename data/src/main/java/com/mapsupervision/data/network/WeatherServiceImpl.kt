package com.mapsupervision.data.network

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.service.WeatherData
import com.mapsupervision.domain.service.WeatherService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class WeatherServiceImpl @Inject constructor() : WeatherService {

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchWeather(
        latitude: Double,
        longitude: Double
    ): AppResult<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("https://api.open-meteo.com/v1/forecast")
                append("?latitude=").append(latitude)
                append("&longitude=").append(longitude)
                append("&current=temperature_2m,weather_code")
                append("&forecast_days=1")
                append("&timezone=auto")
            }
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Error(
                        Exception("Weather API error: ${response.code} ${response.message}")
                    )
                }
                val bodyString = response.body?.string()
                    ?: return@withContext AppResult.Error(Exception("Empty body response"))
                val openMeteoRes = json.decodeFromString<OpenMeteoResponse>(bodyString)
                val current = openMeteoRes.current
                    ?: return@withContext AppResult.Error(Exception("Current weather data not found"))

                AppResult.Success(
                    WeatherData(
                        condition = weatherConditionFromCode(current.weatherCode),
                        temperature = current.temperature
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error(e)
        }
    }

    internal fun weatherConditionFromCode(weatherCode: Int): String = when (weatherCode) {
        0 -> "Nắng"
        1 -> "Ít mây"
        2, 3 -> "Nhiều mây"
        45, 48 -> "Sương mù"
        51, 53, 55, 56, 57 -> "Mưa phùn"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Mưa"
        71, 73, 75, 77, 85, 86 -> "Mưa đá hoặc tuyết"
        95, 96, 99 -> "Giông bão"
        else -> "Thời tiết khác"
    }

    @Serializable
    private data class OpenMeteoResponse(
        val current: CurrentWeather? = null
    )

    @Serializable
    private data class CurrentWeather(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int
    )
}

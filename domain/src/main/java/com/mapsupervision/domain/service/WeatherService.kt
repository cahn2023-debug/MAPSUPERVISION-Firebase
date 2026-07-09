package com.mapsupervision.domain.service

import com.mapsupervision.core.result.AppResult

interface WeatherService {
    suspend fun fetchWeather(latitude: Double, longitude: Double): AppResult<WeatherData>
}

data class WeatherData(
    val condition: String, // Nắng, Mưa, Nhiều mây, Giông bão
    val temperature: Double
)

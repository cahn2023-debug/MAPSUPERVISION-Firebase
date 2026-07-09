package com.mapsupervision.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherServiceImplTest {

    private val service = WeatherServiceImpl()

    @Test
    fun `weather codes are mapped to Vietnamese labels`() {
        assertEquals("Nắng", service.weatherConditionFromCode(0))
        assertEquals("Nhiều mây", service.weatherConditionFromCode(3))
        assertEquals("Mưa", service.weatherConditionFromCode(63))
        assertEquals("Giông bão", service.weatherConditionFromCode(95))
    }

    @Test
    fun `unknown weather code falls back to generic label`() {
        assertEquals("Thời tiết khác", service.weatherConditionFromCode(999))
    }
}

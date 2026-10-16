package dev.chungjungsoo.gptmobile.data.routine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoWeatherTest {
    @Test
    fun `geocoding prefers Newbawn in County Wexford`() {
        val hits = OpenMeteo.parseGeocoding(GEOCODING)
        val picked = OpenMeteo.pickPlace("Newbawn, Co. Wexford", hits)
        assertEquals("County Wexford", picked?.admin2)
        assertEquals(52.34138, picked?.latitude ?: 0.0, 0.00001)
        assertEquals(-6.79665, picked?.longitude ?: 0.0, 0.00001)
        assertEquals("Newbawn, County Wexford, Ireland", picked?.label())
    }

    @Test
    fun `forecast text uses the selected place`() {
        val report = OpenMeteo.formatReport("Newbawn, County Wexford, Ireland", OpenMeteo.parseForecast(FORECAST))
        assertTrue(report.contains("Newbawn, County Wexford, Ireland"))
        assertTrue(report.contains("12°C"))
        assertTrue(report.contains("Overcast"))
        assertTrue(report.contains("7–14°C"))
        assertTrue(report.contains("Slight rain"))
        assertTrue(report.contains("80% chance of rain"))
    }

    @Test
    fun `default place falls back when geocoding is empty`() = runBlocking {
        val http = ScriptedHttp(geocoding = """{"results":[]}""", forecast = FORECAST)
        val report = OpenMeteoWeatherClient(http).forecast("Newbawn, Co. Wexford")
        assertTrue(http.urls.any { it.contains("latitude=52.34138") && it.contains("longitude=-6.79665") })
        assertTrue(report.contains("Newbawn, County Wexford, Ireland"))
    }

    @Test
    fun `an unknown place does not invent a forecast`() = runBlocking {
        val http = ScriptedHttp(geocoding = """{"results":[]}""", forecast = FORECAST)
        val report = OpenMeteoWeatherClient(http).forecast("Not A Real Village")
        assertEquals("No weather match for Not A Real Village.", report)
        assertTrue(http.urls.none { it.contains("forecast") })
    }

    @Test
    fun `the client asks Open-Meteo with the edited place`() = runBlocking {
        val http = ScriptedHttp(geocoding = GEOCODING, forecast = FORECAST)
        OpenMeteoWeatherClient(http).forecast("Newbawn, Co. Wexford")
        assertTrue(http.urls.first().startsWith("https://geocoding-api.open-meteo.com/v1/search?name=Newbawn"))
        assertTrue(http.urls.last().startsWith("https://api.open-meteo.com/v1/forecast?"))
    }

    private class ScriptedHttp(
        private val geocoding: String,
        private val forecast: String
    ) : WeatherHttp {
        val urls = mutableListOf<String>()

        override suspend fun get(url: String): String {
            urls += url
            return if (url.contains("geocoding-api")) geocoding else forecast
        }
    }

    private companion object {
        const val GEOCODING = """
            {"results":[
              {"name":"Newbawn","latitude":52.34138,"longitude":-6.79665,"country":"Ireland","admin1":"Leinster","admin2":"County Wexford"},
              {"name":"Newbawn","latitude":52.92384,"longitude":-6.19323,"country":"Ireland","admin1":"Leinster","admin2":"County Wicklow"}
            ]}
        """
        const val FORECAST = """
            {"current":{"temperature_2m":11.6,"weather_code":3,"wind_speed_10m":18.2},
             "daily":{"time":["2026-10-16"],"weather_code":[61],"temperature_2m_max":[14.2],"temperature_2m_min":[7.4],"precipitation_probability_max":[80]}}
        """
    }
}

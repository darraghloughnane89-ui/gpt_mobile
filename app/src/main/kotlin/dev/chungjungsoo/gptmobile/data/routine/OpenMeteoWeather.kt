package dev.chungjungsoo.gptmobile.data.routine

import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface WeatherHttp {
    suspend fun get(url: String): String
}

interface WeatherClient {
    suspend fun forecast(place: String): String
}

data class GeoHit(
    val name: String,
    val admin1: String?,
    val admin2: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double
) {
    fun label(): String = listOfNotNull(name, admin2, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

data class ForecastSnapshot(
    val temperatureC: Double?,
    val weatherCode: Int?,
    val windKmh: Double?,
    val todayMax: Double?,
    val todayMin: Double?,
    val todayCode: Int?,
    val rainChance: Int?
)

object OpenMeteo {
    const val FALLBACK_LATITUDE = 52.34138
    const val FALLBACK_LONGITUDE = -6.79665

    private val json = Json { ignoreUnknownKeys = true }
    private val ignoredHints = setOf("co", "county", "the")

    fun geocodeUrl(name: String): String {
        val encoded = URLEncoder.encode(name, Charsets.UTF_8)
        return "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=en&format=json"
    }

    fun forecastUrl(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=1"

    fun searchName(query: String): String {
        val head = query.substringBefore(",").trim()
        return head.ifBlank { query.trim() }.ifBlank { "Newbawn" }
    }

    fun isDefaultPlace(query: String): Boolean {
        val normalized = query.lowercase().replace(Regex("[^a-z]"), "")
        return normalized == "newbawncowexford" ||
            normalized == "newbawncountywexford" ||
            normalized == "newbawnwexford" ||
            normalized == "newbawn"
    }

    fun parseGeocoding(body: String): List<GeoHit> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj.stringOrNull("name") ?: return@mapNotNull null
            val latitude = obj.doubleOrNull("latitude") ?: return@mapNotNull null
            val longitude = obj.doubleOrNull("longitude") ?: return@mapNotNull null
            GeoHit(
                name = name,
                admin1 = obj.stringOrNull("admin1"),
                admin2 = obj.stringOrNull("admin2"),
                country = obj.stringOrNull("country"),
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    fun pickPlace(query: String, hits: List<GeoHit>): GeoHit? {
        if (hits.isEmpty()) return null
        val hints = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in ignoredHints }
        if (hints.isEmpty()) return hits.first()
        return hits.maxWith(
            compareBy { hit ->
                val blob = listOfNotNull(hit.name, hit.admin1, hit.admin2, hit.country).joinToString(" ").lowercase()
                hints.count { hint -> hint in blob }
            }
        )
    }

    fun parseForecast(body: String): ForecastSnapshot {
        val root = json.parseToJsonElement(body).jsonObject
        val current = root["current"]?.jsonObject
        val daily = root["daily"]?.jsonObject
        return ForecastSnapshot(
            temperatureC = current?.doubleOrNull("temperature_2m"),
            weatherCode = current?.intFlexible("weather_code"),
            windKmh = current?.doubleOrNull("wind_speed_10m"),
            todayMax = daily?.firstDouble("temperature_2m_max"),
            todayMin = daily?.firstDouble("temperature_2m_min"),
            todayCode = daily?.firstInt("weather_code"),
            rainChance = daily?.firstInt("precipitation_probability_max")
        )
    }

    fun formatReport(placeLabel: String, forecast: ForecastSnapshot): String = buildString {
        append(placeLabel)
        append("\nNow: ")
        append(forecast.temperatureC?.let { "${it.roundToInt()}°C" } ?: "unknown")
        forecast.weatherCode?.let { append(", ${weatherLabel(it)}") }
        forecast.windKmh?.let { append(", wind ${it.roundToInt()} km/h") }
        append("\nToday: ")
        val min = forecast.todayMin?.roundToInt()
        val max = forecast.todayMax?.roundToInt()
        if (min != null && max != null) append("$min–$max°C") else append("no daily range")
        forecast.todayCode?.let { append(", ${weatherLabel(it)}") }
        forecast.rainChance?.let { append(", $it% chance of rain") }
    }

    fun fallbackHit(): GeoHit = GeoHit(
        name = "Newbawn",
        admin1 = "Leinster",
        admin2 = "County Wexford",
        country = "Ireland",
        latitude = FALLBACK_LATITUDE,
        longitude = FALLBACK_LONGITUDE
    )

    fun weatherLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 80 -> "Slight rain"
        63, 81 -> "Rain"
        65, 82 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Weather code $code"
    }
}

@Singleton
class OpenMeteoWeatherClient @Inject constructor(
    private val http: WeatherHttp
) : WeatherClient {
    override suspend fun forecast(place: String): String {
        val query = place.trim().ifBlank { RoutineTemplates.DEFAULT_WEATHER_PLACE }
        val hits = try {
            OpenMeteo.parseGeocoding(http.get(OpenMeteo.geocodeUrl(OpenMeteo.searchName(query))))
        } catch (error: Exception) {
            if (OpenMeteo.isDefaultPlace(query)) emptyList() else throw error
        }
        val picked = OpenMeteo.pickPlace(query, hits)
            ?: if (OpenMeteo.isDefaultPlace(query)) {
                OpenMeteo.fallbackHit()
            } else {
                return "No weather match for $query."
            }
        val forecast = OpenMeteo.parseForecast(http.get(OpenMeteo.forecastUrl(picked.latitude, picked.longitude)))
        return OpenMeteo.formatReport(picked.label(), forecast)
    }
}

@Singleton
class KtorWeatherHttp @Inject constructor(
    private val networkClient: NetworkClient
) : WeatherHttp {
    override suspend fun get(url: String): String {
        val response = networkClient().get(url)
        if (response.status.value !in 200..299) {
            throw IllegalStateException("Weather service returned ${response.status.value}.")
        }
        return response.bodyAsText()
    }
}

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.doubleOrNull(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

private fun JsonObject.intFlexible(key: String): Int? {
    val primitive = this[key]?.jsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.doubleOrNull?.roundToInt()
}

private fun JsonObject.firstDouble(key: String): Double? = this[key]?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull

private fun JsonObject.firstInt(key: String): Int? {
    val primitive = this[key]?.jsonArray?.firstOrNull()?.jsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.doubleOrNull?.roundToInt()
}

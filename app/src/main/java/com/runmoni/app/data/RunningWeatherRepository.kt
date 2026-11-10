package com.runmoni.app.data

import android.content.Context
import com.runmoni.app.BuildConfig
import com.runmoni.app.domain.RunningWeatherSnapshot
import com.runmoni.app.domain.WeatherCondition
import com.runmoni.app.domain.WeatherLocation
import com.runmoni.app.domain.currentWeatherForecastIndex
import com.runmoni.app.domain.weatherInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherRequestException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** User-triggered, approximate-location forecast. Memory only: no GPS or weather-location disk writes. */
class RunningWeatherRepository(@Suppress("UNUSED_PARAMETER") context: Context) {
    private data class Entry(val body: String, val checkedAtMillis: Long, val freshUntilMillis: Long,
        val modified: String?, val control: String?, val expires: String?)
    private val request = Mutex()
    private val memory = LinkedHashMap<WeatherLocation, Entry>()
    private var retryAtMillis = 0L

    suspend fun load(location: WeatherLocation): RunningWeatherSnapshot = withContext(Dispatchers.IO) {
        try { request.withLock {
            currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            val cached = memory[location]
            if (cached != null && now < cached.freshUntilMillis)
                return@withLock MetWeatherParser.parse(cached.body, location, now, cached.checkedAtMillis)
            if (now < retryAtMillis) throw WeatherRequestException("날씨 제공자가 잠시 후 다시 확인하도록 안내했어요.")
            val http = URL("$ENDPOINT?${location.query}").openConnection() as HttpURLConnection
            try {
                http.instanceFollowRedirects = false
                http.connectTimeout = 5_000
                http.readTimeout = 7_000
                http.setRequestProperty("User-Agent", "Runmoni/${BuildConfig.VERSION_NAME} (https://github.com/wjdals988/runmoni)")
                http.setRequestProperty("Accept", "application/json")
                cached?.modified?.let { http.setRequestProperty("If-Modified-Since", it) }
                val code = http.responseCode
                currentCoroutineContext().ensureActive()
                if (code == 429 || code == 503) {
                    val raw = http.getHeaderField("Retry-After")
                    val seconds = raw?.toLongOrNull()?.coerceIn(0, 86_400)
                    val until = httpDate(raw)
                    retryAtMillis = maxOf(now + 60_000L, seconds?.let { now + it * 1_000L } ?: until ?: now + 15 * 60_000L)
                    throw WeatherRequestException("날씨 제공자가 잠시 후 다시 확인하도록 안내했어요.")
                }
                val control = http.getHeaderField("Cache-Control") ?: if (code == HttpURLConnection.HTTP_NOT_MODIFIED) cached?.control else null
                val expires = http.getHeaderField("Expires") ?: if (code == HttpURLConnection.HTTP_NOT_MODIFIED) cached?.expires else null
                val modified = http.getHeaderField("Last-Modified") ?: cached?.modified
                val text = if (code == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) cached.body else {
                    if (code != HttpURLConnection.HTTP_OK) throw IOException("날씨 정보를 불러오지 못했어요. 잠시 후 다시 확인해 주세요.")
                    val type = http.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                    require(type == "application/json" || type == "application/geo+json") { "날씨 응답 형식을 확인할 수 없어요." }
                    require(http.contentLengthLong <= MAX_RESPONSE_BYTES) { "날씨 응답이 너무 커요." }
                    http.inputStream.use { input ->
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(8_192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(bytes.size() + count <= MAX_RESPONSE_BYTES) { "날씨 응답이 너무 커요." }
                            bytes.write(buffer, 0, count)
                        }
                        bytes.toString(Charsets.UTF_8.name())
                    }
                }
                val snapshot = MetWeatherParser.parse(text, location, now, now)
                currentCoroutineContext().ensureActive()
                val directives = control.orEmpty().lowercase(Locale.ROOT)
                if (directives.split(',').any { it.trim() == "no-store" }) memory.remove(location) else {
                    val freshUntil = if (directives.split(',').any { it.trim() == "no-cache" }) now else {
                        val maxAge = Regex("(?:^|,)\\s*max-age\\s*=\\s*\"?(\\d+)").find(directives)?.groupValues?.get(1)?.toLongOrNull()
                        val age = http.getHeaderFieldLong("Age", 0).coerceAtLeast(0)
                        when {
                            maxAge != null -> now + (maxAge.coerceAtMost(86_400) - age).coerceAtLeast(0) * 1_000L
                            httpDate(expires) != null -> requireNotNull(httpDate(expires))
                            else -> now + 15 * 60_000L
                        }
                    }
                    memory[location] = Entry(text, now, freshUntil, modified, control, expires)
                    while (memory.size > 2) memory.remove(memory.keys.first())
                }
                snapshot
            } finally { http.disconnect() }
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (known: WeatherRequestException) { throw known }
        catch (failure: Exception) { throw WeatherRequestException("날씨 정보를 불러오지 못했어요. 잠시 후 다시 확인해 주세요.", failure) }
    }

    companion object {
        const val SOURCE_NAME = "MET Norway"
        const val SOURCE_URL = "https://www.met.no/en"
        const val LICENSE_URL = "https://api.met.no/doc/License"
        private const val ENDPOINT = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private fun httpDate(value: String?): Long? = value?.let {
            runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        }
    }
}

/** Kept independent of the HTTP client so bounded genuine provider fixtures can be tested without requests. */
internal object MetWeatherParser {
    fun parse(json: String, location: WeatherLocation, nowMillis: Long, checkedAtMillis: Long): RunningWeatherSnapshot {
        val properties = JSONObject(json).getJSONObject("properties")
        val meta = properties.getJSONObject("meta")
        val units = meta.getJSONObject("units")
        require(units.optString("air_temperature") == "celsius" && units.optString("wind_speed") == "m/s") { "날씨 단위를 확인할 수 없어요." }
        val issued = weatherInstant(meta.getString("updated_at"))
        require(issued in (nowMillis - 36 * 60 * 60_000L)..(nowMillis + 10 * 60_000L)) { "오래된 예보만 있어요. 나중에 다시 확인해 주세요." }
        val series = properties.getJSONArray("timeseries")
        val times = (0 until series.length()).map { weatherInstant(series.getJSONObject(it).getString("time")) }
        val index = requireNotNull(currentWeatherForecastIndex(times, nowMillis)) { "현재 시간의 예보가 없어요." }
        val data = series.getJSONObject(index).getJSONObject("data")
        val instant = data.getJSONObject("instant").getJSONObject("details")
        val nextHour = data.optJSONObject("next_1_hours")
        val rainDetails = nextHour?.optJSONObject("details")
        val rain = if (rainDetails != null && rainDetails.has("precipitation_amount") && !rainDetails.isNull("precipitation_amount")) {
            require(units.optString("precipitation_amount") == "mm") { "강수량 단위를 확인할 수 없어요." }
            rainDetails.getDouble("precipitation_amount")
        } else null
        val humidity = if (instant.has("relative_humidity") && !instant.isNull("relative_humidity")) {
            require(units.optString("relative_humidity") == "%") { "습도 단위를 확인할 수 없어요." }
            instant.getDouble("relative_humidity")
        } else null
        val symbol = nextHour?.optJSONObject("summary")?.optString("symbol_code").orEmpty().lowercase(Locale.ROOT)
        val condition = when {
            "thunder" in symbol -> WeatherCondition.THUNDER
            "snow" in symbol || "sleet" in symbol -> WeatherCondition.SNOW
            "rain" in symbol -> WeatherCondition.RAIN
            "clearsky" in symbol || "fair" in symbol -> WeatherCondition.CLEAR
            "fog" in symbol -> WeatherCondition.FOG
            "cloud" in symbol -> WeatherCondition.CLOUDY
            else -> WeatherCondition.UNKNOWN
        }
        return RunningWeatherSnapshot(location, times[index], issued, checkedAtMillis,
            instant.getDouble("air_temperature"), instant.getDouble("wind_speed"), rain, condition, humidity)
    }
}

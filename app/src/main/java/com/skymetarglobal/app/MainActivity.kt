package com.skymetarglobal.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SkyMetarApp() }
    }
}

data class CloudLayer(
    val code: String,
    val oktas: String,
    val height: Int?,
    val type: String = ""
)

data class CloudAnalysis(
    val layers: List<CloudLayer>,
    val ceiling: Int?,
    val unknownCeiling: Boolean
)

data class Category(
    val name: String,
    val color: Color,
    val visibility: Double,
    val ceiling: Int
)

data class ForecastCloud(
    val cover: String,
    val baseFt: Int?,
    val type: String
)

data class HourCloudForecast(
    val time: String,
    val layers: List<ForecastCloud>,
    val ceilingFt: Int?,
    val available: Boolean
)

data class ManualSegment(
    val start: Long,
    val layers: List<ForecastCloud>,
    val clear: Boolean
)

val categories = listOf(
    Category("BLUE", Color(0xFF168BFF), 8.0, 2500),
    Category("GREEN", Color(0xFF19A65A), 5.0, 1500),
    Category("GREEN / YELLOW", Color(0xFF9ACD32), 5.0, 1000),
    Category("YELLOW", Color(0xFFFFD21F), 3.0, 500),
    Category("YELLOW / RED", Color(0xFFFF9B21), 1.5, 350),
    Category("RED", Color(0xFFE53935), 1.0, 220),
    Category("GREY", Color(0xFF777777), 0.0, 0)
)

fun parseNumber(value: String): Double? {
    if (value.isEmpty() || value == "////") return null
    val sign = if (value.startsWith("M")) -1.0 else 1.0
    return value.removePrefix("M").toDoubleOrNull()?.times(sign)
}

fun parseVisibility(raw: String): Double? {
    val tokens = raw.uppercase(Locale.US).trim().split(Regex("\\s+"))
    if ("CAVOK" in tokens) return 10.0

    val windIndex = tokens.indexOfFirst {
        it.matches(Regex("""(?:\d{3}|VRB)\d{2,3}(?:G\d{2,3})?KT"""))
    }
    val start = if (windIndex >= 0) windIndex + 1 else 0

    for (i in start until tokens.size) {
        val token = tokens[i]

        if (token.matches(Regex("\\d{4}"))) {
            val meters = token.toInt()
            return if (meters == 9999) 10.0 else meters / 1000.0
        }

        if (token.endsWith("SM")) {
            var value = token.removeSuffix("SM")
            val whole = if (i > 0 && tokens[i - 1].matches(Regex("\\d+"))) {
                tokens[i - 1].toDoubleOrNull() ?: 0.0
            } else 0.0

            value = value.removePrefix("P").removePrefix("M")

            val miles = if (value.contains("/")) {
                val parts = value.split("/")
                if (parts.size != 2) return null
                val n = parts[0].toDoubleOrNull() ?: return null
                val d = parts[1].toDoubleOrNull() ?: return null
                if (d == 0.0) return null
                whole + n / d
            } else {
                value.toDoubleOrNull() ?: continue
            }
            return miles * 1.60934
        }
    }
    return null
}

fun cloudLayersFromText(raw: String): List<ForecastCloud> {
    val regex = Regex(
        """(?<![A-Z0-9])(?:FEW|SCT|BKN|OVC)(?:\d{3}|///)(?:CB|TCU)?|(?<![A-Z0-9])VV(?:\d{3}|///)"""
    )

    return regex.findAll(raw.uppercase(Locale.US)).map { match ->
        val token = match.value
        val code = if (token.startsWith("VV")) "VV" else token.take(3)
        val heightText = if (code == "VV") token.substring(2, 5)
                         else token.substring(3, 6)
        val height = heightText.toIntOrNull()?.times(100)
        val type = when {
            token.endsWith("CB") -> "CB"
            token.endsWith("TCU") -> "TCU"
            else -> "Not specified"
        }
        ForecastCloud(code, height, type)
    }.toList()
}

fun analyzeClouds(raw: String): CloudAnalysis {
    val layers = cloudLayersFromText(raw).map {
        val oktas = when (it.cover) {
            "FEW" -> "1–2 OKTAS"
            "SCT" -> "3–4 OKTAS"
            "BKN" -> "5–7 OKTAS"
            "OVC" -> "8 OKTAS"
            else -> "VERTICAL VISIBILITY"
        }
        CloudLayer(it.cover, oktas, it.baseFt, it.type)
    }

    val ceilingLayers = layers.filter {
        it.code == "BKN" || it.code == "OVC" || it.code == "VV"
    }

    return CloudAnalysis(
        layers,
        ceilingLayers.mapNotNull { it.height }.minOrNull(),
        ceilingLayers.any { it.height == null }
    )
}

fun classifyMetar(visibility: Double?, clouds: CloudAnalysis): Category? {
    if (visibility == null || clouds.unknownCeiling) return null
    val ceiling = clouds.ceiling ?: Int.MAX_VALUE

    return categories.firstOrNull {
        visibility >= it.visibility && ceiling >= it.ceiling
    } ?: categories.last()
}

fun extractWind(raw: String): String =
    Regex("""(?:\d{3}|VRB)\d{2,3}(?:G\d{2,3})?KT""")
        .find(raw.uppercase(Locale.US))?.value ?: "Unavailable"

fun extractTemperature(raw: String): Pair<String, String> {
    val match = Regex("""(?<![A-Z0-9])(M?\d{2})/(M?\d{2})(?![A-Z0-9])""")
        .find(raw.uppercase(Locale.US)) ?: return Pair("—", "—")
    val t = parseNumber(match.groupValues[1])
    val d = parseNumber(match.groupValues[2])
    return Pair(t?.let { "$it °C" } ?: "—", d?.let { "$it °C" } ?: "—")
}

fun extractPressure(raw: String): String {
    val qnh = Regex("""(?<![A-Z0-9])Q\d{4}""")
        .find(raw.uppercase(Locale.US))
    if (qnh != null) return "${qnh.value} hPa"
    return Regex("""(?<![A-Z0-9])A\d{4}""")
        .find(raw.uppercase(Locale.US))?.value ?: "Unavailable"
}

fun extractObservation(raw: String): String =
    Regex("""(?<!\d)\d{6}Z""").find(raw.uppercase(Locale.US))?.value ?: "Unavailable"

fun utcFormat(epoch: Long): String {
    val sdf = SimpleDateFormat("HH:mm 'UTC'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(epoch * 1000L))
}

fun jsonClouds(item: JSONObject): List<ForecastCloud> {
    val array = item.optJSONArray("clouds") ?: JSONArray()
    val result = mutableListOf<ForecastCloud>()

    for (i in 0 until array.length()) {
        val cloud = array.optJSONObject(i) ?: continue
        val cover = cloud.optString("cover", "—")
        val base = if (cloud.isNull("base")) null else cloud.optInt("base")
        val type = cloud.optString("type", "Not specified")
        result.add(ForecastCloud(cover, base, type))
    }
    return result
}

fun ceilingOf(layers: List<ForecastCloud>, item: JSONObject? = null): Int? {
    val vv = item?.optInt("vertVis", -1) ?: -1
    if (vv >= 0) return vv

    return layers.filter {
        it.cover == "BKN" || it.cover == "OVC" || it.cover == "VV"
    }.mapNotNull { it.baseFt }.minOrNull()
}

fun fetchTafForecast(
    code: String,
    callback: (List<HourCloudForecast>, String) -> Unit
) {
    Thread {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(
                "https://aviationweather.gov/api/data/taf?ids=$code&format=json"
            )
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "SKY-METAR-GLOBAL/1.0")

            val response = connection.responseCode
            if (response !in 200..299) {
                callback(emptyList(), "TAF HTTP error: $response")
                return@Thread
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)

            if (array.length() == 0) {
                callback(emptyList(), "No TAF available for $code")
                return@Thread
            }

            val taf = array.getJSONObject(0)
            val periods = taf.optJSONArray("fcsts") ?: JSONArray()
            val now = System.currentTimeMillis() / 1000L
            val forecasts = mutableListOf<HourCloudForecast>()

            for (hour in 0..7) {
                val target = now + hour * 3600L
                var selected: JSONObject? = null

                for (i in 0 until periods.length()) {
                    val period = periods.optJSONObject(i) ?: continue
                    val from = period.optLong("timeFrom", 0L)
                    val to = period.optLong("timeTo", 0L)
                    if (from <= target && target < to) selected = period
                }

                if (selected == null) {
                    forecasts.add(
                        HourCloudForecast(utcFormat(target), emptyList(), null, false)
                    )
                } else {
                    val layers = jsonClouds(selected)
                    forecasts.add(
                        HourCloudForecast(
                            utcFormat(target),
                            layers,
                            ceilingOf(layers, selected),
                            true
                        )
                    )
                }
            }

            callback(forecasts, "TAF forecast loaded")
        } catch (e: Exception) {
            callback(emptyList(), "TAF error: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }.start()
}

fun resolveTafTime(day: Int, hour: Int, reference: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = reference * 1000L
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(
        Calendar.DAY_OF_MONTH,
        day.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    )

    var value = cal.timeInMillis / 1000L
    val diff = value - reference

    if (diff > 15L * 86400L) {
        cal.add(Calendar.MONTH, -1)
        value = cal.timeInMillis / 1000L
    } else if (diff < -15L * 86400L) {
        cal.add(Calendar.MONTH, 1)
        value = cal.timeInMillis / 1000L
    }
    return value
}

fun parseManualTaf(raw: String): List<HourCloudForecast> {
    val text = raw.uppercase(Locale.US).replace("\n", " ").trim()
    val validity = Regex("""\b(\d{2})(\d{2})/(\d{2})(\d{2})\b""")
        .find(text) ?: return emptyList()

    val now = System.currentTimeMillis() / 1000L
    val start = resolveTafTime(
        validity.groupValues[1].toInt(),
        validity.groupValues[2].toInt(),
        now
    )

    var end = resolveTafTime(
        validity.groupValues[3].toInt(),
        validity.groupValues[4].toInt(),
        start
    )
    if (end <= start) end += 86400L

    val markers = Regex("""\bFM(\d{2})(\d{2})(\d{2})\b""")
        .findAll(text).toList()

    val segments = mutableListOf<ManualSegment>()
    val firstEnd = markers.firstOrNull()?.range?.first ?: text.length
    val initialText = text.substring(validity.range.last + 1, firstEnd)

    segments.add(
        ManualSegment(
            start,
            cloudLayersFromText(initialText),
            Regex("""\b(SKC|CLR|NSC|NCD)\b""").containsMatchIn(initialText)
        )
    )

    markers.forEachIndexed { index, marker ->
        val day = marker.groupValues[1].toInt()
        val hour = marker.groupValues[2].toInt()
        val minute = marker.groupValues[3].toInt()
        val segmentStart = resolveTafTime(day, hour, now) + minute * 60L
        val nextEnd = markers.getOrNull(index + 1)?.range?.first ?: text.length
        val block = text.substring(marker.range.last + 1, nextEnd)

        segments.add(
            ManualSegment(
                segmentStart,
                cloudLayersFromText(block),
                Regex("""\b(SKC|CLR|NSC|NCD)\b""").containsMatchIn(block)
            )
        )
    }

    val sorted = segments.sortedBy { it.start }
    var inherited = emptyList<ForecastCloud>()
    var inheritedClear = false

    val effective = sorted.map { segment ->
        if (segment.layers.isNotEmpty() || segment.clear) {
            inherited = segment.layers
            inheritedClear = segment.clear
        }
        Pair(segment.start, if (inheritedClear) emptyList() else inherited)
    }

    val output = mutableListOf<HourCloudForecast>()

    for (hour in 0..7) {
        val target = now + hour * 3600L
        val chosen = effective.lastOrNull { it.first <= target }
        val available = target >= start && target < end && chosen != null
        val layers = if (available) chosen!!.second else emptyList()

        output.add(
            HourCloudForecast(
                utcFormat(target),
                layers,
                ceilingOf(layers),
                available
            )
        )
    }
    return output
}

/* PROFESSIONAL CLOUD PROFILE */

@Composable
fun CloudProfileChart(forecast: List<HourCloudForecast>) {
    val gridColor = Color(0xFF456078)
    val maxHeightFt = 12000f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF102638), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "CLOUD VERTICAL PROFILE",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Cloud base height • Feet AGL • UTC",
            color = Color(0xFF9EB4C8),
            fontSize = 11.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12k", "9k", "6k", "3k", "0").forEach {
                Text(it, color = Color(0xFFB9D9F2), fontSize = 10.sp)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val left = 8f
            val right = size.width - 8f
            val top = 8f
            val bottom = size.height - 8f
            val chartHeight = bottom - top
            val chartWidth = right - left

            for (level in 0..4) {
                val y = top + chartHeight * level / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            for (hour in 0..7) {
                val x = left + chartWidth * hour / 7f
                drawLine(
                    color = gridColor.copy(alpha = 0.55f),
                    start = Offset(x, top),
                    end = Offset(x, bottom),
                    strokeWidth = 1.dp.toPx()
                )
            }

            forecast.forEachIndexed { index, hour ->
                if (!hour.available) return@forEachIndexed

                val x = left + chartWidth * index / 7f

                hour.layers.forEachIndexed { layerIndex, layer ->
                    val base = layer.baseFt ?: return@forEachIndexed
                    val y = bottom -
                        (base.coerceIn(0, 12000) / maxHeightFt) * chartHeight

                    val color = when (layer.cover) {
                        "FEW" -> Color(0xFF70C8FF)
                        "SCT" -> Color(0xFF9AC7E8)
                        "BKN" -> Color(0xFFB2BEC9)
                        "OVC" -> Color(0xFF71869B)
                        "VV" -> Color(0xFFB48EDE)
                        else -> Color(0xFFB9D9F2)
                    }

                    val radius = 7.dp.toPx()
                    val cloudY = y - layerIndex * 13.dp.toPx()

                    drawCircle(
                        color = color,
                        radius = radius,
                        center = Offset(x - 8.dp.toPx(), cloudY)
                    )
                    drawCircle(
                        color = color,
                        radius = radius * 1.25f,
                        center = Offset(x, cloudY - 3.dp.toPx())
                    )
                    drawCircle(
                        color = color,
                        radius = radius * 0.9f,
                        center = Offset(x + 9.dp.toPx(), cloudY)
                    )
                    drawLine(
                        color = color,
                        start = Offset(x - 12.dp.toPx(), cloudY + 5.dp.toPx()),
                        end = Offset(x + 12.dp.toPx(), cloudY + 5.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            forecast.forEachIndexed { index, hour ->
                if (index % 2 == 0) {
                    Text(
                        hour.time,
                        color = Color(0xFFB9D9F2),
                        fontSize = 9.sp
                    )
                }
            }
        }

        Text(
            "Cloud symbols use reported cloud-base heights.",
            color = Color(0xFF9EB4C8),
            fontSize = 10.sp
        )
    }
}

@Composable
fun SkyMetarApp() {
    val handler = remember { Handler(Looper.getMainLooper()) }

    var mode by remember { mutableStateOf("LIVE") }
    var station by remember { mutableStateOf("GMMN") }
    var manualReport by remember { mutableStateOf("") }
    var manualTaf by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }

    var airport by remember { mutableStateOf("") }
    var rawReport by remember { mutableStateOf("") }
    var observation by remember { mutableStateOf("—") }

    var temperature by remember { mutableStateOf("—") }
    var dewpoint by remember { mutableStateOf("—") }
    var wind by remember { mutableStateOf("—") }
    var visibility by remember { mutableStateOf("—") }
    var pressure by remember { mutableStateOf("—") }

    var clouds by remember {
        mutableStateOf(CloudAnalysis(emptyList(), null, false))
    }

    var finalCategory by remember { mutableStateOf<Category?>(null) }
    var forecast by remember { mutableStateOf(emptyList<HourCloudForecast>()) }
    var forecastStatus by remember { mutableStateOf("TAF forecast not loaded") }
    var tafMode by remember { mutableStateOf("LIVE") }

    val background = Color(0xFF081522)
    val panel = Color(0xFF102638)
    val white = Color.White
    val muted = Color(0xFF9EB4C8)

    fun resetResults() {
        airport = ""
        rawReport = ""
        finalCategory = null
        clouds = CloudAnalysis(emptyList(), null, false)
    }

    fun displayReport(raw: String, name: String, time: String) {
        val report = raw.uppercase(Locale.US).trim()
        if (report.isEmpty()) {
            status = "Empty METAR report"
            return
        }

        val vis = parseVisibility(report)
        val cloudResult = analyzeClouds(report)
        val category = classifyMetar(vis, cloudResult)
        val temps = extractTemperature(report)

        airport = name
        rawReport = report
        observation = time
        temperature = temps.first
        dewpoint = temps.second
        wind = extractWind(report)
        pressure = extractPressure(report)
        visibility = vis?.let {
            String.format(Locale.US, "%.2f km", it)
        } ?: "Unavailable"

        clouds = cloudResult
        finalCategory = category
        status = if (category == null) {
            "METAR loaded - some data unavailable"
        } else {
            "METAR analysis completed"
        }
    }

    fun searchLiveMetar() {
        val code = station.trim().uppercase(Locale.US)

        if (!code.matches(Regex("[A-Z0-9]{4}"))) {
            status = "Enter a valid 4-character ICAO code"
            return
        }

        loading = true
        status = "Downloading METAR..."
        resetResults()

        Thread {
            var connection: HttpURLConnection? = null
            try {
                val address =
                    "https://aviationweather.gov/api/data/metar?ids=$code&format=json"

                connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "SKY-METAR-GLOBAL/1.0")

                val response = connection.responseCode
                if (response !in 200..299) {
                    handler.post {
                        status = "METAR HTTP error: $response"
                        loading = false
                    }
                    return@Thread
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val data = JSONArray(body)

                if (data.length() == 0) {
                    handler.post {
                        status = "No METAR found for $code"
                        loading = false
                    }
                    return@Thread
                }

                val item = data.getJSONObject(0)
                val name = item.optString("name", code)
                val raw = item.optString("rawOb", "")
                val timestamp = item.optLong("obsTime", 0L)

                val time = if (timestamp > 0L) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    sdf.format(Date(timestamp * 1000L))
                } else extractObservation(raw)

                handler.post {
                    displayReport(raw, name, time)
                    loading = false
                }
            } catch (e: Exception) {
                handler.post {
                    status = "Connection error: ${e.message}"
                    loading = false
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    fun analyzeManualMetar() {
        val raw = manualReport.trim()
        if (raw.isEmpty()) {
            status = "Please enter a METAR report"
            return
        }

        resetResults()
        val code = Regex("""(?:^|\s)([A-Z]{4})(?=\s)""")
            .find(raw.uppercase(Locale.US))
            ?.groupValues?.get(1) ?: "MANUAL REPORT"

        displayReport(raw, code, extractObservation(raw))
    }

    fun searchLiveTaf() {
        val code = station.trim().uppercase(Locale.US)

        if (!code.matches(Regex("[A-Z0-9]{4}"))) {
            forecastStatus = "Enter a valid 4-character ICAO code"
            return
        }

        loading = true
        forecastStatus = "Downloading TAF..."

        fetchTafForecast(code) { result, message ->
            handler.post {
                forecast = result
                forecastStatus = message
                loading = false
            }
        }
    }

    fun analyzeManualTaf() {
        val raw = manualTaf.trim()
        if (raw.isEmpty()) {
            forecastStatus = "Please enter a TAF report"
            return
        }

        val result = parseManualTaf(raw)
        forecast = result
        forecastStatus = if (result.isEmpty()) {
            "Could not read TAF validity period"
        } else {
            "Manual TAF analyzed (FM and initial groups)"
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "SKY METAR GLOBAL",
                    color = white,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "GLOBAL AVIATION WEATHER",
                    color = muted,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = station,
                    onValueChange = {
                        station = it.uppercase(Locale.US)
                            .filter { c -> c.isLetterOrDigit() }
                            .take(4)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ICAO AIRPORT CODE") },
                    placeholder = { Text("Example: GMMN") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii
                    )
                )

                Text(
                    "METAR — CURRENT OBSERVATION",
                    color = white,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { mode = "LIVE" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("LIVE METAR")
                    }
                    Button(
                        onClick = { mode = "MANUAL" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("MANUAL METAR")
                    }
                }

                if (mode == "LIVE") {
                    Button(
                        onClick = { searchLiveMetar() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SEARCH LIVE METAR")
                    }
                } else {
                    OutlinedTextField(
                        value = manualReport,
                        onValueChange = {
                            manualReport = it.uppercase(Locale.US)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp),
                        label = { Text("PASTE OR TYPE METAR") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        )
                    )

                    Button(
                        onClick = { analyzeManualMetar() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ANALYZE METAR")
                    }
                }

                Text(status, color = muted, fontSize = 12.sp)

                if (airport.isNotEmpty()) {
                    Text(
                        airport,
                        color = white,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text("OBSERVATION: $observation", color = muted)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = panel),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("FINAL METAR CATEGORY", color = muted)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(45.dp)
                                        .background(
                                            finalCategory?.color ?: Color.DarkGray,
                                            RoundedCornerShape(8.dp)
                                        )
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    finalCategory?.name ?: "UNAVAILABLE",
                                    color = white,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text("WEATHER DETAILS", color = white, fontWeight = FontWeight.Bold)
                    Text("TEMPERATURE: $temperature", color = white)
                    Text("DEW POINT: $dewpoint", color = white)
                    Text("WIND: $wind", color = white)
                    Text("VISIBILITY: $visibility", color = white)
                    Text("PRESSURE: $pressure", color = white)

                    HorizontalDivider(color = muted)

                    Text("CLOUD LAYERS", color = white, fontWeight = FontWeight.Bold)

                    if (clouds.layers.isEmpty()) {
                        Text("No cloud layers reported", color = muted)
                    } else {
                        clouds.layers.forEach { layer ->
                            Text(
                                "☁ ${layer.code} — ${layer.oktas} — " +
                                    (layer.height?.let { "$it ft" } ?: "Height unavailable") +
                                    if (layer.type.isNotEmpty()) " — ${layer.type}" else "",
                                color = Color(0xFFB9D9F2)
                            )
                        }
                    }

                    Text(
                        "CLOUD CEILING: " +
                            (clouds.ceiling?.let { "$it ft" } ?: "No ceiling reported"),
                        color = white
                    )

                    Text(
                        "RAW METAR: $rawReport",
                        color = Color(0xFFB9D9F2)
                    )
                }

                HorizontalDivider(color = muted)

                Text(
                    "☁ 8-HOUR CLOUD FORECAST",
                    color = white,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Forecast time is displayed in UTC",
                    color = muted,
                    fontSize = 11.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { tafMode = "LIVE" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("LIVE TAF")
                    }
                    Button(
                        onClick = { tafMode = "MANUAL" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("MANUAL TAF")
                    }
                }

                if (tafMode == "LIVE") {
                    Button(
                        onClick = { searchLiveTaf() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GET 8-HOUR TAF FORECAST")
                    }
                } else {
                    OutlinedTextField(
                        value = manualTaf,
                        onValueChange = {
                            manualTaf = it.uppercase(Locale.US)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        label = { Text("PASTE OR TYPE TAF") },
                        placeholder = {
                            Text("TAF GMMN 191000Z 1912/2012 02010KT 8000 SCT030")
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        )
                    )

                    Button(
                        onClick = { analyzeManualTaf() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ANALYZE MANUAL TAF")
                    }
                }

                Text(forecastStatus, color = muted, fontSize = 12.sp)

                forecast.forEach { hour ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = panel),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    hour.time,
                                    color = white,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (hour.available) "FORECAST" else "NO DATA",
                                    color = if (hour.available) {
                                        Color(0xFF72D59B)
                                    } else {
                                        Color(0xFFFFB74D)
                                    },
                                    fontSize = 11.sp
                                )
                            }

                            if (!hour.available) {
                                Text("No TAF data for this hour", color = muted)
                            } else if (hour.layers.isEmpty()) {
                                Text("No cloud layer reported", color = Color(0xFFB9D9F2))
                            } else {
                                hour.layers.forEach { layer ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("☁️", fontSize = 23.sp)
                                        Column {
                                            Text(
                                                layer.cover,
                                                color = white,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Base: " +
                                                    (layer.baseFt?.let { "$it ft" }
                                                        ?: "Unavailable") +
                                                    " | Type: ${layer.type}",
                                                color = Color(0xFFB9D9F2),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                "CEILING: " +
                                    (hour.ceilingFt?.let { "$it ft" } ?: "Not reported"),
                                color = white,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // رقم 2: استدعاء المنحنى في المكان الصحيح
                CloudProfileChart(forecast)

                Text(
                    "Cloud profile: cloud base heights are shown in feet AGL when supplied by TAF.",
                    color = muted,
                    fontSize = 11.sp
                )

                HorizontalDivider(color = muted)

                Text(
                    "DATA SOURCE: AviationWeather.gov",
                    color = muted,
                    fontSize = 11.sp
                )

                Text(
                    "For weather information only. Not for operational flight decisions.",
                    color = muted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

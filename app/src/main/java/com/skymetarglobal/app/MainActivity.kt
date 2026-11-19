package com.skymetarglobal.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SkyMetarApp() }
    }
}

data class CloudAnalysis(
    val layers: String,
    val ceilingFeet: Int?,
    val ceilingText: String
)

data class FlightColor(
    val name: String,
    val color: Color,
    val minVisibility: Double,
    val minCeiling: Int
)

val flightColors = listOf(
    FlightColor("BLUE", Color(0xFF168BFF), 8.0, 2500),
    FlightColor("GREEN", Color(0xFF19A65A), 5.0, 1500),
    FlightColor("GREEN / YELLOW", Color(0xFF9ACD32), 5.0, 1000),
    FlightColor("YELLOW", Color(0xFFFFD21F), 3.0, 500),
    FlightColor("YELLOW / RED", Color(0xFFFF9B21), 1.5, 350),
    FlightColor("RED", Color(0xFFE53935), 1.0, 220),
    FlightColor("GREY", Color(0xFF777777), 0.0, 0)
)

fun classifyConditions(
    visibilityKm: Double?,
    ceilingFeet: Int?
): FlightColor? {

    if (visibilityKm == null) return null

    val ceiling = ceilingFeet ?: Int.MAX_VALUE

    return flightColors.firstOrNull {
        visibilityKm >= it.minVisibility &&
        ceiling >= it.minCeiling
    } ?: flightColors.last()
}

fun parseVisibilityKm(raw: String): Double? {

    val metric = Regex(
        """(?<![A-Z0-9])(\d{4})(?![A-Z0-9])"""
    ).find(raw)

    if (metric != null) {
        val value = metric.groupValues[1].toInt()

        return when {
            value == 9999 -> 10.0
            else -> value / 1000.0
        }
    }

    val smToken = raw.split(Regex("\\s+"))
        .firstOrNull { it.endsWith("SM") }
        ?: return null

    var valueText = smToken.removeSuffix("SM")
    var multiplier = 1.0

    if (valueText.startsWith("P")) {
        valueText = valueText.drop(1)
    } else if (valueText.startsWith("M")) {
        valueText = valueText.drop(1)
    }

    fun fractionValue(text: String): Double? {
        val parts = text.split("/")
        if (parts.size != 2) return null

        val numerator = parts[0].toDoubleOrNull() ?: return null
        val denominator = parts[1].toDoubleOrNull() ?: return null

        if (denominator == 0.0) return null

        return numerator / denominator
    }

    val miles = if (valueText.contains(" ")) {
        val parts = valueText.split(" ", limit = 2)
        val whole = parts[0].toDoubleOrNull() ?: return null
        val fraction = fractionValue(parts[1]) ?: return null
        whole + fraction
    } else if (valueText.contains("/")) {
        fractionValue(valueText) ?: return null
    } else {
        valueText.toDoubleOrNull() ?: return null
    }

    return miles * 1.60934 * multiplier
}

fun analyzeClouds(raw: String): CloudAnalysis {

    val regex = Regex(
        """(?<![A-Z0-9])(?:FEW|SCT|BKN|OVC)\d{3}(?:CB|TCU)?|(?<![A-Z0-9])VV(?:\d{3}|///)"""
    )

    val matches = regex.findAll(raw)
        .map { it.value }
        .toList()

    if (matches.isEmpty()) {
        return CloudAnalysis(
            layers = "No cloud layers reported",
            ceilingFeet = null,
            ceilingText = "No ceiling reported"
        )
    }

    val descriptions = matches.map { token ->

        val cover = token.take(3)

        val heightText = if (cover == "VV") {
            token.substring(2, 5)
        } else {
            token.substring(3, 6)
        }

        val height = heightText.toIntOrNull()?.times(100)

        val oktas = when (cover) {
            "FEW" -> "1–2 OKTAS"
            "SCT" -> "3–4 OKTAS"
            "BKN" -> "5–7 OKTAS"
            "OVC" -> "8 OKTAS"
            "VV" -> "Vertical visibility"
            else -> ""
        }

        "$cover — $oktas — ${height?.let { "$it ft" } ?: "Unknown height"}"
    }

    val ceilingHeights = matches.mapNotNull { token ->

        val cover = token.take(3)

        if (cover == "BKN" || cover == "OVC" || cover == "VV") {

            val heightText = if (cover == "VV") {
                token.substring(2, 5)
            } else {
                token.substring(3, 6)
            }

            heightText.toIntOrNull()?.times(100)
        } else {
            null
        }
    }

    val ceiling = ceilingHeights.minOrNull()

    return CloudAnalysis(
        layers = descriptions.joinToString("\n"),
        ceilingFeet = ceiling,
        ceilingText = ceiling?.let { "$it ft" }
            ?: "No ceiling reported"
    )
}

@Composable
fun SkyMetarApp() {

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    var station by remember { mutableStateOf("GMMN") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Enter ICAO code") }

    var airport by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }
    var observation by remember { mutableStateOf("") }

    var temperature by remember { mutableStateOf("—") }
    var dewpoint by remember { mutableStateOf("—") }
    var wind by remember { mutableStateOf("—") }

    var visibility by remember { mutableStateOf("—") }
    var visibilityKm by remember { mutableStateOf<Double?>(null) }

    var cloudLayers by remember { mutableStateOf("—") }
    var cloudCeiling by remember { mutableStateOf("—") }
    var ceilingFeet by remember { mutableStateOf<Int?>(null) }

    var finalColor by remember { mutableStateOf<FlightColor?>(null) }
    var visibilityColor by remember { mutableStateOf<FlightColor?>(null) }
    var ceilingColor by remember { mutableStateOf<FlightColor?>(null) }

    val bg = Color(0xFF081522)
    val panel = Color(0xFF102638)
    val white = Color.White
    val muted = Color(0xFF9EB4C8)

    fun searchMetar() {

        val code = station.trim().uppercase(Locale.US)

        if (!code.matches(Regex("[A-Z0-9]{4}"))) {
            status = "Enter a valid 4-character ICAO code"
            return
        }

        loading = true
        status = "Connecting..."

        airport = ""
        report = ""
        finalColor = null
        visibilityColor = null
        ceilingColor = null

        Thread {

            var connection: HttpURLConnection? = null

            try {

                val address =
                    "https://aviationweather.gov/api/data/metar?ids=$code&format=json"

                connection =
                    URL(address).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.setRequestProperty(
                    "User-Agent",
                    "SKY-METAR-GLOBAL/1.0"
                )

                val responseCode = connection.responseCode

                if (responseCode == 204) {

                    mainHandler.post {
                        status = "No METAR found for $code"
                        loading = false
                    }

                } else if (responseCode in 200..299) {

                    val body = connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                    val data = JSONArray(body)

                    if (data.length() == 0) {

                        mainHandler.post {
                            status = "No METAR available"
                            loading = false
                        }

                    } else {

                        val item = data.getJSONObject(0)

                        val name = item.optString("name", code)
                        val raw = item.optString("rawOb", "")

                        val temp = item.optString("temp", "—")
                        val dew = item.optString("dewp", "—")

                        val visKm = parseVisibilityKm(raw)
                        val visText = visKm?.let {
                            String.format(Locale.US, "%.1f km", it)
                        } ?: "Unavailable"

                        val direction = item.optString("wdir", "—")
                        val speed = item.optString("wspd", "—")
                        val gust = item.optString("wgst", "")

                        val windText =
                            "$direction° / $speed kt" +
                            if (gust.isNotEmpty() && gust != "null") {
                                " (gust $gust kt)"
                            } else {
                                ""
                            }

                        val time = item.optLong("obsTime", 0L)

                        val timeText = if (time > 0L) {
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm 'UTC'",
                                Locale.US
                            ).format(Date(time * 1000L))
                        } else {
                            "Time unavailable"
                        }

                        val clouds = analyzeClouds(raw)

                        val visClass = classifyConditions(
                            visKm,
                            null
                        )

                        val ceilClass = if (clouds.ceilingFeet != null) {
                            classifyConditions(
                                10.0,
                                clouds.ceilingFeet
                            )
                        } else {
                            null
                        }

                        val overall = classifyConditions(
                            visKm,
                            clouds.ceilingFeet
                        )

                        mainHandler.post {

                            airport = name
                            report = raw
                            observation = timeText

                            temperature = "$temp °C"
                            dewpoint = "$dew °C"
                            wind = windText

                            visibility = visText
                            visibilityKm = visKm

                            cloudLayers = clouds.layers
                            cloudCeiling = clouds.ceilingText
                            ceilingFeet = clouds.ceilingFeet

                            visibilityColor = visClass
                            ceilingColor = ceilClass
                            finalColor = overall

                            status = "METAR received successfully"
                            loading = false
                        }
                    }

                } else {

                    mainHandler.post {
                        status = "Server error: HTTP $responseCode"
                        loading = false
                    }
                }

            } catch (e: Exception) {

                mainHandler.post {
                    status = "Connection error: ${e.message}"
                    loading = false
                }

            } finally {
                connection?.disconnect()
            }

        }.start()
    }

    @Composable
    fun ColorResult(
        heading: String,
        result: FlightColor?
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(panel, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                heading,
                color = muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment =
                    androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(
                            result?.color ?: Color.DarkGray,
                            RoundedCornerShape(5.dp)
                        )
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    result?.name ?: "UNAVAILABLE",
                    color = white,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        }
    }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bg
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

                Button(
                    onClick = { searchMetar() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (loading) "LOADING..." else "SEARCH METAR"
                    )
                }

                Text(status, color = muted, fontSize = 13.sp)

                if (airport.isNotEmpty()) {

                    Text(
                        airport,
                        color = white,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "OBSERVATION: $observation",
                        color = muted,
                        fontSize = 12.sp
                    )

                    Text("TEMPERATURE: $temperature", color = white)
                    Text("DEW POINT: $dewpoint", color = white)
                    Text("WIND: $wind", color = white)
                    Text("VISIBILITY: $visibility", color = white)

                    ColorResult(
                        "VISIBILITY CATEGORY",
                        visibilityColor
                    )

                    HorizontalDivider(color = muted)

                    Text(
                        "CLOUD LAYERS",
                        color = white,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        cloudLayers,
                        color = Color(0xFFB9D9F2),
                        fontSize = 14.sp
                    )

                    Text(
                        "CLOUD CEILING: $cloudCeiling",
                        color = white
                    )

                    ColorResult(
                        "CLOUD CEILING CATEGORY",
                        ceilingColor
                    )

                    ColorResult(
                        "FINAL FLIGHT CATEGORY",
                        finalColor
                    )

                    Text(
                        "RAW METAR REPORT",
                        color = white,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        report,
                        color = Color(0xFFB9D9F2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                panel,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    )
                }

                Text(
                    "DATA SOURCE: AviationWeather.gov",
                    color = muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

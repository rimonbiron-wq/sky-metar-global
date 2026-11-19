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
import androidx.compose.ui.Alignment
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

data class CloudLayer(
    val code: String,
    val oktas: String,
    val height: Int?
)

data class CloudAnalysis(
    val layers: List<CloudLayer>,
    val ceilingFeet: Int?,
    val ceilingUnknown: Boolean
)

data class MetarCategory(
    val name: String,
    val color: Color,
    val minVisibility: Double,
    val minCeiling: Int
)

val categories = listOf(
    MetarCategory("BLUE", Color(0xFF168BFF), 8.0, 2500),
    MetarCategory("GREEN", Color(0xFF19A65A), 5.0, 1500),
    MetarCategory("GREEN / YELLOW", Color(0xFF9ACD32), 5.0, 1000),
    MetarCategory("YELLOW", Color(0xFFFFD21F), 3.0, 500),
    MetarCategory("YELLOW / RED", Color(0xFFFF9B21), 1.5, 350),
    MetarCategory("RED", Color(0xFFE53935), 1.0, 220),
    MetarCategory("GREY", Color(0xFF777777), 0.0, 0)
)

fun parseVisibility(raw: String): Double? {
    val tokens = raw.trim().split(Regex("\\s+"))
    val windIndex = tokens.indexOfFirst {
        it.matches(Regex("(?:\\d{3}|VRB)\\d{2,3}(?:G\\d{2,3})?KT"))
    }

    if (windIndex < 0 || windIndex + 1 >= tokens.size) {
        return null
    }

    val token = tokens[windIndex + 1]

    if (token == "CAVOK") return 10.0

    if (token.matches(Regex("\\d{4}"))) {
        val meters = token.toInt()
        return if (meters == 9999) 10.0 else meters / 1000.0
    }

    var smToken = token

    if (
        windIndex + 2 < tokens.size &&
        tokens[windIndex + 2].endsWith("SM") &&
        token.matches(Regex("\\d+"))
    ) {
        smToken = "$token ${tokens[windIndex + 2]}"
    }

    if (!smToken.endsWith("SM")) return null

    var value = smToken.removeSuffix("SM")
        .replace(" ", "")

    val greaterThan = value.startsWith("P")
    val lessThan = value.startsWith("M")

    if (greaterThan || lessThan) {
        value = value.drop(1)
    }

    val miles = if (value.contains("/")) {
        val parts = value.split("/")
        if (parts.size != 2) return null

        val numerator = parts[0].toDoubleOrNull() ?: return null
        val denominator = parts[1].toDoubleOrNull() ?: return null

        if (denominator == 0.0) return null

        numerator / denominator
    } else {
        value.toDoubleOrNull() ?: return null
    }

    return miles * 1.60934
}

fun analyzeClouds(raw: String): CloudAnalysis {
    val regex = Regex(
        """(?<![A-Z0-9])(?:FEW|SCT|BKN|OVC)\d{3}(?:CB|TCU)?|(?<![A-Z0-9])VV(?:\d{3}|///)"""
    )

    val matches = regex.findAll(raw).map {
        it.value
    }.toList()

    val layers = matches.map { token ->
        val code = token.take(3)

        val heightText = if (code == "VV") {
            token.substring(2, 5)
        } else {
            token.substring(3, 6)
        }

        val height = heightText.toIntOrNull()?.times(100)

        val oktas = when (code) {
            "FEW" -> "1–2 OKTAS"
            "SCT" -> "3–4 OKTAS"
            "BKN" -> "5–7 OKTAS"
            "OVC" -> "8 OKTAS"
            "VV" -> "VERTICAL VISIBILITY"
            else -> ""
        }

        CloudLayer(code, oktas, height)
    }

    val ceilingLayers = layers.filter {
        it.code == "BKN" ||
        it.code == "OVC" ||
        it.code == "VV"
    }

    val ceilingUnknown = ceilingLayers.any {
        it.height == null
    }

    val ceiling = ceilingLayers
        .mapNotNull { it.height }
        .minOrNull()

    return CloudAnalysis(
        layers = layers,
        ceilingFeet = ceiling,
        ceilingUnknown = ceilingUnknown
    )
}

fun classifyMetar(
    visibilityKm: Double?,
    ceilingFeet: Int?
): MetarCategory? {

    if (visibilityKm == null) return null

    val ceiling = ceilingFeet ?: Int.MAX_VALUE

    return categories.firstOrNull { category ->
        visibilityKm >= category.minVisibility &&
        ceiling >= category.minCeiling
    } ?: categories.last()
}

@Composable
fun SkyMetarApp() {

    val handler = remember {
        Handler(Looper.getMainLooper())
    }

    var station by remember { mutableStateOf("GMMN") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Enter ICAO code") }

    var airport by remember { mutableStateOf("") }
    var rawReport by remember { mutableStateOf("") }
    var observation by remember { mutableStateOf("") }

    var temperature by remember { mutableStateOf("—") }
    var dewpoint by remember { mutableStateOf("—") }
    var wind by remember { mutableStateOf("—") }
    var visibility by remember { mutableStateOf("—") }

    var cloudAnalysis by remember {
        mutableStateOf(CloudAnalysis(emptyList(), null, false))
    }

    var finalCategory by remember {
        mutableStateOf<MetarCategory?>(null)
    }

    val background = Color(0xFF081522)
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
        status = "Connecting to aviation weather service..."

        airport = ""
        rawReport = ""
        finalCategory = null
        cloudAnalysis = CloudAnalysis(emptyList(), null, false)

        Thread {
            var connection: HttpURLConnection? = null

            try {
                val address =
                    "https://aviationweather.gov/api/data/metar?ids=$code&format=json"

                connection = URL(address).openConnection()
                    as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.setRequestProperty(
                    "User-Agent",
                    "SKY-METAR-GLOBAL/1.0"
                )

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    handler.post {
                        status = "Server response: HTTP $responseCode"
                        loading = false
                    }
                    return@Thread
                }

                val body = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val data = JSONArray(body)

                if (data.length() == 0) {
                    handler.post {
                        status = "No METAR available for $code"
                        loading = false
                    }
                    return@Thread
                }

                val item = data.getJSONObject(0)

                val name = item.optString("name", code)
                val raw = item.optString("rawOb", "")

                val temp = item.optString("temp", "—")
                val dew = item.optString("dewp", "—")

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

                val vis = parseVisibility(raw)

                val clouds = analyzeClouds(raw)

                val category = classifyMetar(
                    visibilityKm = vis,
                    ceilingFeet = clouds.ceilingFeet
                )

                val time = item.optLong("obsTime", 0L)

                val timeText = if (time > 0L) {
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm 'UTC'",
                        Locale.US
                    ).format(Date(time * 1000L))
                } else {
                    "Time unavailable"
                }

                handler.post {
                    airport = name
                    rawReport = raw
                    observation = timeText

                    temperature = "$temp °C"
                    dewpoint = "$dew °C"
                    wind = windText

                    visibility = vis?.let {
                        String.format(Locale.US, "%.1f km", it)
                    } ?: "Unavailable"

                    cloudAnalysis = clouds
                    finalCategory = category

                    status = if (category == null) {
                        "METAR received, visibility could not be read"
                    } else {
                        "METAR received successfully"
                    }

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

                Button(
                    onClick = { searchMetar() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "LOADING..." else "SEARCH METAR")
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(panel, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            "FINAL METAR CATEGORY",
                            color = muted,
                            fontSize = 12.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        finalCategory?.color
                                            ?: Color.DarkGray,
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

                        Text(
                            "One final color calculated from visibility and cloud ceiling",
                            color = muted,
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        "WEATHER DETAILS",
                        color = white,
                        fontWeight = FontWeight.Bold
                    )

                    Text("TEMPERATURE: $temperature", color = white)
                    Text("DEW POINT: $dewpoint", color = white)
                    Text("WIND: $wind", color = white)
                    Text("VISIBILITY: $visibility", color = white)

                    HorizontalDivider(color = muted)

                    Text(
                        "CLOUD COVERAGE",
                        color = white,
                        fontWeight = FontWeight.Bold
                    )

                    if (cloudAnalysis.layers.isEmpty()) {
                        Text(
                            "No cloud layers reported",
                            color = muted
                        )
                    } else {
                        cloudAnalysis.layers.forEach { layer ->
                            Text(
                                "${layer.code} — ${layer.oktas} — " +
                                (layer.height?.let { "$it ft" }
                                    ?: "Height unavailable"),
                                color = Color(0xFFB9D9F2)
                            )
                        }
                    }

                    Text(
                        "CLOUD CEILING: " +
                        (cloudAnalysis.ceilingFeet?.let { "$it ft" }
                            ?: "No ceiling reported"),
                        color = white
                    )

                    if (cloudAnalysis.ceilingUnknown) {
                        Text(
                            "WARNING: Cloud ceiling height is incomplete",
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp
                        )
                    }

                    HorizontalDivider(color = muted)

                    Text(
                        "RAW METAR",
                        color = white,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        rawReport,
                        color = Color(0xFFB9D9F2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(panel, RoundedCornerShape(12.dp))
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

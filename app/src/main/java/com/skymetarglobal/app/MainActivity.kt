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
    val ceiling: Int?,
    val unknownCeiling: Boolean
)

data class Category(
    val name: String,
    val color: Color,
    val visibility: Double,
    val ceiling: Int
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

    var text = value
    var sign = 1.0

    if (text.startsWith("M")) {
        sign = -1.0
        text = text.drop(1)
    }

    return text.toDoubleOrNull()?.times(sign)
}

fun parseVisibility(raw: String): Double? {

    val tokens = raw.uppercase(Locale.US)
        .trim()
        .split(Regex("\\s+"))

    if (tokens.contains("CAVOK")) return 10.0

    val windIndex = tokens.indexOfFirst {
        it.matches(
            Regex("""(?:\d{3}|VRB)\d{2,3}(?:G\d{2,3})?KT""")
        )
    }

    val start = if (windIndex >= 0) windIndex + 1 else 0

    for (i in start until tokens.size) {

        val token = tokens[i]

        if (token.matches(Regex("\\d{4}"))) {
            val meters = token.toInt()
            return if (meters == 9999) 10.0 else meters / 1000.0
        }

        var sm = token

        if (
            i + 1 < tokens.size &&
            tokens[i + 1].endsWith("SM") &&
            token.matches(Regex("\\d+"))
        ) {
            sm = "$token ${tokens[i + 1]}"
        }

        if (!sm.endsWith("SM")) continue

        var value = sm.removeSuffix("SM").replace(" ", "")

        if (value.startsWith("P") || value.startsWith("M")) {
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
            value.toDoubleOrNull() ?: continue
        }

        return miles * 1.60934
    }

    return null
}

fun analyzeClouds(raw: String): CloudAnalysis {

    val regex = Regex(
        """(?<![A-Z0-9])(?:FEW|SCT|BKN|OVC)(?:\d{3}|///)(?:CB|TCU)?|(?<![A-Z0-9])VV(?:\d{3}|///)"""
    )

    val matches = regex.findAll(
        raw.uppercase(Locale.US)
    ).map { it.value }.toList()

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

    val unknown = ceilingLayers.any {
        it.height == null
    }

    val ceiling = ceilingLayers
        .mapNotNull { it.height }
        .minOrNull()

    return CloudAnalysis(
        layers = layers,
        ceiling = ceiling,
        unknownCeiling = unknown
    )
}

fun classifyMetar(
    visibility: Double?,
    clouds: CloudAnalysis
): Category? {

    if (visibility == null || clouds.unknownCeiling) {
        return null
    }

    val ceiling = clouds.ceiling ?: Int.MAX_VALUE

    return categories.firstOrNull {
        visibility >= it.visibility &&
        ceiling >= it.ceiling
    } ?: categories.last()
}

fun extractWind(raw: String): String {
    val match = Regex(
        """(?:\d{3}|VRB)\d{2,3}(?:G\d{2,3})?KT"""
    ).find(raw.uppercase(Locale.US))

    return match?.value ?: "Unavailable"
}

fun extractTemperature(raw: String): Pair<String, String> {

    val match = Regex(
        """(?<![A-Z0-9])(M?\d{2})/(M?\d{2})(?![A-Z0-9])"""
    ).find(raw.uppercase(Locale.US)) ?: return Pair("—", "—")

    val temperature = parseNumber(match.groupValues[1])
    val dewpoint = parseNumber(match.groupValues[2])

    return Pair(
        temperature?.let { "$it °C" } ?: "—",
        dewpoint?.let { "$it °C" } ?: "—"
    )
}

fun extractPressure(raw: String): String {

    val qnh = Regex("""(?<![A-Z0-9])Q\d{4}""")
        .find(raw.uppercase(Locale.US))

    if (qnh != null) {
        return "${qnh.value} hPa"
    }

    val altimeter = Regex("""(?<![A-Z0-9])A\d{4}""")
        .find(raw.uppercase(Locale.US))

    return altimeter?.value ?: "Unavailable"
}

fun extractObservation(raw: String): String {
    val match = Regex("""(?<!\d)\d{6}Z""")
        .find(raw.uppercase(Locale.US))

    return match?.value ?: "Unavailable"
}

@Composable
fun SkyMetarApp() {

    val handler = remember {
        Handler(Looper.getMainLooper())
    }

    var mode by remember { mutableStateOf("LIVE") }

    var station by remember { mutableStateOf("GMMN") }
    var manualReport by remember { mutableStateOf("") }

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
        mutableStateOf(
            CloudAnalysis(emptyList(), null, false)
        )
    }

    var finalCategory by remember {
        mutableStateOf<Category?>(null)
    }

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

    fun displayReport(
        raw: String,
        name: String,
        time: String
    ) {

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
            "Analysis completed successfully"
        }
    }

    fun searchLive() {

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

                if (responseCode !in 200..299) {
                    handler.post {
                        status = "HTTP error: $responseCode"
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
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm 'UTC'",
                        Locale.US
                    ).format(Date(timestamp * 1000L))
                } else {
                    extractObservation(raw)
                }

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

    fun analyzeManual() {

        val raw = manualReport.trim()

        if (raw.isEmpty()) {
            status = "Please enter a METAR report"
            return
        }

        resetResults()

        val code = Regex(
            """(?:^|\s)([A-Z]{4})(?=\s)"""
        ).find(raw.uppercase(Locale.US))
            ?.groupValues?.get(1)
            ?: "MANUAL REPORT"

        displayReport(
            raw = raw,
            name = code,
            time = extractObservation(raw)
        )
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        onClick = {
                            mode = "LIVE"
                            status = "Enter ICAO airport code"
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == "LIVE") {
                                Color(0xFF176CA5)
                            } else {
                                panel
                            }
                        )
                    ) {
                        Text("LIVE METAR")
                    }

                    Button(
                        onClick = {
                            mode = "MANUAL"
                            status = "Enter METAR report"
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == "MANUAL") {
                                Color(0xFF23734F)
                            } else {
                                panel
                            }
                        )
                    ) {
                        Text("MANUAL METAR")
                    }
                }

                if (mode == "LIVE") {

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
                            capitalization =
                                KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        )
                    )

                    Button(
                        onClick = { searchLive() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (loading) "LOADING..."
                            else "SEARCH LIVE METAR"
                        )
                    }

                } else {

                    OutlinedTextField(
                        value = manualReport,
                        onValueChange = {
                            manualReport = it.uppercase(Locale.US)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        label = { Text("PASTE OR TYPE METAR") },
                        placeholder = {
                            Text(
                                "Example:\nGMMN 191200Z 02010KT 8000 FEW020 SCT035 BKN080 18/12 Q1015"
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization =
                                KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        )
                    )

                    Button(
                        onClick = { analyzeManual() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ANALYZE METAR")
                    }

                    Text(
                        "Manual analysis works without internet.",
                        color = muted,
                        fontSize = 12.sp
                    )
                }

                Text(
                    status,
                    color = muted,
                    fontSize = 13.sp
                )

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
                            .background(
                                panel,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(16.dp),

                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        Text(
                            "FINAL METAR CATEGORY",
                            color = muted,
                            fontSize = 12.sp
                        )

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        finalCategory?.color
                                            ?: Color.DarkGray,
                                        RoundedCornerShape(8.dp)
                                    )
                            )

                            Spacer(Modifier.width(12.dp))

                            Column {
                                Text(
                                    finalCategory?.name
                                        ?: "UNAVAILABLE",
                                    color = white,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "ONE COLOR FOR THE COMPLETE METAR",
                                    color = muted,
                                    fontSize = 9.sp
                                )
                            }
                        }
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
                    Text("PRESSURE: $pressure", color = white)

                    HorizontalDivider(color = muted)

                    Text(
                        "CLOUD COVERAGE",
                        color = white,
                        fontWeight = FontWeight.Bold
                    )

                    if (clouds.layers.isEmpty()) {
                        Text(
                            "No cloud layers reported",
                            color = muted
                        )
                    } else {

                        clouds.layers.forEach { layer ->

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
                        (clouds.ceiling?.let { "$it ft" }
                            ?: "No ceiling reported"),
                        color = white
                    )

                    if (clouds.unknownCeiling) {
                        Text(
                            "Cloud ceiling height incomplete",
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


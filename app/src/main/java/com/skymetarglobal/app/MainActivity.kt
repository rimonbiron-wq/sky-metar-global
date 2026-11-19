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
        setContent {
            SkyMetarApp()
        }
    }
}

data class CloudAnalysis(
    val layers: String,
    val ceiling: String
)

fun analyzeClouds(raw: String): CloudAnalysis {

    val regex = Regex(
        """(?<![A-Z0-9])(?:FEW|SCT|BKN|OVC)\d{3}(?:CB|TCU)?|(?<![A-Z0-9])VV(?:\d{3}|///)"""
    )

    val matches = regex.findAll(raw).map {
        it.value
    }.toList()

    if (matches.isEmpty()) {
        val message = if (
            raw.contains("CAVOK") ||
            raw.contains("SKC") ||
            raw.contains("CLR") ||
            raw.contains("NSC") ||
            raw.contains("NCD")
        ) {
            "No significant cloud reported"
        } else {
            "No cloud layers reported"
        }

        return CloudAnalysis(
            layers = message,
            ceiling = "No ceiling reported"
        )
    }

    val descriptions = matches.map { token ->

        val cover = token.take(3)

        val heightText = if (cover == "VV") {
            token.substring(2, 5)
        } else {
            token.substring(3, 6)
        }

        val heightFeet = heightText.toIntOrNull()?.times(100)

        val oktas = when (cover) {
            "FEW" -> "1–2 OKTAS"
            "SCT" -> "3–4 OKTAS"
            "BKN" -> "5–7 OKTAS"
            "OVC" -> "8 OKTAS"
            "VV" -> "Vertical visibility"
            else -> ""
        }

        val altitude = heightFeet?.let {
            "$it ft"
        } ?: "Height unavailable"

        "$cover — $oktas — $altitude"
    }

    val ceilingHeights = matches.mapNotNull { token ->

        val cover = token.take(3)

        if (
            cover == "BKN" ||
            cover == "OVC" ||
            cover == "VV"
        ) {
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

    val ceiling = ceilingHeights.minOrNull()?.let {
        "$it ft"
    } ?: "No ceiling reported"

    return CloudAnalysis(
        layers = descriptions.joinToString("\n"),
        ceiling = ceiling
    )
}

@Composable
fun SkyMetarApp() {

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    var station by remember {
        mutableStateOf("GMMN")
    }

    var language by remember {
        mutableStateOf("English")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var status by remember {
        mutableStateOf("Enter an ICAO airport code")
    }

    var airport by remember {
        mutableStateOf("")
    }

    var report by remember {
        mutableStateOf("")
    }

    var observation by remember {
        mutableStateOf("")
    }

    var temperature by remember {
        mutableStateOf("—")
    }

    var dewpoint by remember {
        mutableStateOf("—")
    }

    var wind by remember {
        mutableStateOf("—")
    }

    var visibility by remember {
        mutableStateOf("—")
    }

    var cloudLayers by remember {
        mutableStateOf("—")
    }

    var cloudCeiling by remember {
        mutableStateOf("—")
    }

    val bg = Color(0xFF081522)
    val panel = Color(0xFF102638)
    val white = Color.White
    val muted = Color(0xFF9EB4C8)

    val searchLabel = when (language) {
        "العربية" -> "البحث عن مطار"
        "Français" -> "Rechercher un aéroport"
        else -> "AIRPORT SEARCH"
    }

    val buttonLabel = when (language) {
        "العربية" -> "بحث عن METAR"
        "Français" -> "RECHERCHER METAR"
        else -> "SEARCH METAR"
    }

    val rawLabel = when (language) {
        "العربية" -> "تقرير METAR الخام"
        "Français" -> "METAR brut"
        else -> "RAW METAR REPORT"
    }

    fun searchMetar() {

        val code = station.trim().uppercase(Locale.US)

        if (!code.matches(Regex("[A-Z0-9]{4}"))) {
            status = "Enter a valid 4-character ICAO code"
            return
        }

        loading = true
        status = "Connecting to aviation weather service..."

        report = ""
        airport = ""
        observation = ""
        temperature = "—"
        dewpoint = "—"
        wind = "—"
        visibility = "—"
        cloudLayers = "—"
        cloudCeiling = "—"

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
                        status = "No recent METAR found for $code"
                        loading = false
                    }

                } else if (responseCode in 200..299) {

                    val body = connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                    val data = JSONArray(body)

                    if (data.length() == 0) {

                        mainHandler.post {
                            status = "No METAR available for $code"
                            loading = false
                        }

                    } else {

                        val item = data.getJSONObject(0)

                        val name = item.optString("name", code)

                        val raw = item.optString(
                            "rawOb",
                            "No raw report"
                        )

                        val temp = item.optString("temp", "—")
                        val dew = item.optString("dewp", "—")
                        val vis = item.optString("visib", "—")

                        val direction = item.optString("wdir", "—")
                        val speed = item.optString("wspd", "—")
                        val gust = item.optString("wgst", "")

                        val windText =
                            "$direction° / $speed kt" +
                            if (
                                gust.isNotEmpty() &&
                                gust != "null"
                            ) {
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

                        val cloudAnalysis = analyzeClouds(raw)

                        mainHandler.post {

                            airport = name
                            report = raw
                            observation = timeText

                            temperature = "$temp °C"
                            dewpoint = "$dew °C"
                            wind = windText
                            visibility = vis

                            cloudLayers = cloudAnalysis.layers
                            cloudCeiling = cloudAnalysis.ceiling

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
                    status =
                        "Connection error: ${e.message ?: "Unknown error"}"

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    listOf(
                        "العربية",
                        "English",
                        "Français"
                    ).forEach { lang ->

                        Button(
                            onClick = {
                                language = lang
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {

                            Text(
                                lang,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Text(
                    searchLabel,
                    color = white,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = station,

                    onValueChange = {
                        station = it.uppercase(Locale.US)
                            .filter { c ->
                                c.isLetterOrDigit()
                            }
                            .take(4)
                    },

                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,

                    label = {
                        Text("ICAO CODE")
                    },

                    placeholder = {
                        Text("Example: GMMN")
                    },

                    keyboardOptions = KeyboardOptions(
                        capitalization =
                            KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii
                    )
                )

                Button(
                    onClick = {
                        searchMetar()
                    },

                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        if (loading) {
                            "LOADING..."
                        } else {
                            buttonLabel
                        }
                    )
                }

                Text(
                    status,

                    color = if (
                        status.startsWith("Connection error") ||
                        status.startsWith("Server error")
                    ) {
                        Color(0xFFFF7777)
                    } else {
                        muted
                    },

                    fontSize = 13.sp
                )

                if (airport.isNotEmpty()) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                panel,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(16.dp),

                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            airport,
                            color = white,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Text(
                            "Observation: $observation",
                            color = muted,
                            fontSize = 12.sp
                        )

                        HorizontalDivider(color = muted)

                        Text(
                            "TEMPERATURE: $temperature",
                            color = white
                        )

                        Text(
                            "DEW POINT: $dewpoint",
                            color = white
                        )

                        Text(
                            "WIND: $wind",
                            color = white
                        )

                        Text(
                            "VISIBILITY: $visibility",
                            color = white
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
                            color = white,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        rawLabel,
                        color = white,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        report,
                        color = Color(0xFFB9D9F2),
                        fontSize = 14.sp,

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

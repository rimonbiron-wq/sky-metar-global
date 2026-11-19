package com.skymetarglobal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(8, 17, 35)
        window.navigationBarColor = android.graphics.Color.rgb(8, 17, 35)

        setContent {
            SkyMetarApp()
        }
    }
}

private val Background = Color(0xFF081323)
private val Panel = Color(0xFF13243A)
private val Accent = Color(0xFF35B9E9)
private val White = Color(0xFFF4F7FC)
private val Muted = Color(0xFF9EADC0)

data class MetarResult(
    val station: String,
    val raw: String,
    val visibility: Double?,
    val ceiling: Int?,
    val category: String,
    val color: Color
)

data class CloudLayer(
    val amount: String,
    val altitude: Int,
    val type: String = ""
)

data class Airport(
    val name: String,
    val icao: String,
    val city: String
)

@Composable
fun SkyMetarApp() {
    var selectedTab by remember { mutableStateOf("METAR & TAF") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background,
            surface = Panel,
            primary = Accent,
            onBackground = White,
            onSurface = White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            Header()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabButton(
                    text = "METAR & TAF",
                    selected = selectedTab == "METAR & TAF",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedTab = "METAR & TAF"
                }

                TabButton(
                    text = "CLOUDS",
                    selected = selectedTab == "CLOUDS",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedTab = "CLOUDS"
                }
            }

            when (selectedTab) {
                "METAR & TAF" -> MetarTafScreen()
                "CLOUDS" -> CloudsScreen()
            }
        }
    }
}

@Composable
fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1D32))
            .padding(18.dp)
    ) {
        Text(
            text = "SKY METAR GLOBAL",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = White
        )

        Text(
            text = "GLOBAL AVIATION WEATHER",
            fontSize = 11.sp,
            color = Accent,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun TabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Panel,
            contentColor = if (selected) Background else White
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
@Composable
fun MetarTafScreen() {
    var station by remember { mutableStateOf("GMMG") }
    var manualMetar by remember { mutableStateOf("") }
    var manualTaf by remember { mutableStateOf("") }
    var metarResult by remember { mutableStateOf<MetarResult?>(null) }
    var liveMetar by remember { mutableStateOf("") }
    var liveTaf by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("METAR WEATHER", color = Accent, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = station,
            onValueChange = { station = it.uppercase(Locale.US) },
            label = { Text("ICAO Station (مثال GMMG)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                loading = true
                message = "جاري البحث..."
                fetchWeather(station.trim()) { metar, taf, error ->
                    liveMetar = metar
                    liveTaf = taf
                    metarResult = if (metar.isNotBlank()) parseMetar(metar) else null
                    message = error
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text(if (loading) "Loading..." else "LIVE METAR + TAF")
        }

        if (message.isNotBlank()) {
            Text(message, color = Muted)
        }

        if (liveMetar.isNotBlank()) {
            WeatherCard("LIVE METAR", liveMetar)
        }

        metarResult?.let {
            CategoryCard(it)
        }

        HorizontalDivider(color = Muted.copy(alpha = 0.3f))

        Text("MANUAL METAR", color = Accent, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = manualMetar,
            onValueChange = { manualMetar = it },
            label = { Text("Paste METAR here") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = {
                metarResult = parseMetar(manualMetar)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ANALYZE METAR")
        }

        metarResult?.let {
            CategoryCard(it)
        }

        HorizontalDivider(color = Muted.copy(alpha = 0.3f))

        Text("LIVE TAF", color = Accent, fontWeight = FontWeight.Bold)

        if (liveTaf.isNotBlank()) {
            WeatherCard("TAF FORECAST", liveTaf)
        } else {
            Text("Search an ICAO station to load its TAF.", color = Muted)
        }

        Text("MANUAL TAF", color = Accent, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = manualTaf,
            onValueChange = { manualTaf = it },
            label = { Text("Paste TAF here") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )

        Button(
            onClick = {
                liveTaf = manualTaf
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("DISPLAY MANUAL TAF")
        }
    }
}

@Composable
fun WeatherCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(value, color = White, fontSize = 14.sp)
        }
    }
}

@Composable
fun CategoryCard(result: MetarResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = result.color),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                result.category,
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Station: ${result.station}", color = Color.Black)
            Text(
                "Visibility: ${result.visibility ?: "Unknown"} km",
                color = Color.Black
            )
            Text(
                "Ceiling: ${result.ceiling?.let { "$it ft" } ?: "Unknown"}",
                color = Color.Black
            )
        }
    }
    @Composable
fun CloudsScreen() {
    var city by remember { mutableStateOf("") }
    var airports by remember { mutableStateOf(listOf<Airport>()) }
    var selectedAirport by remember { mutableStateOf<Airport?>(null) }
    var taf by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var layers by remember { mutableStateOf(listOf<CloudLayer>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "CLOUD FORECAST",
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp
        )

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("Search city worldwide") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                loading = true
                message = "Searching airports..."
                searchAirports(city) { results, error ->
                    airports = results
                    message = error
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text(if (loading) "Searching..." else "SEARCH AIRPORTS")
        }

        if (message.isNotBlank()) {
            Text(message, color = Muted)
        }

        airports.forEach { airport ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedAirport = airport
                        loading = true
                        fetchWeather(airport.icao) { _, result, error ->
                            taf = result
                            layers = parseCloudLayers(result)
                            message = error
                            loading = false
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(airport.name, color = White, fontWeight = FontWeight.Bold)
                    Text("${airport.city} • ${airport.icao}", color = Muted)
                }
            }
        }

        selectedAirport?.let { airport ->
            Text(
                "SELECTED: ${airport.name} (${airport.icao})",
                color = Accent,
                fontWeight = FontWeight.Bold
            )
        }

        if (loading) {
            CircularProgressIndicator(color = Accent)
        }

        if (taf.isNotBlank()) {
            WeatherCard("LIVE TAF", taf)
            Text("CLOUD PROFILE", color = Accent, fontWeight = FontWeight.Bold)
            CloudProfile(layers)
        } else {
            Text(
                "Select an airport to load its cloud forecast.",
                color = Muted
            )
        }
    }
}

@Composable
fun CloudProfile(layers: List<CloudLayer>) {
    val maxAltitude = 20000

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text("Altitude (ft)", color = Accent, fontWeight = FontWeight.Bold)

        val marks = listOf(20000, 15000, 10000, 5000, 0)

        marks.forEach { altitude ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    altitude.toString(),
                    color = Muted,
                    modifier = Modifier.width(60.dp),
                    fontSize = 11.sp
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    HorizontalDivider(
                        modifier = Modifier.align(Alignment.Center),
                        color = Muted.copy(alpha = 0.2f)
                    )

                    layers.filter {
                        (it.altitude / 5000) * 5000 == altitude
                    }.forEach {
                        Text(
                            "${it.amount} ${it.type}",
                            color = Accent,
                            modifier = Modifier.align(Alignment.CenterStart),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Text(
            "UTC time → Forecast layers from available TAF",
            color = Muted,
            fontSize = 11.sp
        )
    }
}
}
fun fetchWeather(
    station: String,
    callback: (String, String, String) -> Unit
) {
    thread {
        try {
            val metarUrl =
                URL("https://aviationweather.gov/api/data/metar?ids=$station&format=json")

            val tafUrl =
                URL("https://aviationweather.gov/api/data/taf?ids=$station&format=json")

            fun download(url: URL): String {
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "SkyMetarGlobal")
                return try {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }

            val metarJson = download(metarUrl)
            val tafJson = download(tafUrl)

            val metar = JSONArray(metarJson)
            val taf = JSONArray(tafJson)

            val metarText = if (metar.length() > 0) {
                metar.getJSONObject(0).optString("rawOb", "")
            } else ""

            val tafText = if (taf.length() > 0) {
                taf.getJSONObject(0).optString("rawTAF", "")
            } else ""

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(
                    metarText,
                    tafText,
                    if (metarText.isBlank() && tafText.isBlank())
                        "No METAR or TAF found for $station"
                    else ""
                )
            }
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback("", "", "Network error: ${e.message}")
            }
        }
    }
}

fun parseMetar(raw: String): MetarResult {
    val text = raw.uppercase(Locale.US)
    val station = Regex("\\b[A-Z]{4}\\b")
        .find(text)?.value ?: "UNKNOWN"

    val visibilityMatch =
        Regex("\\b(\\d{4})\\b").findAll(text).lastOrNull()

    val visibility = visibilityMatch?.value?.toDoubleOrNull()?.let {
        if (it == 9999.0) 10.0 else it / 1000.0
    }

    val cloudHeights = Regex("\\b(?:BKN|OVC|VV)(\\d{3})\\b")
        .findAll(text)
        .mapNotNull { it.groupValues[1].toIntOrNull()?.times(100) }
        .toList()

    val ceiling = cloudHeights.minOrNull()

    val category: String
    val color: Color

    when {
        visibility == null || ceiling == null -> {
            category = "GREY"
            color = Color(0xFF9E9E9E)
        }
        visibility >= 8 && ceiling >= 2500 -> {
            category = "BLUE"
            color = Color(0xFF42A5F5)
        }
        visibility >= 5 && ceiling >= 1500 -> {
            category = "GREEN"
            color = Color(0xFF4CAF50)
        }
        visibility >= 5 && ceiling >= 1000 -> {
            category = "GREEN / YELLOW"
            color = Color(0xFFCDDC39)
        }
        visibility >= 3 && ceiling >= 500 -> {
            category = "YELLOW"
            color = Color(0xFFFFEB3B)
        }
        visibility >= 1.5 && ceiling >= 350 -> {
            category = "YELLOW / RED"
            color = Color(0xFFFF9800)
        }
        else -> {
            category = "RED"
            color = Color(0xFFF44336)
        }
    }

    return MetarResult(
        station,
        raw,
        visibility,
        ceiling,
        category,
        color
    )
}

fun parseCloudLayers(raw: String): List<CloudLayer> {
    val regex = Regex(
        "\\b(FEW|SCT|BKN|OVC|VV)(\\d{3})(CB|TCU)?\\b"
    )

    return regex.findAll(raw.uppercase(Locale.US)).map {
        CloudLayer(
            amount = it.groupValues[1],
            altitude = it.groupValues[2].toInt() * 100,
            type = it.groupValues[3]
        )
    }.toList()
}

fun searchAirports(
    city: String,
    callback: (List<Airport>, String) -> Unit
) {
    thread {
        try {
            val url = URL(
                "https://raw.githubusercontent.com/davidmegginson/ourairports-data/main/airports.csv"
            )

            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 30000

            val lines = connection.inputStream.bufferedReader().readLines()
            connection.disconnect()

            val results = lines.drop(1).mapNotNull { line ->
                val fields = line.split(",")
                if (fields.size < 14) return@mapNotNull null

                val name = fields[3].trim('"')
                val municipality = fields[10].trim('"')
                val icao = fields[5].trim('"')
                val type = fields[2].trim('"')
                val scheduled = fields[13].trim('"')

                if (
                    municipality.contains(city, ignoreCase = true) &&
                    (type == "large_airport" || type == "medium_airport") &&
                    scheduled == "yes" &&
                    icao.matches(Regex("[A-Z]{4}"))
                ) {
                    Airport(name, icao, municipality)
                } else null
            }.take(30)

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(
                    results,
                    if (results.isEmpty()) "No airports found for this city." else ""
                )
            }
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(emptyList(), "Airport search error: ${e.message}")
            }
        }
    }
}

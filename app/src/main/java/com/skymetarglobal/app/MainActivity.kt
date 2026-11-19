package com.skymetarglobal.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

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

private val mainHandler = Handler(Looper.getMainLooper())
private var airportsCache: List<Airport>? = null

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
                ) { selectedTab = "METAR & TAF" }

                TabButton(
                    text = "CLOUDS",
                    selected = selectedTab == "CLOUDS",
                    modifier = Modifier.weight(1f)
                ) { selectedTab = "CLOUDS" }
            }

            when (selectedTab) {
                "METAR & TAF" -> MetarTafScreen(Modifier.weight(1f))
                "CLOUDS" -> CloudsScreen(Modifier.weight(1f))
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
fun MetarTafScreen(modifier: Modifier = Modifier) {
    var station by remember { mutableStateOf("GMMG") }
    var manualMetar by remember { mutableStateOf("") }
    var manualTaf by remember { mutableStateOf("") }
    var liveResult by remember { mutableStateOf<MetarResult?>(null) }
    var manualResult by remember { mutableStateOf<MetarResult?>(null) }
    var liveMetar by remember { mutableStateOf("") }
    var liveTaf by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
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
                val id = station.trim()
                if (id.length != 4) {
                    message = "أدخل رمز ICAO من 4 أحرف"
                    return@Button
                }
                loading = true
                message = "جاري البحث..."
                fetchWeather(id) { metar, taf, error ->
                    liveMetar = metar
                    liveTaf = taf
                    liveResult = if (metar.isNotBlank()) parseMetar(metar) else null
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

        liveResult?.let { CategoryCard(it) }

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
                manualResult = if (manualMetar.isNotBlank()) parseMetar(manualMetar) else null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ANALYZE METAR")
        }

        manualResult?.let { CategoryCard(it) }

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
            onClick = { liveTaf = manualTaf },
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
    val visText = result.visibility?.let { String.format(Locale.US, "%.1f", it) } ?: "Unknown"
    val ceilText = result.ceiling?.let { "$it ft" } ?: "None"

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
            Text("Visibility: $visText km", color = Color.Black)
            Text("Ceiling: $ceilText", color = Color.Black)
        }
    }
}

@Composable
fun CloudsScreen(modifier: Modifier = Modifier) {
    var city by remember { mutableStateOf("") }
    var airports by remember { mutableStateOf(listOf<Airport>()) }
    var selectedAirport by remember { mutableStateOf<Airport?>(null) }
    var taf by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var layers by remember { mutableStateOf(listOf<CloudLayer>()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
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
                if (city.isBlank()) {
                    message = "اكتب اسم المدينة"
                    return@Button
                }
                loading = true
                message = "Searching airports..."
                searchAirports(city.trim()) { results, error ->
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
                    Text("${airport.icao} - ${airport.city}", color = Muted)
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
        } else if (selectedAirport == null) {
            Text(
                "Select an airport to load its cloud forecast.",
                color = Muted
            )
        }
    }
}

@Composable
fun CloudProfile(layers: List<CloudLayer>) {
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

                    val inBand = layers.filter {
                        val band = minOf((it.altitude / 5000) * 5000, 20000)
                        band == altitude
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        inBand.forEach {
                            Text(
                                "${it.amount}${it.altitude / 100} ${it.type}".trim(),
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
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

private fun download(url: URL): String {
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.setRequestProperty("User-Agent", "SkyMetarGlobal")
    return try {
        if (connection.responseCode == 204) ""
        else connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

fun fetchWeather(
    station: String,
    callback: (String, String, String) -> Unit
) {
    thread {
        try {
            val metarUrl = URL("https://aviationweather.gov/api/data/metar?ids=$station&format=json")
            val tafUrl = URL("https://aviationweather.gov/api/data/taf?ids=$station&format=json")

            val metarJson = download(metarUrl).trim()
            val tafJson = download(tafUrl).trim()

            val metar = if (metarJson.startsWith("[")) JSONArray(metarJson) else JSONArray()
            val taf = if (tafJson.startsWith("[")) JSONArray(tafJson) else JSONArray()

            val metarText = if (metar.length() > 0) {
                metar.getJSONObject(0).optString("rawOb", "")
            } else ""

            val tafText = if (taf.length() > 0) {
                taf.getJSONObject(0).optString("rawTAF", "")
            } else ""

            mainHandler.post {
                callback(
                    metarText,
                    tafText,
                    if (metarText.isBlank() && tafText.isBlank())
                        "No METAR or TAF found for $station" else ""
                )
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback("", "", "Network error: ${e.message}")
            }
        }
    }
}

fun parseMetar(raw: String): MetarResult {
    val text = " " + raw.uppercase(Locale.US).trim() + " "

    val station = Regex("""(?:METAR|SPECI)?\s*(?:COR\s+)?\b([A-Z]{4})\b\s+\d{6}Z""")
        .find(text)?.groupValues?.get(1)
        ?: Regex("""\b[A-Z]{4}\b""").find(text)?.value
        ?: "UNKNOWN"

    // Visibility in km
    val visibility: Double? = when {
        text.contains("CAVOK") -> 10.0
        else -> {
            val sm = Regex("""\s(?:(\d+)\s)?(\d+)(?:/(\d+))?SM\s""").find(text)
            if (sm != null) {
                val whole = sm.groupValues[1].toDoubleOrNull() ?: 0.0
                val num = sm.groupValues[2].toDoubleOrNull() ?: 0.0
                val den = sm.groupValues[3].toDoubleOrNull()
                val miles = if (den != null && den != 0.0) whole + num / den else whole + num
                miles * 1.609
            } else {
                Regex("""\s(\d{4})(?=\s)""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                    if (it >= 9999.0) 10.0 else it / 1000.0
                }
            }
        }
    }

    val cloudHeights = Regex("""\b(?:BKN|OVC|VV)(\d{3})""")
        .findAll(text)
        .mapNotNull { it.groupValues[1].toIntOrNull()?.times(100) }
        .toList()

    // null = no ceiling (clear / few / scattered / CAVOK)
    val ceiling = cloudHeights.minOrNull()
    val effectiveCeiling = ceiling ?: Int.MAX_VALUE

    val category: String
    val color: Color

    when {
        visibility == null -> {
            category = "GREY"
            color = Color(0xFF9E9E9E)
        }
        visibility >= 8 && effectiveCeiling >= 2500 -> {
            category = "BLUE"
            color = Color(0xFF42A5F5)
        }
        visibility >= 5 && effectiveCeiling >= 1500 -> {
            category = "GREEN"
            color = Color(0xFF4CAF50)
        }
        visibility >= 5 && effectiveCeiling >= 1000 -> {
            category = "GREEN / YELLOW"
            color = Color(0xFFCDDC39)
        }
        visibility >= 3 && effectiveCeiling >= 500 -> {
            category = "YELLOW"
            color = Color(0xFFFFEB3B)
        }
        visibility >= 1.5 && effectiveCeiling >= 350 -> {
            category = "YELLOW / RED"
            color = Color(0xFFFF9800)
        }
        else -> {
            category = "RED"
            color = Color(0xFFF44336)
        }
    }

    return MetarResult(station, raw, visibility, ceiling, category, color)
}

fun parseCloudLayers(raw: String): List<CloudLayer> {
    val regex = Regex("""\b(FEW|SCT|BKN|OVC|VV)(\d{3})(CB|TCU)?\b""")

    return regex.findAll(raw.uppercase(Locale.US)).map {
        CloudLayer(
            amount = it.groupValues[1],
            altitude = it.groupValues[2].toInt() * 100,
            type = it.groupValues[3]
        )
    }.toList()
}

private fun parseCsvLine(line: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                sb.append('"'); i++
            }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> {
                out.add(sb.toString()); sb.setLength(0)
            }
            else -> sb.append(c)
        }
        i++
    }
    out.add(sb.toString())
    return out
}

fun searchAirports(
    city: String,
    callback: (List<Airport>, String) -> Unit
) {
    thread {
        try {
            // Download once, then reuse (the file is large)
            val all = airportsCache ?: run {
                val url = URL(
                    "https://raw.githubusercontent.com/davidmegginson/ourairports-data/main/airports.csv"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                val lines = try {
                    connection.inputStream.bufferedReader().readLines()
                } finally {
                    connection.disconnect()
                }

                // Columns: 0 id, 1 ident, 2 type, 3 name, ..., 10 municipality,
                // 11 scheduled_service, 12 icao_code, ...
                val parsed = lines.drop(1).mapNotNull { line ->
                    val f = parseCsvLine(line)
                    if (f.size < 13) return@mapNotNull null

                    val type = f[2]
                    val scheduled = f[11]
                    val icao = f[12].ifBlank { f[1] }

                    if (
                        (type == "large_airport" || type == "medium_airport") &&
                        scheduled == "yes" &&
                        icao.matches(Regex("[A-Z]{4}"))
                    ) Airport(f[3], icao, f[10]) else null
                }
                airportsCache = parsed
                parsed
            }

            val results = all.filter {
                it.city.contains(city, ignoreCase = true) ||
                        it.name.contains(city, ignoreCase = true)
            }.take(30)

            mainHandler.post {
                callback(
                    results,
                    if (results.isEmpty()) "No airports found for this city." else ""
                )
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback(emptyList(), "Airport search error: ${e.message}")
            }
        }
    }
}

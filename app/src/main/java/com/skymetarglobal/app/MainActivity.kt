package com.skymetarglobal.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(8, 17, 35)
        window.navigationBarColor = android.graphics.Color.rgb(8, 17, 35)

        val prefs = getSharedPreferences("sky_metar", MODE_PRIVATE)
        val saved = prefs.getString("lang", null)
        val initial = Lang.values().firstOrNull { it.code == saved } ?: deviceLang()

        setContent {
            SkyMetarApp(initial) { prefs.edit().putString("lang", it.code).apply() }
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

// ---------------------------------------------------------------- i18n
enum class Lang(val code: String, val label: String) {
    AR("ar", "العربية"),
    FR("fr", "Français"),
    EN("en", "English")
}

val LocalLang = staticCompositionLocalOf { Lang.EN }

fun deviceLang(): Lang = when (Locale.getDefault().language) {
    "ar" -> Lang.AR
    "fr" -> Lang.FR
    else -> Lang.EN
}

private fun tr(en: String, fr: String, ar: String): Map<Lang, String> =
    mapOf(Lang.EN to en, Lang.FR to fr, Lang.AR to ar)

private val translations: Map<String, Map<Lang, String>> = mapOf(
    "subtitle" to tr("GLOBAL AVIATION WEATHER", "MÉTÉO AÉRONAUTIQUE MONDIALE", "الطقس الجوي العالمي"),
    "tab_clouds" to tr("CLOUDS", "NUAGES", "السحب"),
    "metar_weather" to tr("METAR WEATHER", "MÉTÉO METAR", "طقس METAR"),
    "station_label" to tr("ICAO Station (e.g. GMMG)", "Station OACI (ex. GMMG)", "محطة ICAO (مثال GMMG)"),
    "btn_live" to tr("LIVE METAR + TAF", "METAR + TAF EN DIRECT", "METAR + TAF مباشر"),
    "btn_loading" to tr("Loading...", "Chargement...", "جاري التحميل..."),
    "msg_searching" to tr("Searching...", "Recherche en cours...", "جاري البحث..."),
    "err_station" to tr("Enter a 4-letter ICAO code", "Entrez un code OACI de 4 lettres", "أدخل رمز ICAO من 4 أحرف"),
    "err_not_found" to tr("No METAR or TAF found for", "Aucun METAR ou TAF trouvé pour", "لم يتم العثور على METAR أو TAF للمحطة"),
    "err_network" to tr("Network error:", "Erreur réseau :", "خطأ في الشبكة:"),
    "err_airports" to tr("Airport list error:", "Erreur de la liste des aéroports :", "خطأ في تحميل قائمة المطارات:"),
    "live_metar" to tr("LIVE METAR", "METAR EN DIRECT", "METAR مباشر"),
    "manual_metar" to tr("MANUAL METAR", "METAR MANUEL", "METAR يدوي"),
    "paste_metar" to tr("Paste METAR here", "Collez le METAR ici", "الصق METAR هنا"),
    "btn_analyze" to tr("ANALYZE METAR", "ANALYSER LE METAR", "تحليل METAR"),
    "live_taf" to tr("LIVE TAF", "TAF EN DIRECT", "TAF مباشر"),
    "taf_forecast" to tr("TAF FORECAST", "PRÉVISION TAF", "توقعات TAF"),
    "taf_hint" to tr("Search an ICAO station to load its TAF.", "Recherchez une station OACI pour charger son TAF.", "ابحث عن محطة ICAO لتحميل TAF الخاص بها."),
    "manual_taf" to tr("MANUAL TAF", "TAF MANUEL", "TAF يدوي"),
    "paste_taf" to tr("Paste TAF here", "Collez le TAF ici", "الصق TAF هنا"),
    "btn_display_taf" to tr("DISPLAY MANUAL TAF", "AFFICHER LE TAF MANUEL", "عرض TAF اليدوي"),
    "cat_station" to tr("Station", "Station", "المحطة"),
    "cat_visibility" to tr("Visibility", "Visibilité", "الرؤية"),
    "cat_ceiling" to tr("Ceiling", "Plafond", "السقف"),
    "unknown" to tr("Unknown", "Inconnue", "غير معروفة"),
    "none" to tr("None", "Aucun", "لا يوجد"),
    "km" to tr("km", "km", "كم"),
    "ft" to tr("ft", "ft", "قدم"),
    "clouds_title" to tr("CLOUD FORECAST", "PRÉVISION DES NUAGES", "توقعات السحب"),
    "search_field" to tr("ICAO / city / airport", "OACI / ville / aéroport", "ICAO / مدينة / مطار"),
    "btn_search" to tr("SEARCH", "RECHERCHE", "بحث"),
    "loading_codes" to tr("Loading all ICAO codes...", "Chargement de tous les codes OACI...", "جاري تحميل جميع أكواد ICAO..."),
    "selected" to tr("SELECTED", "SÉLECTIONNÉ", "المحدد"),
    "no_taf" to tr("No TAF available for this airport.", "Aucun TAF disponible pour cet aéroport.", "لا يوجد TAF لهذا المطار."),
    "all_codes" to tr("ALL ICAO CODES", "TOUS LES CODES OACI", "جميع أكواد ICAO"),
    "no_airports" to tr("No airports found.", "Aucun aéroport trouvé.", "لم يتم العثور على مطارات."),
    "chart_title" to tr("CLOUD PROFILE", "PROFIL NUAGEUX", "منحنى السحب"),
    "chart_axes" to tr("Cloud base in ft AGL  ·  time in UTC", "Base des nuages en ft AGL  ·  heure en UTC", "قاعدة السحب بالقدم AGL  ·  التوقيت UTC"),
    "taf_unreadable" to tr("Could not read the TAF time periods.", "Impossible de lire les périodes du TAF.", "تعذّر قراءة فترات TAF."),
    "clear" to tr("CLEAR", "DÉGAGÉ", "صافٍ"),
    "now" to tr("NOW", "MAINT.", "الآن"),
    "clear_desc" to tr("Clear (SKC / NSC / CAVOK)", "Dégagé (SKC / NSC / CAVOK)", "سماء صافية (SKC / NSC / CAVOK)"),
    "no_cloud" to tr("No cloud data", "Aucune donnée de nuages", "لا توجد بيانات سحب"),
    "forecast_title" to tr("8-HOUR FORECAST", "PRÉVISION 8 HEURES", "توقعات 8 ساعات"),
    "elevation" to tr("Elevation", "Altitude de l'aéroport", "ارتفاع المطار"),
    "cloud_cover" to tr("Cloud cover", "Couverture", "الغطاء السحابي"),
    "low" to tr("Low", "Bas", "منخفضة"),
    "mid" to tr("Mid", "Moyen", "متوسطة"),
    "high" to tr("High", "Haut", "عالية"),
    "wind" to tr("Wind", "Vent", "الرياح"),
    "humidity" to tr("Humidity", "Humidité", "الرطوبة"),
    "kt" to tr("kt", "kt", "عقدة"),
    "err_forecast" to tr("Forecast error:", "Erreur de prévision :", "خطأ في جلب التوقعات:"),
    "no_forecast" to tr("No forecast data for this airport.", "Aucune donnée de prévision pour cet aéroport.", "لا توجد بيانات توقعات لهذا المطار."),
    "forecast_note" to tr(
        "Model forecast (Open-Meteo), UTC. QNH ≈ sea-level pressure; QFE computed from airport elevation (ISA). Not for flight planning.",
        "Prévision issue d'un modèle (Open-Meteo), UTC. QNH ≈ pression au niveau de la mer ; QFE calculé à partir de l'altitude de l'aéroport (ISA). Ne pas utiliser pour la préparation du vol.",
        "توقعات من نموذج رقمي (Open-Meteo) بتوقيت UTC. QNH ≈ الضغط عند مستوى البحر، وQFE محسوب من ارتفاع المطار (ISA). لا تعتمد عليها في تخطيط الرحلات."
    )
)

@Composable
fun t(key: String): String = translations[key]?.get(LocalLang.current) ?: key

/** Errors/messages are stored as "key" or "key|detail" so they follow the language live. */
@Composable
fun errText(raw: String): String {
    if (raw.isBlank()) return ""
    val i = raw.indexOf('|')
    val key = if (i < 0) raw else raw.substring(0, i)
    val detail = if (i < 0) "" else raw.substring(i + 1)
    return if (detail.isBlank()) t(key) else t(key) + " " + detail
}

data class MetarResult(
    val station: String,
    val raw: String,
    val visibility: Double?,
    val ceiling: Int?,
    val category: String,
    val color: Color
)

data class Airport(
    val name: String,
    val icao: String,
    val city: String,
    val lat: Double,
    val lon: Double,
    val elevFt: Int
)

data class HourForecast(
    val time: String,
    val cloud: Double,
    val low: Double,
    val mid: Double,
    val high: Double,
    val windDir: Double?,
    val windKt: Double?,
    val gustKt: Double?,
    val humidity: Double?,
    val qnh: Double?,
    val qfe: Double?
)

@Composable
fun SkyMetarApp(initialLang: Lang = Lang.EN, onLangChange: (Lang) -> Unit = {}) {
    var selectedTab by remember { mutableStateOf("METAR") }
    var lang by remember { mutableStateOf(initialLang) }

    CompositionLocalProvider(
        LocalLang provides lang,
        LocalLayoutDirection provides (if (lang == Lang.AR) LayoutDirection.Rtl else LayoutDirection.Ltr)
    ) {
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
                Header(lang) {
                    lang = it
                    onLangChange(it)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabButton(
                        text = "METAR & TAF",
                        selected = selectedTab == "METAR",
                        modifier = Modifier.weight(1f)
                    ) { selectedTab = "METAR" }

                    TabButton(
                        text = "☁ " + t("tab_clouds"),
                        selected = selectedTab == "CLOUDS",
                        modifier = Modifier.weight(1f)
                    ) { selectedTab = "CLOUDS" }
                }

                when (selectedTab) {
                    "METAR" -> MetarTafScreen(Modifier.weight(1f))
                    "CLOUDS" -> CloudsScreen(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun Header(lang: Lang, onLangChange: (Lang) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1D32))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SKY METAR GLOBAL",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Text(
                text = t("subtitle"),
                fontSize = 11.sp,
                color = Accent,
                letterSpacing = when (lang) {
                    Lang.EN -> 2.sp
                    Lang.FR -> 1.sp
                    Lang.AR -> 0.sp   // letter spacing breaks Arabic letter joining
                }
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Panel)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("🌐", fontSize = 15.sp)
                Text(
                    lang.code.uppercase(Locale.US),
                    color = White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                Lang.values().forEach { l ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                l.label,
                                fontWeight = if (l == lang) FontWeight.Bold else FontWeight.Normal,
                                color = if (l == lang) Accent else White
                            )
                        },
                        onClick = {
                            onLangChange(l)
                            menuOpen = false
                        }
                    )
                }
            }
        }
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
        Text(t("metar_weather"), color = Accent, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = station,
            onValueChange = { station = it.uppercase(Locale.US) },
            label = { Text(t("station_label")) },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                val id = station.trim()
                if (id.length != 4) {
                    message = "err_station"
                    return@Button
                }
                loading = true
                message = "msg_searching"
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
            Text(if (loading) t("btn_loading") else t("btn_live"))
        }

        if (message.isNotBlank()) {
            Text(errText(message), color = Muted)
        }

        if (liveMetar.isNotBlank()) {
            WeatherCard(t("live_metar"), liveMetar)
        }

        liveResult?.let { CategoryCard(it) }

        HorizontalDivider(color = Muted.copy(alpha = 0.3f))

        Text(t("manual_metar"), color = Accent, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = manualMetar,
            onValueChange = { manualMetar = it },
            label = { Text(t("paste_metar")) },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = {
                manualResult = if (manualMetar.isNotBlank()) parseMetar(manualMetar) else null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(t("btn_analyze"))
        }

        manualResult?.let { CategoryCard(it) }

        HorizontalDivider(color = Muted.copy(alpha = 0.3f))

        Text(t("live_taf"), color = Accent, fontWeight = FontWeight.Bold)

        if (liveTaf.isNotBlank()) {
            WeatherCard(t("taf_forecast"), liveTaf)
        } else {
            Text(t("taf_hint"), color = Muted)
        }

        Text(t("manual_taf"), color = Accent, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = manualTaf,
            onValueChange = { manualTaf = it },
            label = { Text(t("paste_taf")) },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )

        Button(
            onClick = { liveTaf = manualTaf },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(t("btn_display_taf"))
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
            // raw METAR / TAF is always left-to-right Latin text
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(value, color = White, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun CategoryCard(result: MetarResult) {
    val visText = result.visibility?.let { String.format(Locale.US, "%.1f", it) } ?: t("unknown")
    val ceilText = result.ceiling?.let { "$it ${t("ft")}" } ?: t("none")

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
            Text("${t("cat_station")}: ${result.station}", color = Color.Black)
            Text("${t("cat_visibility")}: $visText ${t("km")}", color = Color.Black)
            Text("${t("cat_ceiling")}: $ceilText", color = Color.Black)
        }
    }
}

@Composable
fun CloudsScreen(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var allAirports by remember { mutableStateOf(airportsCache ?: emptyList()) }
    var selectedAirport by remember { mutableStateOf<Airport?>(null) }
    var forecast by remember { mutableStateOf(listOf<HourForecast>()) }
    var listLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Load every ICAO code once, when the CLOUDS tab opens
    LaunchedEffect(Unit) {
        if (allAirports.isEmpty()) {
            listLoading = true
            message = "loading_codes"
            loadAirports { list, error ->
                allAirports = list
                message = error
                listLoading = false
            }
        }
    }

    val filtered = remember(appliedQuery, allAirports) {
        val q = appliedQuery.trim()
        if (q.isEmpty()) allAirports
        else allAirports.filter {
            it.icao.contains(q, ignoreCase = true) ||
                    it.city.contains(q, ignoreCase = true) ||
                    it.name.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "☁ " + t("clouds_title"),
                color = Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(t("search_field")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { appliedQuery = query.trim() })
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { appliedQuery = query.trim() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🔍 " + t("btn_search"), fontWeight = FontWeight.Bold)
                }

                if (appliedQuery.isNotEmpty() || query.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            query = ""
                            appliedQuery = ""
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("✕")
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            item { Text(errText(message), color = Muted) }
        }

        if (listLoading || loading) {
            item { CircularProgressIndicator(color = Accent) }
        }

        selectedAirport?.let { airport ->
            item {
                Column {
                    Text(
                        "${t("selected")}: ${airport.name} (${airport.icao})",
                        color = Accent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${t("elevation")}: ${airport.elevFt} ${t("ft")}",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (forecast.isNotEmpty()) {
            item {
                Text(t("forecast_title"), color = Accent, fontWeight = FontWeight.Bold)
            }
            items(forecast) { ForecastCard(it) }
            item { Text(t("forecast_note"), color = Muted, fontSize = 11.sp) }
        }

        item {
            Text(
                "${t("all_codes")} (${filtered.size})",
                color = Accent,
                fontWeight = FontWeight.Bold
            )
        }

        items(filtered, key = { it.icao }) { airport ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedAirport = airport
                        forecast = emptyList()
                        message = ""
                        loading = true
                        scope.launch { listState.animateScrollToItem(0) }
                        fetchForecast(airport) { list, error ->
                            forecast = list
                            message = error
                            loading = false
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("☁", color = Accent, fontSize = 22.sp)
                    Column {
                        Text(airport.icao, color = Accent, fontWeight = FontWeight.Bold)
                        Text(airport.name, color = White)
                        if (airport.city.isNotBlank()) {
                            Text(airport.city, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

fun cloudOktas(pct: Double): Int = (pct / 12.5).roundToInt().coerceIn(0, 8)

fun cloudCategory(oktas: Int): String = when {
    oktas == 0 -> "SKC"
    oktas <= 2 -> "FEW"
    oktas <= 4 -> "SCT"
    oktas <= 7 -> "BKN"
    else -> "OVC"
}

fun cloudCatColor(cat: String): Color = when (cat) {
    "SKC" -> Color(0xFF42A5F5)
    "FEW" -> Color(0xFF6FB7DF)
    "SCT" -> Color(0xFF9CCDEB)
    "BKN" -> Color(0xFFCFE3F3)
    else -> Color(0xFFF4F7FC)
}

@Composable
fun PercentBar(label: String, pct: Double, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.width(72.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Muted.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((pct / 100.0).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color)
            )
        }
        Text("${pct.roundToInt()}%", color = White, fontSize = 11.sp, modifier = Modifier.width(40.dp))
    }
}

@Composable
fun InfoCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        leading?.invoke()
        Column {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(value, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ForecastCard(h: HourForecast) {
    val oktas = cloudOktas(h.cloud)
    val cat = cloudCategory(oktas)
    val dash = "--"

    val windKt = h.windKt
    val windText = if (windKt == null) dash else {
        val d = h.windDir?.roundToInt()?.let { if (it % 360 == 0) 360 else it % 360 }
        val dirTxt = if (d == null) "VRB" else String.format(Locale.US, "%03d°", d)
        val gust = h.gustKt
        val gTxt = if (gust != null && gust - windKt >= 5.0) " G${gust.roundToInt()}" else ""
        "$dirTxt / ${windKt.roundToInt()} ${t("kt")}$gTxt"
    }
    val humText = h.humidity?.let { "${it.roundToInt()}%" } ?: dash
    val qnhText = h.qnh?.let { String.format(Locale.US, "%.1f hPa", it) } ?: dash
    val qfeText = h.qfe?.let { String.format(Locale.US, "%.1f hPa", it) } ?: dash

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.time,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(cloudCatColor(cat))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$cat $oktas/8",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            PercentBar(t("cloud_cover"), h.cloud, cloudCatColor(cat))
            PercentBar(t("low"), h.low, Color(0xFF6FB7DF))
            PercentBar(t("mid"), h.mid, Color(0xFF9CCDEB))
            PercentBar(t("high"), h.high, Color(0xFFCFE3F3))

            HorizontalDivider(color = Muted.copy(alpha = 0.3f))

            Row(modifier = Modifier.fillMaxWidth()) {
                InfoCell(t("wind"), windText, Modifier.weight(1f)) {
                    val dir = h.windDir
                    if (dir != null) {
                        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            // wind blows FROM dir, so the arrow points the opposite way
                            Text(
                                "↑",
                                color = Accent,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.rotate(((dir + 180.0) % 360.0).toFloat())
                            )
                        }
                    }
                }
                InfoCell(t("humidity"), humText, Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                InfoCell("QNH", qnhText, Modifier.weight(1f))
                InfoCell("QFE", qfeText, Modifier.weight(1f))
            }
        }
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
                        "err_not_found|$station" else ""
                )
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback("", "", "err_network|${e.message ?: ""}")
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

private fun JSONArray.dbl(i: Int): Double? =
    if (i >= length() || isNull(i)) null else optDouble(i)

/** ISA conversion from QNH (sea level) to the pressure at the airport elevation. */
fun qfeFromQnh(qnh: Double, elevFt: Int): Double {
    val hMeters = elevFt * 0.3048
    return qnh * Math.pow(1.0 - 0.0065 * hMeters / 288.15, 5.255)
}

fun fetchForecast(airport: Airport, callback: (List<HourForecast>, String) -> Unit) {
    thread {
        try {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                            "&hourly=cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high," +
                            "wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
                            "relative_humidity_2m,pressure_msl" +
                            "&wind_speed_unit=kn&timezone=UTC&forecast_days=2",
                    airport.lat, airport.lon
                )
            )
            val hourly = JSONObject(download(url)).getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val cloud = hourly.getJSONArray("cloud_cover")
            val low = hourly.getJSONArray("cloud_cover_low")
            val mid = hourly.getJSONArray("cloud_cover_mid")
            val high = hourly.getJSONArray("cloud_cover_high")
            val wSpeed = hourly.getJSONArray("wind_speed_10m")
            val wDir = hourly.getJSONArray("wind_direction_10m")
            val wGust = hourly.getJSONArray("wind_gusts_10m")
            val rh = hourly.getJSONArray("relative_humidity_2m")
            val pMsl = hourly.getJSONArray("pressure_msl")

            val nowFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowKey = nowFmt.format(Date())

            val list = ArrayList<HourForecast>()
            for (i in 0 until times.length()) {
                val ts = times.getString(i)
                if (ts < nowKey) continue
                if (list.size >= 8) break

                val qnh = pMsl.dbl(i)
                list.add(
                    HourForecast(
                        time = ts.substring(8, 10) + "/" + ts.substring(5, 7) +
                                "  " + ts.substring(11, 16) + "Z",
                        cloud = cloud.dbl(i) ?: 0.0,
                        low = low.dbl(i) ?: 0.0,
                        mid = mid.dbl(i) ?: 0.0,
                        high = high.dbl(i) ?: 0.0,
                        windDir = wDir.dbl(i),
                        windKt = wSpeed.dbl(i),
                        gustKt = wGust.dbl(i),
                        humidity = rh.dbl(i),
                        qnh = qnh,
                        qfe = qnh?.let { qfeFromQnh(it, airport.elevFt) }
                    )
                )
            }

            mainHandler.post {
                callback(list, if (list.isEmpty()) "no_forecast" else "")
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback(emptyList(), "err_forecast|${e.message ?: ""}")
            }
        }
    }
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

fun loadAirports(callback: (List<Airport>, String) -> Unit) {
    airportsCache?.let {
        callback(it, "")
        return
    }

    thread {
        try {
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

            val validTypes = setOf("large_airport", "medium_airport", "small_airport")
            val icaoRegex = Regex("[A-Z]{4}")

            // Columns: 1 ident, 2 type, 3 name, 10 municipality, 12 icao_code
            val parsed = lines.drop(1).mapNotNull { line ->
                val f = parseCsvLine(line)
                if (f.size < 13) return@mapNotNull null
                if (f[2] !in validTypes) return@mapNotNull null

                val icao = f[12].ifBlank { f[1] }
                if (!icao.matches(icaoRegex)) return@mapNotNull null

                val lat = f[4].toDoubleOrNull() ?: return@mapNotNull null
                val lon = f[5].toDoubleOrNull() ?: return@mapNotNull null
                val elev = f[6].toDoubleOrNull()?.roundToInt() ?: 0

                Airport(f[3], icao, f[10], lat, lon, elev)
            }.distinctBy { it.icao }.sortedBy { it.icao }

            airportsCache = parsed

            mainHandler.post {
                callback(parsed, if (parsed.isEmpty()) "no_airports" else "")
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback(emptyList(), "err_airports|${e.message ?: ""}")
            }
        }
    }
}

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
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
    "cig" to tr("CIG", "PLAF.", "السقف")
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
    var taf by remember { mutableStateOf("") }
    var listLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var layers by remember { mutableStateOf(listOf<CloudLayer>()) }

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
                    Text("\uD83D\uDD0D " + t("btn_search"), fontWeight = FontWeight.Bold)
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
                Text(
                    "${t("selected")}: ${airport.name} (${airport.icao})",
                    color = Accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (taf.isNotBlank()) {
            item { WeatherCard(t("live_taf"), taf) }
            item { CloudProfile(taf, layers) }
        } else if (selectedAirport != null && !loading) {
            item { Text(t("no_taf"), color = Muted) }
        }

        item {
            Text(
                "${t("all_codes")} (${filtered.size})",
                color = Accent,
                fontWeight = FontWeight.Bold
            )
        }

        items(filtered) { airport ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedAirport = airport
                        taf = ""
                        layers = emptyList()
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

enum class BarMode { SOLID, FADE_IN, FADE_OUT, TEMPO }

data class CloudBar(
    val layer: CloudLayer,
    val startH: Float,
    val endH: Float,
    val mode: BarMode,
    val tag: String = ""
)

data class TafGroup(
    val kind: String,
    val startH: Float,
    val endH: Float,
    val layers: List<CloudLayer>,
    val clear: Boolean
)

data class TafTimeline(
    val station: String,
    val startDay: Int,
    val startHour: Int,
    val totalHours: Float,
    val groups: List<TafGroup>,
    val bars: List<CloudBar>,
    val clearSpans: List<Pair<Float, Float>>
)

fun parseTafTimeline(raw: String): TafTimeline? {
    val tokens = raw.uppercase(Locale.US).replace("=", " ")
        .split(Regex("""\s+""")).filter { it.isNotBlank() }

    val periodRegex = Regex("""^(\d{2})(\d{2})/(\d{2})(\d{2})$""")
    val vIdx = tokens.indexOfFirst { periodRegex.matches(it) }
    if (vIdx < 0) return null

    val vm = periodRegex.matchEntire(tokens[vIdx])!!.groupValues
    val startDay = vm[1].toInt()
    val startHour = vm[2].toInt()

    fun off(day: Int, hour: Int): Float =
        (((day - startDay + 31) % 31) * 24 + hour - startHour).toFloat()

    val total = off(vm[3].toInt(), vm[4].toInt())
    if (total <= 0f) return null

    val station = tokens.take(vIdx)
        .firstOrNull { it.length == 4 && it.all { c -> c in 'A'..'Z' } } ?: ""

    val cloudRegex = Regex("""^(FEW|SCT|BKN|OVC|VV)(\d{3})(CB|TCU)?$""")
    val fmRegex = Regex("""^FM(\d{2})(\d{2})(\d{2})$""")
    val clearWords = setOf("CAVOK", "SKC", "NSC", "CLR", "NCD")

    val groups = ArrayList<TafGroup>()
    var kind = "BASE"
    var gs = 0f
    var ge = total
    var layers = ArrayList<CloudLayer>()
    var clear = false

    fun flush() {
        groups.add(TafGroup(kind, gs, ge, layers.toList(), clear))
    }

    var i = vIdx + 1
    while (i < tokens.size) {
        val t = tokens[i]
        val fm = fmRegex.matchEntire(t)
        if (fm != null) {
            flush()
            kind = "FM"
            gs = off(fm.groupValues[1].toInt(), fm.groupValues[2].toInt()) +
                    fm.groupValues[3].toInt() / 60f
            ge = total
            layers = ArrayList()
            clear = false
        } else if (t == "BECMG" || t == "TEMPO" || t.startsWith("PROB")) {
            var j = i
            if (t.startsWith("PROB") && j + 1 < tokens.size && tokens[j + 1] == "TEMPO") j++
            val pm = if (j + 1 < tokens.size) periodRegex.matchEntire(tokens[j + 1]) else null
            if (pm != null) {
                flush()
                kind = t
                gs = off(pm.groupValues[1].toInt(), pm.groupValues[2].toInt())
                ge = off(pm.groupValues[3].toInt(), pm.groupValues[4].toInt())
                layers = ArrayList()
                clear = false
                i = j + 1
            }
        } else {
            val cm = cloudRegex.matchEntire(t)
            if (cm != null) {
                layers.add(
                    CloudLayer(cm.groupValues[1], cm.groupValues[2].toInt() * 100, cm.groupValues[3])
                )
            } else if (t in clearWords) {
                clear = true
            }
        }
        i++
    }
    flush()

    // ---- build the bars
    val bars = ArrayList<CloudBar>()
    val clearSpans = ArrayList<Pair<Float, Float>>()
    val base = groups.first()
    var curLayers: List<CloudLayer> = base.layers
    var curClear = base.clear
    var curStart = 0f

    fun closeState(end: Float) {
        if (end > curStart) {
            curLayers.forEach { bars.add(CloudBar(it, curStart, end, BarMode.SOLID)) }
            if (curLayers.isEmpty() && curClear) clearSpans.add(curStart to end)
        }
    }

    val events = groups.drop(1)
        .filter { (it.kind == "FM" || it.kind == "BECMG") && (it.layers.isNotEmpty() || it.clear) }
        .sortedBy { it.startH }

    for (e in events) {
        if (e.kind == "FM") {
            closeState(e.startH)
            curLayers = e.layers
            curClear = e.clear
            curStart = maxOf(e.startH, curStart)
        } else {
            val start = maxOf(e.startH, curStart)
            closeState(start)
            val end = maxOf(e.endH, start)
            curLayers.forEach { bars.add(CloudBar(it, start, end, BarMode.FADE_OUT)) }
            e.layers.forEach { bars.add(CloudBar(it, start, end, BarMode.FADE_IN)) }
            curLayers = e.layers
            curClear = e.clear
            curStart = end
        }
    }
    closeState(total)

    groups.filter { it.kind == "TEMPO" || it.kind.startsWith("PROB") }.forEach { g ->
        g.layers.forEach { bars.add(CloudBar(it, g.startH, g.endH, BarMode.TEMPO, g.kind)) }
    }

    return TafTimeline(station, startDay, startHour, total, groups, bars, clearSpans)
}

fun layerLabel(l: CloudLayer): String =
    String.format(Locale.US, "%s%03d%s", l.amount, l.altitude / 100, l.type)

fun layerColor(l: CloudLayer): Color = when {
    l.type.isNotEmpty() -> Color(0xFFFF7043)
    l.amount == "VV" -> Color(0xFFFFB74D)
    l.amount == "FEW" -> Color(0xFF6FB7DF)
    l.amount == "SCT" -> Color(0xFF9CCDEB)
    l.amount == "BKN" -> Color(0xFFCFE3F3)
    else -> Color(0xFFF4F7FC)
}

fun layerThicknessDp(amount: String): Float = when (amount) {
    "FEW" -> 8f
    "SCT" -> 11f
    "BKN" -> 15f
    else -> 18f
}

fun ceilingOf(layers: List<CloudLayer>): Int? =
    layers.filter { it.amount == "BKN" || it.amount == "OVC" || it.amount == "VV" }
        .minOfOrNull { it.altitude }

fun formatTafTime(tl: TafTimeline, h: Float): String {
    val whole = h.toInt()
    val mins = ((h - whole) * 60f).roundToInt()
    val abs = tl.startHour + whole
    val hour = abs % 24
    val day = ((tl.startDay - 1 + abs / 24) % 31) + 1
    return if (mins == 0) String.format(Locale.US, "%02d/%02dZ", day, hour)
    else String.format(Locale.US, "%02d/%02d:%02dZ", day, hour, mins)
}

@Composable
fun CloudProfile(taf: String, layers: List<CloudLayer>) {
    val timeline = remember(taf) { parseTafTimeline(taf) }

    if (timeline == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(t("taf_unreadable"), color = Muted)
            if (layers.isNotEmpty()) {
                Text(
                    layers.joinToString("   ") { layerLabel(it) },
                    color = White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        CloudTimelineChart(timeline)
    }
}

@Composable
fun LegendItem(color: Color, text: String, dashed: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .then(
                    if (dashed) Modifier.border(1.5.dp, color, RoundedCornerShape(3.dp))
                    else Modifier.background(color, RoundedCornerShape(3.dp))
                )
        )
        Text(text, color = Muted, fontSize = 11.sp)
    }
}

private fun DrawScope.labelPaint(sizeSp: Float, color: Color, bold: Boolean): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizeSp.sp.toPx()
        this.color = color.toArgb()
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD
        else android.graphics.Typeface.DEFAULT
    }

private fun DrawScope.labelWidth(text: String, sizeSp: Float, bold: Boolean = false): Float =
    labelPaint(sizeSp, Color.Black, bold).measureText(text)

private fun DrawScope.labelHeight(sizeSp: Float): Float {
    val fm = labelPaint(sizeSp, Color.Black, false).fontMetrics
    return fm.descent - fm.ascent
}

private fun DrawScope.drawLabel(
    text: String,
    left: Float,
    top: Float,
    color: Color,
    sizeSp: Float,
    bold: Boolean = false
) {
    val paint = labelPaint(sizeSp, color, bold)
    drawContext.canvas.nativeCanvas.drawText(text, left, top - paint.fontMetrics.ascent, paint)
}

@Composable
fun CloudTimelineChart(tl: TafTimeline) {
    val clearText = t("clear")
    val nowText = t("now")

    val maxAlt = tl.bars.maxOfOrNull { it.layer.altitude } ?: 3000
    val step = when {
        maxAlt <= 3000 -> 500
        maxAlt <= 8000 -> 1000
        maxAlt <= 16000 -> 2000
        else -> 5000
    }
    val topAlt = ((maxAlt / step) + 2) * step
    val labelStep = when {
        tl.totalHours <= 12f -> 2
        tl.totalHours <= 36f -> 3
        else -> 6
    }

    val nowH = remember(tl) {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val day = c.get(Calendar.DAY_OF_MONTH)
        ((day - tl.startDay + 31) % 31) * 24 +
                c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f - tl.startHour
    }

    val fmStarts = tl.groups.filter { it.kind == "FM" }.map { it.startH }
    val rows = tl.groups
        .filter { it.kind == "BASE" || it.layers.isNotEmpty() || it.clear }
        .map { g ->
            val end = when (g.kind) {
                "BASE", "FM" -> fmStarts.filter { it > g.startH }.minOrNull() ?: tl.totalHours
                else -> g.endH
            }
            Triple(g, g.startH, end)
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(t("chart_title"), color = Accent, fontWeight = FontWeight.Bold)
            Text(
                "${tl.station}  ·  ${formatTafTime(tl, 0f)} → ${formatTafTime(tl, tl.totalHours)}",
                color = White,
                fontSize = 13.sp
            )
            Text(t("chart_axes"), color = Muted, fontSize = 11.sp)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            val left = 46.dp.toPx()
            val right = 6.dp.toPx()
            val topPad = 8.dp.toPx()
            val bottomPad = 22.dp.toPx()
            val pw = size.width - left - right
            val ph = size.height - topPad - bottomPad
            val bottom = topPad + ph

            fun xOf(h: Float): Float = left + (h.coerceIn(0f, tl.totalHours) / tl.totalHours) * pw
            fun yOf(alt: Int): Float = bottom - (alt.toFloat() / topAlt) * ph

            val gridColor = Muted.copy(alpha = 0.18f)

            // horizontal grid + altitude labels
            var alt = 0
            while (alt <= topAlt) {
                val y = yOf(alt)
                drawLine(gridColor, Offset(left, y), Offset(left + pw, y), strokeWidth = 1f)
                val text = alt.toString()
                val w = labelWidth(text, 9f)
                val h = labelHeight(9f)
                drawLabel(text, left - w - 6.dp.toPx(), y - h / 2f, Muted, 9f)
                alt += step
            }

            // vertical grid + time labels
            var k = 0
            while (k <= tl.totalHours.toInt()) {
                val x = xOf(k.toFloat())
                drawLine(gridColor, Offset(x, topPad), Offset(x, bottom), strokeWidth = 1f)
                val abs = tl.startHour + k
                val hr = abs % 24
                val midnight = hr == 0
                val text = if (midnight)
                    String.format(Locale.US, "%02d/00", ((tl.startDay - 1 + abs / 24) % 31) + 1)
                else String.format(Locale.US, "%02d", hr)
                val w = labelWidth(text, 9f, midnight)
                drawLabel(
                    text,
                    (x - w / 2f).coerceIn(0f, size.width - w),
                    bottom + 5.dp.toPx(),
                    if (midnight) Accent else Muted,
                    9f,
                    midnight
                )
                k += labelStep
            }

            // clear / CAVOK spans
            tl.clearSpans.forEach { (s0, e0) ->
                val x0 = xOf(s0)
                val x1 = xOf(e0)
                val w = labelWidth(clearText, 11f, true)
                val h = labelHeight(11f)
                if (x1 - x0 > w + 8.dp.toPx()) {
                    drawLabel(
                        clearText,
                        (x0 + x1) / 2f - w / 2f,
                        yOf(topAlt / 2) - h / 2f,
                        Accent.copy(alpha = 0.55f),
                        11f,
                        true
                    )
                }
            }

            // cloud bars (TEMPO / PROB on top)
            tl.bars.sortedBy { if (it.mode == BarMode.TEMPO) 1 else 0 }.forEach { b ->
                val x0 = xOf(b.startH)
                val x1 = xOf(b.endH)
                if (x1 - x0 < 2f) return@forEach

                val thick = layerThicknessDp(b.layer.amount).dp.toPx()
                val yTop = yOf(b.layer.altitude) - thick
                val color = layerColor(b.layer)
                val pos = Offset(x0, yTop)
                val sz = Size(x1 - x0, thick)
                val corner = CornerRadius(4.dp.toPx())

                when (b.mode) {
                    BarMode.SOLID -> drawRoundRect(color.copy(alpha = 0.92f), pos, sz, corner)
                    BarMode.FADE_IN -> drawRoundRect(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0f), color.copy(alpha = 0.92f)),
                            startX = x0, endX = x1
                        ), pos, sz, corner
                    )
                    BarMode.FADE_OUT -> drawRoundRect(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.92f), color.copy(alpha = 0f)),
                            startX = x0, endX = x1
                        ), pos, sz, corner
                    )
                    BarMode.TEMPO -> {
                        drawRoundRect(color.copy(alpha = 0.22f), pos, sz, corner)
                        drawRoundRect(
                            color, pos, sz, corner,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                            )
                        )
                    }
                }

                // CB / TCU tower
                if (b.layer.type.isNotEmpty() && b.mode != BarMode.TEMPO) {
                    val towerH = 30.dp.toPx()
                    val inset = (x1 - x0) * 0.2f
                    val path = Path().apply {
                        moveTo(x0, yTop)
                        lineTo(x1, yTop)
                        lineTo(x1 - inset, yTop - towerH)
                        lineTo(x0 + inset, yTop - towerH)
                        close()
                    }
                    drawPath(path, color.copy(alpha = 0.35f))
                }

                // label
                if (b.mode == BarMode.SOLID || b.mode == BarMode.TEMPO) {
                    val prefix = when {
                        b.tag == "TEMPO" -> "T "
                        b.tag.startsWith("PROB") -> b.tag.replace("PROB", "P") + " "
                        else -> ""
                    }
                    val inside = b.mode == BarMode.SOLID && thick >= 14.dp.toPx()
                    val text = prefix + layerLabel(b.layer)
                    val w = labelWidth(text, 9f, true)
                    val h = labelHeight(9f)
                    val labelColor = when {
                        inside -> Background
                        b.mode == BarMode.TEMPO -> color
                        else -> White
                    }
                    if (x1 - x0 >= w * 0.6f && w < size.width) {
                        val tx = ((x0 + x1) / 2f - w / 2f).coerceIn(0f, size.width - w)
                        val ty = if (inside) yTop + (thick - h) / 2f else yTop - h - 1.dp.toPx()
                        drawLabel(text, tx, ty, labelColor, 9f, true)
                    }
                }
            }

            // NOW marker
            if (nowH in 0f..tl.totalHours) {
                val x = xOf(nowH)
                drawLine(
                    Color(0xFFFF5252), Offset(x, topPad), Offset(x, bottom),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
                val w = labelWidth(nowText, 9f, true)
                drawLabel(
                    nowText,
                    (x + 3.dp.toPx()).coerceAtMost(size.width - w),
                    topPad,
                    Color(0xFFFF5252),
                    9f,
                    true
                )
            }
        }

        // legend
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LegendItem(Color(0xFF6FB7DF), "FEW")
            LegendItem(Color(0xFF9CCDEB), "SCT")
            LegendItem(Color(0xFFCFE3F3), "BKN")
            LegendItem(Color(0xFFF4F7FC), "OVC")
            LegendItem(Color(0xFFFF7043), "CB/TCU")
            LegendItem(Color(0xFFFFB74D), "VV")
            LegendItem(Color(0xFFFF7043), "TEMPO / PROB", dashed = true)
        }

        HorizontalDivider(color = Muted.copy(alpha = 0.3f))

        // forecast periods
        rows.forEach { (g, start, end) ->
            val kindColor = when {
                g.kind == "BASE" -> Accent
                g.kind == "FM" -> White
                g.kind == "BECMG" -> Color(0xFFFFB74D)
                else -> Color(0xFFFF7043)
            }
            val cloudless = if (g.clear) t("clear_desc") else t("no_cloud")
            val cigWord = t("cig")
            val ftWord = t("ft")
            val desc = if (g.layers.isEmpty()) {
                cloudless
            } else {
                val cig = ceilingOf(g.layers)
                g.layers.joinToString("  ") { layerLabel(it) } +
                        (if (cig != null) "   ·  $cigWord $cig $ftWord" else "")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    g.kind,
                    color = kindColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.width(64.dp)
                )
                Column {
                    Text(
                        "${formatTafTime(tl, start)} → ${formatTafTime(tl, end)}",
                        color = Muted,
                        fontSize = 11.sp
                    )
                    Text(desc, color = White, fontSize = 13.sp)
                }
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

                Airport(f[3], icao, f[10])
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

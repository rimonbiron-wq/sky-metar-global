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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(14, 35, 64)
        window.navigationBarColor = android.graphics.Color.rgb(10, 21, 38)

        val prefs = getSharedPreferences("sky_metar", MODE_PRIVATE)
        val saved = prefs.getString("lang", null)
        val initial = Lang.values().firstOrNull { it.code == saved } ?: deviceLang()

        setContent {
            SkyMetarApp(initial) { prefs.edit().putString("lang", it.code).apply() }
        }
    }
}

// ---------------------------------------------------------------- design tokens
private val Background = Color(0xFF060E1B)
private val Panel = Color(0xFF0F1D31)
private val PanelHi = Color(0xFF16273F)
private val Line = Color(0xFF223652)
private val Accent = Color(0xFF35B9E9)
private val Accent2 = Color(0xFF4F8CFF)
private val White = Color(0xFFF4F7FC)
private val Muted = Color(0xFF8FA0B6)
private val Warn = Color(0xFFFFA726)
private val Danger = Color(0xFFF44336)

private fun accentBrush(): Brush = Brush.horizontalGradient(listOf(Accent, Accent2))

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
    "tab_calc" to tr("TOOLS", "OUTILS", "الأدوات"),
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
    ),

    // ---- tools tab
    "calc_title" to tr("AVIATION TOOLS", "OUTILS AÉRO", "أدوات الطيران"),
    "calc_error" to tr("Error", "Erreur", "خطأ"),
    "cat_dist" to tr("Distance", "Distance", "المسافة"),
    "cat_speed" to tr("Speed", "Vitesse", "السرعة"),
    "cat_temp" to tr("Temperature", "Température", "الحرارة"),
    "cat_press" to tr("Pressure", "Pression", "الضغط"),
    "cat_xwind" to tr("Crosswind", "Vent traversier", "الرياح الجانبية"),
    "cat_calc" to tr("Calculator", "Calculatrice", "الحاسبة"),
    "quick_title" to tr("QUICK CONVERT", "CONVERSION RAPIDE", "تحويل سريع"),
    "conv_from" to tr("CONVERT FROM", "CONVERTIR DEPUIS", "التحويل من"),
    "conv_to" to tr("RESULTS", "RÉSULTATS", "النتائج"),
    "u_ft" to tr("Feet (ft)", "Pieds (ft)", "قدم (ft)"),
    "u_m" to tr("Meters (m)", "Mètres (m)", "متر (m)"),
    "u_km" to tr("Kilometers (km)", "Kilomètres (km)", "كيلومتر (km)"),
    "u_nm" to tr("Nautical miles (NM)", "Milles nautiques (NM)", "ميل بحري (NM)"),
    "u_mi" to tr("Miles (mi)", "Milles (mi)", "ميل (mi)"),
    "u_kt" to tr("Knots (kt)", "Nœuds (kt)", "عقدة (kt)"),
    "u_kmh" to tr("Kilometers/hour (km/h)", "Kilomètres/heure (km/h)", "كيلومتر/ساعة (km/h)"),
    "u_ms" to tr("Meters/second (m/s)", "Mètres/seconde (m/s)", "متر/ثانية (m/s)"),
    "u_mph" to tr("Miles/hour (mph)", "Miles/heure (mph)", "ميل/ساعة (mph)"),
    "u_c" to tr("Celsius (°C)", "Celsius (°C)", "سيلسيوس (°C)"),
    "u_f" to tr("Fahrenheit (°F)", "Fahrenheit (°F)", "فهرنهايت (°F)"),
    "u_k" to tr("Kelvin (K)", "Kelvin (K)", "كلفن (K)"),
    "u_hpa" to tr("Hectopascals (hPa)", "Hectopascals (hPa)", "هكتوباسكال (hPa)"),
    "u_inhg" to tr("Inches of mercury (inHg)", "Pouces de mercure (inHg)", "بوصة زئبق (inHg)"),
    "u_mmhg" to tr("Millimeters of mercury (mmHg)", "Millimètres de mercure (mmHg)", "ملم زئبق (mmHg)"),
    "u_kpa" to tr("Kilopascals (kPa)", "Kilopascals (kPa)", "كيلوباسكال (kPa)"),
    "xw_wdir" to tr("Wind direction (°)", "Direction du vent (°)", "اتجاه الرياح (°)"),
    "xw_wspd" to tr("Wind speed (kt)", "Vitesse du vent (kt)", "سرعة الرياح (kt)"),
    "xw_rwy" to tr("Runway (01–36)", "Piste (01–36)", "المدرج (01–36)"),
    "xw_head" to tr("HEADWIND", "VENT DE FACE", "رياح أمامية"),
    "xw_tail" to tr("TAILWIND", "VENT ARRIÈRE", "رياح خلفية"),
    "xw_cross" to tr("CROSSWIND", "VENT TRAVERSIER", "رياح جانبية"),
    "xw_left" to tr("from the left", "de gauche", "من اليسار"),
    "xw_right" to tr("from the right", "de droite", "من اليمين"),
    "xw_hint" to tr(
        "Enter wind and runway to get the components.",
        "Saisissez le vent et la piste pour obtenir les composantes.",
        "أدخل الرياح والمدرج لحساب المركّبات."
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

// ================================================================ SHARED UI KIT

@Composable
private fun ProCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Line,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, borderColor, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(text: String) {
    val ls = if (LocalLang.current == Lang.AR) 0.sp else 1.sp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Accent)
        )
        Text(
            text,
            color = White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = ls
        )
    }
}

@Composable
private fun ProButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val base = modifier
        .height(52.dp)
        .clip(shape)
    val styled = when {
        !enabled -> base.background(Line)
        filled -> base.background(accentBrush())
        else -> base
            .background(PanelHi)
            .border(1.dp, Line, shape)
    }
    Box(
        modifier = styled.clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                !enabled -> Muted
                filled -> Background
                else -> White
            },
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun proFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedContainerColor = PanelHi,
    unfocusedContainerColor = PanelHi,
    cursorColor = Accent,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Line,
    focusedLabelColor = Accent,
    unfocusedLabelColor = Muted
)

@Composable
private fun ProField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    ltr: Boolean = true,
    fontSize: TextUnit = 16.sp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = proFieldColors(),
        textStyle = LocalTextStyle.current.copy(
            textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
            fontSize = fontSize
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PanelHi)
            .padding(12.dp)
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

// ================================================================ APP SHELL

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

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        "METAR" -> MetarTafScreen(Modifier.fillMaxSize())
                        "CLOUDS" -> CloudsScreen(Modifier.fillMaxSize())
                        "CALC" -> CalculatorScreen(Modifier.fillMaxSize())
                    }
                }

                BottomBar(selectedTab) { selectedTab = it }
            }
        }
    }
}

private fun utcNow(): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date()) + " Z"

@Composable
fun UtcClock() {
    var now by remember { mutableStateOf(utcNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = utcNow()
            delay(1000)
        }
    }
    Text(
        now,
        color = Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
fun Header(lang: Lang, onLangChange: (Lang) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E2340), Background)))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentBrush()),
            contentAlignment = Alignment.Center
        ) {
            Text("✈", fontSize = 22.sp, color = Background)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SKY METAR GLOBAL",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                maxLines = 1
            )
            Text(
                text = t("subtitle"),
                fontSize = 10.sp,
                color = Accent,
                maxLines = 1,
                letterSpacing = when (lang) {
                    Lang.EN -> 1.5.sp
                    Lang.FR -> 0.8.sp
                    Lang.AR -> 0.sp   // letter spacing breaks Arabic letter joining
                }
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PanelHi)
                        .border(1.dp, Line, RoundedCornerShape(10.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🌐", fontSize = 14.sp)
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
            UtcClock()
        }
    }
}

@Composable
private fun BottomBar(selected: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("METAR", "📡", "METAR & TAF"),
        Triple("CLOUDS", "☁", t("tab_clouds")),
        Triple("CALC", "🧮", t("tab_calc"))
    )
    NavigationBar(
        containerColor = Color(0xFF0A1526),
        tonalElevation = 0.dp
    ) {
        items.forEach { (id, icon, label) ->
            NavigationBarItem(
                selected = selected == id,
                onClick = { onSelect(id) },
                icon = { Text(icon, fontSize = 22.sp) },
                label = {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = Accent,
                    unselectedTextColor = Muted,
                    indicatorColor = Accent.copy(alpha = 0.18f)
                )
            )
        }
    }
}

// ================================================================ METAR / TAF TAB

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProCard {
            SectionTitle(t("metar_weather"))

            ProField(
                value = station,
                onValueChange = { station = it.uppercase(Locale.US) },
                label = t("station_label")
            )

            ProButton(
                text = if (loading) t("btn_loading") else t("btn_live"),
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                val id = station.trim()
                if (id.length != 4) {
                    message = "err_station"
                } else {
                    loading = true
                    message = "msg_searching"
                    fetchWeather(id) { metar, taf, error ->
                        liveMetar = metar
                        liveTaf = taf
                        liveResult = if (metar.isNotBlank()) parseMetar(metar) else null
                        message = error
                        loading = false
                    }
                }
            }

            if (message.isNotBlank()) {
                Text(errText(message), color = Muted, fontSize = 13.sp)
            }
        }

        if (liveMetar.isNotBlank()) {
            WeatherCard(t("live_metar"), liveMetar)
        }

        liveResult?.let { CategoryCard(it) }

        ProCard {
            SectionTitle(t("manual_metar"))

            ProField(
                value = manualMetar,
                onValueChange = { manualMetar = it },
                label = t("paste_metar"),
                singleLine = false,
                minLines = 3
            )

            ProButton(t("btn_analyze"), Modifier.fillMaxWidth(), filled = false) {
                manualResult = if (manualMetar.isNotBlank()) parseMetar(manualMetar) else null
            }
        }

        manualResult?.let { CategoryCard(it) }

        if (liveTaf.isNotBlank()) {
            WeatherCard(t("taf_forecast"), liveTaf)
        } else {
            ProCard {
                SectionTitle(t("live_taf"))
                Text(t("taf_hint"), color = Muted, fontSize = 13.sp)
            }
        }

        ProCard {
            SectionTitle(t("manual_taf"))

            ProField(
                value = manualTaf,
                onValueChange = { manualTaf = it },
                label = t("paste_taf"),
                singleLine = false,
                minLines = 4
            )

            ProButton(t("btn_display_taf"), Modifier.fillMaxWidth(), filled = false) {
                liveTaf = manualTaf
            }
        }
    }
}

@Composable
fun WeatherCard(title: String, value: String) {
    ProCard {
        SectionTitle(title)
        // raw METAR / TAF is always left-to-right Latin text
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                value,
                color = White,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CategoryCard(result: MetarResult) {
    val visText = result.visibility?.let { String.format(Locale.US, "%.1f", it) + " " + t("km") }
        ?: t("unknown")
    val ceilText = result.ceiling?.let { "$it ${t("ft")}" } ?: t("none")

    ProCard(borderColor = result.color.copy(alpha = 0.7f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(result.color)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    result.category,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                result.station,
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(t("cat_visibility"), visText, Modifier.weight(1f))
            StatTile(t("cat_ceiling"), ceilText, Modifier.weight(1f))
        }
    }
}

// ================================================================ CLOUDS TAB

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
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ProCard {
                SectionTitle("☁ " + t("clouds_title"))

                ProField(
                    value = query,
                    onValueChange = { query = it },
                    label = t("search_field"),
                    ltr = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { appliedQuery = query.trim() })
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProButton("🔍 " + t("btn_search"), Modifier.weight(1f)) {
                        appliedQuery = query.trim()
                    }

                    if (appliedQuery.isNotEmpty() || query.isNotEmpty()) {
                        ProButton("✕", Modifier.width(56.dp), filled = false) {
                            query = ""
                            appliedQuery = ""
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            item { Text(errText(message), color = Muted, fontSize = 13.sp) }
        }

        if (listLoading || loading) {
            item { CircularProgressIndicator(color = Accent) }
        }

        selectedAirport?.let { airport ->
            item {
                ProCard(borderColor = Accent.copy(alpha = 0.6f)) {
                    Text(t("selected"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                airport.name,
                                color = White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "${t("elevation")}: ${airport.elevFt} ${t("ft")}",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            airport.icao,
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        if (forecast.isNotEmpty()) {
            item { SectionTitle(t("forecast_title")) }
            items(forecast) { ForecastCard(it) }
            item { Text(t("forecast_note"), color = Muted, fontSize = 11.sp) }
        }

        item { SectionTitle("${t("all_codes")} (${filtered.size})") }

        items(filtered, key = { it.icao }) { airport ->
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Panel)
                    .border(1.dp, Line, shape)
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
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Accent.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        airport.icao,
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column {
                    Text(airport.name, color = White, fontSize = 14.sp, maxLines = 1)
                    if (airport.city.isNotBlank()) {
                        Text(airport.city, color = Muted, fontSize = 12.sp)
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
                .background(Line)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((pct / 100.0).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
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

    ProCard {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelHi)
                    .padding(12.dp)
            ) {
                InfoCell(t("wind"), windText) {
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
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelHi)
                    .padding(12.dp)
            ) {
                InfoCell(t("humidity"), humText)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile("QNH", qnhText, Modifier.weight(1f))
            StatTile("QFE", qfeText, Modifier.weight(1f))
        }
    }
}

// ================================================================ TOOLS TAB (converters, crosswind, calculator)

// ---------------------------------------------------------------- helpers
private fun calcFmtScale(d: Double, scale: Int): String? {
    if (!d.isFinite()) return null
    val s = BigDecimal(d).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    return if (s == "-0") "0" else s
}

/** Calculator formatting: up to 8 decimals, no trailing zeros. */
private fun calcFmt(d: Double): String? = calcFmtScale(d, 8)

/** Converter formatting: fewer decimals for big numbers, more for tiny ones. */
private fun calcConvFmt(d: Double): String? {
    val a = abs(d)
    val scale = when {
        a >= 1000 -> 3
        a >= 1 -> 4
        else -> 8
    }
    return calcFmtScale(d, scale)
}

private fun calcSanitize(raw: String, allowNegative: Boolean): String {
    val sb = StringBuilder()
    var dot = false
    for (ch in raw.replace(',', '.')) {
        when {
            ch in '0'..'9' -> sb.append(ch)
            ch == '.' && !dot -> {
                dot = true
                sb.append(ch)
            }
            ch == '-' && allowNegative && sb.isEmpty() -> sb.append(ch)
        }
    }
    return sb.toString()
}

// ---------------------------------------------------------------- expression parser (+ − × ÷ %)
private class CalcExprParser(private val s: String) {
    private var p = 0

    fun parse(): Double {
        val v = expr()
        if (p != s.length) throw IllegalArgumentException()
        return v
    }

    private fun peek(): Char? = if (p < s.length) s[p] else null

    private fun expr(): Double {
        var v = term()
        while (true) {
            when (peek()) {
                '+' -> { p++; v += term() }
                '-', '−' -> { p++; v -= term() }
                else -> return v
            }
        }
    }

    private fun term(): Double {
        var v = factor()
        while (true) {
            when (peek()) {
                '×' -> { p++; v *= factor() }
                '÷' -> {
                    p++
                    val d = factor()
                    if (d == 0.0) throw ArithmeticException()
                    v /= d
                }
                else -> return v
            }
        }
    }

    private fun factor(): Double {
        if (peek() == '-' || peek() == '−') {
            p++
            return -factor()
        }
        val start = p
        while (peek()?.let { it in '0'..'9' || it == '.' } == true) p++
        if (start == p) throw IllegalArgumentException()
        var v = s.substring(start, p).toDouble()
        while (peek() == '%') {
            p++
            v /= 100.0
        }
        return v
    }
}

private fun calcEval(s: String): Double? {
    if (s.isBlank()) return null
    return try {
        CalcExprParser(s).parse().takeIf { it.isFinite() }
    } catch (e: Exception) {
        null
    }
}

// ---------------------------------------------------------------- units
private class CalcUnit(
    val symbol: String,
    val key: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double
)

// base = meters
private val distUnits = listOf(
    CalcUnit("km", "u_km", { it * 1000.0 }, { it / 1000.0 }),
    CalcUnit("m", "u_m", { it }, { it }),
    CalcUnit("ft", "u_ft", { it * 0.3048 }, { it / 0.3048 }),
    CalcUnit("NM", "u_nm", { it * 1852.0 }, { it / 1852.0 }),
    CalcUnit("mi", "u_mi", { it * 1609.344 }, { it / 1609.344 })
)

// base = km/h
private val speedUnits = listOf(
    CalcUnit("km/h", "u_kmh", { it }, { it }),
    CalcUnit("m/s", "u_ms", { it * 3.6 }, { it / 3.6 }),
    CalcUnit("kt", "u_kt", { it * 1.852 }, { it / 1.852 }),
    CalcUnit("mph", "u_mph", { it * 1.609344 }, { it / 1.609344 })
)

// base = °C
private val tempUnits = listOf(
    CalcUnit("°C", "u_c", { it }, { it }),
    CalcUnit("°F", "u_f", { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }),
    CalcUnit("K", "u_k", { it - 273.15 }, { it + 273.15 })
)

// base = hPa
private val pressUnits = listOf(
    CalcUnit("hPa", "u_hpa", { it }, { it }),
    CalcUnit("inHg", "u_inhg", { it * 33.8638866667 }, { it / 33.8638866667 }),
    CalcUnit("mmHg", "u_mmhg", { it * 1.33322387415 }, { it / 1.33322387415 }),
    CalcUnit("kPa", "u_kpa", { it * 10.0 }, { it / 10.0 })
)

private enum class ToolCat { DIST, SPEED, TEMP, PRESS, XWIND, CALC }

// ---------------------------------------------------------------- screen
@Composable
fun CalculatorScreen(modifier: Modifier = Modifier) {
    var cat by remember { mutableStateOf(ToolCat.DIST) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle(t("calc_title"))

        // quick convert (km ⇄ m and km/h ⇄ m/s) always visible at the top
        QuickConvertCard()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolCat.values().forEach { c ->
                val label = when (c) {
                    ToolCat.DIST -> "📏 " + t("cat_dist")
                    ToolCat.SPEED -> "💨 " + t("cat_speed")
                    ToolCat.TEMP -> "🌡 " + t("cat_temp")
                    ToolCat.PRESS -> "⏲ " + t("cat_press")
                    ToolCat.XWIND -> "🧭 " + t("cat_xwind")
                    ToolCat.CALC -> "🧮 " + t("cat_calc")
                }
                CategoryChip(label, cat == c) { cat = c }
            }
        }

        when (cat) {
            ToolCat.DIST -> ConverterPanel(distUnits, "1 NM = 1852 m   ·   1 ft = 0.3048 m")
            ToolCat.SPEED -> ConverterPanel(speedUnits, "1 kt = 1.852 km/h   ·   1 m/s = 3.6 km/h")
            ToolCat.TEMP -> ConverterPanel(
                tempUnits,
                "°F = °C × 9/5 + 32   ·   K = °C + 273.15",
                allowNegative = true
            )
            ToolCat.PRESS -> ConverterPanel(pressUnits, "1013.25 hPa = 29.92 inHg (ISA)")
            ToolCat.XWIND -> CrosswindPanel()
            // keypad must never be mirrored in Arabic
            ToolCat.CALC -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BasicCalculator()
            }
        }
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Accent else PanelHi)
            .border(1.dp, if (selected) Accent else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Background else White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun UnitChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accentBrush() else Brush.horizontalGradient(listOf(PanelHi, PanelHi)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Background else White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------- quick convert (km ⇄ m / km/h ⇄ m/s)
@Composable
private fun QuickConvertCard() {
    var isSpeed by remember { mutableStateOf(false) }   // false: km ⇄ m · true: km/h ⇄ m/s
    var swapped by remember { mutableStateOf(false) }   // ⇄ reverses the conversion direction
    var input by remember { mutableStateOf("") }
    val value = input.toDoubleOrNull()

    val srcSymbol = if (isSpeed) (if (swapped) "m/s" else "km/h") else (if (swapped) "m" else "km")
    val dstSymbol = if (isSpeed) (if (swapped) "km/h" else "m/s") else (if (swapped) "km" else "m")
    val units = if (isSpeed) speedUnits else distUnits
    val src = units.first { it.symbol == srcSymbol }
    val dst = units.first { it.symbol == dstSymbol }

    val out = value?.let { calcConvFmt(dst.fromBase(src.toBase(it))) }

    ProCard(borderColor = Accent.copy(alpha = 0.5f)) {
        SectionTitle("⚡ " + t("quick_title"))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UnitChip(t("cat_dist"), !isSpeed) { isSpeed = false }
            UnitChip(t("cat_speed"), isSpeed) { isSpeed = true }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelHi)
                    .border(1.dp, Line, RoundedCornerShape(10.dp))
                    .clickable { swapped = !swapped },
                contentAlignment = Alignment.Center
            ) {
                Text("⇄", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        ProField(
            value = input,
            onValueChange = { input = calcSanitize(it, false) },
            label = t(src.key),
            fontSize = 28.sp,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        ResultRow(t(dst.key), dst.symbol, out)

        Text(
            if (isSpeed) "1 km/h = 0.2778 m/s   ·   1 m/s = 3.6 km/h"
            else "1 km = 1000 m   ·   1 m = 0.001 km",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

// ---------------------------------------------------------------- converters
@Composable
private fun ConverterPanel(units: List<CalcUnit>, note: String, allowNegative: Boolean = false) {
    var fromIdx by remember(units) { mutableStateOf(0) }
    var input by remember(units) { mutableStateOf("") }
    val value = input.toDoubleOrNull()

    ProCard {
        Text(t("conv_from"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            units.forEachIndexed { i, u ->
                UnitChip(u.symbol, i == fromIdx) { fromIdx = i }
            }
        }

        val trailing: (@Composable () -> Unit)? = if (allowNegative) {
            @Composable {
                TextButton(onClick = {
                    input = if (input.startsWith("-")) input.drop(1) else "-$input"
                }) {
                    Text("±", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Accent)
                }
            }
        } else null

        ProField(
            value = input,
            onValueChange = { input = calcSanitize(it, allowNegative) },
            label = t(units[fromIdx].key),
            fontSize = 28.sp,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = trailing
        )
    }

    SectionTitle(t("conv_to"))

    units.forEachIndexed { j, u ->
        if (j != fromIdx) {
            val out = value?.let { calcConvFmt(u.fromBase(units[fromIdx].toBase(it))) }
            ResultRow(t(u.key), u.symbol, out)
        }
    }

    Text(note, color = Muted, fontSize = 12.sp)
}

@Composable
private fun ResultRow(name: String, symbol: String, value: String?) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Muted, fontSize = 12.sp)
            Text(
                text = value ?: "—",
                color = if (value != null) White else Muted,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Accent.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(symbol, color = Accent, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- crosswind
@Composable
private fun CrosswindPanel() {
    var wdir by remember { mutableStateOf("") }
    var wspd by remember { mutableStateOf("") }
    var rwy by remember { mutableStateOf("") }

    val dir = wdir.toIntOrNull()
    val spd = wspd.toDoubleOrNull()
    val rw = rwy.toIntOrNull()

    ProCard {
        ProField(
            value = wdir,
            onValueChange = { wdir = it.filter { c -> c in '0'..'9' }.take(3) },
            label = t("xw_wdir"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        ProField(
            value = wspd,
            onValueChange = { wspd = calcSanitize(it, false) },
            label = t("xw_wspd"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        ProField(
            value = rwy,
            onValueChange = { rwy = it.filter { c -> c in '0'..'9' }.take(2) },
            label = t("xw_rwy"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }

    if (dir != null && dir in 0..360 && spd != null && rw != null && rw in 1..36) {
        val rad = Math.toRadians(dir - rw * 10.0)
        val head = spd * cos(rad)
        val cross = spd * sin(rad)
        val headR = head.roundToInt()
        val crossR = cross.roundToInt()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BigTile(
                label = if (headR >= 0) t("xw_head") else t("xw_tail"),
                value = "${abs(headR)} ${t("kt")}",
                sub = "",
                valueColor = if (headR >= 0) Accent else Warn,
                modifier = Modifier.weight(1f)
            )
            BigTile(
                label = t("xw_cross"),
                value = "${abs(crossR)} ${t("kt")}",
                sub = when {
                    crossR > 0 -> t("xw_right")
                    crossR < 0 -> t("xw_left")
                    else -> ""
                },
                valueColor = Accent,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Text(t("xw_hint"), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun BigTile(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(16.dp)
    ) {
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(if (sub.isBlank()) " " else sub, color = Muted, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- basic calculator
private enum class KeyStyle { DIGIT, FN, OP, EQ }

@Composable
private fun BasicCalculator() {
    var expr by remember { mutableStateOf("") }
    var history by remember { mutableStateOf("") }
    var justEvaluated by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val ops = "+−×÷"

    fun press(key: String) {
        error = false
        val seg = expr.takeLastWhile { it in '0'..'9' || it == '.' }

        when {
            key == "C" -> {
                expr = ""
                history = ""
                justEvaluated = false
            }
            key == "⌫" -> {
                expr = if (justEvaluated) "" else expr.dropLast(1)
                justEvaluated = false
            }
            key == "=" -> {
                val clean = expr.trimEnd('+', '−', '×', '÷')
                val r = calcEval(clean)?.let { calcFmt(it) }
                if (r != null) {
                    history = "$clean ="
                    expr = r.replace('-', '−')
                    justEvaluated = true
                } else if (expr.isNotEmpty()) {
                    error = true
                }
            }
            key == "%" -> {
                val l = expr.lastOrNull()
                if (l != null && (l in '0'..'9' || l == '%')) {
                    expr += "%"
                    justEvaluated = false
                }
            }
            key == "." -> {
                if (justEvaluated) {
                    expr = "0."
                    justEvaluated = false
                } else if (expr.lastOrNull() != '%' && '.' !in seg) {
                    expr += if (seg.isEmpty()) "0." else "."
                }
            }
            key.length == 1 && key[0] in ops -> {
                justEvaluated = false
                val last = expr.lastOrNull()
                when {
                    expr.isEmpty() -> if (key == "−") expr = "−"
                    expr == "−" -> {}
                    last != null && last in ops -> expr = expr.dropLast(1) + key
                    else -> expr += key
                }
            }
            key.length == 1 && key[0] in '0'..'9' -> {
                if (justEvaluated) {
                    expr = key
                    justEvaluated = false
                } else if (expr.lastOrNull() == '%') {
                    // ignore: "5%3" is not valid
                } else if (seg == "0") {
                    expr = expr.dropLast(1) + key
                } else {
                    expr += key
                }
            }
        }
    }

    val preview = if (justEvaluated) null
    else calcEval(expr.trimEnd('+', '−', '×', '÷'))?.let { calcFmt(it) }?.takeIf { it != expr }

    val smallLine = when {
        justEvaluated -> history
        preview != null -> expr
        else -> ""
    }
    val bigLine = when {
        error -> t("calc_error")
        preview != null -> preview.replace('-', '−')
        expr.isEmpty() -> "0"
        else -> expr
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // display
        val displayShape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(displayShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF12304F), Panel)))
                .border(1.dp, Line, displayShape)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (smallLine.isBlank()) " " else smallLine,
                color = Muted,
                fontSize = 20.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = bigLine,
                color = if (error) Danger else White,
                fontSize = if (bigLine.length > 12) 32.sp else 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // keypad
        val rows = listOf(
            listOf("C", "⌫", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+")
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    val style = when (k) {
                        "÷", "×", "−", "+" -> KeyStyle.OP
                        "C", "⌫", "%" -> KeyStyle.FN
                        else -> KeyStyle.DIGIT
                    }
                    CalcKey(k, Modifier.weight(1f), style) { press(k) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcKey("0", Modifier.weight(2f), KeyStyle.DIGIT) { press("0") }
            CalcKey(".", Modifier.weight(1f), KeyStyle.DIGIT) { press(".") }
            CalcKey("=", Modifier.weight(1f), KeyStyle.EQ) { press("=") }
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier,
    style: KeyStyle,
    onClick: () -> Unit
) {
    val base = modifier
        .height(60.dp)
        .clip(RoundedCornerShape(18.dp))
    val styled = when (style) {
        KeyStyle.EQ -> base.background(accentBrush())
        KeyStyle.OP -> base.background(Accent.copy(alpha = 0.16f))
        KeyStyle.FN -> base.background(Line)
        KeyStyle.DIGIT -> base.background(PanelHi)
    }
    val fg = when (style) {
        KeyStyle.EQ -> Background
        KeyStyle.OP -> Accent
        else -> White
    }
    Box(
        modifier = styled.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = fg,
            fontSize = 24.sp,
            fontWeight = if (style == KeyStyle.DIGIT) FontWeight.Medium else FontWeight.Bold
        )
    }
}

// ================================================================ NETWORK / PARSING

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

    // Total cloud cover in oktas, worst case (FEW=2, SCT=4, BKN=5, OVC/VV=8).
    // METAR FEW/SCT do not give the exact oktas (1-2 / 3-4), so the higher
    // value is assumed; cover >= 4/8 blocks BLUE/GREEN like the table demands.
    val coverOktas: Int = when {
        Regex("""\b(?:VV|OVC)\d{3}""").containsMatchIn(text) -> 8
        Regex("""\bBKN\d{3}""").containsMatchIn(text) -> 5
        Regex("""\bSCT\d{3}""").containsMatchIn(text) -> 4
        Regex("""\bFEW\d{3}""").containsMatchIn(text) -> 2
        else -> 0    // SKC / NSC / NCD / CAVOK / no cloud group
    }

    val category: String
    val color: Color

    // Colour-code table (poster): PLAFOND >= (ft) / VISIBILITY > (km) / NEBULOSITE
    // BLUE >=2500' >8km <4/8 - GREEN >=1500' >5km <4/8 - GREEN/YELLOW >=1000' >5km
    // YELLOW >=500' >3km - YELLOW/RED >=350' >1.5km - RED >=220' >1km (cover >=4/8)
    // below (<220' or <=1km) = SPECIAL grey. No ceiling = unlimited (cover <4/8).
    when {
        visibility == null -> {
            category = "GREY"
            color = Color(0xFF9E9E9E)
        }
        coverOktas < 4 && visibility > 8 -> {            // < 4/8: plafond illimite
            category = "BLUE"
            color = Color(0xFF42A5F5)
        }
        coverOktas < 4 && visibility > 5 -> {
            category = "GREEN"
            color = Color(0xFF4CAF50)
        }
        effectiveCeiling >= 1000 && visibility > 5 -> {  // cover >= 4/8
            category = "GREEN / YELLOW"
            color = Color(0xFFCDDC39)
        }
        effectiveCeiling >= 500 && visibility > 3 -> {
            category = "YELLOW"
            color = Color(0xFFFFEB3B)
        }
        effectiveCeiling >= 350 && visibility > 1.5 -> {
            category = "YELLOW / RED"
            color = Color(0xFFFF9800)
        }
        effectiveCeiling >= 220 && visibility > 1 -> {
            category = "RED"
            color = Color(0xFFF44336)
        }
        else -> {                                        // < 220' or <= 1 km
            category = "SPECIAL"
            color = Color(0xFF9E9E9E)
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

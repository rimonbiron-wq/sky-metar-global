
package com.skymetarglobal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SkyMetarApp()
        }
    }
}

@Composable
fun SkyMetarApp() {
    var station by remember { mutableStateOf("GMMN") }
    var selectedLanguage by remember { mutableStateOf("English") }
    var message by remember { mutableStateOf("") }

    val background = Color(0xFF081522)
    val panel = Color(0xFF102638)
    val muted = Color(0xFF9EB4C8)

    val weatherColors = listOf(
        Color(0xFF168BFF),
        Color(0xFF19A65A),
        Color(0xFF9ACD32),
        Color(0xFFFFD21F),
        Color(0xFFFF9B21),
        Color(0xFFE53935),
        Color(0xFF777777)
    )

    val labels = listOf(
        "BLUE",
        "GREEN",
        "GREEN / YELLOW",
        "YELLOW",
        "YELLOW / RED",
        "RED",
        "GREY"
    )

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "SKY METAR GLOBAL",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "GLOBAL AVIATION WEATHER",
                    color = muted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("العربية", "English", "Français").forEach { language ->
                        Button(
                            onClick = {
                                selectedLanguage = language
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = 4.dp,
                                vertical = 8.dp
                            )
                        ) {
                            Text(language, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "AIRPORT SEARCH",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = station,
                    onValueChange = {
                        station = it
                            .uppercase()
                            .filter { char -> char.isLetterOrDigit() }
                            .take(4)
                        message = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ICAO CODE") },
                    placeholder = { Text("Example: GMMN") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii
                    )
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        message = if (
                            station.length == 4 &&
                            station.all { it.isLetterOrDigit() }
                        ) {
                            "Airport code: $station"
                        } else {
                            "Please enter a valid 4-character ICAO code."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SEARCH AIRPORT")
                }

                if (message.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "WEATHER COLOR GUIDE",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            panel,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    weatherColors.forEachIndexed { index, color ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 23.dp)
                                    .background(
                                        color,
                                        RoundedCornerShape(6.dp)
                                    )
                            )

                            Text(
                                text = labels[index],
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                Text(
                    text = "METAR / TAF",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Live aviation weather connection is not enabled in this test version.",
                    color = muted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Language: $selectedLanguage",
                    color = muted,
                    fontSize = 12.sp
                )
            }
        }
    }
}


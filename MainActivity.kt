package com.skymetarglobal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SkyMetarApp() }
    }
}

@Composable
fun SkyMetarApp() {
    var station by remember { mutableStateOf("GMMN") }

    val colors = listOf(
        Color(0xFF168BFF),
        Color(0xFF19A65A),
        Color(0xFF9ACD32),
        Color(0xFFFFD21F),
        Color(0xFFFF9B21),
        Color(0xFFE53935),
        Color(0xFF777777)
    )

    val labels = listOf(
        "Blue", "Green", "Green / Yellow",
        "Yellow", "Yellow / Red", "Red", "Grey"
    )

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF081522)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "SKY METAR GLOBAL",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "GLOBAL AVIATION WEATHER",
                    color = Color(0xFF9EB4C8),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("العربية", "English", "Français").forEach {
                        Button(onClick = { }) {
                            Text(it)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text("Airport / ICAO", color = Color.White)

                OutlinedTextField(
                    value = station,
                    onValueChange = {
                        station = it.uppercase().take(4)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("مثال: GMMN") }
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SEARCH AIRPORT")
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "WEATHER COLOR GUIDE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF102638),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colors.forEachIndexed { index, color ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Spacer(
                                Modifier
                                    .height(18.dp)
                                    .weight(1f)
                                    .background(color, RoundedCornerShape(5.dp))
                            )

                            Text(
                                labels[index],
                                color = Color.White,
                                modifier = Modifier.weight(3f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "METAR / TAF data and airport map will be connected in the next build.",
                    color = Color(0xFF9EB4C8),
                    fontSize = 12.sp
                )
            }
        }
    }
}

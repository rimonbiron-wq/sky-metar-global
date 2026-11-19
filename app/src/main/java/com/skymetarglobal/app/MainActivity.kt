package com.skymetarglobal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF081522)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "SKY METAR GLOBAL",
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "GLOBAL AVIATION WEATHER",
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

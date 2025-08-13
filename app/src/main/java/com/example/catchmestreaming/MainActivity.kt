package com.example.catchmestreaming

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.catchmestreaming.ui.navigation.AppNavigation
import com.example.catchmestreaming.ui.theme.CatchMeStreamingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatchMeStreamingTheme {
                AppNavigation()
            }
        }
    }
}
package com.example.videoqongiroq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.videoqongiroq.ui.VideoQongiroqApp
import com.example.videoqongiroq.ui.theme.VIDEOQONGIROQTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VIDEOQONGIROQTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VideoQongiroqApp()
                }
            }
        }
    }
}

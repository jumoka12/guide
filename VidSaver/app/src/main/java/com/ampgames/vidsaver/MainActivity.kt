package com.ampgames.vidsaver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ampgames.vidsaver.ui.VidSaverApp
import com.ampgames.vidsaver.ui.theme.VidSaverTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VidSaverTheme {
                VidSaverApp()
            }
        }
    }
}

package com.ampgames.vidsaver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import com.ampgames.vidsaver.ui.VidSaverApp
import com.ampgames.vidsaver.ui.theme.VidSaverTheme
import dagger.hilt.android.AndroidEntryPoint

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come before super.onCreate(): it swaps the launch theme for the
        // app theme, and after super it is too late to matter.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VidSaverTheme {
                VidSaverApp()
            }
        }
    }
}

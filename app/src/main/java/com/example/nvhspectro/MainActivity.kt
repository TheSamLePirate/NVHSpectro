package com.example.nvhspectro

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.nvhspectro.theme.NVHSpectroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // [U8, plan 4.9] The platform splash screen — shown by the system while the process
        // starts and dismissed the moment the first frame is ready. It replaces a Compose
        // screen that held the app back with a fixed 2-second `delay(2000)`: a vanity wait an
        // operator paid on every cold start in the field, for nothing.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // [V14 UX-M11] The app is fixed-dark (D4), so the system-bar icons must be light in
        // every state — SystemBarStyle.dark states that intent explicitly instead of letting
        // the platform guess from a theme that no longer configures the bars (the XML
        // statusBarColor/navigationBarColor attributes are ignored from API 35).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            NVHSpectroTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppNavigation() } }
        }
    }
}

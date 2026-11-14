package com.example.odedim

import android.os.Bundle
import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.odedim.ui.theme.OdedimTheme

class MainActivity : ComponentActivity() {
    private var resumeToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        ReminderScheduler(this).createChannel()
        setContent {
            OdedimTheme {
                OdedimApp(resumeToken)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeToken++
    }
}

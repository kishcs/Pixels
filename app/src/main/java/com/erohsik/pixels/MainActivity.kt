package com.erohsik.pixels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.erohsik.pixels.data.ThemeMode
import com.erohsik.pixels.di.ServiceLocator
import com.erohsik.pixels.ui.PixelsRoot
import com.erohsik.pixels.ui.theme.PixelsTheme

class MainActivity : ComponentActivity() {

    /** Moves the "today" ring at midnight, and follows manual clock or zone changes. */
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ServiceLocator.refreshToday()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = ServiceLocator.settings
        setContent {
            val theme by settings.themeMode.collectAsState(initial = settings.currentTheme())
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep system bar icons legible when the in-app theme differs from the system one.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            PixelsTheme(theme) {
                PixelsRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ServiceLocator.refreshToday()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, dateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(dateReceiver)
        super.onStop()
    }
}

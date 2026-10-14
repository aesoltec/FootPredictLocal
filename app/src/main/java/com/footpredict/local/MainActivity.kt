package com.footpredict.local

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.footpredict.local.presentation.screen.matchlist.AppNav
import com.footpredict.local.presentation.theme.FootPredictTheme
import com.footpredict.local.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val PREFS_UI = "ui_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var mode by remember {
                mutableStateOf(
                    runCatching {
                        ThemeMode.valueOf(
                            getSharedPreferences(PREFS_UI, MODE_PRIVATE)
                                .getString("theme", ThemeMode.SYSTEM.name)
                                ?: ThemeMode.SYSTEM.name
                        )
                    }.getOrDefault(ThemeMode.SYSTEM)
                )
            }
            FootPredictTheme(mode = mode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNav(
                        themeMode = mode,
                        onThemeChange = { next ->
                            getSharedPreferences(PREFS_UI, MODE_PRIVATE)
                                .edit().putString("theme", next.name).apply()
                            mode = next
                        }
                    )
                }
            }
        }
    }
}

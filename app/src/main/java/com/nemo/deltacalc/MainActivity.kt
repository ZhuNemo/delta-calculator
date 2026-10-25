package com.nemo.deltacalc

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nemo.deltacalc.data.AppSettings
import com.nemo.deltacalc.data.HistoryEntity
import com.nemo.deltacalc.data.ThemeMode
import com.nemo.deltacalc.ui.CalculatorScreen
import com.nemo.deltacalc.ui.HistoryScreen
import com.nemo.deltacalc.ui.SettingsScreen
import com.nemo.deltacalc.ui.theme.DeltaTheme
import com.nemo.deltacalc.vm.CalculatorViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: CalculatorViewModel by viewModels {
        viewModelFactory {
            initializer {
                CalculatorViewModel((application as DeltaApp).historyRepository)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as DeltaApp
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current
            val activity = LocalContext.current as? Activity
            LaunchedEffect(dark, activity) {
                val window = activity?.window ?: return@LaunchedEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            DeltaTheme(settings) {
                DeltaRoot(vm = vm, app = app, settings = settings)
            }
        }
    }
}

@Composable
private fun DeltaRoot(
    vm: CalculatorViewModel,
    app: DeltaApp,
    settings: AppSettings,
) {
    var screen by rememberSaveable { mutableStateOf("calc") }
    val history by app.historyRepository.observe()
        .collectAsStateWithLifecycle(initialValue = emptyList<HistoryEntity>())
    val scope = rememberCoroutineScope()

    when (screen) {
        "history" -> HistoryScreen(
            records = history,
            onBack = { screen = "calc" },
            onPick = { item ->
                vm.restore(item)
                screen = "calc"
            },
            onDelete = { id -> scope.launch { app.historyRepository.delete(id) } },
            onClear = { scope.launch { app.historyRepository.clear() } },
        )

        "settings" -> SettingsScreen(
            settings = settings,
            repository = app.settingsRepository,
            onBack = { screen = "calc" },
        )

        else -> CalculatorScreen(
            vm = vm,
            settings = settings,
            onOpenHistory = { screen = "history" },
            onOpenSettings = { screen = "settings" },
        )
    }

    BackHandler(enabled = screen != "calc") { screen = "calc" }
}

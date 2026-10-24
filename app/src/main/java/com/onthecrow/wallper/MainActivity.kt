package com.onthecrow.wallper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.onthecrow.wallper.core.designsystem.theme.WallperTheme
import com.onthecrow.wallper.core.navigation.NavigationProvider
import com.onthecrow.wallper.diagnostics.DebugFileTree
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.settings.domain.ThemeMode
import com.onthecrow.wallper.splash.SplashScreenDelegate
import com.onthecrow.wallper.splash.SplashScreenDelegateImpl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), SplashScreenDelegate by SplashScreenDelegateImpl() {

    @Inject
    lateinit var navigationProvider: NavigationProvider

    @Inject
    lateinit var appSettings: AppSettingsSource

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplash()
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { DebugFileTree.copyToExternal(this@MainActivity) }
                    .onFailure { Timber.w(it, "Can't copy the log") }
            }
        }
        setContent {
            val settings by appSettings.settings.collectAsState()
            WallperTheme(
                darkTheme = when (settings.theme) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                dynamicColor = settings.dynamicColor,
            ) {
                navigationProvider.Navigation(Modifier)
            }
        }
    }
}

package com.onthecrow.wallper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.onthecrow.wallper.core.designsystem.theme.ThemeRevealHost
import com.onthecrow.wallper.core.designsystem.theme.WallperTheme
import com.onthecrow.wallper.core.designsystem.theme.rememberThemeRevealState
import com.onthecrow.wallper.core.navigation.NavigationProvider
import com.onthecrow.wallper.diagnostics.DebugFileTree
import com.onthecrow.wallper.settings.domain.AppSettingsSource
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
            // The store has read the settings before the first frame: the app is never drawn in a guessed theme
            // that the stored one would then replace without the reveal.
            val settings by appSettings.settings.collectAsState()
            val darkTheme = settings.theme.isDark(isSystemInDarkTheme())
            val themeReveal = rememberThemeRevealState()
            WallperTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                // The system bars keep the old theme until the reveal has uncovered the whole screen.
                systemBarsDarkTheme = themeReveal.outgoingDarkTheme ?: darkTheme,
            ) {
                ThemeRevealHost(
                    darkTheme = darkTheme,
                    modifier = Modifier.fillMaxSize(),
                    state = themeReveal,
                ) {
                    // Opaque, so the old theme's snapshot never shows the window's background through.
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        navigationProvider.Navigation(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

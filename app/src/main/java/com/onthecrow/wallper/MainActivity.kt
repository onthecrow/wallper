package com.onthecrow.wallper

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.lifecycleScope
import com.onthecrow.wallper.core.designsystem.theme.WallperTheme
import com.onthecrow.wallper.core.navigation.NavigationProvider
import com.onthecrow.wallper.diagnostics.DebugFileTree
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigationProvider: NavigationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Below API 31 the activity theme has no splash icon to wait for.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener(::finishSplash)
        }
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { DebugFileTree.copyToExternal(this@MainActivity) }
                    .onFailure { Timber.w(it, "Can't copy the log") }
            }
        }
        setContent {
            WallperTheme {
                navigationProvider.Navigation(Modifier)
            }
        }
    }

    /**
     * The splash would go with the first frame, usually before the brush has written the whole W:
     * it stays until the icon animation ends, then fades out. Without animations the delay is 0.
     */
    private fun finishSplash(splash: SplashScreenViewProvider) {
        // On API 31+ the start is in milliseconds since the epoch.
        val end = splash.iconAnimationStartMillis + splash.iconAnimationDurationMillis
        val left = (end - System.currentTimeMillis()).coerceAtLeast(0)
        // The animated icon is a SurfaceView on top of the splash: the splash's alpha doesn't reach it.
        splash.iconView.animate().alpha(0f).setStartDelay(left).setDuration(SPLASH_FADE_MS)
        splash.view.animate().alpha(0f).setStartDelay(left).setDuration(SPLASH_FADE_MS)
            .withEndAction { splash.remove() }
    }

    private companion object {
        const val SPLASH_FADE_MS = 200L
    }
}

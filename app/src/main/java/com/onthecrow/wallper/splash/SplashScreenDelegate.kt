package com.onthecrow.wallper.splash

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider

/** The launch splash, whose icon is the W written by a brush (avd_splash_logo). */
interface SplashScreenDelegate {
    /** Call in `onCreate` before `super.onCreate`. */
    fun ComponentActivity.installSplash()
}

/**
 * The splash would go with the first frame, usually before the brush has written the whole W: on API 31+
 * it stays until the icon animation ends, then fades out. Without animations the delay is 0.
 */
class SplashScreenDelegateImpl : SplashScreenDelegate {

    override fun ComponentActivity.installSplash() {
        val splashScreen = installSplashScreen()
        // Below API 31 the activity theme has no splash icon to wait for.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener(::finishSplash)
        }
    }

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

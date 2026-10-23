package com.onthecrow.wallper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
}

package com.onthecrow.wallper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.onthecrow.wallper.core.designsystem.theme.WallperTheme
import com.onthecrow.wallper.core.navigation.NavigationProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var filesDir: File

    @Inject
    lateinit var navigationProvider: NavigationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        filesDir = this.getExternalFilesDir(null)!!
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                copyLogFile()
            }
        }
        setContent {
            WallperTheme {
                navigationProvider.Navigation(Modifier)
            }
        }
    }

    private fun copyLogFile() {
        val logFile = File(
            this.createDeviceProtectedStorageContext().filesDir,
            "wallper-log.txt",
        )
        if (logFile.exists()) {
            val extLogFile = File(filesDir.absolutePath, "wallper-log.txt")
            if (extLogFile.exists()) extLogFile.delete()
            extLogFile.writeBytes(logFile.readBytes())
        }
    }
}

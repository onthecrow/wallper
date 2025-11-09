package com.onthecrow.wallper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.onthecrow.wallper.navigation.SetupNavigation
import com.onthecrow.wallper.presentation.picker.ImagePicker
import com.onthecrow.wallper.ui.theme.WallperTheme
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
    protected lateinit var screenResolutionManager: ScreenResolutionManager

    init {
        ImagePicker.init(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filesDir = this.getExternalFilesDir(null)!!
        screenResolutionManager.obtainScreenResolution(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                copyLogFile()
            }
        }
        setContent {
            WallperTheme {
                SetupNavigation()
            }
        }
    }

    private fun copyLogFile() {
        val logFile = File(
            this.createDeviceProtectedStorageContext().filesDir,
            "wallper-log.txt",
        )
        if (logFile.exists()) {
//            val bytes = logFile.readText()
            val extLogFile = File(filesDir.absolutePath, "wallper-log.txt")
            if (extLogFile.exists()) extLogFile.delete()
            extLogFile.writeBytes(logFile.readBytes())
//            extLogFile.bufferedWriter().use {
//                it.write(bytes)
//            }
        }
    }
}

package com.onthecrow.wallper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.onthecrow.wallper.navigation.SetupNavigation
import com.onthecrow.wallper.presentation.picker.ImagePicker
import com.onthecrow.wallper.ui.theme.WallperTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    protected lateinit var screenResolutionManager: ScreenResolutionManager

    init {
        ImagePicker.init(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenResolutionManager.obtainScreenResolution(this)
        setContent {
            WallperTheme {
                SetupNavigation()
            }
        }
    }
}

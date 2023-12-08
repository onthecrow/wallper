package com.onthecrow.wallper

import android.os.Build
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import com.onthecrow.wallper.data.SettingsRepository
import com.onthecrow.wallper.data.model.ScreenResolution
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class ScreenResolutionManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
   fun obtainScreenResolution(activity: ComponentActivity) {
       val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
           val windowMetrics = activity.windowManager.currentWindowMetrics
           windowMetrics.bounds.width().toFloat() to windowMetrics.bounds.height().toFloat()
       } else {
           val displayMetrics = DisplayMetrics()
           activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
           displayMetrics.widthPixels.toFloat() to displayMetrics.heightPixels.toFloat()
       }
       MainScope().launch {
           settingsRepository.putScreenResolution(ScreenResolution(width, height))
       }
   }
}
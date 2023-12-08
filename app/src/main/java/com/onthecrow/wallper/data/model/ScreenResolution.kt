package com.onthecrow.wallper.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScreenResolution(
    val width: Float,
    val height: Float,
): Parcelable
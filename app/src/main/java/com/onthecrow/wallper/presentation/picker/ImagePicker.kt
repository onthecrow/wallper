package com.onthecrow.wallper.presentation.picker

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher

object ImagePicker {

    private var pickMediaLauncher: ActivityResultLauncher<List<String>>? = null
    private var listener: ((uri: Uri?) -> Unit)? = null

    fun init(activity: ComponentActivity) {
        pickMediaLauncher =
            activity.registerForActivityResult(PickMediaContract()) { uri ->
                listener?.invoke(uri)
            }
    }

    fun setListener(listener: (uri: Uri?) -> Unit) {
        this.listener = listener
    }

    fun removeListener() {
        listener = null
    }

    fun launch() {
        pickMediaLauncher?.launch(
            listOf("")
//            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }
}
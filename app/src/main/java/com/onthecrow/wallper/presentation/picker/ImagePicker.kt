package com.onthecrow.wallper.presentation.picker

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

object ImagePicker {

    private var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var listener: ((uri: Uri?) -> Unit)? = null

    fun init(activity: ComponentActivity) {
        pickMediaLauncher =
            activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
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
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }
}
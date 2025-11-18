package com.onthecrow.wallper.presentation.components.cropper.model

/**
 * Interface for containing multiple [CropOutline]s, currently selected item and index
 * for displaying on settings UI
 */
interface CropOutlineContainer<O : CropOutline> {
    val outlines: List<O>
    val size: Int
        get() = outlines.size
}

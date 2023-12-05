package com.onthecrow.wallper.presentation.components.cropper.model

/**
 * Interface for containing multiple [CropOutline]s, currently selected item and index
 * for displaying on settings UI
 */
interface CropOutlineContainer<O : CropOutline> {
    var selectedIndex: Int
    val outlines: List<O>
    val selectedItem: O
        get() = outlines[selectedIndex]
    val size: Int
        get() = outlines.size
}

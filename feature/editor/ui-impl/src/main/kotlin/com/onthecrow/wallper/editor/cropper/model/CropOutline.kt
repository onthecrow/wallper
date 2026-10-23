package com.onthecrow.wallper.editor.cropper.model

/**
 * Common ancestor for list of shapes, paths or images to crop inside [CropOutlineContainer]
 */
internal interface CropOutline {
    val id: Int
    val title: String
}

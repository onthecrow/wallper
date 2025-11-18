package com.onthecrow.wallper.presentation.components.cropper.state

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import com.onthecrow.wallper.presentation.components.cropper.TouchRegion
import com.onthecrow.wallper.presentation.components.cropper.model.CropData
import com.onthecrow.wallper.presentation.components.cropper.settings.CropProperties

val CropState.cropData: CropData
    get() = CropData(
        overlayRect = overlayRect,
        cropRect = cropRect
    )

/**
 * Base class for crop operations. Any class that extends this class gets access to pan, zoom,
 * rotation values and animations via [TransformState], fling and moving back to bounds animations.
 * @param imageSize size of the **Bitmap**
 * @param containerSize size of the Composable that draws **Bitmap**. This is full size
 * of the Composable. [drawAreaSize] can be smaller than [containerSize] initially based
 * on content scale of Image composable
 * @param drawAreaSize size of the area that **Bitmap** is drawn
 * @param fling when set to true dragging pointer builds up velocity. When last
 * pointer leaves Composable a movement invoked against friction till velocity drops below
 * to threshold
 * empty space on sides or edges of parent
 */
abstract class CropState internal constructor(
    imageSize: IntSize,
    containerSize: IntSize,
    drawAreaSize: IntSize,
    internal var fling: Boolean = true,
    internal var aspectRatio: Float,
    internal var overlayRatio: Float,
) : TransformState(
    imageSize = imageSize,
    containerSize = containerSize,
    drawAreaSize = drawAreaSize,
) {

    private val animatableRectOverlay = Animatable(
        getOverlayFromAspectRatio(
            containerSize.width.toFloat(),
            containerSize.height.toFloat(),
            aspectRatio,
            overlayRatio
        ),
        Rect.VectorConverter
    )

    val overlayRect: Rect
        get() = animatableRectOverlay.value

    var cropRect: Rect = Rect.Zero
        get() = getCropRectangle(
            imageSize.width,
            imageSize.height,
            drawAreaRect,
            animatableRectOverlay.targetValue
        )
        private set


    private var initialized: Boolean = false

    /**
     * Region of touch inside, corners of or outside of overlay rectangle
     */
    var touchRegion by mutableStateOf(TouchRegion.None)

    internal fun init() {
        // When initial aspect ratio doesn't match drawable area
        // overlay gets updated so updates draw area as well
        initialized = true
    }

    /**
     * Update properties of [CropState] and animate to valid intervals if required
     */
    internal open suspend fun updateProperties(
        cropProperties: CropProperties,
        forceUpdate: Boolean = false
    ) {

        if (!initialized) return

        fling = cropProperties.fling

        // Update overlay rectangle
        val aspectRatio = cropProperties.aspectRatio

        // Ratio of overlay to screen
        val overlayRatio = cropProperties.overlayRatio

        if (
            this.aspectRatio != aspectRatio ||
            this.overlayRatio != overlayRatio ||
            forceUpdate
        ) {
            this.aspectRatio = aspectRatio
            this.overlayRatio = overlayRatio

            // Update overlay rectangle based on current draw area and new aspect ratio
            animateOverlayRectTo(
                getOverlayFromAspectRatio(
                    containerSize.width.toFloat(),
                    containerSize.height.toFloat(),
                    aspectRatio,
                    overlayRatio
                )
            )
        }
    }

    /**
     * Animate overlay rectangle to target value
     */
    internal suspend fun animateOverlayRectTo(
        rect: Rect,
        animationSpec: AnimationSpec<Rect> = tween(400)
    ) {
        animatableRectOverlay.animateTo(
            targetValue = rect,
            animationSpec = animationSpec
        )
    }

    /**
     * Snap overlay rectangle to target value
     */
    internal suspend fun snapOverlayRectTo(rect: Rect) {
        animatableRectOverlay.snapTo(rect)
    }

    /*
        Touch gestures
     */
    internal abstract suspend fun onDown(change: PointerInputChange)

    internal abstract suspend fun onMove(changes: List<PointerInputChange>)

    internal abstract suspend fun onUp(change: PointerInputChange)

    /*
        Transform gestures
     */
    internal abstract suspend fun onGesture(
        centroid: Offset,
        panChange: Offset,
        zoomChange: Float,
        rotationChange: Float,
        mainPointer: PointerInputChange,
        changes: List<PointerInputChange>
    )

    internal abstract suspend fun onGestureStart()

    internal abstract suspend fun onGestureEnd(onBoundsCalculated: () -> Unit)

    // Double Tap
    internal abstract suspend fun onDoubleTap(
        offset: Offset,
        onAnimationEnd: () -> Unit
    )

    /**
     * Check if area that image is drawn covers [overlayRect]
     */
    internal fun isOverlayInImageDrawBounds(): Boolean {
        return drawAreaRect.left <= overlayRect.left &&
                drawAreaRect.top <= overlayRect.top &&
                drawAreaRect.right >= overlayRect.right &&
                drawAreaRect.bottom >= overlayRect.bottom
    }

    /**
     * Check if [rect] is inside container bounds
     */
    internal fun isRectInContainerBounds(rect: Rect): Boolean {
        return rect.left >= 0 &&
                rect.right <= containerSize.width &&
                rect.top >= 0 &&
                rect.bottom <= containerSize.height
    }



    /**
     * Calculate valid position for image draw rectangle when pointer is up. Overlay rectangle
     * should fit inside draw image rectangle to have valid bounds when calculation is completed.
     *
     * @param rectOverlay rectangle of overlay that is used for cropping
     * @param rectDrawArea rectangle of image that is being drawn
     */
    private fun calculateValidImageDrawRect(rectOverlay: Rect, rectDrawArea: Rect): Rect {

        var width = rectDrawArea.width
        var height = rectDrawArea.height

        if (width < rectOverlay.width) {
            width = rectOverlay.width
        }

        if (height < rectOverlay.height) {
            height = rectOverlay.height
        }

        var rectImageArea = Rect(offset = rectDrawArea.topLeft, size = Size(width, height))

        if (rectImageArea.left > rectOverlay.left) {
            rectImageArea = rectImageArea.translate(rectOverlay.left - rectImageArea.left, 0f)
        }

        if (rectImageArea.right < rectOverlay.right) {
            rectImageArea = rectImageArea.translate(rectOverlay.right - rectImageArea.right, 0f)
        }

        if (rectImageArea.top > rectOverlay.top) {
            rectImageArea = rectImageArea.translate(0f, rectOverlay.top - rectImageArea.top)
        }

        if (rectImageArea.bottom < rectOverlay.bottom) {
            rectImageArea = rectImageArea.translate(0f, rectOverlay.bottom - rectImageArea.bottom)
        }

        return rectImageArea
    }

    /**
     * Create [Rect] to draw overlay based on selected aspect ratio
     */
    internal fun getOverlayFromAspectRatio(
        containerWidth: Float,
        containerHeight: Float,
        aspectRatio: Float,
        coefficient: Float
    ): Rect {

        val overlayWidthMax = containerWidth * coefficient
        val overlayHeightMax = containerHeight * coefficient

        val aspectRatioValue = aspectRatio

        var width = overlayWidthMax
        var height = overlayWidthMax / aspectRatioValue

        if (height > overlayHeightMax) {
            height = overlayHeightMax
            width = height * aspectRatioValue
        }

        val offsetX = (containerWidth - width) / 2f
        val offsetY = (containerHeight - height) / 2f

        return Rect(offset = Offset(offsetX, offsetY), size = Size(width, height))
    }

    /**
     * Get crop rectangle
     */
    private fun getCropRectangle(
        bitmapWidth: Int,
        bitmapHeight: Int,
        drawAreaRect: Rect,
        overlayRect: Rect
    ): Rect {

        if (drawAreaRect == Rect.Zero || overlayRect == Rect.Zero) return Rect(
            offset = Offset.Zero,
            Size(bitmapWidth.toFloat(), bitmapHeight.toFloat())
        )

        // Calculate latest image draw area based on overlay position
        // This is valid rectangle that contains crop area inside overlay
        val newRect = calculateValidImageDrawRect(overlayRect, drawAreaRect)

        val overlayWidth = overlayRect.width
        val overlayHeight = overlayRect.height

        val drawAreaWidth = newRect.width
        val drawAreaHeight = newRect.height

        val widthRatio = overlayWidth / drawAreaWidth
        val heightRatio = overlayHeight / drawAreaHeight

        val diffLeft = overlayRect.left - newRect.left
        val diffTop = overlayRect.top - newRect.top

        val croppedBitmapLeft = (diffLeft * (bitmapWidth / drawAreaWidth))
        val croppedBitmapTop = (diffTop * (bitmapHeight / drawAreaHeight))

        val croppedBitmapWidth = bitmapWidth * widthRatio
        val croppedBitmapHeight = bitmapHeight * heightRatio

        return Rect(
            offset = Offset(croppedBitmapLeft, croppedBitmapTop),
            size = Size(croppedBitmapWidth, croppedBitmapHeight)
        )
    }
}

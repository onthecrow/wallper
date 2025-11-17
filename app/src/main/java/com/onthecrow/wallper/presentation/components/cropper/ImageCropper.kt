package com.onthecrow.wallper.presentation.components.cropper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.onthecrow.wallper.presentation.components.cropper.draw.DrawingOverlay
import com.onthecrow.wallper.presentation.components.cropper.model.RectCropShape
import com.onthecrow.wallper.presentation.components.cropper.settings.CropDefaults
import com.onthecrow.wallper.presentation.components.cropper.settings.CropProperties
import com.onthecrow.wallper.presentation.components.cropper.settings.CropStyle
import com.onthecrow.wallper.presentation.components.cropper.settings.CropType
import com.onthecrow.wallper.presentation.components.cropper.state.DynamicCropState
import com.onthecrow.wallper.presentation.components.cropper.state.rememberCropState
import kotlinx.coroutines.delay

@Composable
fun ImageCropper(
    modifier: Modifier = Modifier,
    videoUri: String,
    imageBitmap: ImageBitmap,
    contentDescription: String?,
    cropStyle: CropStyle = CropDefaults.style(),
    cropProperties: CropProperties,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    backgroundColor: Color = Color.Transparent,
    cropRect: (Rect) -> Unit,
    onDrawGrid: (DrawScope.(rect: Rect, strokeWidth: Float, color: Color) -> Unit)? = null,
) {

    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
//        contentScale = cropProperties.contentScale,
//        contentDescription = contentDescription,
//        filterQuality = filterQuality,
//        imageBitmap = imageBitmap,
//        drawImage = false
    ) {

        // No crop operation is applied by ScalableImage so rect points to bounds of original
        // bitmap
//        val scaledImageBitmap = getScaledImageBitmap(
//            imageWidth = imageWidth,
//            imageHeight = imageHeight,
//            rect = rect,
//            bitmap = imageBitmap,
//            contentScale = cropProperties.contentScale,
//        )

        // Container Dimensions
        val containerWidthPx = constraints.maxWidth
        val containerHeightPx = constraints.maxHeight

        val containerWidth: Dp
        val containerHeight: Dp

        // Bitmap Dimensions
        // todo change for the actual size of the video
        val bitmapWidth = 3840
        val bitmapHeight = 2160

        // Dimensions of Composable that displays Bitmap
        val imageWidthPx: Int
        val imageHeightPx: Int

        with(LocalDensity.current) {
            imageWidthPx = containerWidthPx
            imageHeightPx = containerHeightPx
            containerWidth = containerWidthPx.toDp()
            containerHeight = containerHeightPx.toDp()
        }

        val cropType = cropProperties.cropType
        val contentScale = cropProperties.contentScale
        val fixedAspectRatio = cropProperties.fixedAspectRatio
        val cropOutline = cropProperties.cropOutlineProperty.cropOutline

        // these keys are for resetting cropper when image width/height, contentScale or
        // overlay aspect ratio changes
        val resetKeys =
            getResetKeys(
                imageWidthPx,
                imageHeightPx,
                contentScale,
                cropType,
                fixedAspectRatio
            )

        val cropState = rememberCropState(
            imageSize = IntSize(bitmapWidth, bitmapHeight),
            containerSize = IntSize(containerWidthPx, containerHeightPx),
            drawAreaSize = IntSize(imageWidthPx, imageHeightPx),
            cropProperties = cropProperties,
            keys = resetKeys
        )

        val isHandleTouched by remember(cropState) {
            derivedStateOf {
                cropState is DynamicCropState && handlesTouched(cropState.touchRegion)
            }
        }

        val pressedStateColor = remember(cropStyle.backgroundColor){
            cropStyle.backgroundColor
                .copy(cropStyle.backgroundColor.alpha * .7f)
        }

        val transparentColor by animateColorAsState(
            animationSpec = tween(300, easing = LinearEasing),
            targetValue = if (isHandleTouched) pressedStateColor else cropStyle.backgroundColor
        )

        cropRect(cropState.cropRect)

        val imageModifier = Modifier
            .size(containerWidth, containerHeight)
            .crop(
                keys = resetKeys,
                cropState = cropState
            )

        LaunchedEffect(key1 = cropProperties) {
            cropState.updateProperties(cropProperties)
        }

        /// Create a MutableTransitionState<Boolean> for the AnimatedVisibility.
        var visible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(100)
            visible = true
        }

        ImageCropper(
            modifier = imageModifier,
            videoUri = videoUri,
            visible = visible,
            handleSize = cropProperties.handleSize,
            overlayRect = cropState.overlayRect,
            cropType = cropType,
            cropOutline = cropOutline,
            cropStyle = cropStyle,
            transparentColor = transparentColor,
            backgroundColor = backgroundColor,
            onDrawGrid = onDrawGrid,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ImageCropper(
    modifier: Modifier,
    videoUri: String,
    visible: Boolean,
    handleSize: Float,
    cropType: CropType,
    cropOutline: RectCropShape,
    cropStyle: CropStyle,
    overlayRect: Rect,
    transparentColor: Color,
    backgroundColor: Color,
    onDrawGrid: (DrawScope.(rect: Rect, strokeWidth: Float, color: Color) -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(tween(500))
        ) {

            Box(contentAlignment = Alignment.Center) {
                // todo draw video
                VideoSurface(videoUri)

                val drawOverlay = cropStyle.drawOverlay

                val drawGrid = cropStyle.drawGrid
                val overlayColor = cropStyle.overlayColor
                val handleColor = cropStyle.handleColor
                val drawHandles = cropType == CropType.Dynamic
                val strokeWidth = cropStyle.strokeWidth

                DrawingOverlay(
                    modifier = modifier,
                    drawOverlay = drawOverlay,
                    rect = overlayRect,
                    cropOutline = cropOutline,
                    drawGrid = drawGrid,
                    overlayColor = overlayColor,
                    handleColor = handleColor,
                    strokeWidth = strokeWidth,
                    drawHandles = drawHandles,
                    handleSize = handleSize,
                    transparentColor = transparentColor,
                    onDrawGrid = onDrawGrid,
                )
            }
        }
    }
}

@Composable
private fun getResetKeys(
    imageWidthPx: Int,
    imageHeightPx: Int,
    contentScale: ContentScale,
    cropType: CropType,
    fixedAspectRatio: Boolean,
) = remember(
    imageWidthPx,
    imageHeightPx,
    contentScale,
    cropType,
    fixedAspectRatio,
) {
    arrayOf(
        imageWidthPx,
        imageHeightPx,
        contentScale,
        cropType,
        fixedAspectRatio,
    )
}
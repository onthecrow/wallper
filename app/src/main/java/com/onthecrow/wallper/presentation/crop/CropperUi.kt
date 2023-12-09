@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.presentation.crop

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.R
import com.onthecrow.wallper.presentation.components.cropper.ImageCropper
import com.onthecrow.wallper.presentation.components.cropper.model.AspectRatio
import com.onthecrow.wallper.presentation.components.cropper.model.OutlineType
import com.onthecrow.wallper.presentation.components.cropper.model.RectCropShape
import com.onthecrow.wallper.presentation.components.cropper.settings.CropDefaults
import com.onthecrow.wallper.presentation.components.cropper.settings.CropOutlineProperty
import com.onthecrow.wallper.presentation.crop.model.CropperState


@Composable
fun CropperUi(uiState: CropperState) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = it.calculateBottomPadding(),
                    top = it.calculateTopPadding(),
                )
        ) {
            val rect = remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }

            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .weight(1f)
                    .aspectRatio(uiState.bitmap.width.toFloat() / uiState.bitmap.height.toFloat())
                    .align(Alignment.CenterHorizontally)
            ) {

                val handleSize: Float = LocalDensity.current.run { 20.dp.toPx() }

                val cropProperties by remember {
                    mutableStateOf(
                        CropDefaults.properties(
                            cropOutlineProperty = CropOutlineProperty(
                                OutlineType.Rect,
                                RectCropShape(0, "Rect")
                            ),
                            pannable = false,
                            zoomable = false,
                            overlayRatio = 1f,
                            fling = false,
                            aspectRatio = AspectRatio(uiState.screenWidth / uiState.screenHeight),
                            fixedAspectRatio = true,
                            handleSize = handleSize
                        )
                    )
                }
                val cropStyle by remember { mutableStateOf(CropDefaults.style()) }

                val crop by remember { mutableStateOf(false) }

                ImageCropper(
                    modifier = Modifier
                        .fillMaxSize(),
                    imageBitmap = uiState.bitmap,
                    contentDescription = "Image Cropper",
                    cropStyle = cropStyle,
                    cropProperties = cropProperties,
                    crop = crop,
                    cropRect = {
                        rect.value = it
                    },
                    onCropStart = {

                    },
                    onCropSuccess = {

                    },
                )
            }
            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(start = 32.dp, bottom = 32.dp, end = 32.dp),
                onClick = {
                    Toast.makeText(
                        context,
                        "Left: ${rect.value.left}, top: ${rect.value.top}, right: ${rect.value.right}, bottom: ${rect.value.bottom}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Text(text = stringResource(id = R.string.save))
            }
        }
    }
}
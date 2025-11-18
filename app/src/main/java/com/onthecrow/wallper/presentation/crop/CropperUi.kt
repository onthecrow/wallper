@file:OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterial3Api::class
)

package com.onthecrow.wallper.presentation.crop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onthecrow.wallper.R
import com.onthecrow.wallper.crop.VideoCroppingStatus
import com.onthecrow.wallper.presentation.components.cropper.ImageCropper
import com.onthecrow.wallper.presentation.components.cropper.settings.CropDefaults
import com.onthecrow.wallper.presentation.crop.model.CropperEvent
import com.onthecrow.wallper.presentation.crop.model.CropperState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber


@Composable
fun CropperUi(
    uiState: CropperState,
    conversionStateChannel: Channel<VideoCroppingStatus>,
    onEventSend: (CropperEvent) -> Unit,
) {
    val channel =
        conversionStateChannel.receiveAsFlow().collectAsState(VideoCroppingStatus.Undefinite)
    Scaffold {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = it.calculateBottomPadding(),
                        top = it.calculateTopPadding(),
                    ),
                verticalArrangement = Arrangement.Bottom,
            ) {
                val rect = remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }

                if (uiState.screenHeight != 1f) {
                    Box(
                        modifier = Modifier
                            .padding(32.dp)
                            .weight(1f)
                            .aspectRatio(uiState.videoSize.width.toFloat() / uiState.videoSize.height.toFloat())
                            .align(Alignment.CenterHorizontally)
                    ) {

                        val handleSize: Float = LocalDensity.current.run { 20.dp.toPx() }

                        val cropProperties by remember {
                            mutableStateOf(
                                CropDefaults.properties(
                                    overlayRatio = 1f,
                                    aspectRatio = uiState.screenWidth / uiState.screenHeight,
                                    fixedAspectRatio = true,
                                    handleSize = handleSize
                                )
                            )
                        }
                        val cropStyle by remember { mutableStateOf(CropDefaults.style()) }

                        ImageCropper(
                            modifier = Modifier
                                .fillMaxSize(),
                            videoUri = uiState.originalFilePath,
                            videoSize = uiState.videoSize,
                            cropStyle = cropStyle,
                            cropProperties = cropProperties,
                            cropRect = { cropRect ->
                                Timber.d("cropRect: $cropRect")
                                rect.value = cropRect
                            },
                        )
                    }
                }

                RangeSlider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    value = uiState.timeLineRange,
                    onValueChange = { onEventSend.invoke(CropperEvent.TimeLineRangeChanged(it)) },
                )

                Row(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = uiState.isAdditionalProcessing,
                        onCheckedChange = { onEventSend.invoke(CropperEvent.ToggleAdditionalProcessing) }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Additional processing")
                    IconButton(onClick = { onEventSend.invoke(CropperEvent.ShowAdditionalProcessingInfo) }) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_question_mark),
                            contentDescription = "",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp, bottom = 16.dp, top = 8.dp)
                ) {
                    Button(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = { onEventSend.invoke(CropperEvent.CreateWallpaper(rect.value)) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_save_outline),
                                contentDescription = "",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = "SAVE")
                        }
                    }
                }

            }
            channel.value.let { status ->
                if (status is VideoCroppingStatus.InProgress) {
                    Dialog(
                        onDismissRequest = { },
                        DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(16.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            item { Text(text = if (status.remainingTime == -1) "Remaining time(s): N/A" else "Remaining time(s): ${status.remainingTime}") }
                            item { Text(text = if (status.elapsedTime == -1) "Elapsed time(s): N/A" else "Elapsed time(s): ${status.elapsedTime}") }
                            item {
                                LinearProgressIndicator(
                                    progress = (status.progress.toFloat() / 100f),
                                )
                            }
                        }
                    }
                }
                if (status is VideoCroppingStatus.Error) {
                    ErrorDialog(error = status.error)
                }
            }
        }
    }
}

@Preview
@Composable
fun CropperUiPreview() {
    CropperUi(uiState = CropperState(), conversionStateChannel = Channel()) {

    }
}

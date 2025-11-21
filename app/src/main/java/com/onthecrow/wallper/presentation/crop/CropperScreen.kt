package com.onthecrow.wallper.presentation.crop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.onthecrow.wallper.core.actions.HandleUiActions
import com.onthecrow.wallper.navigation.LocalNavController
import com.onthecrow.wallper.presentation.crop.model.CropperAction
import com.onthecrow.wallper.presentation.crop.model.CropperState
import com.onthecrow.wallper.presentation.wallpapers.popUpToWallpapersScreen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import timber.log.Timber

@OptIn(FlowPreview::class)
@Composable
fun CropperScreen() {
    val viewModel = hiltViewModel<CropperViewModel>()
    val uiState = viewModel.uiState
        .collectAsState(CropperState()).value
    val sampledFlow = remember(viewModel) {
        viewModel.uiState
            .sample(300)
            .onEach { Timber.d("#### sample") }
    }
    val seekerState = sampledFlow.collectAsState(CropperState()).value
    val context = LocalContext.current
    val navController = LocalNavController.current

    CropperUi(
        uiState = uiState,
        playerState = seekerState,
        conversionStateChannel = viewModel.conversionChannel,
        onEventSend = { event -> viewModel.sendEvent(event) },
    )

    HandleUiActions(viewModel) { action ->
        when (action) {
            CropperAction.NavigateToWallpapersScreen ->
                navController.popUpToWallpapersScreen()
        }
    }
}
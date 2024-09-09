package com.onthecrow.wallper.presentation.crop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.onthecrow.wallper.core.actions.HandleUiActions
import com.onthecrow.wallper.navigation.LocalNavController
import com.onthecrow.wallper.presentation.crop.model.CropperAction
import com.onthecrow.wallper.presentation.crop.model.CropperEvent
import com.onthecrow.wallper.presentation.wallpapers.popUpToWallpapersScreen

@Composable
fun CropperScreen() {
    val viewModel = hiltViewModel<CropperViewModel>()
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current
    val navController = LocalNavController.current

    CropperUi(
        uiState = uiState,
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
package com.onthecrow.wallper.presentation.crop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.onthecrow.wallper.core.actions.HandleUiActions

@Composable
fun CropperScreen() {
    val viewModel = hiltViewModel<CropperViewModel>()
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current

    CropperUi(
        uiState = uiState,
    )

    HandleUiActions(viewModel) { _ ->
        TODO("Actions are not implemented yet")
    }
}
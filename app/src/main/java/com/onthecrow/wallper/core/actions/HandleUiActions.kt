package com.onthecrow.wallper.core.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.onthecrow.wallper.core.viewmodel.BaseState
import com.onthecrow.wallper.core.viewmodel.BaseViewModel

@Composable
fun <State : BaseState<Action>, Action> HandleUiActions(
    viewModel: BaseViewModel<State, Action, *>,
    handler: (Action) -> Unit
) {
    val actions = viewModel.uiState.collectAsState().value.actions

    LaunchedEffect(actions) {
        actions.firstOrNull()?.let { action ->
            handler(action)
            viewModel.completedAction(action)
        }
    }
}

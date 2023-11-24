package com.onthecrow.wallper.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.onthecrow.wallper.R
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.presentation.components.FAB
import com.onthecrow.wallper.presentation.wallpaperlist.WallpaperListState
import com.onthecrow.wallper.presentation.list.ListUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: State<WallpaperListState>,
    onAddClick: () -> Unit,
    onItemClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    Image(
                        modifier = Modifier.clickable { onSettingsClick() },
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = ""
                    )
                }
            )
        },
        floatingActionButton = { FAB(onAddClick) }
    ) {
        ListUI(
            modifier = Modifier.padding(
                top = it.calculateTopPadding(),
                bottom = it.calculateBottomPadding()
            ),
            state.value.items,
            onItemClick,
        )
    }
}

@Preview
@Composable
fun MainScreenPreview() {
    val state = remember {
        mutableStateOf(
            WallpaperListState(
                listOf(
                    WallpaperEntity("", "", false),
                    WallpaperEntity("", "", false),
                    WallpaperEntity("", "", false),
                    WallpaperEntity("", "", true),
                    WallpaperEntity("", "", false),
                )
            )
        )
    }
    MainScreen(state, {}, {}) {}
}
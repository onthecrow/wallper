@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.presentation.wallpapers

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.R
import com.onthecrow.wallper.presentation.components.FAB
import com.onthecrow.wallper.presentation.wallpapers.models.WallpapersState

@Composable
fun WallpapersUi(
    uiState: WallpapersState,
    onWallpaperClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    Image(
                        modifier = Modifier.clickable { onSettingsClick() }
                            .padding(9.dp)
                            .size(30.dp),
                        painter = painterResource(id = R.drawable.ic_settings),
                        colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onBackground),
                        contentDescription = "",
                    )
                }
            )
        },
        floatingActionButton = { FAB { onAddClick() } }
    ) {
        LazyVerticalGrid(
            modifier = Modifier.padding(
                top = it.calculateTopPadding(),
                bottom = it.calculateBottomPadding()
            ),
            contentPadding = PaddingValues(8.dp),
            columns = GridCells.Fixed(2)
        ) {
            uiState.items.forEach {
                item {
                    WallpaperCard(it) { onWallpaperClick.invoke(it.id) }
                }
            }
        }
    }
}
@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.wallpapers

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.core.designsystem.R as DesignR
import com.onthecrow.wallper.core.designsystem.component.FAB
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import com.onthecrow.wallper.wallpapers.ui.R
import com.onthecrow.wallper.wallpapers.view.DeleteWallpaperDialog
import com.onthecrow.wallper.wallpapers.view.WallpaperCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
internal fun WallpapersScreen(
    state: WallpapersState,
    eventFlow: Flow<WallpapersEvent>,
    onEvent: (WallpapersEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val unsupportedFileMessage = stringResource(R.string.unsupported_file)
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onEvent(WallpapersEvent.OnMediaPicked(it.toString())) }
    }

    LaunchedEffect(eventFlow) {
        eventFlow.collect { event ->
            when (event) {
                WallpapersEvent.OnUnsupportedMediaPicked ->
                    Toast.makeText(context, unsupportedFileMessage, Toast.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_wallper_title),
                        colorFilter = ColorFilter.tint(color = LocalContentColor.current),
                        contentDescription = stringResource(id = DesignR.string.app_name),
                    )
                },
                actions = {
                    IconButton(onClick = { onEvent(WallpapersEvent.OnSettingsClick) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = stringResource(id = R.string.settings),
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FAB(contentDescription = stringResource(R.string.add_wallpaper)) {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
        }
    ) {
        LazyVerticalGrid(
            modifier = Modifier.padding(
                top = it.calculateTopPadding(),
                bottom = it.calculateBottomPadding()
            ),
            contentPadding = PaddingValues(8.dp),
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.items, { wallpaper -> wallpaper.id }) { wallpaper ->
                WallpaperCard(
                    wallpaper = wallpaper,
                    onClick = { onEvent(WallpapersEvent.OnWallpaperClick(wallpaper.id)) },
                    onEditClick = {
                        val builtIn = wallpaper.builtIn
                        onEvent(
                            if (builtIn != null) {
                                WallpapersEvent.OnBuiltInSettingsClick(builtIn)
                            } else {
                                WallpapersEvent.OnEditClick(wallpaper.id)
                            }
                        )
                    },
                    onDeleteClick = { onEvent(WallpapersEvent.OnDeleteClick(wallpaper.id)) },
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth()
                        .animateItem(),
                )
            }
        }
    }

    state.deleteCandidate?.let { id ->
        DeleteWallpaperDialog(
            onConfirm = { onEvent(WallpapersEvent.OnDeleteConfirm(id)) },
            onDismiss = { onEvent(WallpapersEvent.OnDeleteDismiss) },
        )
    }
}

@Preview
@Composable
private fun WallpapersScreenPreview() {
    WallpapersScreen(
        state = WallpapersState(
            items = listOf(
                WallpaperItem(1, null, false, BuiltInWallpaper.BLACK_HOLE),
                WallpaperItem(2, "", true),
                WallpaperItem(3, "", false, isOptimised = true),
            ),
        ),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}

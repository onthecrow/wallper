@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.wallpapers

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.core.designsystem.R as DesignR
import com.onthecrow.wallper.core.designsystem.component.FAB
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import com.onthecrow.wallper.wallpapers.ui.R
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
                is WallpapersEvent.OpenLiveWallpaperChooser -> context.startActivity(event.intent)
                WallpapersEvent.OnUnsupportedMediaPicked ->
                    Toast.makeText(context, unsupportedFileMessage, Toast.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = DesignR.string.app_name)) },
                actions = {
                    Image(
                        modifier = Modifier
                            .clickable { onEvent(WallpapersEvent.OnSettingsClick) }
                            .padding(9.dp)
                            .size(30.dp),
                        painter = painterResource(id = R.drawable.ic_settings),
                        colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onBackground),
                        contentDescription = "",
                    )
                }
            )
        },
        floatingActionButton = {
            FAB {
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
                var expanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth()
                        .animateItem(),
                ) {
                    WallpaperCard(
                        wallpaper = wallpaper,
                        onCardClick = { onEvent(WallpapersEvent.OnWallpaperClick(wallpaper.id)) },
                        onCardLongClick = { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        offset = DpOffset(x = 0.dp, y = 8.dp),
                        onDismissRequest = { expanded = false },
                    ) {
                        val builtIn = wallpaper.builtIn
                        if (builtIn != null) {
                            // Built-ins have no media to edit and can't be deleted.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                onClick = {
                                    expanded = false
                                    onEvent(WallpapersEvent.OnBuiltInSettingsClick(builtIn))
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                onClick = {
                                    // TODO Edit mode (ARCHITECTURE_MIGRATION.md, TODO): open the editor for this wallpaper.
                                    expanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    expanded = false
                                    onEvent(WallpapersEvent.OnDeleteClick(wallpaper.id))
                                },
                            )
                        }
                    }
                }
            }
        }
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
                WallpaperItem(3, "", false),
            ),
        ),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}

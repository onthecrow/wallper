@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.wallpapers

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onthecrow.wallper.core.designsystem.R as DesignR
import com.onthecrow.wallper.core.designsystem.component.DeleteWallpaperDialog
import com.onthecrow.wallper.core.designsystem.component.FAB
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import com.onthecrow.wallper.wallpapers.ui.R
import com.onthecrow.wallper.wallpapers.view.AddWallpaperDialog
import com.onthecrow.wallper.wallpapers.view.WallpaperCard
import com.onthecrow.wallper.wallpapers.view.WallperTitle
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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

    // Back from the system wallpaper picker or from anywhere else the wallpaper could have changed.
    LifecycleResumeEffect(Unit) {
        onEvent(WallpapersEvent.OnResume)
        onPauseOrDispose { }
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

    val gridState = rememberLazyGridState()
    val hazeState = rememberHazeState()
    // The list goes on under the bar, which is clear: at the list's top the active card's glow reaches over it.
    // Scrolled, what goes under the bar is blurred and dimmed, the most at the top and not at all at its bottom
    // edge, fading in and out.
    val isScrolled by remember { derivedStateOf { gridState.canScrollBackward } }
    val barHaze by animateFloatAsState(targetValue = if (isScrolled) 1f else 0f, label = "bar haze")
    val background = MaterialTheme.colorScheme.background

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { WallperTitle(writes = state.isWallpaperSet) },
                modifier = Modifier.hazeEffect(state = hazeState) {
                    alpha = barHaze
                    // What is behind the list, under its gaps.
                    backgroundColor = background
                    blurRadius = BarBlurRadius
                    noiseFactor = 0f
                    progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                    // The background's colour, so it shades a dark theme and keeps the title readable in a light one.
                    tints = listOf(
                        HazeTint(Brush.verticalGradient(0f to background.copy(alpha = BarDimAlpha), 1f to Color.Transparent)),
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    // As bright as the title: Material dims trailing icons (onSurfaceVariant).
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
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
                onEvent(WallpapersEvent.OnAddClick)
            }
        }
    ) {
        // The grid goes on under the bar and the system's navigation, so that the active card's glow does too
        // instead of ending in a line at the grid's edge. It fills the screen even with a row or two: a lazy grid
        // is only as tall as its content otherwise, and clips the glow below the last row.
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            // At the end the last row stops above the «+», not under it.
            contentPadding = PaddingValues(
                start = 8.dp,
                top = it.calculateTopPadding() + 8.dp,
                end = 8.dp,
                bottom = it.calculateBottomPadding() + ListEndPadding,
            ),
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
                            when {
                                builtIn != null -> WallpapersEvent.OnBuiltInSettingsClick(builtIn)
                                wallpaper.isGroup -> WallpapersEvent.OnGroupEditClick(wallpaper.id)
                                else -> WallpapersEvent.OnEditClick(wallpaper.id)
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
        val isGroup = state.items.firstOrNull { it.id == id }?.isGroup == true
        DeleteWallpaperDialog(
            onConfirm = { onEvent(WallpapersEvent.OnDeleteConfirm(id)) },
            onDismiss = { onEvent(WallpapersEvent.OnDeleteDismiss) },
            // A group keeps no files: only it goes.
            text = if (isGroup) stringResource(R.string.delete_group_text) else stringResource(DesignR.string.delete_wallpaper_text),
        )
    }

    if (state.isAddDialogShown) {
        AddWallpaperDialog(
            offersSeries = state.offersSeries,
            onMediaClick = {
                onEvent(WallpapersEvent.OnAddMediaClick)
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onSeriesClick = { onEvent(WallpapersEvent.OnAddSeriesClick) },
            onDismiss = { onEvent(WallpapersEvent.OnAddDismiss) },
        )
    }
}

/** The «+» (56 dp) and its 16 dp margin, and the grid's own 8 dp gap above it. */
private val ListEndPadding = 56.dp + 16.dp + 8.dp

/** The blur at the top of the bar over a scrolled list, and how far the background dims it there. */
private val BarBlurRadius = 24.dp
private const val BarDimAlpha = 0.5f

@Preview
@Composable
private fun WallpapersScreenPreview() {
    WallpapersScreen(
        state = WallpapersState(
            items = listOf(
                WallpaperItem(1, null, false, BuiltInWallpaper.BLACK_HOLE),
                WallpaperItem(2, "", true),
                WallpaperItem(3, "", false, isOptimised = true),
                WallpaperItem(4, "", false, isGroup = true),
            ),
        ),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}

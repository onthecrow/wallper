package com.onthecrow.wallper.presentation.list

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.presentation.wallpaperlist.WallpaperCard

@Composable
fun ListUI(
    modifier: Modifier,
    items: List<WallpaperEntity>,
    onItemClick: (Int) -> Unit
) {
    LazyVerticalGrid(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        columns = GridCells.Fixed(2)
    ) {
        items.forEach {
            item {
                WallpaperCard(it) { onItemClick.invoke(it.uid) }
            }
        }
    }
}
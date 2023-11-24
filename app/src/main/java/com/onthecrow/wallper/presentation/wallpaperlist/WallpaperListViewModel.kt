package com.onthecrow.wallper.presentation.wallpaperlist

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.onthecrow.wallper.WallperApplication
import com.onthecrow.wallper.data.WallpaperDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class WallpaperListViewModel(
    wallpaperDao: WallpaperDao = WallperApplication.db!!.wallpaperDao()
) : ViewModel() {

    // TODO add mapping mechanism for state
    val state: StateFlow<WallpaperListState> = wallpaperDao.getAll()
        .map { WallpaperListState(it) }
        .map { mainState ->
            mainState.copy(items = mainState.items.map { wallpaperEntity ->
                wallpaperEntity.apply {
                    bitmap = BitmapFactory.decodeFile(wallpaperEntity.thumbnailUri).asImageBitmap()
                }
            })
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, WallpaperListState(listOf()))

    fun activateWallpaper(it: Int) {
        viewModelScope.launch {
            WallperApplication.db?.run {
                withTransaction {
                    with(wallpaperDao()) {
                        val activeOld = getActive().first().copy(isActive = false)
                        val activeNew = getWallpaper(it).copy(isActive = true)

                        update(activeNew)
                        update(activeOld)
                    }
                }
            }
        }
    }
}
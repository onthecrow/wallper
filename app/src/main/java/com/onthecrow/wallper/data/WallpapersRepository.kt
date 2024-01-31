package com.onthecrow.wallper.data

import android.graphics.Rect
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class WallpapersRepository @Inject constructor(
    private val appDatabase: AppDatabase
) {

    suspend fun saveWallpaper(
        originalFileUri: String,
        rect: Rect,
        isVideo: Boolean = false,
        thumbnailUri: String = "",
        isActive: Boolean = false,
    ) {
        appDatabase.wallpaperDao().insertAll(
            WallpaperEntity(
                thumbnailUri = thumbnailUri,
                originalUri = originalFileUri,
                isActive = isActive,
                shownRect = rect,
                isVideo = isVideo,
            )
        )
    }

    fun getWallpapers(): Flow<List<WallpaperEntity>> {
        return appDatabase.wallpaperDao().getAll()
    }

    fun getActiveWallpaper(): Flow<WallpaperEntity> {
        return appDatabase.wallpaperDao().getActive()
    }

    suspend fun activateWallpaper(id: Int) {
        appDatabase.run {
            withTransaction {
                with(wallpaperDao()) {
                    getActive().firstOrNull()?.run {
                        update(copy(isActive = false))
                    }
                    val activeNew = getWallpaper(id).copy(isActive = true)
                    update(activeNew)
                }
            }
        }
    }
}
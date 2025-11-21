package com.onthecrow.wallper.data

import android.graphics.Rect
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject

class WallpapersRepository @Inject constructor(
    private val appDatabase: AppDatabase
) {

    suspend fun saveWallpaper(
        originalFileUri: String,
        rect: Rect,
        thumbnailUri: String = "",
        croppedFilePath: String,
        startTime: Long = 0,
        endTime: Long = 0,
        isActive: Boolean = false,
        isVideo: Boolean = false,
        isProcessed: Boolean,
        startPosition: Long,
        endPosition: Long,
    ) {
        appDatabase.wallpaperDao().insertAll(
            WallpaperEntity(
                originalUri = originalFileUri,
                thumbnailUri = thumbnailUri,
                processedUri = croppedFilePath,
                shownRect = rect,
                startTime = startTime,
                endTime = endTime,
                isActive = isActive,
                isProcessed = isProcessed,
                isVideo = isVideo,
                startPosition = startPosition,
                endPosition = endPosition,
            )
        )
    }

    fun getWallpapers(): Flow<List<WallpaperEntity>> {
        return appDatabase.wallpaperDao().getAll()
    }

    fun getActiveWallpaper(): Flow<WallpaperEntity?> {
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
                    Timber.d("#### ${activeNew.originalUri}")
                    update(activeNew)
                }
            }
        }
    }

    suspend fun getWallpaper(id: Int): WallpaperEntity {
        return appDatabase.wallpaperDao().getWallpaper(id)
    }

    suspend fun deleteWallpaper(wallpaperEntity: WallpaperEntity) {
        appDatabase.wallpaperDao().delete(wallpaperEntity)
    }
}

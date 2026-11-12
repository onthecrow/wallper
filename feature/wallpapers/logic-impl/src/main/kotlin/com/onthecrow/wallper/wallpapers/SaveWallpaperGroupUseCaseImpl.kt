package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.SaveWallpaperGroupUseCase
import com.onthecrow.wallper.wallpapers.mapper.isGroup
import javax.inject.Inject

internal class SaveWallpaperGroupUseCaseImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
    private val activateWallpaperUseCase: ActivateWallpaperUseCase,
    private val appSettings: AppSettingsSource,
) : SaveWallpaperGroupUseCase {
    override suspend fun invoke(id: Int?, memberIds: List<Int>, slideDuration: SlideDuration): Int {
        require(memberIds.isNotEmpty()) { "A group needs members" }
        if (id == null) {
            val newId = wallpapersRepository.insertGroup(memberIds, slideDuration)
            // Like a new wallpaper from the editor.
            if (appSettings.settings.value.activateNewWallpapers) activateWallpaperUseCase(newId)
            return newId
        }
        val old = checkNotNull(wallpapersRepository.get(id)) { "No wallpaper $id" }
        check(old.isGroup) { "Wallpaper $id isn't a group" }
        wallpapersRepository.updateGroup(id, memberIds, slideDuration, isActive = old.isActive)
        return id
    }
}

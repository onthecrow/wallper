@file:OptIn(ExperimentalEncodingApi::class)

package com.onthecrow.wallper.presentation.wallpapers

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.viewmodel.BaseViewModel
import com.onthecrow.wallper.domain.ActivateWallpaperUseCase
import com.onthecrow.wallper.domain.GetWallpapersUseCase
import com.onthecrow.wallper.presentation.picker.ImagePicker
import com.onthecrow.wallper.presentation.wallpapers.models.Wallpaper
import com.onthecrow.wallper.presentation.wallpapers.models.WallpapersAction
import com.onthecrow.wallper.presentation.wallpapers.models.WallpapersEvent
import com.onthecrow.wallper.presentation.wallpapers.models.WallpapersState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.io.encoding.ExperimentalEncodingApi


@HiltViewModel
class WallpapersViewModel @Inject constructor(
    getWallpapersUseCase: GetWallpapersUseCase,
    private val activateWallpaperUseCase: ActivateWallpaperUseCase,
) : BaseViewModel<WallpapersState, WallpapersAction, WallpapersEvent>(WallpapersState()) {

    init {
        ImagePicker.setListener { uri ->
            if (uri != null) {
                performAction(WallpapersAction.NavigateToCropper(uri.toString()))
            }
        }
        getWallpapersUseCase()
            .map { wallpapers -> wallpapers.map(Wallpaper::mapFromDomain) }
            .onEach { wallpapers -> updateUiState { copy(items = wallpapers) } }
            .catch { updateUiState { copy(error = it.message ?: "") } }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    fun addWallpaper() {
        ImagePicker.launch()
    }

    override fun onCleared() {
        super.onCleared()
        ImagePicker.removeListener()
    }

    override fun sendEvent(uiEvent: WallpapersEvent) = when (uiEvent) {
        is WallpapersEvent.OnWallpaperClick -> activateWallpaper(uiEvent.wallpaperId)
    }

    private fun activateWallpaper(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            activateWallpaperUseCase(id)
        }
    }
}
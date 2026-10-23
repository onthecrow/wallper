@file:OptIn(ExperimentalLayoutApi::class)

package com.onthecrow.wallper.blackhole

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.blackhole.gl.BlackHolePreview
import com.onthecrow.wallper.blackhole.gl.DragMode
import com.onthecrow.wallper.blackhole.model.DebugSlider
import com.onthecrow.wallper.blackhole.model.DebugToggle
import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.blackhole.physics.QualityLevel
import com.onthecrow.wallper.blackhole.renderer.DebugView
import com.onthecrow.wallper.blackhole.renderer.LensMode
import com.onthecrow.wallper.blackhole.ui.R
import com.onthecrow.wallper.blackhole.view.ChoiceRow
import com.onthecrow.wallper.blackhole.view.ControlsSheet
import com.onthecrow.wallper.blackhole.view.PreviewStats
import com.onthecrow.wallper.blackhole.view.SHEET_HEIGHT_FRACTION
import com.onthecrow.wallper.blackhole.view.SectionTitle
import com.onthecrow.wallper.blackhole.view.SliderRow
import com.onthecrow.wallper.blackhole.view.SwitchRow
import com.onthecrow.wallper.blackhole.view.label

/** The live renderer under a stats overlay, with the tuning controls in a collapsible bottom panel. */
@Composable
internal fun BlackHoleDebugScreen(
    state: BlackHoleDebugState,
    onEvent: (BlackHoleDebugEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.background(Color.Black)) {
        BlackHolePreview(
            config = state.toPreviewConfig(),
            dragMode = DragMode.GYRO,
            onStats = { fps, gpuMs -> onEvent(BlackHoleDebugEvent.OnFrameStats(fps, gpuMs)) },
            onFailure = { onEvent(BlackHoleDebugEvent.OnRenderFailure(it)) },
            modifier = Modifier.fillMaxSize(),
        )
        PreviewStats(
            fps = state.fps,
            gpuMs = state.gpuMs,
            quality = state.quality,
            isTablesLoading = state.isTablesLoading,
            tablesError = state.tablesError,
            renderFailure = state.renderFailure,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        )
        ControlsSheet(
            title = stringResource(R.string.black_hole_debug_controls),
            isExpanded = state.isPanelExpanded,
            onToggle = { onEvent(BlackHoleDebugEvent.OnPanelToggle) },
            maxContentHeight = maxHeight * SHEET_HEIGHT_FRACTION,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Controls(state, onEvent)
        }
    }
}

@Composable
private fun Controls(state: BlackHoleDebugState, onEvent: (BlackHoleDebugEvent) -> Unit) {
    val params = state.params

    SectionTitle(stringResource(R.string.black_hole_debug_camera))
    Text(
        text = stringResource(R.string.black_hole_debug_drag_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SwitchRow(
        text = stringResource(R.string.black_hole_debug_manual_camera),
        checked = state.isManualCamera,
        onCheckedChange = { onEvent(BlackHoleDebugEvent.OnManualCameraToggle(it)) },
    )
    SliderRow(
        label = stringResource(R.string.black_hole_debug_elevation, state.manualElevationDeg),
        value = state.manualElevationDeg,
        range = BlackHoleSettings.ELEVATION_RANGE,
        enabled = state.isManualCamera,
        onValueChange = { onEvent(BlackHoleDebugEvent.OnManualElevationChange(it)) },
    )
    SliderRow(
        label = stringResource(R.string.black_hole_debug_azimuth, state.manualAzimuthDeg),
        value = state.manualAzimuthDeg,
        range = AZIMUTH_RANGE,
        enabled = state.isManualCamera,
        onValueChange = { onEvent(BlackHoleDebugEvent.OnManualAzimuthChange(it)) },
    )
    SliderRow(
        label = stringResource(R.string.black_hole_debug_distance, state.distance),
        supportingText = stringResource(R.string.black_hole_debug_distance_hint),
        value = state.distance,
        range = BlackHoleSettings.DISTANCE_RANGE,
        onValueChange = { onEvent(BlackHoleDebugEvent.OnDistanceChange(it)) },
        onValueChangeFinished = { onEvent(BlackHoleDebugEvent.OnDistanceChangeFinished) },
    )
    ParamSlider(DebugSlider.FOV, R.string.black_hole_debug_fov, state, onEvent)
    ParamSlider(DebugSlider.DISK_ROLL, R.string.black_hole_debug_disk_roll, state, onEvent)

    SectionTitle(stringResource(R.string.black_hole_debug_picture))
    ParamSlider(DebugSlider.DISK_BRIGHTNESS, R.string.black_hole_debug_disk_brightness, state, onEvent)
    ParamSlider(DebugSlider.DISK_OPACITY, R.string.black_hole_debug_disk_opacity, state, onEvent)
    ParamSlider(DebugSlider.DISK_OPACITY_FALLOFF, R.string.black_hole_debug_disk_opacity_falloff, state, onEvent)
    ParamSlider(DebugSlider.DISK_ROTATION_PERIOD, R.string.black_hole_debug_disk_period, state, onEvent)
    ParamSlider(DebugSlider.BLOOM_INTENSITY, R.string.black_hole_debug_bloom_intensity, state, onEvent)
    ParamSlider(DebugSlider.EXPOSURE, R.string.black_hole_debug_exposure, state, onEvent)
    ParamSlider(DebugSlider.LENS_STRENGTH, R.string.black_hole_debug_lens_strength, state, onEvent)
    ParamSlider(DebugSlider.STAR_SIZE, R.string.black_hole_debug_star_size, state, onEvent)
    ParamSlider(DebugSlider.STAR_COUNT, R.string.black_hole_debug_star_count, state, onEvent)
    ParamSlider(DebugSlider.STAR_WIDENING, R.string.black_hole_debug_star_widening, state, onEvent)

    SectionTitle(stringResource(R.string.black_hole_debug_layers))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DebugToggle.entries.forEach { toggle ->
            val isOn = toggle.isOn(params)
            FilterChip(
                selected = isOn,
                onClick = { onEvent(BlackHoleDebugEvent.OnToggle(toggle, !isOn)) },
                label = { Text(text = toggle.label()) },
            )
        }
        FilterChip(
            selected = state.isTimeFrozen,
            onClick = { onEvent(BlackHoleDebugEvent.OnFreezeTimeToggle(!state.isTimeFrozen)) },
            label = { Text(text = stringResource(R.string.black_hole_debug_freeze_time)) },
        )
    }

    SectionTitle(stringResource(R.string.black_hole_quality))
    ChoiceRow(
        options = QualityLevel.entries,
        selected = state.quality,
        label = { it.label() },
        onSelect = { onEvent(BlackHoleDebugEvent.OnQualityChange(it)) },
    )
    SectionTitle(stringResource(R.string.black_hole_debug_lens))
    ChoiceRow(
        options = LensMode.entries,
        selected = state.debugOptions.lens,
        label = { it.label() },
        onSelect = { onEvent(BlackHoleDebugEvent.OnLensModeChange(it)) },
    )
    SectionTitle(stringResource(R.string.black_hole_debug_view))
    ChoiceRow(
        options = DebugView.entries,
        selected = state.debugOptions.view,
        label = { it.label() },
        onSelect = { onEvent(BlackHoleDebugEvent.OnDebugViewChange(it)) },
    )
}

/** [labelRes] takes the value as its only argument. */
@Composable
private fun ParamSlider(
    slider: DebugSlider,
    @StringRes labelRes: Int,
    state: BlackHoleDebugState,
    onEvent: (BlackHoleDebugEvent) -> Unit,
) {
    val value = slider.value(state.params)
    SliderRow(
        label = stringResource(labelRes, value),
        value = value,
        range = slider.range,
        onValueChange = { onEvent(BlackHoleDebugEvent.OnSliderChange(slider, it)) },
    )
}

@Composable
private fun DebugToggle.label(): String = stringResource(
    when (this) {
        DebugToggle.DISK -> R.string.black_hole_debug_disk
        DebugToggle.STARS -> R.string.black_hole_debug_stars
        DebugToggle.HAZE -> R.string.black_hole_debug_haze
        DebugToggle.BLOOM -> R.string.black_hole_debug_bloom
        DebugToggle.DOPPLER -> R.string.black_hole_debug_doppler
        DebugToggle.OPACITY_FALLOFF -> R.string.black_hole_debug_thin_edge
        DebugToggle.OPTIMIZED -> R.string.black_hole_debug_optimized
    },
)

@Composable
private fun LensMode.label(): String = stringResource(
    when (this) {
        LensMode.TABLE -> R.string.black_hole_debug_lens_table
        LensMode.REFERENCE -> R.string.black_hole_debug_lens_reference
        LensMode.SPLIT -> R.string.black_hole_debug_lens_split
        LensMode.DIFF -> R.string.black_hole_debug_lens_diff
    },
)

@Composable
private fun DebugView.label(): String = stringResource(
    when (this) {
        DebugView.IMAGE -> R.string.black_hole_debug_view_image
        DebugView.TABLE_T -> R.string.black_hole_debug_view_table_t
        DebugView.DISK_IMAGE -> R.string.black_hole_debug_view_disk_image
        DebugView.FOOTPRINT -> R.string.black_hole_debug_view_footprint
    },
)

private val AZIMUTH_RANGE = 0.0..360.0

@file:OptIn(ExperimentalLayoutApi::class)

package com.onthecrow.wallper.blackhole

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.blackhole.gl.BlackHolePreview
import com.onthecrow.wallper.blackhole.gl.DragMode
import com.onthecrow.wallper.blackhole.model.PictureLayer
import com.onthecrow.wallper.blackhole.model.SettingsSlider
import com.onthecrow.wallper.blackhole.ui.R
import com.onthecrow.wallper.blackhole.view.ChoiceRow
import com.onthecrow.wallper.blackhole.view.ControlsSheet
import com.onthecrow.wallper.blackhole.view.PreviewStats
import com.onthecrow.wallper.blackhole.view.SHEET_HEIGHT_FRACTION
import com.onthecrow.wallper.blackhole.view.SectionTitle
import com.onthecrow.wallper.blackhole.view.SliderRow
import com.onthecrow.wallper.blackhole.view.SwitchRow
import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.blackhole.physics.QualitySetting
import kotlin.math.roundToInt

/**
 * The live preview on the whole screen (a drag turns the camera) with the user settings in a bottom sheet
 * over it, stored as they change. What most people change comes first; the rest waits under Advanced.
 */
@Composable
internal fun BlackHoleSettingsScreen(
    state: BlackHoleSettingsState,
    onEvent: (BlackHoleSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.background(Color.Black)) {
        BlackHolePreview(
            config = state.toPreviewConfig(),
            dragMode = DragMode.ORBIT,
            followsGyroscope = state.settings.gyroEnabled,
            onStats = { fps, gpuMs -> onEvent(BlackHoleSettingsEvent.OnFrameStats(fps, gpuMs)) },
            onFailure = { onEvent(BlackHoleSettingsEvent.OnRenderFailure(it)) },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.isDebugAvailable) {
                PreviewStats(
                    fps = state.fps,
                    gpuMs = state.gpuMs,
                    quality = state.quality,
                    isTablesLoading = state.isTablesLoading,
                    tablesError = state.tablesError,
                    renderFailure = state.renderFailure,
                )
            }
            Text(
                text = stringResource(R.string.black_hole_preview_drag_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(HintBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        // Only what the list shows goes in: the frame stats change every second and must not recompose it.
        val settings = state.settings
        val isAdvancedExpanded = state.isAdvancedExpanded
        val isTimeFrozen = state.isTimeFrozen
        val isDebugAvailable = state.isDebugAvailable
        ControlsSheet(
            title = stringResource(R.string.black_hole_settings_title),
            isExpanded = state.isPanelExpanded,
            onToggle = { onEvent(BlackHoleSettingsEvent.OnPanelToggle) },
            maxContentHeight = maxHeight * SHEET_HEIGHT_FRACTION,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SettingsList(settings, isAdvancedExpanded, isTimeFrozen, isDebugAvailable, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.SettingsList(
    settings: BlackHoleSettings,
    isAdvancedExpanded: Boolean,
    isTimeFrozen: Boolean,
    isDebugAvailable: Boolean,
    onEvent: (BlackHoleSettingsEvent) -> Unit,
) {
    BasicSettings(settings, onEvent)
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEvent(BlackHoleSettingsEvent.OnAdvancedToggle) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.black_hole_advanced),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(if (isAdvancedExpanded) R.string.black_hole_debug_hide else R.string.black_hole_debug_show),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    AnimatedVisibility(visible = isAdvancedExpanded) {
        Column {
            AdvancedSettings(settings, isTimeFrozen, onEvent)
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        TextButton(onClick = { onEvent(BlackHoleSettingsEvent.OnResetClick) }) {
            Text(text = stringResource(R.string.black_hole_reset))
        }
        if (isDebugAvailable) {
            Button(onClick = { onEvent(BlackHoleSettingsEvent.OnDebugClick) }) {
                Text(text = stringResource(R.string.black_hole_debug_renderer))
            }
        }
    }
}

/** Quality, how the picture moves with the phone, and the few looks people tune first. */
@Composable
private fun BasicSettings(settings: BlackHoleSettings, onEvent: (BlackHoleSettingsEvent) -> Unit) {
    SectionTitle(stringResource(R.string.black_hole_quality))
    ChoiceRow(
        options = QualitySetting.entries,
        selected = settings.quality,
        label = { it.label() },
        onSelect = { onEvent(BlackHoleSettingsEvent.OnQualityChange(it)) },
    )

    SectionTitle(stringResource(R.string.black_hole_motion))
    SwitchRow(
        text = stringResource(R.string.black_hole_gyroscope),
        supportingText = stringResource(R.string.black_hole_gyroscope_hint),
        checked = settings.gyroEnabled,
        onCheckedChange = { onEvent(BlackHoleSettingsEvent.OnGyroToggle(it)) },
    )
    SettingsSliderRow(
        slider = SettingsSlider.GYRO_SENSITIVITY,
        label = stringResource(R.string.black_hole_gyro_sensitivity, settings.gyroSensitivity),
        supportingText = stringResource(R.string.black_hole_gyro_sensitivity_hint),
        settings = settings,
        onEvent = onEvent,
        enabled = settings.gyroEnabled,
    )
    SwitchRow(
        text = stringResource(R.string.black_hole_launcher),
        supportingText = stringResource(R.string.black_hole_launcher_hint),
        checked = settings.launcherEnabled,
        onCheckedChange = { onEvent(BlackHoleSettingsEvent.OnLauncherToggle(it)) },
    )

    SectionTitle(stringResource(R.string.black_hole_picture))
    SettingsSliderRow(
        slider = SettingsSlider.DISK_SPEED,
        label = stringResource(R.string.black_hole_disk_speed, settings.diskSpeed),
        supportingText = stringResource(R.string.black_hole_disk_speed_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.ELEVATION,
        label = stringResource(R.string.black_hole_elevation, settings.elevationDeg),
        supportingText = stringResource(R.string.black_hole_elevation_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.EXPOSURE,
        label = stringResource(R.string.black_hole_exposure, settings.exposure),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.STAR_COUNT,
        label = stringResource(R.string.black_hole_star_count, settings.starCount),
        settings = settings,
        onEvent = onEvent,
    )
    OutlinedButton(onClick = { onEvent(BlackHoleSettingsEvent.OnShuffleStarsClick) }) {
        Text(text = stringResource(R.string.black_hole_shuffle_stars))
    }
}

@Composable
private fun AdvancedSettings(
    settings: BlackHoleSettings,
    isTimeFrozen: Boolean,
    onEvent: (BlackHoleSettingsEvent) -> Unit,
) {
    SectionTitle(stringResource(R.string.black_hole_motion))
    SwitchRow(
        text = stringResource(R.string.black_hole_zoom_out),
        supportingText = stringResource(R.string.black_hole_zoom_out_hint),
        checked = settings.zoomOutEnabled,
        onCheckedChange = { onEvent(BlackHoleSettingsEvent.OnZoomOutToggle(it)) },
    )
    SwitchRow(
        text = stringResource(R.string.black_hole_orbit),
        checked = settings.orbitEnabled,
        onCheckedChange = { onEvent(BlackHoleSettingsEvent.OnOrbitToggle(it)) },
    )
    SettingsSliderRow(
        slider = SettingsSlider.ORBIT_PERIOD,
        label = stringResource(R.string.black_hole_orbit_period, settings.orbitPeriodSec.roundToInt()),
        settings = settings,
        onEvent = onEvent,
        enabled = settings.orbitEnabled,
    )
    ResponseRows(settings, onEvent)

    SectionTitle(stringResource(R.string.black_hole_camera))
    SettingsSliderRow(
        slider = SettingsSlider.DISK_ROLL,
        label = stringResource(R.string.black_hole_disk_roll, settings.diskRollDeg),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.DISTANCE,
        label = stringResource(R.string.black_hole_distance, settings.cameraDistance),
        supportingText = stringResource(R.string.black_hole_distance_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.FOV,
        label = stringResource(R.string.black_hole_fov, settings.fovDeg),
        supportingText = stringResource(R.string.black_hole_fov_hint),
        settings = settings,
        onEvent = onEvent,
    )

    SectionTitle(stringResource(R.string.black_hole_picture))
    SettingsSliderRow(
        slider = SettingsSlider.LENS_STRENGTH,
        label = stringResource(R.string.black_hole_lens_strength, settings.lensStrength),
        supportingText = stringResource(R.string.black_hole_lens_strength_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.DISK_BRIGHTNESS,
        label = stringResource(R.string.black_hole_disk_brightness, settings.diskBrightness),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.DISK_OPACITY,
        label = stringResource(R.string.black_hole_disk_opacity, settings.diskOpacity),
        settings = settings,
        onEvent = onEvent,
    )
    SwitchRow(
        text = stringResource(R.string.black_hole_disk_opacity_falloff),
        supportingText = stringResource(R.string.black_hole_disk_opacity_falloff_hint),
        checked = settings.diskOpacityFalloff,
        onCheckedChange = { onEvent(BlackHoleSettingsEvent.OnOpacityFalloffToggle(it)) },
    )
    SettingsSliderRow(
        slider = SettingsSlider.BLOOM_INTENSITY,
        label = stringResource(R.string.black_hole_bloom_intensity, settings.bloomIntensity),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.STAR_SHARPNESS,
        label = stringResource(R.string.black_hole_star_sharpness, SettingsSlider.STAR_SHARPNESS.value(settings) * 100),
        supportingText = stringResource(R.string.black_hole_star_sharpness_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.STAR_SIZE,
        label = stringResource(R.string.black_hole_star_size, settings.starSizePx),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.STAR_WIDENING,
        label = stringResource(R.string.black_hole_star_widening, settings.starWidening),
        supportingText = stringResource(R.string.black_hole_star_widening_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SwitchRow(
        text = stringResource(R.string.black_hole_doppler),
        supportingText = stringResource(R.string.black_hole_doppler_hint),
        checked = settings.dopplerEnabled,
        onCheckedChange = { onEvent(BlackHoleSettingsEvent.OnDopplerToggle(it)) },
    )
    SettingsSliderRow(
        slider = SettingsSlider.DOPPLER_STRENGTH,
        label = stringResource(R.string.black_hole_doppler_strength, settings.dopplerStrength),
        settings = settings,
        onEvent = onEvent,
        enabled = settings.dopplerEnabled,
    )

    SectionTitle(stringResource(R.string.black_hole_layers))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PictureLayer.entries.forEach { layer ->
            val isOn = layer.isOn(settings)
            FilterChip(
                selected = isOn,
                onClick = { onEvent(BlackHoleSettingsEvent.OnLayerToggle(layer, !isOn)) },
                label = { Text(text = layer.label()) },
            )
        }
        FilterChip(
            selected = isTimeFrozen,
            onClick = { onEvent(BlackHoleSettingsEvent.OnFreezeTimeToggle(!isTimeFrozen)) },
            label = { Text(text = stringResource(R.string.black_hole_freeze_preview)) },
        )
    }

    SectionTitle(stringResource(R.string.black_hole_idle_fps))
    ChoiceRow(
        options = BlackHoleSettings.IDLE_FPS_OPTIONS,
        selected = settings.idleFps,
        label = { stringResource(R.string.black_hole_fps, it) },
        onSelect = { onEvent(BlackHoleSettingsEvent.OnIdleFpsChange(it)) },
    )
}

/** The state follows the drag; the store gets the value on release. */
@Composable
private fun SettingsSliderRow(
    slider: SettingsSlider,
    label: String,
    settings: BlackHoleSettings,
    onEvent: (BlackHoleSettingsEvent) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    SliderRow(
        label = label,
        supportingText = supportingText,
        value = slider.value(settings),
        range = slider.range,
        enabled = enabled,
        onValueChange = { onEvent(BlackHoleSettingsEvent.OnSliderChange(slider, it)) },
        onValueChangeFinished = { onEvent(BlackHoleSettingsEvent.OnSliderChangeFinished(slider)) },
    )
}

/** How the camera answers the gyroscope and the launcher, and the recoil of the hole; the strength is basic. */
@Composable
private fun ResponseRows(settings: BlackHoleSettings, onEvent: (BlackHoleSettingsEvent) -> Unit) {
    SettingsSliderRow(
        slider = SettingsSlider.GYRO_SMOOTHING,
        label = stringResource(R.string.black_hole_gyro_smoothing, settings.gyroSmoothing * 100),
        supportingText = stringResource(R.string.black_hole_gyro_smoothing_hint),
        settings = settings,
        onEvent = onEvent,
        enabled = settings.gyroEnabled,
    )
    SettingsSliderRow(
        slider = SettingsSlider.LAUNCHER_ANGLE,
        label = stringResource(R.string.black_hole_launcher_angle, settings.launcherAngleDeg),
        supportingText = stringResource(R.string.black_hole_launcher_angle_hint),
        settings = settings,
        onEvent = onEvent,
        enabled = settings.launcherEnabled,
    )
    SettingsSliderRow(
        slider = SettingsSlider.RESPONSE_SPEED,
        label = stringResource(R.string.black_hole_response_speed, settings.responseSpeed),
        supportingText = stringResource(R.string.black_hole_response_speed_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.RESPONSE_SMOOTHNESS,
        label = stringResource(R.string.black_hole_response_smoothness, settings.responseSmoothness),
        supportingText = stringResource(R.string.black_hole_response_smoothness_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.RECOIL_STRENGTH,
        label = stringResource(R.string.black_hole_recoil_strength, settings.recoilStrength),
        supportingText = stringResource(R.string.black_hole_recoil_strength_hint),
        settings = settings,
        onEvent = onEvent,
    )
    SettingsSliderRow(
        slider = SettingsSlider.RECOIL_RETURN,
        label = stringResource(R.string.black_hole_recoil_return, settings.recoilReturnSpeed),
        supportingText = stringResource(R.string.black_hole_recoil_return_hint),
        settings = settings,
        onEvent = onEvent,
        enabled = settings.recoilStrength > 0,
    )
}

@Composable
private fun QualitySetting.label(): String = stringResource(
    when (this) {
        QualitySetting.AUTO -> R.string.black_hole_quality_auto
        QualitySetting.LOW -> R.string.black_hole_quality_low
        QualitySetting.MEDIUM -> R.string.black_hole_quality_medium
        QualitySetting.HIGH -> R.string.black_hole_quality_high
    },
)

@Composable
private fun PictureLayer.label(): String = stringResource(
    when (this) {
        PictureLayer.DISK -> R.string.black_hole_layer_disk
        PictureLayer.STARS -> R.string.black_hole_layer_stars
        PictureLayer.HAZE -> R.string.black_hole_layer_haze
        PictureLayer.BLOOM -> R.string.black_hole_layer_bloom
    },
)

@Preview
@Composable
private fun BlackHoleSettingsScreenPreview() {
    BlackHoleSettingsScreen(state = BlackHoleSettingsState(isDebugAvailable = true), onEvent = {})
}

private val HintBackground = Color.Black.copy(alpha = 0.55f)

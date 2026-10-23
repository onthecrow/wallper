package com.onthecrow.wallper.core.blackhole.renderer

import android.content.Context
import android.content.SharedPreferences
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.CameraParams
import com.onthecrow.wallper.core.blackhole.physics.DiskParams
import com.onthecrow.wallper.core.blackhole.physics.FrameParams
import com.onthecrow.wallper.core.blackhole.physics.HazeParams
import com.onthecrow.wallper.core.blackhole.physics.InputParams
import com.onthecrow.wallper.core.blackhole.physics.LensParams
import com.onthecrow.wallper.core.blackhole.physics.PostParams
import com.onthecrow.wallper.core.blackhole.physics.StarParams
import com.onthecrow.wallper.core.blackhole.physics.QualitySetting
import com.onthecrow.wallper.core.blackhole.physics.StarMath
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the user may change about the black hole; everything else lives in [BlackHoleParams]. */
data class BlackHoleSettings(
    val quality: QualitySetting = FrameParams().quality,
    val gyroEnabled: Boolean = true,
    val launcherEnabled: Boolean = true,
    val orbitEnabled: Boolean = true,
    val orbitPeriodSec: Double = CameraParams().orbitPeriodSec,
    val elevationDeg: Double = CameraParams().elevationDeg,
    /** Changing it regenerates the lens tables, a second or two. */
    val cameraDistance: Double = CameraParams().distance,
    val starSeed: Int = 1337,
    /** Multiplies the number of stars of the spec; 0 leaves only the Milky Way. */
    val starCount: Double = StarParams().countScale,
    /** σ of a star in render pixels; the screen's sharpness slider sets it together with [starWidening]. */
    val starSizePx: Double = StarMath.sizePx(StarMath.DEFAULT_SHARPNESS),
    /** How much stars widen with the pixel footprint to avoid shimmering; lower is crisper. */
    val starWidening: Double = StarMath.footprintWidening(StarMath.DEFAULT_SHARPNESS),
    /** Vertical field of view: lower brings the hole closer on screen without new lens tables. */
    val fovDeg: Double = CameraParams().fovYDeg,
    /** Multiplies the speed of [DiskParams.rotationPeriodSec]; 0 stops the disk. */
    val diskSpeed: Double = 1.0,
    /** Tilts the disk on screen, about the hole. How edge-on it looks is [elevationDeg]. */
    val diskRollDeg: Double = CameraParams().rollDeg,
    /** Strength of the gravitational lens on the sky: 1 is physical, lower brings the lens ring closer to the shadow. */
    val lensStrength: Double = LensParams().strength,
    /** The view widens when the system zooms the wallpaper out, e.g. while unlocking. */
    val zoomOutEnabled: Boolean = true,
    /** Degrees the camera turns per degree the phone tilts. */
    val gyroSensitivity: Double = InputParams().gyroGain,
    /** How much hand tremor the gyroscope filter takes out, 0..1. */
    val gyroSmoothing: Double = InputParams().gyroSmoothing,
    /** How far the camera turns across all launcher pages. */
    val launcherAngleDeg: Double = InputParams().launcherAngleDeg,
    /** Multiplies how fast the camera follows the gyroscope and the launcher. */
    val responseSpeed: Double = 1.0,
    /** ζ of that follow: 1 eases in without overshoot, lower swings past and back, higher eases in more softly. */
    val responseSmoothness: Double = InputParams().springDamping,
    /** How far the hole lags against a turn; 0 turns the recoil off. */
    val recoilStrength: Double = InputParams().recoilStrength,
    /** Multiplies how fast the hole comes back after a recoil. */
    val recoilReturnSpeed: Double = 1.0,
    val diskEnabled: Boolean = DiskParams().enabled,
    val diskBrightness: Double = DiskParams().brightness,
    val diskOpacity: Double = DiskParams().opacity,
    /** The disk turns see-through towards its dim outer edge instead of a dark rim around the glow. */
    val diskOpacityFalloff: Boolean = DiskParams().opacityFalloffEnabled,
    val starsEnabled: Boolean = StarParams().enabled,
    val hazeEnabled: Boolean = HazeParams().enabled,
    val bloomEnabled: Boolean = PostParams().bloomEnabled,
    val bloomIntensity: Double = PostParams().bloomIntensity,
    val exposure: Double = PostParams().exposure,
    val idleFps: Int = 30,
    val dopplerEnabled: Boolean = false,
    val dopplerStrength: Double = 0.6,
) {
    fun applyTo(base: BlackHoleParams): BlackHoleParams = base.copy(
        camera = base.camera.copy(
            distance = cameraDistance.coerceIn(DISTANCE_RANGE),
            elevationDeg = elevationDeg.coerceIn(ELEVATION_RANGE),
            orbitPeriodSec = if (orbitEnabled) orbitPeriodSec.coerceIn(ORBIT_RANGE) else 0.0,
            rollDeg = diskRollDeg.coerceIn(ROLL_RANGE),
            fovYDeg = fovDeg.coerceIn(FOV_RANGE),
            zoomOutFov = if (zoomOutEnabled) base.camera.zoomOutFov else 0.0,
        ),
        disk = base.disk.copy(
            enabled = diskEnabled,
            brightness = diskBrightness.coerceIn(DISK_BRIGHTNESS_RANGE),
            opacity = diskOpacity.coerceIn(DISK_OPACITY_RANGE),
            opacityFalloffEnabled = diskOpacityFalloff,
            rotationPeriodSec = diskSpeed.coerceIn(DISK_SPEED_RANGE).let { speed ->
                if (speed > 0) base.disk.rotationPeriodSec / speed else 0.0
            },
        ),
        lens = base.lens.copy(strength = lensStrength.coerceIn(LENS_STRENGTH_RANGE)),
        haze = base.haze.copy(enabled = hazeEnabled),
        post = base.post.copy(
            bloomEnabled = bloomEnabled,
            bloomIntensity = bloomIntensity.coerceIn(BLOOM_INTENSITY_RANGE),
            exposure = exposure.coerceIn(EXPOSURE_RANGE),
        ),
        stars = base.stars.copy(
            enabled = starsEnabled,
            seed = starSeed,
            countScale = starCount.coerceIn(STAR_COUNT_RANGE),
            sizePx = starSizePx.coerceIn(STAR_SIZE_RANGE),
            footprintWidening = starWidening.coerceIn(STAR_WIDENING_RANGE),
        ),
        doppler = base.doppler.copy(enabled = dopplerEnabled, strength = dopplerStrength.coerceIn(0.0, 1.0)),
        input = base.input.copy(
            gyroEnabled = gyroEnabled,
            launcherEnabled = launcherEnabled,
            gyroGain = gyroSensitivity.coerceIn(GYRO_SENSITIVITY_RANGE),
            gyroSmoothing = gyroSmoothing.coerceIn(GYRO_SMOOTHING_RANGE),
            launcherAngleDeg = launcherAngleDeg.coerceIn(LAUNCHER_ANGLE_RANGE),
            springOmega = base.input.springOmega * responseSpeed.coerceIn(RESPONSE_SPEED_RANGE),
            springDamping = responseSmoothness.coerceIn(RESPONSE_SMOOTHNESS_RANGE),
            recoilStrength = recoilStrength.coerceIn(RECOIL_STRENGTH_RANGE),
            recoilReturnOmega = base.input.recoilReturnOmega * recoilReturnSpeed.coerceIn(RECOIL_RETURN_RANGE),
        ),
        frames = base.frames.copy(fpsIdle = idleFps, quality = quality),
    )

    companion object {
        /** r_c ≥ R_OUT + 5 keeps the camera outside the disk (§4). */
        val DISTANCE_RANGE = 20.0..60.0
        val ELEVATION_RANGE = 1.0..60.0
        val ROLL_RANGE = -45.0..45.0
        val LENS_STRENGTH_RANGE = 0.0..2.0
        val DISK_BRIGHTNESS_RANGE = 0.0..3.0
        val DISK_OPACITY_RANGE = 0.0..1.0
        val BLOOM_INTENSITY_RANGE = 0.0..2.0
        val EXPOSURE_RANGE = 0.25..4.0
        val DISK_SPEED_RANGE = 0.0..4.0
        val STAR_COUNT_RANGE = 0.0..10.0
        val STAR_SHARPNESS_RANGE = 0.0..1.0
        val STAR_SIZE_RANGE = 0.2..2.0
        val STAR_WIDENING_RANGE = 0.0..0.5
        val FOV_RANGE = 30.0..90.0
        val ORBIT_RANGE = 60.0..3600.0
        val GYRO_SENSITIVITY_RANGE = 0.0..1.5
        val GYRO_SMOOTHING_RANGE = 0.0..1.0
        val LAUNCHER_ANGLE_RANGE = 0.0..30.0
        val RESPONSE_SPEED_RANGE = 0.2..3.0
        val RESPONSE_SMOOTHNESS_RANGE = 0.3..2.0
        val RECOIL_STRENGTH_RANGE = 0.0..3.0
        val RECOIL_RETURN_RANGE = 0.3..3.0
        val IDLE_FPS_OPTIONS = listOf(24, 30)
    }
}

/**
 * [BlackHoleSettings] in device-protected SharedPreferences: the wallpaper reads them in direct boot.
 * One instance per process, so the settings screen and the wallpaper see the same flow.
 */
@Singleton
class BlackHoleSettingsStore @Inject constructor(@ApplicationContext context: Context) {

    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<BlackHoleSettings> = _settings.asStateFlow()

    // Kept as a field: SharedPreferences holds listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.value = read() }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(transform: (BlackHoleSettings) -> BlackHoleSettings) {
        val next = transform(_settings.value)
        preferences.edit()
            .putString(KEY_QUALITY, next.quality.name)
            .putBoolean(KEY_GYRO, next.gyroEnabled)
            .putBoolean(KEY_LAUNCHER, next.launcherEnabled)
            .putBoolean(KEY_ORBIT, next.orbitEnabled)
            .putFloat(KEY_ORBIT_PERIOD, next.orbitPeriodSec.toFloat())
            .putFloat(KEY_ELEVATION, next.elevationDeg.toFloat())
            .putFloat(KEY_DISTANCE, next.cameraDistance.toFloat())
            .putInt(KEY_STAR_SEED, next.starSeed)
            .putFloat(KEY_STAR_COUNT, next.starCount.toFloat())
            .putFloat(KEY_STAR_SIZE, next.starSizePx.toFloat())
            .putFloat(KEY_STAR_WIDENING, next.starWidening.toFloat())
            .putFloat(KEY_FOV, next.fovDeg.toFloat())
            .putFloat(KEY_DISK_SPEED, next.diskSpeed.toFloat())
            .putFloat(KEY_DISK_ROLL, next.diskRollDeg.toFloat())
            .putFloat(KEY_LENS_STRENGTH, next.lensStrength.toFloat())
            .putBoolean(KEY_ZOOM_OUT, next.zoomOutEnabled)
            .putFloat(KEY_GYRO_SENSITIVITY, next.gyroSensitivity.toFloat())
            .putFloat(KEY_GYRO_SMOOTHING, next.gyroSmoothing.toFloat())
            .putFloat(KEY_LAUNCHER_ANGLE, next.launcherAngleDeg.toFloat())
            .putFloat(KEY_RESPONSE_SPEED, next.responseSpeed.toFloat())
            .putFloat(KEY_RESPONSE_SMOOTHNESS, next.responseSmoothness.toFloat())
            .putFloat(KEY_RECOIL_STRENGTH, next.recoilStrength.toFloat())
            .putFloat(KEY_RECOIL_RETURN, next.recoilReturnSpeed.toFloat())
            .putBoolean(KEY_DISK_ENABLED, next.diskEnabled)
            .putFloat(KEY_DISK_BRIGHTNESS, next.diskBrightness.toFloat())
            .putFloat(KEY_DISK_OPACITY, next.diskOpacity.toFloat())
            .putBoolean(KEY_DISK_OPACITY_FALLOFF, next.diskOpacityFalloff)
            .putBoolean(KEY_STARS_ENABLED, next.starsEnabled)
            .putBoolean(KEY_HAZE_ENABLED, next.hazeEnabled)
            .putBoolean(KEY_BLOOM_ENABLED, next.bloomEnabled)
            .putFloat(KEY_BLOOM_INTENSITY, next.bloomIntensity.toFloat())
            .putFloat(KEY_EXPOSURE, next.exposure.toFloat())
            .putInt(KEY_IDLE_FPS, next.idleFps)
            .putBoolean(KEY_DOPPLER, next.dopplerEnabled)
            .putFloat(KEY_DOPPLER_STRENGTH, next.dopplerStrength.toFloat())
            .apply()
        _settings.value = next
    }

    private fun read(): BlackHoleSettings {
        val defaults = BlackHoleSettings()
        // The first version stored a sharpness; size and widening come from it until they are saved.
        val legacySharpness = preferences.getFloat(KEY_STAR_SHARPNESS, Float.NaN).toDouble().takeUnless { it.isNaN() }
        return BlackHoleSettings(
            quality = preferences.getString(KEY_QUALITY, null)
                ?.let { name -> QualitySetting.entries.find { it.name == name } } ?: defaults.quality,
            gyroEnabled = preferences.getBoolean(KEY_GYRO, defaults.gyroEnabled),
            launcherEnabled = preferences.getBoolean(KEY_LAUNCHER, defaults.launcherEnabled),
            orbitEnabled = preferences.getBoolean(KEY_ORBIT, defaults.orbitEnabled),
            orbitPeriodSec = preferences.getFloat(KEY_ORBIT_PERIOD, defaults.orbitPeriodSec.toFloat()).toDouble(),
            elevationDeg = preferences.getFloat(KEY_ELEVATION, defaults.elevationDeg.toFloat()).toDouble(),
            cameraDistance = preferences.getFloat(KEY_DISTANCE, defaults.cameraDistance.toFloat()).toDouble(),
            starSeed = preferences.getInt(KEY_STAR_SEED, defaults.starSeed),
            starCount = preferences.getFloat(KEY_STAR_COUNT, defaults.starCount.toFloat()).toDouble(),
            starSizePx = preferences.getFloat(KEY_STAR_SIZE, legacySharpness?.let(StarMath::sizePx)?.toFloat() ?: defaults.starSizePx.toFloat()).toDouble(),
            starWidening = preferences.getFloat(
                KEY_STAR_WIDENING,
                legacySharpness?.let(StarMath::footprintWidening)?.toFloat() ?: defaults.starWidening.toFloat(),
            ).toDouble(),
            fovDeg = preferences.getFloat(KEY_FOV, defaults.fovDeg.toFloat()).toDouble(),
            diskSpeed = preferences.getFloat(KEY_DISK_SPEED, defaults.diskSpeed.toFloat()).toDouble(),
            diskRollDeg = preferences.getFloat(KEY_DISK_ROLL, defaults.diskRollDeg.toFloat()).toDouble(),
            lensStrength = preferences.getFloat(KEY_LENS_STRENGTH, defaults.lensStrength.toFloat()).toDouble(),
            zoomOutEnabled = preferences.getBoolean(KEY_ZOOM_OUT, defaults.zoomOutEnabled),
            gyroSensitivity = preferences.getFloat(KEY_GYRO_SENSITIVITY, defaults.gyroSensitivity.toFloat()).toDouble(),
            gyroSmoothing = preferences.getFloat(KEY_GYRO_SMOOTHING, defaults.gyroSmoothing.toFloat()).toDouble(),
            launcherAngleDeg = preferences.getFloat(KEY_LAUNCHER_ANGLE, defaults.launcherAngleDeg.toFloat()).toDouble(),
            responseSpeed = preferences.getFloat(KEY_RESPONSE_SPEED, defaults.responseSpeed.toFloat()).toDouble(),
            responseSmoothness = preferences.getFloat(KEY_RESPONSE_SMOOTHNESS, defaults.responseSmoothness.toFloat()).toDouble(),
            recoilStrength = preferences.getFloat(KEY_RECOIL_STRENGTH, defaults.recoilStrength.toFloat()).toDouble(),
            recoilReturnSpeed = preferences.getFloat(KEY_RECOIL_RETURN, defaults.recoilReturnSpeed.toFloat()).toDouble(),
            diskEnabled = preferences.getBoolean(KEY_DISK_ENABLED, defaults.diskEnabled),
            diskBrightness = preferences.getFloat(KEY_DISK_BRIGHTNESS, defaults.diskBrightness.toFloat()).toDouble(),
            diskOpacity = preferences.getFloat(KEY_DISK_OPACITY, defaults.diskOpacity.toFloat()).toDouble(),
            diskOpacityFalloff = preferences.getBoolean(KEY_DISK_OPACITY_FALLOFF, defaults.diskOpacityFalloff),
            starsEnabled = preferences.getBoolean(KEY_STARS_ENABLED, defaults.starsEnabled),
            hazeEnabled = preferences.getBoolean(KEY_HAZE_ENABLED, defaults.hazeEnabled),
            bloomEnabled = preferences.getBoolean(KEY_BLOOM_ENABLED, defaults.bloomEnabled),
            bloomIntensity = preferences.getFloat(KEY_BLOOM_INTENSITY, defaults.bloomIntensity.toFloat()).toDouble(),
            exposure = preferences.getFloat(KEY_EXPOSURE, defaults.exposure.toFloat()).toDouble(),
            idleFps = preferences.getInt(KEY_IDLE_FPS, defaults.idleFps),
            dopplerEnabled = preferences.getBoolean(KEY_DOPPLER, defaults.dopplerEnabled),
            dopplerStrength = preferences.getFloat(KEY_DOPPLER_STRENGTH, defaults.dopplerStrength.toFloat()).toDouble(),
        )
    }

    private companion object {
        const val FILE = "black_hole_settings"
        const val KEY_QUALITY = "quality"
        const val KEY_GYRO = "gyro"
        const val KEY_LAUNCHER = "launcher"
        const val KEY_ORBIT = "orbit"
        const val KEY_ORBIT_PERIOD = "orbit_period"
        const val KEY_ELEVATION = "elevation"
        const val KEY_DISTANCE = "distance"
        const val KEY_STAR_SEED = "star_seed"
        // v2: the default count went from ×1 to ×3, so the old key would keep the old look.
        const val KEY_STAR_COUNT = "star_count_v2"
        const val KEY_STAR_SHARPNESS = "star_sharpness"
        const val KEY_STAR_SIZE = "star_size"
        const val KEY_STAR_WIDENING = "star_widening"
        const val KEY_FOV = "fov"
        const val KEY_DISK_SPEED = "disk_speed"
        const val KEY_DISK_ROLL = "disk_roll"
        const val KEY_LENS_STRENGTH = "lens_strength"
        const val KEY_ZOOM_OUT = "zoom_out"
        const val KEY_GYRO_SENSITIVITY = "gyro_sensitivity"
        const val KEY_GYRO_SMOOTHING = "gyro_smoothing"
        const val KEY_LAUNCHER_ANGLE = "launcher_angle"
        const val KEY_RESPONSE_SPEED = "response_speed"
        const val KEY_RESPONSE_SMOOTHNESS = "response_smoothness"
        const val KEY_RECOIL_STRENGTH = "recoil_strength"
        const val KEY_RECOIL_RETURN = "recoil_return_speed"
        const val KEY_DISK_ENABLED = "disk_enabled"
        const val KEY_DISK_BRIGHTNESS = "disk_brightness"
        const val KEY_DISK_OPACITY = "disk_opacity"
        const val KEY_DISK_OPACITY_FALLOFF = "disk_opacity_falloff"
        const val KEY_STARS_ENABLED = "stars_enabled"
        const val KEY_HAZE_ENABLED = "haze_enabled"
        const val KEY_BLOOM_ENABLED = "bloom_enabled"
        const val KEY_BLOOM_INTENSITY = "bloom_intensity"
        const val KEY_EXPOSURE = "exposure"
        const val KEY_IDLE_FPS = "idle_fps"
        const val KEY_DOPPLER = "doppler"
        const val KEY_DOPPLER_STRENGTH = "doppler_strength"
    }
}

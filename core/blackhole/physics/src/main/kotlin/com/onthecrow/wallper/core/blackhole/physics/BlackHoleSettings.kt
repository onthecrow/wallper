package com.onthecrow.wallper.core.blackhole.physics

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What the user may change about the black hole; everything else lives in [BlackHoleParams].
 * Stored as JSON ([encode], [decode]) with only what differs from the defaults: a setting the user never
 * changed follows the defaults of the next version.
 */
@Serializable
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

        private val json = Json {
            ignoreUnknownKeys = true // a setting a newer version stored and this one doesn't know
            coerceInputValues = true // an enum value this version doesn't know becomes the default
        }

        /** Malformed text gives the defaults. */
        fun decode(text: String): BlackHoleSettings = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            BlackHoleSettings()
        } catch (_: IllegalArgumentException) {
            BlackHoleSettings()
        }

        /**
         * The first versions stored one SharedPreferences key per setting; [values] is their `getAll()`.
         * Their floats are read back as the shortest decimal, so 44.2f gives 44.2, not 44.20000076.
         */
        fun fromLegacyPreferences(values: Map<String, *>): BlackHoleSettings {
            val defaults = BlackHoleSettings()
            fun double(key: String, default: Double) = (values[key] as? Float)?.toString()?.toDouble() ?: default
            fun bool(key: String, default: Boolean) = values[key] as? Boolean ?: default
            fun int(key: String, default: Int) = values[key] as? Int ?: default
            // The very first ones stored a sharpness instead of size and widening.
            val sharpness = (values["star_sharpness"] as? Float)?.toDouble()
            return BlackHoleSettings(
                quality = (values["quality"] as? String)?.let { name -> QualitySetting.entries.find { it.name == name } }
                    ?: defaults.quality,
                gyroEnabled = bool("gyro", defaults.gyroEnabled),
                launcherEnabled = bool("launcher", defaults.launcherEnabled),
                orbitEnabled = bool("orbit", defaults.orbitEnabled),
                orbitPeriodSec = double("orbit_period", defaults.orbitPeriodSec),
                elevationDeg = double("elevation", defaults.elevationDeg),
                cameraDistance = double("distance", defaults.cameraDistance),
                starSeed = int("star_seed", defaults.starSeed),
                starCount = double("star_count_v2", defaults.starCount),
                starSizePx = double("star_size", sharpness?.let(StarMath::sizePx) ?: defaults.starSizePx),
                starWidening = double("star_widening", sharpness?.let(StarMath::footprintWidening) ?: defaults.starWidening),
                fovDeg = double("fov", defaults.fovDeg),
                diskSpeed = double("disk_speed", defaults.diskSpeed),
                diskRollDeg = double("disk_roll", defaults.diskRollDeg),
                lensStrength = double("lens_strength", defaults.lensStrength),
                zoomOutEnabled = bool("zoom_out", defaults.zoomOutEnabled),
                gyroSensitivity = double("gyro_sensitivity", defaults.gyroSensitivity),
                gyroSmoothing = double("gyro_smoothing", defaults.gyroSmoothing),
                launcherAngleDeg = double("launcher_angle", defaults.launcherAngleDeg),
                responseSpeed = double("response_speed", defaults.responseSpeed),
                responseSmoothness = double("response_smoothness", defaults.responseSmoothness),
                recoilStrength = double("recoil_strength", defaults.recoilStrength),
                recoilReturnSpeed = double("recoil_return_speed", defaults.recoilReturnSpeed),
                diskEnabled = bool("disk_enabled", defaults.diskEnabled),
                diskBrightness = double("disk_brightness", defaults.diskBrightness),
                diskOpacity = double("disk_opacity", defaults.diskOpacity),
                diskOpacityFalloff = bool("disk_opacity_falloff", defaults.diskOpacityFalloff),
                starsEnabled = bool("stars_enabled", defaults.starsEnabled),
                hazeEnabled = bool("haze_enabled", defaults.hazeEnabled),
                bloomEnabled = bool("bloom_enabled", defaults.bloomEnabled),
                bloomIntensity = double("bloom_intensity", defaults.bloomIntensity),
                exposure = double("exposure", defaults.exposure),
                idleFps = int("idle_fps", defaults.idleFps),
                dopplerEnabled = bool("doppler", defaults.dopplerEnabled),
                dopplerStrength = double("doppler_strength", defaults.dopplerStrength),
            )
        }
    }

    /** Only what differs from the defaults. */
    fun encode(): String = json.encodeToString(serializer(), this)
}

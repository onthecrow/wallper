package com.onthecrow.wallper.core.blackhole.physics

/**
 * Every tunable of the black hole wallpaper. Defaults start from BLACKHOLE_TZ.md §10; the camera, lens,
 * disk, stars, glow and quality were then tuned on a phone (BLACKHOLE_PLAN.md §9).
 * Units: r_s = 1 (photon sphere at 1.5, ISCO at 3), angles in degrees, times in seconds.
 */
data class BlackHoleParams(
    val camera: CameraParams = CameraParams(),
    val lens: LensParams = LensParams(),
    val disk: DiskParams = DiskParams(),
    val stars: StarParams = StarParams(),
    val haze: HazeParams = HazeParams(),
    val post: PostParams = PostParams(),
    val doppler: DopplerParams = DopplerParams(),
    val input: InputParams = InputParams(),
    val frames: FrameParams = FrameParams(),
)

data class CameraParams(
    /** r_c. The apparent size of the hole and the lens follow from it; changing it regenerates the tables. */
    val distance: Double = 44.2,
    val fovYDeg: Double = 53.0,
    /** Where the hole sits on screen, as an NDC offset of the projection centre (off-axis projection). */
    val principalX: Double = 0.0,
    val principalY: Double = 0.10,
    /** θ0: height above the disk plane. */
    val elevationDeg: Double = 5.6,
    val elevationSwingDeg: Double = 3.0,
    val elevationPeriodSec: Double = 120.0,
    val elevationMinDeg: Double = 1.0,
    /** §4 says 30°; anything well below the pole works, and a higher view shows the disk more face-on. */
    val elevationMaxDeg: Double = 60.0,
    /** Turns the picture around the view axis, about the hole: the disk tilts on screen. Positive is counter-clockwise. */
    val rollDeg: Double = 45.0,
    /**
     * How much wider the view gets when the system zooms the wallpaper out (`onZoomChanged`, e.g. while
     * unlocking or pulling the shade): tan(fov/2) grows by this fraction at full zoom. 0 disables it.
     */
    val zoomOutFov: Double = 0.15,
    /** One orbit around the hole; 0 stops the orbit. */
    val orbitPeriodSec: Double = 600.0,
    val startAzimuthDeg: Double = 0.0,
)

data class LensParams(
    /** Density of the table near the shadow edge (§5.2). Changing it regenerates the tables. */
    val tableK: Double = 7.0,
    /**
     * Gravitational lensing of the sky: 1 is physical. It sets how far from the shadow the lens ring
     * (Einstein ring) sits: at r_c = 32 it is 3.4× the shadow radius at 1, 1.4× at 0.1, 4.7× at 2.
     * An artistic knob: the disk and the shadow stay physical.
     */
    val strength: Double = 0.25,
)

data class DiskParams(
    val enabled: Boolean = true,
    val innerRadius: Double = 3.0,
    val outerRadius: Double = 14.0,
    /** W_IN: how fast the emission rises past the inner edge, in r_s. */
    val innerWidth: Double = 0.4,
    /** W_OUT: fade towards the outer edge, as a fraction of the disk width. */
    val outerFade: Double = 0.15,
    /** P in (R_IN / r)^P. */
    val falloffPower: Double = 2.5,
    /** One turn of the inner edge; 0 stops the disk. */
    val rotationPeriodSec: Double = 10.0,
    /**
     * Length of one crossfade cycle of the two texture phases (§8.3). The pattern shift over a cycle,
     * `turns(R_IN) − turns(R_OUT)`, has to stay ≲ 1, otherwise the pattern doubles mid-crossfade; the
     * scene shortens the cycle for fast disks to keep it so.
     */
    val flowPeriodSec: Double = 40.0,
    /** +1: counter-clockwise seen from +Y. */
    val sense: Int = 1,
    val opacity: Double = 0.37,
    /**
     * Opacity falls towards the outer edge like the glow does, as opacity × (R_IN / r)^[opacityFalloffPower].
     * Without it the dim outer disk still blocks half the sky behind it and reads as a dark rim.
     * The glow, and so the look of the disk, doesn't change.
     */
    val opacityFalloffEnabled: Boolean = false,
    val opacityFalloffPower: Double = 1.25,
    val brightness: Double = 1.92,
    val ramp: List<RampStop> = DEFAULT_DISK_RAMP,
    val detailSeed: Int = 7,
    /** Anisotropy of the fibres: noise stretch along φ against r (A ≪ B in §8.2). */
    val fibreStretch: Double = 20.0,
    /** Rare elongated bright clumps, 0..1. */
    val clumps: Double = 0.35,
)

/** A colour stop of the disk ramp; [multiplier] makes it HDR. */
data class RampStop(val position: Float, val red: Float, val green: Float, val blue: Float, val multiplier: Float)

val DEFAULT_DISK_RAMP = listOf(
    RampStop(0.0f, 1.00f, 0.95f, 0.85f, 6.0f),
    RampStop(0.2f, 1.00f, 0.80f, 0.50f, 3.0f),
    RampStop(0.6f, 0.90f, 0.50f, 0.20f, 1.2f),
    RampStop(1.0f, 0.60f, 0.25f, 0.08f, 0.4f),
)

data class StarParams(
    val enabled: Boolean = true,
    val layers: List<StarLayer> = DEFAULT_STAR_LAYERS,
    /**
     * Scales the star count of every layer. Past [StarMath.MAX_CELL_DENSITY] stars per cell the layer
     * gets a finer grid (see [StarMath.effectiveLayer]).
     */
    val countScale: Double = 10.0,
    /** σ of a star in render pixels; [StarMath.sizePx] derives it from a sharpness. */
    val sizePx: Double = StarMath.sizePx(StarMath.DEFAULT_SHARPNESS),
    /**
     * How much a star widens with the pixel footprint: σ²_eff = σ² + w · fp² (§9.3 uses 0.25). Lower is
     * crisper but may shimmer while the camera moves.
     */
    val footprintWidening: Double = StarMath.footprintWidening(StarMath.DEFAULT_SHARPNESS),
    /**
     * A layer fades into its mean radiance as its cells shrink to a few pixels: from `fp / cell`
     * [fadeStart] to [fadeEnd]. §9.3 fades from 0.05 (cells of 20 px), which smears most faint stars.
     */
    val fadeStart: Double = 0.2,
    val fadeEnd: Double = 0.45,
    /** Brightness distribution `flux · h^magPower`; higher means fewer bright stars. */
    val magPower: Double = 8.0,
    /** Blend of the temperature ramp towards white-yellow, 0..1. */
    val warmth: Double = 0.35,
    val brightness: Double = 1.0,
    val seed: Int = 1337,
)

/** A layer of point stars: at most one star per cell of a [cells]² grid on every cube face. */
data class StarLayer(val cells: Int, val density: Double, val flux: Double)

val DEFAULT_STAR_LAYERS = listOf(
    StarLayer(cells = 256, density = 0.30, flux = 0.15),
    StarLayer(cells = 96, density = 0.25, flux = 0.6),
    StarLayer(cells = 32, density = 0.20, flux = 2.5),
)

data class HazeParams(
    val enabled: Boolean = true,
    /** Tilt of the Milky Way plane against the disk plane. */
    val milkyWayTiltDeg: Double = 62.0,
    val milkyWayWidth: Double = 0.22,
    val milkyWayBrightness: Double = 0.035,
    /** Dark dust lanes, 0..1. */
    val dust: Double = 0.6,
    val nebulae: Double = 0.012,
    /** Unresolved glow of faint stars. */
    val glow: Double = 0.004,
    val seed: Int = 11,
)

data class PostParams(
    val bloomEnabled: Boolean = true,
    val bloomThreshold: Double = 1.0,
    val bloomKnee: Double = 0.5,
    val bloomIntensity: Double = 0.21,
    val exposure: Double = 1.0,
    val tonemapper: Tonemapper = Tonemapper.ACES,
    /** Warm grading, 0..1. */
    val warmth: Double = 0.15,
    /** Film grain amplitude in LSB of the 8-bit output; 0 disables it. */
    val grain: Double = 0.5,
    /** Upscale the lens pass with Catmull-Rom instead of bilinear: crisper stars below render scale 1. */
    val sharpUpscale: Boolean = true,
)

enum class Tonemapper { ACES, AGX }

data class DopplerParams(
    val enabled: Boolean = false,
    /** Mix of the Doppler-shifted result with the plain one, 0..1 (Appendix A); used only when [enabled]. */
    val strength: Double = 0.6,
)

data class InputParams(
    val gyroEnabled: Boolean = true,
    val gyroGain: Double = 0.5,
    val gyroMaxAngleDeg: Double = 6.0,
    val gyroRecenterSec: Double = 2.5,
    val gyroInvertPitch: Boolean = false,
    val gyroInvertYaw: Boolean = false,
    /** 0..1: the 1€ filter on the gyroscope ([GyroTracker]). More takes out more hand tremor; 0 is off. */
    val gyroSmoothing: Double = 0.3,
    /** ω_n of the springs that smooth every input: higher follows faster. */
    val springOmega: Double = 10.0,
    /** ζ of those springs: 1 is the fastest approach without overshoot, lower swings past and back, higher creeps in. */
    val springDamping: Double = 1.0,
    /** Inputs moving faster than this keep the active frame rate. */
    val activityDegPerSec: Double = 0.5,
    val activityHoldSec: Double = 1.5,
    val launcherEnabled: Boolean = true,
    val launcherAngleDeg: Double = 10.0,
    /**
     * Recoil: while the gyroscope or the launcher turns the camera, the hole lags the other way and
     * springs back. [recoilStrength] scales the lag, 0 turns it off; [recoilReturnOmega] is the ω of the
     * way back; [recoilMaxShift] is the most it moves, in half screen heights, soft-clamped.
     */
    val recoilStrength: Double = 1.0,
    val recoilReturnOmega: Double = 6.0,
    val recoilMaxShift: Double = 0.12,
)

data class FrameParams(
    val fpsActive: Int = 60,
    val fpsIdle: Int = 30,
    val fpsPowerSave: Int = 15,
    /** In power save mode, show a still frame instead of [fpsPowerSave]. */
    val powerSaveStill: Boolean = false,
    val quality: QualitySetting = QualitySetting.HIGH,
)

/** [startLevel]: Auto starts on High and steps down while the GPU can't keep up ([QualityController]). */
enum class QualitySetting(val startLevel: QualityLevel) {
    AUTO(QualityLevel.HIGH),
    LOW(QualityLevel.LOW),
    MEDIUM(QualityLevel.MEDIUM),
    HIGH(QualityLevel.HIGH),
}

/** BLACKHOLE_TZ.md §12. */
enum class QualityLevel(
    val renderScale: Float,
    val tableAlpha: Int,
    val tablePhi: Int,
    val diskCrossings: Int,
    val starLayers: Int,
    val hazeFace: Int,
    val diskDetailWidth: Int,
    val diskDetailHeight: Int,
    val bloomLevels: Int,
    val maxFpsActive: Int,
    val maxFpsIdle: Int,
) {
    LOW(0.5f, 512, 512, 2, 2, 256, 1024, 128, 3, 30, 24),
    MEDIUM(0.75f, 1024, 768, 3, 3, 512, 2048, 256, 4, 60, 30),
    HIGH(1.0f, 1024, 1024, 3, 3, 512, 2048, 256, 5, 60, 30);

    fun lower(): QualityLevel = entries.getOrElse(ordinal - 1) { this }

    fun higher(): QualityLevel = entries.getOrElse(ordinal + 1) { this }
}

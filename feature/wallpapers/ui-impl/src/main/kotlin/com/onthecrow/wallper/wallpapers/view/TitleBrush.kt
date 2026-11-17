package com.onthecrow.wallper.wallpapers.view

import kotlin.math.hypot
import kotlin.math.max

/**
 * The way a brush goes through the lettering of `ic_wallper_title`, in its viewport ([VIEWPORT_WIDTH] ×
 * [VIEWPORT_HEIGHT]), to write it as the splash's W is written: the W, then «allper» in one stroke.
 * Each stroke is points of `x, y, r`: where the brush is and how far it reaches there, which covers the
 * letter at that point and little of the letters beside it. A point may come twice, where the pen goes
 * back over its own line (the l's, the tip of the W's middle, the p's tail).
 *
 * Made from the lettering's skeleton: the medial axis of the letters at 60 px a unit, its stroke order
 * picked by hand, its points thinned to 1.5 px, and each point's reach the letter's half-width there,
 * a quarter more and 0.03 on top, so that the strokes cover every pixel of the letters. Make it again if
 * the lettering changes.
 */
internal object TitleBrush {
    const val VIEWPORT_WIDTH = 41.8f
    const val VIEWPORT_HEIGHT = 15.8f

    val strokes: List<FloatArray> = listOf(
        floatArrayOf(
            2.935f, 9.120f, 0.116f, 2.758f, 9.275f, 0.197f, 2.408f, 9.408f, 0.283f, 2.125f, 9.458f, 0.405f,
            1.642f, 9.442f, 0.541f, 1.392f, 9.308f, 0.723f, 1.175f, 9.058f, 0.905f, 0.942f, 8.358f, 0.947f,
            0.908f, 8.192f, 0.948f, 0.925f, 7.975f, 0.950f, 0.992f, 7.525f, 0.950f, 1.125f, 7.025f, 0.950f,
            1.458f, 6.142f, 0.940f, 1.942f, 5.175f, 0.915f, 2.558f, 4.192f, 0.878f, 3.258f, 3.275f, 0.834f,
            3.875f, 2.608f, 0.781f, 4.575f, 2.008f, 0.723f, 5.125f, 1.658f, 0.645f, 5.692f, 1.442f, 0.589f,
            6.292f, 1.375f, 0.613f, 6.592f, 1.408f, 0.657f, 6.775f, 1.492f, 0.740f, 6.908f, 1.625f, 0.740f,
            7.092f, 2.175f, 0.859f, 7.175f, 2.742f, 0.900f, 7.125f, 3.308f, 0.935f, 6.908f, 4.275f, 0.959f,
            6.442f, 5.675f, 0.998f, 5.275f, 8.608f, 1.038f, 4.758f, 10.042f, 1.051f, 4.375f, 11.292f, 1.051f,
            4.225f, 12.242f, 1.051f, 4.392f, 13.025f, 1.013f, 4.475f, 13.208f, 1.006f, 4.608f, 13.342f, 1.006f,
            5.042f, 13.458f, 1.003f, 5.642f, 13.242f, 0.893f, 5.942f, 12.892f, 0.810f, 6.625f, 11.725f, 0.752f,
            7.508f, 9.875f, 0.692f, 8.058f, 8.625f, 0.664f, 8.308f, 8.342f, 1.206f, 9.008f, 7.975f, 1.399f,
            9.258f, 7.675f, 1.410f, 9.892f, 5.675f, 1.410f, 10.692f, 3.475f, 1.298f, 11.042f, 3.358f, 1.245f,
            10.692f, 3.475f, 1.298f, 9.892f, 5.675f, 1.415f, 9.242f, 7.692f, 1.415f, 9.342f, 8.058f, 1.415f,
            9.208f, 9.542f, 1.212f, 8.992f, 10.708f, 1.035f, 8.925f, 11.575f, 1.033f, 8.925f, 11.875f, 1.030f,
            9.008f, 12.292f, 1.030f, 9.092f, 12.575f, 1.039f, 9.225f, 12.792f, 1.039f, 9.858f, 12.975f, 1.032f,
            10.392f, 12.808f, 0.990f, 11.042f, 12.108f, 0.990f, 11.725f, 11.142f, 0.914f, 12.492f, 9.892f, 0.887f,
            13.258f, 8.508f, 0.906f, 13.892f, 7.258f, 0.944f, 14.492f, 5.942f, 0.979f, 14.992f, 4.692f, 1.020f,
            15.375f, 3.575f, 1.041f, 15.575f, 2.808f, 1.047f, 15.658f, 2.325f, 1.047f, 15.542f, 1.675f, 1.038f,
            15.342f, 0.992f, 0.822f, 15.208f, 0.708f, 0.594f, 14.925f, 0.358f, 0.511f, 14.608f, 0.192f, 0.320f,
            14.137f, 0.212f, 0.176f,
        ),
        floatArrayOf(
            20.665f, 6.356f, 0.962f, 20.008f, 6.475f, 1.074f, 19.525f, 7.192f, 1.095f, 19.192f, 7.375f, 1.095f,
            18.892f, 7.325f, 1.079f, 18.258f, 6.708f, 0.947f, 17.558f, 6.542f, 0.803f, 17.325f, 6.642f, 0.844f,
            16.608f, 7.408f, 0.863f, 16.208f, 7.992f, 0.869f, 15.808f, 8.708f, 0.893f, 15.375f, 9.742f, 1.036f,
            15.225f, 10.725f, 1.122f, 15.292f, 10.942f, 1.122f, 15.325f, 10.992f, 1.122f, 15.558f, 11.042f, 1.120f,
            16.375f, 10.725f, 1.009f, 16.808f, 10.242f, 0.713f, 17.708f, 9.108f, 1.059f, 18.525f, 8.792f, 1.203f,
            18.792f, 8.525f, 1.203f, 18.875f, 8.342f, 1.209f, 18.992f, 7.658f, 1.209f, 18.875f, 8.342f, 1.209f,
            18.775f, 8.542f, 1.203f, 18.708f, 8.992f, 1.203f, 18.675f, 10.125f, 1.047f, 18.608f, 10.558f, 1.059f,
            18.675f, 10.842f, 1.072f, 18.825f, 11.025f, 1.072f, 18.958f, 11.042f, 1.045f, 19.858f, 10.758f, 0.978f,
            20.308f, 10.342f, 0.609f, 20.525f, 10.075f, 1.063f, 21.358f, 9.758f, 1.122f, 21.642f, 9.542f, 1.126f,
            22.108f, 8.492f, 1.126f, 22.575f, 7.158f, 0.966f, 23.208f, 5.642f, 0.980f, 24.158f, 3.675f, 1.027f,
            24.625f, 2.825f, 1.059f, 25.125f, 2.058f, 1.059f, 25.208f, 1.975f, 1.053f, 25.658f, 1.908f, 0.958f,
            25.208f, 1.975f, 1.053f, 25.125f, 2.058f, 1.059f, 24.625f, 2.825f, 1.059f, 24.158f, 3.675f, 1.027f,
            23.208f, 5.642f, 0.980f, 22.575f, 7.158f, 0.966f, 22.108f, 8.492f, 1.137f, 21.625f, 9.558f, 1.137f,
            21.525f, 10.025f, 1.137f, 21.492f, 11.225f, 1.027f, 21.442f, 11.658f, 1.053f, 21.558f, 12.208f, 1.053f,
            21.708f, 12.375f, 1.053f, 21.842f, 12.392f, 0.988f, 22.642f, 12.075f, 0.966f, 23.042f, 11.508f, 0.515f,
            23.592f, 10.492f, 0.851f, 24.258f, 10.142f, 1.081f, 24.575f, 9.808f, 1.081f, 25.358f, 7.575f, 1.072f,
            26.075f, 5.925f, 0.955f, 27.008f, 4.125f, 0.963f, 27.692f, 3.025f, 0.984f, 28.092f, 2.458f, 0.984f,
            27.692f, 3.025f, 0.984f, 27.008f, 4.125f, 0.963f, 26.075f, 5.925f, 0.955f, 25.358f, 7.575f, 1.081f,
            24.558f, 9.825f, 1.081f, 24.475f, 10.792f, 1.081f, 24.475f, 11.308f, 0.914f, 24.375f, 11.825f, 0.947f,
            24.342f, 12.408f, 0.947f, 24.475f, 12.992f, 0.926f, 24.675f, 13.358f, 0.873f, 24.758f, 13.408f, 0.794f,
            25.208f, 13.458f, 0.768f, 25.308f, 13.508f, 0.884f, 25.408f, 13.875f, 0.925f, 25.308f, 14.575f, 0.933f,
            25.192f, 14.842f, 0.933f, 25.258f, 15.125f, 0.933f, 25.308f, 15.192f, 0.746f, 25.258f, 15.125f, 0.933f,
            25.192f, 14.842f, 0.933f, 25.308f, 14.575f, 0.933f, 25.408f, 13.842f, 0.951f, 25.608f, 13.525f, 0.959f,
            25.708f, 13.458f, 0.959f, 25.958f, 12.758f, 0.925f, 27.075f, 10.042f, 1.059f, 27.925f, 8.225f, 1.191f,
            28.392f, 7.392f, 1.191f, 28.508f, 6.942f, 1.191f, 28.675f, 5.975f, 1.024f, 28.492f, 6.992f, 1.024f,
            28.758f, 7.158f, 1.024f, 29.525f, 6.925f, 0.931f, 29.942f, 6.675f, 0.683f, 30.125f, 6.642f, 0.683f,
            30.675f, 6.725f, 0.663f, 30.808f, 6.842f, 0.712f, 30.942f, 7.108f, 0.822f, 31.042f, 7.475f, 0.929f,
            31.075f, 7.925f, 0.966f, 30.992f, 8.358f, 0.966f, 30.575f, 9.125f, 0.966f, 30.408f, 9.358f, 1.055f,
            29.792f, 10.042f, 1.120f, 28.342f, 11.458f, 1.120f, 30.275f, 9.525f, 1.120f, 30.575f, 9.125f, 0.966f,
            30.992f, 8.325f, 0.973f, 32.108f, 8.025f, 0.973f, 32.592f, 7.758f, 0.657f, 33.192f, 7.658f, 0.664f,
            33.292f, 7.658f, 0.832f, 33.442f, 7.808f, 0.874f, 33.858f, 7.742f, 0.971f, 33.942f, 7.358f, 0.984f,
            34.308f, 6.725f, 0.997f, 34.825f, 6.025f, 0.997f, 35.192f, 5.625f, 0.990f, 35.392f, 5.458f, 0.951f,
            35.542f, 5.425f, 0.916f, 35.675f, 5.425f, 0.863f, 36.325f, 5.575f, 0.785f, 36.492f, 5.942f, 0.748f,
            36.425f, 6.392f, 0.748f, 36.342f, 6.658f, 0.748f, 36.158f, 7.108f, 0.728f, 35.942f, 7.508f, 0.703f,
            35.475f, 8.075f, 0.697f, 35.158f, 8.275f, 0.648f, 34.908f, 8.342f, 0.599f, 34.575f, 8.292f, 0.566f,
            34.158f, 8.292f, 0.805f, 33.892f, 8.025f, 0.867f, 33.508f, 8.175f, 0.945f, 33.475f, 8.708f, 0.945f,
            33.408f, 9.025f, 0.929f, 33.375f, 9.708f, 0.905f, 33.492f, 10.225f, 0.905f, 33.625f, 10.525f, 0.878f,
            34.058f, 11.008f, 0.869f, 34.308f, 11.175f, 0.759f, 34.792f, 11.308f, 0.905f, 35.542f, 11.408f, 0.973f,
            35.742f, 11.342f, 0.989f, 36.158f, 11.008f, 0.989f, 36.875f, 10.175f, 0.938f, 37.458f, 9.225f, 0.885f,
            38.125f, 7.875f, 0.831f, 38.392f, 7.158f, 0.769f, 38.558f, 6.542f, 0.801f, 38.658f, 5.608f, 0.897f,
            38.675f, 5.175f, 0.990f, 38.575f, 4.758f, 1.002f, 38.358f, 4.442f, 1.002f, 38.592f, 4.792f, 1.002f,
            38.942f, 4.942f, 0.990f, 39.708f, 5.092f, 0.834f, 40.258f, 5.408f, 0.739f, 40.642f, 5.808f, 0.778f,
            40.808f, 6.175f, 0.893f, 40.942f, 6.792f, 0.920f, 40.858f, 7.475f, 0.940f, 40.725f, 7.992f, 0.986f,
            39.742f, 10.858f, 0.990f, 39.492f, 11.975f, 0.990f, 39.492f, 12.158f, 0.990f, 39.592f, 12.558f, 0.955f,
            39.808f, 13.158f, 0.785f, 39.925f, 13.358f, 0.555f, 40.125f, 13.558f, 0.496f, 40.258f, 13.625f, 0.343f,
            40.375f, 13.642f, 0.280f, 40.881f, 13.532f, 0.251f,
        ),
    )

    /** The brush's whole way, in viewport units: what [trace] measures its part in. */
    val length: Float = strokes.sumOf { stroke ->
        (0 until stroke.size / POINT - 1).sumOf { i -> stroke.pieceLength(i).toDouble() }
    }.toFloat()

    /**
     * The brush's way from [from] to [to], fractions of [length], as straight [piece]s from `x0, y0` to
     * `x1, y1` with the brush's reach: the part of the letters it went over between those two moments.
     * Nothing between two strokes: the pen is lifted.
     */
    fun trace(from: Float, to: Float, piece: (x0: Float, y0: Float, x1: Float, y1: Float, reach: Float) -> Unit) {
        val start = from.coerceIn(0f, 1f) * length
        val end = to.coerceIn(0f, 1f) * length
        if (end <= start) return
        var walked = 0f
        for (stroke in strokes) {
            for (i in 0 until stroke.size / POINT - 1) {
                val pieceLength = stroke.pieceLength(i)
                val pieceStart = walked
                walked += pieceLength
                if (walked <= start) continue
                val a = i * POINT
                val b = a + POINT
                val f0 = if (pieceLength > 0f) ((start - pieceStart) / pieceLength).coerceAtLeast(0f) else 0f
                val f1 = if (pieceLength > 0f) ((end - pieceStart) / pieceLength).coerceAtMost(1f) else 1f
                piece(
                    lerp(stroke[a], stroke[b], f0),
                    lerp(stroke[a + 1], stroke[b + 1], f0),
                    lerp(stroke[a], stroke[b], f1),
                    lerp(stroke[a + 1], stroke[b + 1], f1),
                    max(stroke[a + 2], stroke[b + 2]),
                )
                if (walked >= end) return
            }
        }
    }

    private fun FloatArray.pieceLength(i: Int): Float {
        val a = i * POINT
        return hypot(this[a + POINT] - this[a], this[a + POINT + 1] - this[a + 1])
    }

    private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction

    /** x, y and the reach. */
    private const val POINT = 3
}

package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.TransitionEffect

/**
 * The shaders that blend two slides into one frame, GLSL ES 1.00 for any context. Each effect is a
 * `transition(uv, p)`: the colour at `uv` (0 to 1, from the bottom left, as the slides were drawn) when `p`
 * (0 to 1) of the way from the slide leaving (`fromColor`) to the one coming in (`toColor`).
 */
internal object TransitionShaders {

    const val POSITION = "aPosition"
    const val FROM = "uFrom"
    const val TO = "uTo"
    const val PROGRESS = "uProgress"
    const val SIZE = "uSize"

    /** A quad over the whole target, from four corners as a triangle strip. */
    val VERTEX = """
        attribute vec2 $POSITION;
        varying vec2 vUv;

        void main() {
            vUv = $POSITION * 0.5 + 0.5;
            gl_Position = vec4($POSITION, 0.0, 1.0);
        }
    """.trimIndent()

    /** The whole fragment shader of [effect]; there is none for [TransitionEffect.NONE]. */
    fun fragment(effect: TransitionEffect): String {
        val body = requireNotNull(BODIES[effect]) { "$effect has no shader" }
        return "$HEADER\n\nvec3 transition(vec2 uv, float p) {\n$body\n}\n\n$MAIN"
    }

    // Coordinates need high precision where there is any: medium has too few steps for a screen's height.
    private val HEADER = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif

        varying vec2 vUv;
        uniform sampler2D $FROM;
        uniform sampler2D $TO;
        uniform float $PROGRESS;
        uniform vec2 $SIZE;

        const float PI = 3.14159265;

        vec3 fromColor(vec2 uv) { return texture2D($FROM, clamp(uv, 0.0, 1.0)).rgb; }
        vec3 toColor(vec2 uv) { return texture2D($TO, clamp(uv, 0.0, 1.0)).rgb; }

        float smoothen(float x) {
            x = clamp(x, 0.0, 1.0);
            return x * x * (3.0 - 2.0 * x);
        }

        float easeInOut(float x) {
            return x < 0.5 ? 4.0 * x * x * x : 1.0 - pow(2.0 - 2.0 * x, 3.0) / 2.0;
        }

        /** 0 to 1, the same for a pixel each frame. */
        float hash(vec2 v) { return fract(sin(dot(v, vec2(12.9898, 78.233))) * 43758.5453); }
    """.trimIndent()

    private val MAIN = """
        void main() {
            gl_FragColor = vec4(transition(vUv, $PROGRESS), 1.0);
        }
    """.trimIndent()

    private val BODIES = mapOf(
        // In linear light: in the screen's own values the middle of the way goes darker than either.
        TransitionEffect.CROSSFADE to """
            vec3 a = pow(fromColor(uv), vec3(2.2));
            vec3 b = pow(toColor(uv), vec3(2.2));
            return pow(mix(a, b, smoothen(p)), vec3(1.0 / 2.2));
        """,
        TransitionEffect.DIP_TO_BLACK to """
            return p < 0.5 ? fromColor(uv) * (1.0 - smoothen(p * 2.0)) : toColor(uv) * smoothen(p * 2.0 - 1.0);
        """,
        // A spiral of taps, turned for each pixel, so that a wide blur from few taps is grain, not copies.
        TransitionEffect.BLUR to """
            float radius = sin(PI * p) * 0.02;
            vec2 scale = vec2($SIZE.y / $SIZE.x, 1.0) * radius;
            float turn = hash(gl_FragCoord.xy) * 2.0 * PI;
            vec3 a = vec3(0.0);
            vec3 b = vec3(0.0);
            for (int i = 0; i < 20; i++) {
                float fi = float(i);
                float angle = fi * 2.39996323 + turn;
                vec2 offset = vec2(cos(angle), sin(angle)) * sqrt((fi + 0.5) / 20.0) * scale;
                a += fromColor(uv + offset);
                b += toColor(uv + offset);
            }
            return mix(a, b, smoothen((p - 0.2) / 0.6)) / 20.0;
        """,
        TransitionEffect.ZOOM to """
            float t = easeInOut(p);
            vec2 q = uv - 0.5;
            vec3 a = fromColor(0.5 + q / (1.0 + 0.35 * t));
            vec3 b = toColor(0.5 + q / (1.12 - 0.12 * t));
            return mix(a, b, smoothen((p - 0.15) / 0.7));
        """,
        TransitionEffect.SLIDE_UP to """
            float y = uv.y - easeInOut(p);
            return y >= 0.0 ? fromColor(vec2(uv.x, y)) : toColor(vec2(uv.x, y + 1.0));
        """,
        // The brighter a part of the next picture, the sooner it shows.
        TransitionEffect.LUMA to """
            vec3 b = toColor(uv);
            float l = dot(b, vec3(0.299, 0.587, 0.114));
            float w = 0.3;
            return mix(fromColor(uv), b, smoothstep(1.0 - l, 1.0 - l + w, p * (1.0 + w)));
        """,
        TransitionEffect.WIPE to """
            float w = 0.25;
            float s = (uv.x + uv.y) * 0.5;
            return mix(fromColor(uv), toColor(uv), 1.0 - smoothstep(p * (1.0 + w) - w, p * (1.0 + w), s));
        """,
        TransitionEffect.IRIS to """
            float aspect = $SIZE.x / $SIZE.y;
            float d = length((uv - 0.5) * vec2(aspect, 1.0));
            float w = 0.12;
            float r = smoothen(p) * (length(vec2(aspect, 1.0)) * 0.5 + w);
            return mix(fromColor(uv), toColor(uv), 1.0 - smoothstep(r - w, r, d));
        """,
        TransitionEffect.RIPPLE to """
            float aspect = $SIZE.x / $SIZE.y;
            vec2 q = (uv - 0.5) * vec2(aspect, 1.0);
            float d = length(q);
            vec2 direction = q / max(d, 0.001);
            vec2 offset = direction * sin(d * 45.0 - p * 30.0) * sin(PI * p) * 0.02 / vec2(aspect, 1.0);
            return mix(fromColor(uv + offset), toColor(uv + offset), smoothen((p - 0.25) / 0.5));
        """,
        // Squares of a height's share, the largest halfway.
        TransitionEffect.PIXELATE to """
            float s = 1.0 - abs(2.0 * p - 1.0);
            float size = max(s * s * 0.09, 1.0 / $SIZE.y);
            vec2 cell = vec2(size * $SIZE.y / $SIZE.x, size);
            vec2 snapped = s < 0.02 ? uv : (floor(uv / cell) + 0.5) * cell;
            return mix(fromColor(snapped), toColor(snapped), smoothen((p - 0.4) / 0.2));
        """,
    ).mapValues { (_, body) -> body.trimIndent().prependIndent("    ") }
}

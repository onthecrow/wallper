// Bakes the polar disk detail (§8.2): x is φ (tiles), y is the normalized radius.
// R: emission detail, G: density. Fibres run along φ.
precision highp float;
precision highp int;

#include "common.glsl"

uniform vec2 uSize;
uniform uint uSeed;
uniform float uStretch;   // anisotropy along φ against r
uniform float uClumps;

out vec4 oColor;

void main() {
    vec2 st = gl_FragCoord.xy / uSize;
    float phi = st.x * TAU;
    float rn = st.y;
    const float A = 2.5;
    float B = A * uStretch * 1.4;
    // A circle in noise space tiles seamlessly in φ.
    vec3 p = vec3(cos(phi) * A, sin(phi) * A, rn * B);

    float n = fbm(p, uSeed);
    float rings = 0.0;
    rings += 0.5 + 0.5 * sin(rn * 41.0 + n * 3.0);
    rings += 0.5 + 0.5 * sin(rn * 97.0 + n * 5.0 + 1.3);
    rings += 0.5 + 0.5 * sin(rn * 173.0 + n * 7.0 + 2.1);
    rings /= 3.0;

    vec3 q = vec3(cos(phi) * A * 2.0, sin(phi) * A * 2.0, rn * B * 0.5);
    float clumpNoise = fbm(q + vec3(31.0), uSeed + 17u);
    float clumps = smoothstep(0.35, 0.75, clumpNoise) * uClumps;

    float emission = clamp(0.25 + 0.45 * (0.5 + 0.5 * n) + 0.3 * rings + clumps * 1.2, 0.0, 1.0);
    float density = clamp(0.45 + 0.35 * (0.5 + 0.5 * fbm(p * 0.7 + vec3(5.0), uSeed + 5u)) + 0.2 * rings, 0.0, 1.0);
    oColor = vec4(emission, density, 0.0, 1.0);
}

// Bakes one face of the diffuse sky cubemap (§9.4): Milky Way with dust lanes, faint nebulae,
// unresolved glow. Face orientation follows the GL specification's cube map table.
precision highp float;
precision highp int;

#include "common.glsl"

uniform int uFace;
uniform float uFaceSize;
uniform uint uSeed;
uniform vec3 uMilkyWayNormal;
uniform float uMilkyWayWidth;
uniform float uMilkyWayBrightness;
uniform float uDust;
uniform float uNebulae;
uniform float uGlow;

out vec4 oColor;

vec3 faceDirection(int face, vec2 st) {
    float sc = st.x * 2.0 - 1.0;
    float tc = st.y * 2.0 - 1.0;
    if (face == 0) return vec3(1.0, -tc, -sc);
    if (face == 1) return vec3(-1.0, -tc, sc);
    if (face == 2) return vec3(sc, 1.0, tc);
    if (face == 3) return vec3(sc, -1.0, -tc);
    if (face == 4) return vec3(sc, -tc, 1.0);
    return vec3(-sc, -tc, -1.0);
}

void main() {
    vec3 dir = normalize(faceDirection(uFace, gl_FragCoord.xy / uFaceSize));

    float d = dot(dir, uMilkyWayNormal);
    float band = exp(-d * d / (uMilkyWayWidth * uMilkyWayWidth));
    float structure = 0.6 + 0.4 * fbm(dir * 4.0, uSeed);
    float dust = clamp(0.5 + 0.5 * fbm(dir * 9.0 + vec3(3.1), uSeed + 7u), 0.0, 1.0);
    dust = smoothstep(0.45, 0.8, dust) * uDust * band;
    vec3 milkyWay = vec3(1.0, 0.93, 0.84) * band * structure * (1.0 - dust) * uMilkyWayBrightness;

    float nebulaShape = smoothstep(0.25, 0.7, 0.5 + 0.5 * fbm(dir * 1.7 + vec3(11.0), uSeed + 23u));
    float hue = 0.5 + 0.5 * gradientNoise(dir * 1.1 + vec3(2.0), uSeed + 29u);
    vec3 nebula = mix(vec3(0.25, 0.55, 0.9), vec3(0.9, 0.3, 0.55), hue) * nebulaShape * uNebulae;

    float glow = (0.5 + 0.5 * fbm(dir * 40.0, uSeed + 41u)) * uGlow;

    oColor = vec4(milkyWay + nebula + vec3(glow), 1.0);
}

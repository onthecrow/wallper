// Pass 3 (§6.2): upscale, bloom, exposure, tonemapping, warm grade, grain, dithering, sRGB.
precision highp float;
precision highp int;

#include "common.glsl"

uniform sampler2D uScene;
uniform vec2 uSceneSize;
uniform float uSharpUpscale;     // 1: Catmull-Rom, 0: bilinear
uniform sampler2D uBloom;
uniform float uBloomIntensity;   // 0 when bloom is off
uniform float uExposure;
uniform int uTonemapper;
uniform float uWarmth;
uniform float uGrain;            // in 8-bit LSB
uniform float uFade;             // fades the picture in once the tables are ready
uniform float uNoiseFrame;

in vec2 vUv;
out vec4 oColor;

// Catmull-Rom from 9 bilinear taps (MJP): keeps stars and disk fibres crisp when the lens pass runs
// below the screen resolution. Its negative lobes can undershoot next to bright stars: clamped.
vec3 sampleCatmullRom(vec2 uv) {
    vec2 position = uv * uSceneSize;
    vec2 p1 = floor(position - 0.5) + 0.5;
    vec2 f = position - p1;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 t0 = (p1 - 1.0) / uSceneSize;
    vec2 t3 = (p1 + 2.0) / uSceneSize;
    vec2 t12 = (p1 + w2 / w12) / uSceneSize;
    vec3 c = texture(uScene, vec2(t0.x, t0.y)).rgb * w0.x * w0.y;
    c += texture(uScene, vec2(t12.x, t0.y)).rgb * w12.x * w0.y;
    c += texture(uScene, vec2(t3.x, t0.y)).rgb * w3.x * w0.y;
    c += texture(uScene, vec2(t0.x, t12.y)).rgb * w0.x * w12.y;
    c += texture(uScene, vec2(t12.x, t12.y)).rgb * w12.x * w12.y;
    c += texture(uScene, vec2(t3.x, t12.y)).rgb * w3.x * w12.y;
    c += texture(uScene, vec2(t0.x, t3.y)).rgb * w0.x * w3.y;
    c += texture(uScene, vec2(t12.x, t3.y)).rgb * w12.x * w3.y;
    c += texture(uScene, vec2(t3.x, t3.y)).rgb * w3.x * w3.y;
    return max(c, vec3(0.0));
}

void main() {
    vec3 c = uSharpUpscale > 0.5 ? sampleCatmullRom(vUv) : texture(uScene, vUv).rgb;
    c += texture(uBloom, vUv).rgb * uBloomIntensity;
#if HDR_TARGET
    c = tonemap(c * uExposure, uTonemapper);
#endif
    c *= mix(vec3(1.0), vec3(1.06, 1.0, 0.9), uWarmth);
    c = linearToSrgb(c) * uFade;
    vec2 p = gl_FragCoord.xy + 5.588238 * uNoiseFrame;
    c += (interleavedGradientNoise(p) - 0.5) / 255.0;
    c += (hash4(uvec4(uvec2(gl_FragCoord.xy), uint(uNoiseFrame), 3u)).x - 0.5) * uGrain / 255.0;
    oColor = vec4(c, 1.0);
}

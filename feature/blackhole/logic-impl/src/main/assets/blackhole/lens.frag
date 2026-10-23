// Pass 1 (§6.3): the lens from the precomputed trajectory tables. A few texture reads per pixel.
precision highp float;
precision highp int;
precision highp sampler2DArray;

#include "common.glsl"
#include "sky.glsl"
#include "disk.glsl"
#include "ray.glsl"

uniform float uK;
uniform float uExpKm1;          // e^K − 1
uniform vec2 uTabSize;          // (N_α, N_φ)
uniform sampler2DArray uTrajU;  // R16F: u(t, φ), layer = branch
uniform sampler2D uEnd;         // R32F: φ_end(t), row = branch, read with texelFetch

in vec2 vNdc;
out vec4 oColor;

float endLookup(float t, int branch) {
    float x = t * (uTabSize.x - 1.0);
    int i0 = int(floor(x));
    int i1 = min(i0 + 1, int(uTabSize.x) - 1);
    return mix(texelFetch(uEnd, ivec2(i0, branch), 0).r, texelFetch(uEnd, ivec2(i1, branch), 0).r, fract(x));
}

void main() {
    Ray ray = cameraRay(vNdc);

    bool captured = ray.alpha < uAlphaC;
    int branch = captured ? 0 : 1;
    float span = captured ? uAlphaC : (PI - uAlphaC);
    float t = clamp(log(1.0 + abs(ray.alpha - uAlphaC) * uExpKm1 / span) / uK, 0.0, 1.0);
    float phiEnd = endLookup(t, branch);
    float tx = (t * (uTabSize.x - 1.0) + 0.5) / uTabSize.x;

    // The sky always: it needs derivatives. Masked at the end.
    float footprint;
    vec3 sky = skyColor(skyDirection(ray, phiEnd), footprint);

    float phi0 = firstCrossing(ray);
    float planeOk = planeCrosses(ray);
    vec3 color = vec3(0.0);
    float transmittance = 1.0;
    float firstImage = -1.0;
#if OPTIMIZED
    // Crossing k lies along ±w0, so its azimuth in the disk is φD0 + kπ: one atan for all of them.
    // The texture repeats in φ, which absorbs the extra half turns.
    vec3 w0 = firstCrossingDir(ray);
    float phiD0 = atan(w0.z, w0.x);
    // Crossings in order of φ, which is the order along the ray: front-to-back. No break: derivatives.
    for (int k = 0; k < K_MAX; k++) {
        float phi = phi0 + float(k) * PI;
        float ty = (min(phi, PHI_MAX) / PHI_MAX * (uTabSize.y - 1.0) + 0.5) / uTabSize.y;
        float u = textureLod(uTrajU, vec3(tx, ty, float(branch)), 0.0).r;
        float r = 1.0 / max(u, 1e-4);
        float valid = planeOk * step(phi, phiEnd) * step(phi, PHI_MAX) * step(uDiskInner, r) * step(r, uDiskOuter);
        // The derivatives here, in uniform control flow. The texture reads and the shading only where
        // the ray really crosses the disk: a few percent of the crossings.
        float q = uDiskInner / max(r, 1e-3);
        DiskCoords coords = diskCoords(phiD0 + float(k) * PI, r, q * sqrt(q));
        if (valid > 0.0) {
            float g = uDopplerStrength > 0.0 ? dopplerFactor(ray, r) : 1.0;
            vec4 disk = diskShade(coords, r, g);
            color += transmittance * disk.rgb;
            transmittance *= 1.0 - disk.a;
            if (firstImage < 0.0 && disk.a > 0.01) firstImage = float(k);
        }
    }
#else
    // Crossings in order of φ, which is the order along the ray: front-to-back. No break: derivatives.
    for (int k = 0; k < K_MAX; k++) {
        float phi = phi0 + float(k) * PI;
        float ty = (min(phi, PHI_MAX) / PHI_MAX * (uTabSize.y - 1.0) + 0.5) / uTabSize.y;
        float u = textureLod(uTrajU, vec3(tx, ty, float(branch)), 0.0).r;
        float r = 1.0 / max(u, 1e-4);
        float g = uDopplerStrength > 0.0 ? dopplerFactor(ray, r) : 1.0;
        vec4 disk = diskSample(rayPoint(ray, r, phi), r, g);
        float valid = planeOk * step(phi, phiEnd) * step(phi, PHI_MAX) * step(uDiskInner, r) * step(r, uDiskOuter);
        color += transmittance * disk.rgb * valid;
        transmittance *= 1.0 - disk.a * valid;
        if (firstImage < 0.0 && valid * disk.a > 0.01) firstImage = float(k);
    }
#endif
    // One-pixel antialiasing of the shadow edge.
    float edge = clamp((ray.alpha - uAlphaC) / max(fwidth(ray.alpha), 1e-7) + 0.5, 0.0, 1.0);
    color += transmittance * sky * edge;

    if (uDebugView == 1) color = heatmap(t) * (captured ? 0.4 : 1.0);
    else if (uDebugView == 2) color = diskImageColor(firstImage);
    else if (uDebugView == 3) color = heatmap(log2(footprint / max(uStarSigma, 1e-9)) / 8.0);
    oColor = encodeTarget(color);
}

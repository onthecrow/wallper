// The reference lens (§13): integrates the same ODE per pixel with RK4 and shades with the same
// diskSample / skyColor. Slow, for A/B against the tables only.
precision highp float;
precision highp int;

#include "common.glsl"
#include "sky.glsl"
#include "disk.glsl"
#include "ray.glsl"

in vec2 vNdc;
out vec4 oColor;

const int MAX_STEPS = 3000;
const float H_MAX = 0.01;
const float C_STEP = 0.004;

float accel(float u) {
    return -u + 1.5 * u * u;
}

void main() {
    Ray ray = cameraRay(vNdc);
    float phi0 = firstCrossing(ray);

    // Integrate first, record the crossings; shade afterwards in uniform control flow.
    float u = 1.0 / uRc;
    float v = u * sqrt(1.0 - u) / tan(max(ray.alpha, 1e-6));
    float phi = 0.0;
    float phiEnd = PHI_END_MAX;
    bool fell = false;
    float hitR[K_MAX];
    for (int k = 0; k < K_MAX; k++) hitR[k] = 0.0;
    int next = 0;
    for (int s = 0; s < MAX_STEPS; s++) {
        float h = min(H_MAX, C_STEP / max(abs(v), 1e-6));
        float u0 = u;
        float phiPrev = phi;
        float k1u = v;
        float k1v = accel(u);
        float k2u = v + 0.5 * h * k1v;
        float k2v = accel(u + 0.5 * h * k1u);
        float k3u = v + 0.5 * h * k2v;
        float k3v = accel(u + 0.5 * h * k2u);
        float k4u = v + h * k3v;
        float k4v = accel(u + h * k3u);
        u += h / 6.0 * (k1u + 2.0 * k2u + 2.0 * k3u + k4u);
        v += h / 6.0 * (k1v + 2.0 * k2v + 2.0 * k3v + k4v);
        phi += h;
        if (next < K_MAX) {
            float crossing = phi0 + float(next) * PI;
            if (phi >= crossing) {
                hitR[next] = 1.0 / max(mix(u0, u, (crossing - phiPrev) / h), 1e-4);
                next++;
            }
        }
        if (u >= 1.0) { phiEnd = phiPrev + h * (1.0 - u0) / (u - u0); fell = true; break; }
        if (u <= 0.0) { phiEnd = phiPrev + h * u0 / (u0 - u); break; }
        if (phi > PHI_END_MAX) break;
    }

    float footprint;
    vec3 sky = skyColor(skyDirection(ray, phiEnd), footprint);
    float planeOk = planeCrosses(ray);
    vec3 color = vec3(0.0);
    float transmittance = 1.0;
    for (int k = 0; k < K_MAX; k++) {
        float r = hitR[k];
        float crossing = phi0 + float(k) * PI;
        float g = uDopplerStrength > 0.0 ? dopplerFactor(ray, max(r, 1.6)) : 1.0;
        vec4 disk = diskSample(rayPoint(ray, max(r, 1e-3), crossing), max(r, 1e-3), g);
        float valid = planeOk * step(crossing, phiEnd) * step(uDiskInner, r) * step(r, uDiskOuter);
        color += transmittance * disk.rgb * valid;
        transmittance *= 1.0 - disk.a * valid;
    }
    if (!fell) color += transmittance * sky;
    oColor = encodeTarget(color);
}

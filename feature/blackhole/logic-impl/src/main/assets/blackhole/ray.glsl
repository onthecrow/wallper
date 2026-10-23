// Camera rays in the plane through the hole (§6.3), shared by the table and the reference lens.
uniform vec3 uCamPos;           // |uCamPos| = r_c
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform vec3 uCamFwd;
uniform vec2 uTanHalfFov;       // tan(fovX / 2), tan(fovY / 2)
uniform vec2 uPrincipal;        // projection centre offset in NDC
uniform float uRc;
uniform float uAlphaC;          // angular radius of the shadow
uniform float uLensStrength;    // 1 is physical; the sky only
uniform vec2 uLensFade;         // angles from the hole where that strength fades out; beyond π: never
uniform float uExposure;
uniform int uTonemapper;
uniform int uDebugView;         // 0 image, 1 table parameter t, 2 disk image index, 3 sky footprint

struct Ray {
    vec3 dir;
    vec3 e1;    // from the hole to the camera
    vec3 e2;    // tangential part of dir
    float alpha;
};

Ray cameraRay(vec2 ndc) {
    vec2 p = ndc - uPrincipal;
    Ray ray;
    ray.dir = normalize(uCamFwd + p.x * uTanHalfFov.x * uCamRight + p.y * uTanHalfFov.y * uCamUp);
    ray.e1 = uCamPos / uRc;
    vec3 tangent = ray.dir - ray.e1 * dot(ray.dir, ray.e1);
    float tl = length(tangent);
    // A strictly radial ray has no plane of its own: any plane through e1 will do.
    ray.e2 = tl > 1e-7 ? tangent / tl : normalize(cross(ray.e1, uCamRight));
    ray.alpha = atan(tl, dot(ray.dir, -ray.e1)); // steadier than acos near 0
    return ray;
}

vec3 rayPoint(Ray ray, float r, float phi) {
    return r * (cos(phi) * ray.e1 + sin(phi) * ray.e2);
}

// First disk crossing φ0 ∈ (0, π]; the others follow every π.
float firstCrossing(Ray ray) {
    float phi0 = atan(-ray.e1.y, ray.e2.y);
    return phi0 <= 0.0 ? phi0 + PI : phi0;
}

// The direction of the first crossing in the disk plane, cos φ0·e1 + sin φ0·e2, without trigonometry:
// (cos, sin) of atan(−e1.y, e2.y) is (e2.y, −e1.y) / ρ, and firstCrossing adds π when that angle is ≤ 0.
// Crossing k is at φ0 + kπ, so its point is (−1)^k · r_k times this.
vec3 firstCrossingDir(Ray ray) {
    vec2 cs = vec2(ray.e2.y, -ray.e1.y);
    float rho = length(cs);
    if (rho < 1e-7) return ray.e1; // the ray plane is the disk plane: no crossings anyway
    float flip = cs.y < 0.0 || (cs.y == 0.0 && cs.x > 0.0) ? -1.0 : 1.0;
    cs *= flip / rho;
    return cs.x * ray.e1 + cs.y * ray.e2;
}

// The ray plane coincides with the disk plane: no crossings.
float planeCrosses(Ray ray) {
    return step(1e-6, abs(ray.e1.y) + abs(ray.e2.y));
}

// Appendix A: g = ν_obs / ν_emit for the Keplerian disk seen by a static observer.
float dopplerFactor(Ray ray, float r) {
    float omega = sqrt(1.0 / (2.0 * r * r * r));
    float ut = 1.0 / sqrt(max(1.0 - 1.5 / r, 1e-4));
    float b = uRc * sin(ray.alpha) / sqrt(1.0 - 1.0 / uRc);
    vec3 normal = cross(ray.e1, ray.dir);
    float nl = length(normal);
    float lambda = nl > 1e-7 ? -b * normal.y / nl : 0.0;
    float g = 1.0 / (sqrt(1.0 - 1.0 / uRc) * ut * (1.0 - uSense * omega * lambda));
    return clamp(g, 0.1, 3.0);
}

vec3 skyDirection(Ray ray, float phiEnd) {
    float straight = PI - ray.alpha;
    float strength = uLensStrength * (1.0 - smoothstep(uLensFade.x, uLensFade.y, ray.alpha));
    float phi = straight + strength * (phiEnd - straight);
    return cos(phi) * ray.e1 + sin(phi) * ray.e2;
}

vec3 diskImageColor(float k) {
    return k < 0.0 ? vec3(0.0) : (k < 0.5 ? vec3(1.0, 0.3, 0.2) : (k < 1.5 ? vec3(0.2, 1.0, 0.3) : vec3(0.3, 0.4, 1.0)));
}

// Without a float target the lens pass tonemaps itself (§6.2 fallback).
vec4 encodeTarget(vec3 c) {
#if HDR_TARGET
    return vec4(c, 1.0);
#else
    return vec4(tonemap(c * uExposure, uTonemapper), 1.0);
#endif
}

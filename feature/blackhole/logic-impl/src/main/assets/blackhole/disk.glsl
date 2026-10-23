// Thin accretion disk in the plane y = 0 (§8): baked polar detail texture, Keplerian flow with two
// crossfaded phases, colour ramp, optional Doppler (Appendix A).
uniform sampler2D uDiskDetail;
uniform float uDiskEnabled;
uniform float uDiskInner;
uniform float uDiskOuter;
uniform float uDiskInnerWidth;
uniform float uDiskOuterFade;
uniform float uDiskPower;
uniform float uSpin;          // turns of the inner edge per flow period
uniform float uSense;         // +1: counter-clockwise seen from +Y
uniform float uPhaseA;
uniform float uPhaseB;
uniform float uFlowMix;
uniform float uDiskOpacity;
uniform float uDiskOpacityFalloff;   // k in (R_IN / r)^k; 0 keeps the opacity even across the disk
uniform float uDiskBrightness;
uniform vec4 uRampPos;
uniform vec3 uRampColor[4];   // colour × multiplier, linear HDR
uniform float uDopplerStrength;

const float GRAD_MAX = 0.05;

vec3 diskRamp(float rn) {
    if (rn <= uRampPos.y) {
        return mix(uRampColor[0], uRampColor[1], clamp((rn - uRampPos.x) / max(uRampPos.y - uRampPos.x, 1e-4), 0.0, 1.0));
    }
    if (rn <= uRampPos.z) {
        return mix(uRampColor[1], uRampColor[2], (rn - uRampPos.y) / max(uRampPos.z - uRampPos.y, 1e-4));
    }
    return mix(uRampColor[2], uRampColor[3], clamp((rn - uRampPos.z) / max(uRampPos.w - uRampPos.z, 1e-4), 0.0, 1.0));
}

float radialIntensity(float r) {
    return smoothstep(uDiskInner, uDiskInner + uDiskInnerWidth, r) * pow(uDiskInner / max(r, 1e-3), uDiskPower);
}

float edgeFade(float rn) {
    return smoothstep(1.0, 1.0 - uDiskOuterFade, rn);
}

vec2 clampLength(vec2 v, float maxLength) {
    float l = length(v);
    return l > maxLength ? v * (maxLength / l) : v;
}

// Where a disk point samples the detail texture: two flow phases at (x, rn), and their derivatives.
struct DiskCoords {
    vec2 a;
    vec2 b;
    vec2 dx;
    vec2 dy;
};

// Must be called in uniform control flow: it takes derivatives. [spiral] is (R_IN / r)^1.5: the inner
// disk turns faster.
DiskCoords diskCoords(float phiD, float r, float spiral) {
    float rn = clamp((r - uDiskInner) / (uDiskOuter - uDiskInner), 0.0, 1.0);
    float turns = uSpin * spiral;
    float xA = phiD / TAU + uSense * turns * uPhaseA;
    float xB = phiD / TAU + uSense * turns * uPhaseB + 0.37; // decorrelates the layers

    // No jump of the φ derivative at the atan seam, otherwise the seam takes the coarsest mip.
    DiskCoords c;
    c.a = vec2(xA, rn);
    c.b = vec2(xB, rn);
    c.dx = vec2(dFdx(xA), dFdx(rn));
    c.dx.x -= floor(c.dx.x + 0.5);
    c.dy = vec2(dFdy(xA), dFdy(rn));
    c.dy.x -= floor(c.dy.x + 0.5);
    return c;
}

// rgb: emission (HDR), a: opacity. g is the Doppler factor, 1 when it's off. Explicit gradients from
// [diskCoords], so any control flow will do.
vec4 diskShade(DiskCoords c, float r, float g) {
    vec2 dx = clampLength(c.dx, GRAD_MAX);
    vec2 dy = clampLength(c.dy, GRAD_MAX);
    vec2 a = textureGrad(uDiskDetail, c.a, dx, dy).rg;
    vec2 b = textureGrad(uDiskDetail, c.b, dx, dy).rg;
    vec2 detail = mix(a, b, uFlowMix);
    float rn = c.a.y;

    float fade = edgeFade(rn);
    // Doppler: a redshifted part looks like the cooler outer disk, and dims as g⁴.
    float shift = (1.0 - g) * 0.6 * uDopplerStrength;
    vec3 emission = diskRamp(clamp(rn + shift, 0.0, 1.0)) * radialIntensity(r) * detail.r * fade * uDiskBrightness;
    emission *= mix(1.0, g * g * g * g, uDopplerStrength);
    // Where the glow fades out the gas gets thin too, instead of a dark rim hiding the sky.
    float falloff = pow(uDiskInner / max(r, uDiskInner), uDiskOpacityFalloff);
    float opacity = clamp(uDiskOpacity * detail.g * fade * falloff, 0.0, 1.0);
    return vec4(emission, opacity) * uDiskEnabled;
}

// The disk at [hit], all in one. Must be called in uniform control flow: it takes derivatives.
vec4 diskSample(vec3 hit, float r, float g) {
    return diskShade(diskCoords(atan(hit.z, hit.x), r, pow(max(r, 1e-3) / uDiskInner, -1.5)), r, g);
}

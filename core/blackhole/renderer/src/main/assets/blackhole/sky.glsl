// Procedural sky (§9): the haze cubemap plus point stars computed analytically, so they stay sharp
// at any lens magnification.
uniform samplerCube uHaze;
uniform float uHazeEnabled;
uniform float uStarsEnabled;
uniform mat3 uStarRot[STAR_LAYERS];
uniform vec4 uStarLayer[STAR_LAYERS];   // cells, density, flux, unused
uniform vec3 uStarAvg[STAR_LAYERS];     // mean radiance of the layer, coloured
uniform float uStarSigma;                // σ of a star, radians
uniform float uStarWidening;             // σ²_eff = σ² + w · fp²
uniform vec2 uStarFade;                  // fp / cell where a layer starts and ends fading into its mean
uniform float uMagPower;
uniform float uStarWarmth;
uniform float uStarBrightness;
uniform uint uStarSeed;
uniform float uFpMax;                    // footprint clamp at the shadow edge, radians

vec3 starTint(float h) {
    // ~3000 K orange through white to ~10000 K blue-white, pulled towards white-yellow.
    vec3 orange = vec3(1.0, 0.62, 0.35);
    vec3 white = vec3(1.0, 0.95, 0.88);
    vec3 blue = vec3(0.72, 0.82, 1.0);
    vec3 c = h < 0.5 ? mix(orange, white, h * 2.0) : mix(white, blue, h * 2.0 - 1.0);
    return mix(c, vec3(1.0, 0.96, 0.86), uStarWarmth);
}

vec3 starLayer(vec3 dir, float fp, int i) {
    vec3 d = uStarRot[i] * dir;
    vec3 a = abs(d);
    uint face;
    vec2 uv;
    if (a.x >= a.y && a.x >= a.z) {
        face = d.x > 0.0 ? 0u : 1u;
        uv = d.yz / a.x;
    } else if (a.y >= a.z) {
        face = d.y > 0.0 ? 2u : 3u;
        uv = d.xz / a.y;
    } else {
        face = d.z > 0.0 ? 4u : 5u;
        uv = d.xy / a.z;
    }
    vec4 layer = uStarLayer[i];
    float cells = layer.x;
    vec2 q = (4.0 / PI) * atan(uv);                  // equi-angular cube map, [-1, 1]
    vec2 c = (q * 0.5 + 0.5) * cells;
    vec2 cell = min(floor(c), vec2(cells - 1.0));
    vec2 f = c - cell;
    vec4 h = hash4(uvec4(uvec2(cell), face, uStarSeed + uint(i) * 7919u));

    // Density compensation by the cell's solid angle, so faces and corners look the same.
    vec2 t = tan(((cell + 0.5) / cells * 2.0 - 1.0) * (PI / 4.0));
    float uu = t.x * t.x;
    float vv = t.y * t.y;
    float jacobian = (PI * PI / 16.0) * (1.0 + uu) * (1.0 + vv) / pow(1.0 + uu + vv, 1.5);
    float probability = layer.y * jacobian / 0.5235988;
    float cellAngle = sqrt(jacobian) * 2.0 / cells;

    // The star keeps inside [0.2, 0.8] of its cell: no neighbours to check.
    vec2 position = 0.2 + 0.6 * h.yz;
    float dist = length(f - position) * cellAngle;
    float magnitude = layer.z * pow(h.w, uMagPower);
    float tint = fract(h.x * 61.7 + h.w * 13.1);

    // Flux-preserving widening by the pixel footprint; the layer fades into its mean when cells get sub-pixel.
    float s2 = uStarSigma * uStarSigma;
    float s2eff = s2 + uStarWidening * fp * fp;
    float fade = 1.0 - smoothstep(uStarFade.x, uStarFade.y, fp / cellAngle);
    float star = step(h.x, probability) * magnitude * (s2 / s2eff) * fade * exp(-0.5 * dist * dist / s2eff);
    return starTint(tint) * star + (1.0 - fade) * uStarAvg[i];
}

// Must be called in uniform control flow: it takes derivatives of dir.
vec3 skyColor(vec3 dir, out float footprint) {
    vec3 ddx = dFdx(dir);
    vec3 ddy = dFdy(dir);
    float fp = max(length(ddx), length(ddy));
    // At the shadow edge the derivatives blow up; clamp them.
    float k = min(1.0, uFpMax / max(fp, 1e-9));
    ddx *= k;
    ddy *= k;
    fp *= k;
    footprint = fp;
    vec3 c = textureGrad(uHaze, dir, ddx, ddy).rgb * uHazeEnabled;
    vec3 stars = vec3(0.0);
    for (int i = 0; i < STAR_LAYERS; i++) stars += starLayer(dir, fp, i);
    return c + stars * (uStarsEnabled * uStarBrightness);
}

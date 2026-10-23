const float PI = 3.14159265359;
const float TAU = 6.28318530718;

// Jarzynski & Olano, "Hash Functions for GPU Rendering", 2020.
uvec4 pcg4d(uvec4 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.w; v.y += v.z * v.x; v.z += v.x * v.y; v.w += v.y * v.z;
    v ^= v >> 16u;
    v.x += v.y * v.w; v.y += v.z * v.x; v.z += v.x * v.y; v.w += v.y * v.z;
    return v;
}

// Four uniform values in [0, 1); 24 bits each, exact in float.
vec4 hash4(uvec4 v) {
    return vec4(pcg4d(v) >> 8u) * (1.0 / 16777216.0);
}

vec3 latticeGradient(ivec3 c, uint seed) {
    vec3 g = hash4(uvec4(uvec3(c), seed)).xyz * 2.0 - 1.0;
    return g / max(length(g), 1e-4);
}

// Gradient noise, roughly in [-0.7, 0.7].
float gradientNoise(vec3 p, uint seed) {
    ivec3 i = ivec3(floor(p));
    vec3 f = fract(p);
    vec3 w = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float n000 = dot(latticeGradient(i, seed), f);
    float n100 = dot(latticeGradient(i + ivec3(1, 0, 0), seed), f - vec3(1.0, 0.0, 0.0));
    float n010 = dot(latticeGradient(i + ivec3(0, 1, 0), seed), f - vec3(0.0, 1.0, 0.0));
    float n110 = dot(latticeGradient(i + ivec3(1, 1, 0), seed), f - vec3(1.0, 1.0, 0.0));
    float n001 = dot(latticeGradient(i + ivec3(0, 0, 1), seed), f - vec3(0.0, 0.0, 1.0));
    float n101 = dot(latticeGradient(i + ivec3(1, 0, 1), seed), f - vec3(1.0, 0.0, 1.0));
    float n011 = dot(latticeGradient(i + ivec3(0, 1, 1), seed), f - vec3(0.0, 1.0, 1.0));
    float n111 = dot(latticeGradient(i + ivec3(1, 1, 1), seed), f - vec3(1.0, 1.0, 1.0));
    float x00 = mix(n000, n100, w.x);
    float x10 = mix(n010, n110, w.x);
    float x01 = mix(n001, n101, w.x);
    float x11 = mix(n011, n111, w.x);
    return mix(mix(x00, x10, w.y), mix(x01, x11, w.y), w.z);
}

// Fractal noise of 6 octaves, roughly in [-1, 1].
float fbm(vec3 p, uint seed) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 6; i++) {
        sum += amplitude * gradientNoise(p, seed + uint(i) * 131u);
        p = p * 2.03 + vec3(17.1, 9.7, 3.3);
        amplitude *= 0.5;
    }
    return sum * 1.4;
}

vec3 tonemapAces(vec3 x) {
    // Narkowicz' fit of ACES.
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

vec3 agxContrast(vec3 x) {
    vec3 x2 = x * x;
    vec3 x4 = x2 * x2;
    return 15.5 * x4 * x2 - 40.14 * x4 * x + 31.96 * x4 - 6.868 * x2 * x + 0.4298 * x2 + 0.1191 * x - 0.00232;
}

vec3 tonemapAgx(vec3 c) {
    const mat3 toAgx = mat3(0.842479062253094, 0.0423282422610123, 0.0423756549057051,
                            0.0784335999999992, 0.878468636469772, 0.0784336,
                            0.0792237451477643, 0.0791661274605434, 0.879142973793104);
    const mat3 fromAgx = mat3(1.19687900512017, -0.0528968517574562, -0.0529716355144438,
                              -0.0980208811401368, 1.15190312990417, -0.0980434501171241,
                              -0.0990297440797205, -0.0989611768448433, 1.15107367264116);
    const float minEv = -12.47393;
    const float maxEv = 4.026069;
    c = toAgx * c;
    c = clamp(log2(max(c, vec3(1e-10))), minEv, maxEv);
    c = agxContrast((c - minEv) / (maxEv - minEv));
    return pow(max(fromAgx * c, vec3(0.0)), vec3(2.2));
}

vec3 tonemap(vec3 c, int tonemapper) {
    return tonemapper == 0 ? tonemapAces(c) : tonemapAgx(c);
}

vec3 linearToSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

// Jimenez' interleaved gradient noise, [0, 1).
float interleavedGradientNoise(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

vec3 heatmap(float x) {
    x = clamp(x, 0.0, 1.0);
    return clamp(vec3(1.5 - abs(4.0 * x - 3.0), 1.5 - abs(4.0 * x - 2.0), 1.5 - abs(4.0 * x - 1.0)), 0.0, 1.0);
}

// Dual filtering bloom (Bjørge, SIGGRAPH 2015): downsample. The first level also applies the
// soft-knee bright pass.
precision highp float;

uniform sampler2D uSource;
uniform vec2 uTexel;        // 1 / source size
uniform int uPrefilter;
uniform float uThreshold;
uniform float uKnee;

in vec2 vUv;
out vec4 oColor;

vec3 brightPass(vec3 c) {
    float brightness = max(c.r, max(c.g, c.b));
    float soft = clamp(brightness - uThreshold + uKnee, 0.0, 2.0 * uKnee);
    soft = soft * soft / (4.0 * uKnee + 1e-5);
    return c * (max(soft, brightness - uThreshold) / max(brightness, 1e-5));
}

void main() {
    vec3 sum = texture(uSource, vUv).rgb * 4.0;
    sum += texture(uSource, vUv + vec2(-1.0, -1.0) * uTexel).rgb;
    sum += texture(uSource, vUv + vec2(1.0, -1.0) * uTexel).rgb;
    sum += texture(uSource, vUv + vec2(-1.0, 1.0) * uTexel).rgb;
    sum += texture(uSource, vUv + vec2(1.0, 1.0) * uTexel).rgb;
    sum *= 0.125;
    oColor = vec4(uPrefilter == 1 ? brightPass(sum) : sum, 1.0);
}

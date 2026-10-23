// Dual filtering bloom: upsample, added onto the larger level by blending.
precision highp float;

uniform sampler2D uSource;
uniform vec2 uTexel;        // 1 / source size

in vec2 vUv;
out vec4 oColor;

void main() {
    vec3 sum = texture(uSource, vUv + vec2(-2.0, 0.0) * uTexel).rgb;
    sum += texture(uSource, vUv + vec2(2.0, 0.0) * uTexel).rgb;
    sum += texture(uSource, vUv + vec2(0.0, -2.0) * uTexel).rgb;
    sum += texture(uSource, vUv + vec2(0.0, 2.0) * uTexel).rgb;
    sum += texture(uSource, vUv + vec2(-1.0, 1.0) * uTexel).rgb * 2.0;
    sum += texture(uSource, vUv + vec2(1.0, 1.0) * uTexel).rgb * 2.0;
    sum += texture(uSource, vUv + vec2(-1.0, -1.0) * uTexel).rgb * 2.0;
    sum += texture(uSource, vUv + vec2(1.0, -1.0) * uTexel).rgb * 2.0;
    oColor = vec4(sum / 12.0, 1.0);
}

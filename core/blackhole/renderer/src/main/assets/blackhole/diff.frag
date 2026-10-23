// Debug: |table − reference| × 10.
precision highp float;

uniform sampler2D uA;
uniform sampler2D uB;
uniform float uScale;

in vec2 vUv;
out vec4 oColor;

void main() {
    oColor = vec4(abs(texture(uA, vUv).rgb - texture(uB, vUv).rgb) * uScale, 1.0);
}

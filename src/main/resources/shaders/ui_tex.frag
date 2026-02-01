#version 330 core

uniform sampler2D uTexture;
uniform vec4 uUVRect; // u0, v0, u1, v1

in vec2 vUV;
out vec4 fragColor;

void main() {
    vec2 uv = mix(uUVRect.xy, uUVRect.zw, vUV);
    vec4 texColor = texture(uTexture, uv);
    if (texColor.a < 0.01) discard;
    fragColor = texColor;
}

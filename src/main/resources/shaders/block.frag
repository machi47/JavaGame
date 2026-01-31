#version 330 core
in vec2 vTexCoord;
in float vLight;

uniform sampler2D uAtlas;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(uAtlas, vTexCoord);
    if (texColor.a < 0.1) discard;
    fragColor = vec4(texColor.rgb * vLight, texColor.a);
}

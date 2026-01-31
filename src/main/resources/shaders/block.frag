#version 330 core
in vec2 vTexCoord;
in float vLight;

uniform sampler2D uAtlas;

out vec4 fragColor;

// Minimum ambient light so caves aren't completely black
const float MIN_AMBIENT = 0.08;

void main() {
    vec4 texColor = texture(uAtlas, vTexCoord);
    if (texColor.a < 0.1) discard;

    // Apply lighting with minimum ambient floor
    float light = max(vLight, MIN_AMBIENT);
    fragColor = vec4(texColor.rgb * light, texColor.a);
}

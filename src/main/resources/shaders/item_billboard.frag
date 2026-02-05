#version 330 core

uniform sampler2D uAtlas;
uniform float uBrightness; // pulsing brightness effect

in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    vec4 texColor = texture(uAtlas, vTexCoord);
    
    // Discard fully transparent pixels
    if (texColor.a < 0.1) discard;
    
    // Apply brightness with a base ambient
    float light = 0.7 + 0.3 * uBrightness;
    
    fragColor = vec4(texColor.rgb * light, texColor.a);
}

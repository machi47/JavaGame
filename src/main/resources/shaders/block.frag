#version 330 core
// MINIMAL BASELINE - Simple texture * light

in vec2 vTexCoord;
in float vLight;
in float vFogFactor;
in vec3 vViewPos;
in vec3 vWorldPos;

uniform sampler2D uAtlas;
uniform float uAlpha;           // 1.0 for opaque, <1.0 for transparent
uniform vec3 uFogColor;         // Sky/fog color

// Minimum ambient so caves aren't pitch black
const float MIN_AMBIENT = 0.05;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragNormal;

void main() {
    vec4 texColor = texture(uAtlas, vTexCoord);

    // Alpha test for cutout textures (flowers, leaves)
    if (texColor.a < 0.1) {
        discard;
    }

    // Simple lighting: texture * light with minimum ambient
    float light = max(vLight, MIN_AMBIENT);
    vec3 litColor = texColor.rgb * light;

    // Apply fog
    vec3 finalColor = mix(litColor, uFogColor, vFogFactor);

    fragColor = vec4(finalColor, texColor.a * uAlpha);

    // Output view-space normal for SSAO (compute from derivatives)
    vec3 normal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    fragNormal = vec4(normal * 0.5 + 0.5, 1.0);
}

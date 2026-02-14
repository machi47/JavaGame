#version 330 core
// MINIMAL BASELINE - 6-float vertex format for maximum performance
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in float aLight;  // Combined sky/AO/face lighting

uniform mat4 uProjection;
uniform mat4 uView;
uniform vec3 uCameraPos;
uniform float uFogStart;
uniform float uFogEnd;

out vec2 vTexCoord;
out float vLight;
out float vFogFactor;
out vec3 vViewPos;
out vec3 vWorldPos;

void main() {
    vec4 viewPos4 = uView * vec4(aPos, 1.0);
    gl_Position = uProjection * viewPos4;
    vTexCoord = aTexCoord;
    vLight = aLight;
    vViewPos = viewPos4.xyz;
    vWorldPos = aPos;

    // Distance-based fog
    float dist = length(aPos - uCameraPos);
    vFogFactor = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
}

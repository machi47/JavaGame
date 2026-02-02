#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in float aSkyLight;
layout(location = 3) in float aBlockLight;

uniform mat4 uProjection;
uniform mat4 uView;
uniform vec3 uCameraPos;
uniform float uFogStart;
uniform float uFogEnd;

out vec2 vTexCoord;
out float vSkyLight;
out float vBlockLight;
out float vFogFactor;
out vec3 vViewPos;   // view-space position (for SSAO)

void main() {
    vec4 viewPos4 = uView * vec4(aPos, 1.0);
    gl_Position = uProjection * viewPos4;
    vTexCoord = aTexCoord;
    vSkyLight = aSkyLight;
    vBlockLight = aBlockLight;
    vViewPos = viewPos4.xyz;

    // Distance-based fog (linear, using dynamic uniforms from LOD config)
    float dist = length(aPos - uCameraPos);
    vFogFactor = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
}

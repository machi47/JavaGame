#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in float aSkyVisibility;  // 0-1 sky visibility (was aSkyLight)
layout(location = 3) in float aBlockLight;     // 0-1 block light with AO baked in

uniform mat4 uProjection;
uniform mat4 uView;
uniform vec3 uCameraPos;
uniform float uFogStart;
uniform float uFogEnd;

out vec2 vTexCoord;
out float vSkyVisibility;
out float vBlockLight;
out float vFogFactor;
out vec3 vViewPos;   // view-space position (for SSAO)
out vec3 vWorldPos;  // world-space position (for directional lighting)

void main() {
    vec4 viewPos4 = uView * vec4(aPos, 1.0);
    gl_Position = uProjection * viewPos4;
    vTexCoord = aTexCoord;
    vSkyVisibility = aSkyVisibility;
    vBlockLight = aBlockLight;
    vViewPos = viewPos4.xyz;
    vWorldPos = aPos;  // Pass world position for normal calculation in fragment

    // Distance-based fog (linear, using dynamic uniforms from LOD config)
    float dist = length(aPos - uCameraPos);
    vFogFactor = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
}

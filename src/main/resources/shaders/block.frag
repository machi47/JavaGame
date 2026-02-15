#version 330 core
// Block fragment shader with debug views and fog modes

in vec2 vTexCoord;
in float vLight;           // Smooth interpolated light (with per-vertex AO)
flat in float vLightFlat;  // Flat light (provoking vertex, no interpolation)
in float vFogFactor;
in vec3 vViewPos;
in vec3 vWorldPos;

uniform sampler2D uAtlas;
uniform float uAlpha;           // 1.0 for opaque, <1.0 for transparent
uniform vec3 uFogColor;         // Sky/fog color

// Debug/visual controls
uniform int uSmoothLighting;    // 0=flat (no AO gradients), 1=smooth (per-vertex AO)
uniform int uDebugView;         // 0=normal, 1=albedo, 2=lighting, 3=depth, 4=fog
uniform int uFogMode;           // 0=both, 1=world only, 2=off
uniform int uWireframe;         // 1=bright white for wireframe visibility

// Minimum ambient so deepest caves aren't 100% black (just nearly black)
const float MIN_AMBIENT = 0.01;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragNormal;

void main() {
    vec4 texColor = texture(uAtlas, vTexCoord);

    // Alpha test for cutout textures (flowers, leaves)
    if (texColor.a < 0.1) {
        discard;
    }

    // Pick smooth or flat lighting based on toggle (instant, no remesh needed)
    float light;
    if (uSmoothLighting == 1) {
        light = max(vLight, MIN_AMBIENT);      // Smooth: per-vertex AO gradients
    } else {
        light = max(vLightFlat, MIN_AMBIENT);  // Flat: uniform per-triangle (no AO)
    }

    vec3 litColor = texColor.rgb * light;

    // Apply fog based on fog mode
    float fogFactor = vFogFactor;
    if (uFogMode == 2) {
        fogFactor = 0.0; // Fog OFF
    }
    vec3 finalColor = mix(litColor, uFogColor, fogFactor);

    // Debug view overrides
    if (uDebugView == 1) {
        // Albedo only (no lighting)
        finalColor = texColor.rgb;
    } else if (uDebugView == 2) {
        // Lighting only (grayscale light value)
        finalColor = vec3(light);
    } else if (uDebugView == 3) {
        // Linear depth visualization
        float linearDepth = length(vViewPos) / 384.0; // Normalize to far plane
        finalColor = vec3(linearDepth);
    } else if (uDebugView == 4) {
        // Fog factor visualization
        finalColor = vec3(vFogFactor, 1.0 - vFogFactor, 0.0); // Red=foggy, Green=clear
    } else if (uDebugView == 5) {
        // Skylight heatmap: blue=0, cyan=low, green=mid, yellow=high, red=15
        // Uses the raw baked light value (before brightness curve it's nonlinear,
        // but this still shows relative levels clearly)
        float t = clamp(light / 1.0, 0.0, 1.0);
        if (t < 0.25) {
            finalColor = mix(vec3(0,0,0.5), vec3(0,1,1), t * 4.0);
        } else if (t < 0.5) {
            finalColor = mix(vec3(0,1,1), vec3(0,1,0), (t - 0.25) * 4.0);
        } else if (t < 0.75) {
            finalColor = mix(vec3(0,1,0), vec3(1,1,0), (t - 0.5) * 4.0);
        } else {
            finalColor = mix(vec3(1,1,0), vec3(1,0,0), (t - 0.75) * 4.0);
        }
    }

    // Wireframe mode: boost texture color so dark blocks are visible
    if (uWireframe == 1) {
        // Use texture color but ensure minimum brightness
        float brightness = max(max(texColor.r, texColor.g), texColor.b);
        if (brightness < 0.3) {
            finalColor = texColor.rgb + vec3(0.4); // Boost dark colors
        } else {
            finalColor = texColor.rgb;
        }
    }

    fragColor = vec4(finalColor, texColor.a * uAlpha);

    // Output view-space normal for SSAO (compute from derivatives)
    vec3 normal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    fragNormal = vec4(normal * 0.5 + 0.5, 1.0);
}

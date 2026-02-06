#version 330 core
in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uSceneColor;   // HDR scene color
uniform sampler2D uSSAO;         // blurred SSAO (single channel)
uniform int uSSAOEnabled;        // 1 = apply SSAO, 0 = skip

// Debug toggles for visual audit
uniform int uGammaMode;          // 0 = manual gamma, 1 = disabled (sRGB framebuffer mode)

// ACES filmic tone mapping — better contrast and color preservation than Reinhard
// Modified with exposure boost to counter the compression
vec3 toneMapACES(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 sceneColor = texture(uSceneColor, vTexCoord).rgb;

    // Apply SSAO (multiplicative darkening in corners/edges)
    if (uSSAOEnabled == 1) {
        float ao = texture(uSSAO, vTexCoord).r;
        // Subtle power curve to make occlusion more visible without crushing
        ao = pow(ao, 1.3);
        // Safety floor: never let SSAO crush below 40% brightness.
        // Prevents total blackout from bad normals or edge cases.
        ao = max(ao, 0.4);
        sceneColor *= ao;
    }

    // Exposure boost: compensate for ACES compression
    // Reduced to 1.0 (neutral) - let tonemapping handle HDR compression naturally
    sceneColor *= 1.0;

    // ACES tone mapping (HDR → LDR with filmic contrast curve)
    vec3 mapped = toneMapACES(sceneColor);

    // Saturation boost to counteract ACES desaturation
    // Increased to 1.35 for more vibrant colors (Minecraft-style)
    float luma = dot(mapped, vec3(0.299, 0.587, 0.114));
    mapped = mix(vec3(luma), mapped, 1.35); // 35% saturation boost

    // Gamma correction (linear → sRGB)
    // uGammaMode: 0 = apply manual gamma, 1 = skip (when using GL_FRAMEBUFFER_SRGB)
    if (uGammaMode == 0) {
        mapped = pow(mapped, vec3(1.0 / 2.2));
    }

    fragColor = vec4(mapped, 1.0);
}

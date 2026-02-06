#version 330 core
in vec2 vTexCoord;
in float vSkyVisibility;   // 0-1 sky visibility (with AO and directional baked in)
in float vBlockLight;      // 0-1 block light (with AO and directional baked in)
in float vFogFactor;
in vec3 vViewPos;
in vec3 vWorldPos;

uniform sampler2D uAtlas;
uniform float uAlpha;           // 1.0 for opaque pass, <1.0 for transparent pass
uniform vec3 uFogColor;         // sky/fog color (matches clear color)

// Unified lighting uniforms
uniform vec3 uSkyColor;         // Current sky color (zenith, from WorldTime)
uniform float uSkyIntensity;    // Sky intensity (0-1, typically 0.3-0.5)
uniform vec3 uSunDirection;     // Direction TO sun (normalized)
uniform float uSunIntensity;    // Sun intensity for directional lighting (0-1)
uniform float uSunBrightness;   // Overall sun brightness (0-1, for ambient)

// Block light color (warm torch glow)
const vec3 BLOCK_LIGHT_COLOR = vec3(1.0, 0.9, 0.7);

// Minimum ambient light (starlight) so caves aren't pitch black
const float MIN_AMBIENT = 0.02;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragNormal; // View-space normals for SSAO (PostFX FBO)

void main() {
    vec4 texColor = texture(uAtlas, vTexCoord);
    if (texColor.a < 0.1) discard;

    // Compute world-space normal from position derivatives (flat shading per face)
    vec3 worldNormal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));

    // ========================================================================
    // UNIFIED RGB LIGHTING MODEL
    // ========================================================================
    
    // 1. SKY LIGHT: Ambient light from sky, modulated by visibility
    //    Sky-visible surfaces get the sky color; caves (visibility=0) get nothing
    vec3 skyRGB = uSkyColor * vSkyVisibility * uSkyIntensity;
    
    // 2. SUN LIGHT: Directional sunlight (N·L), only for sky-visible surfaces
    //    Surfaces in caves don't get any sun contribution
    float NdotL = max(dot(worldNormal, uSunDirection), 0.0);
    vec3 sunRGB = uSkyColor * NdotL * uSunIntensity * vSkyVisibility * 0.6;
    
    // 3. BLOCK LIGHT: Warm light from torches and other emissive blocks
    //    Always active regardless of sky visibility or time of day
    vec3 blockRGB = BLOCK_LIGHT_COLOR * vBlockLight;
    
    // 4. ADDITIVE COMBINATION (not max!)
    //    This allows torches to add to moonlight, multiple light sources to stack
    vec3 totalLight = skyRGB + sunRGB + blockRGB;
    
    // 5. Minimum ambient (starlight) so you can see something in caves
    totalLight = max(totalLight, vec3(MIN_AMBIENT));
    
    // ========================================================================
    // NIGHT TINT: Slight blue tint at night for moonlight atmosphere
    // ========================================================================
    if (uSunBrightness < 0.5) {
        float nightFactor = 1.0 - uSunBrightness * 2.0; // 0 at day, 1 at full night
        totalLight = mix(totalLight, totalLight * vec3(0.7, 0.75, 1.0), nightFactor * 0.4);
    }

    // Apply lighting to texture
    vec3 litColor = texColor.rgb * totalLight;

    // Apply distance fog (blends to sky color, hides chunk pop-in)
    vec3 finalColor = mix(litColor, uFogColor, vFogFactor);

    fragColor = vec4(finalColor, texColor.a * uAlpha);

    // Compute view-space normal from screen-space position derivatives.
    // This gives flat per-face normals for block geometry — exactly what SSAO needs.
    vec3 viewNormal = normalize(cross(dFdx(vViewPos), dFdy(vViewPos)));
    // Encode from [-1,1] to [0,1] for texture storage
    fragNormal = vec4(viewNormal * 0.5 + 0.5, 1.0);
}

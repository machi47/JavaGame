#version 330 core

in vec2 vTexCoord;
out float fragDepth;

uniform sampler2D uSourceTex;
uniform int uSourceMip;
uniform vec2 uTexelSize;
uniform float uNear;
uniform float uFar;

// Linearize depth from depth buffer [0,1] to linear [near, far]
float linearizeDepth(float d) {
    return (2.0 * uNear * uFar) / (uFar + uNear - d * (uFar - uNear));
}

void main() {
    if (uSourceMip < 0) {
        // Mip 0: Copy from depth buffer and linearize
        float depth = texture(uSourceTex, vTexCoord).r;
        fragDepth = linearizeDepth(depth);
    } else {
        // Downsample: take maximum (farthest) depth of 4 texels
        // This is conservative - a region is only occluded if ALL texels are occluded
        vec2 uv = vTexCoord;
        vec2 offset = uTexelSize * 0.5;

        float d0 = textureLod(uSourceTex, uv + vec2(-offset.x, -offset.y), float(uSourceMip)).r;
        float d1 = textureLod(uSourceTex, uv + vec2( offset.x, -offset.y), float(uSourceMip)).r;
        float d2 = textureLod(uSourceTex, uv + vec2(-offset.x,  offset.y), float(uSourceMip)).r;
        float d3 = textureLod(uSourceTex, uv + vec2( offset.x,  offset.y), float(uSourceMip)).r;

        // Max for conservative occlusion (farthest depth)
        fragDepth = max(max(d0, d1), max(d2, d3));
    }
}

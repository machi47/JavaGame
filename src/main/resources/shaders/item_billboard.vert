#version 330 core
layout(location = 0) in vec3 aPos;      // local quad vertex
layout(location = 1) in vec2 aTexCoord; // UV coordinates

uniform mat4 uProjection;
uniform mat4 uView;
uniform vec3 uItemPos;   // world position of item center
uniform float uSize;     // size of billboard
uniform float uRotation; // Y-axis rotation for spinning

out vec2 vTexCoord;

void main() {
    // Extract camera right and up vectors from view matrix
    // These are the first two columns of the inverse view matrix (transposed rows)
    vec3 cameraRight = vec3(uView[0][0], uView[1][0], uView[2][0]);
    vec3 cameraUp = vec3(0.0, 1.0, 0.0); // Keep vertical for grounded items
    
    // Apply Y-axis rotation to the quad vertices before billboarding
    float c = cos(uRotation);
    float s = sin(uRotation);
    vec2 rotatedPos = vec2(
        aPos.x * c - aPos.z * s,
        aPos.x * s + aPos.z * c
    );
    
    // Build billboard vertex position
    // aPos.x is horizontal offset, aPos.y is vertical offset
    vec3 worldPos = uItemPos 
        + cameraRight * rotatedPos.x * uSize
        + cameraUp * aPos.y * uSize;
    
    gl_Position = uProjection * uView * vec4(worldPos, 1.0);
    vTexCoord = aTexCoord;
}

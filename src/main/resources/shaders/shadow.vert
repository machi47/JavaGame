#version 330 core

// Shadow pass vertex shader - outputs depth only.
// Transforms vertices to light space for shadow map generation.

layout(location = 0) in vec3 aPos;
// Ignore other vertex attributes - we only need position for depth

uniform mat4 uLightViewProj;

void main() {
    gl_Position = uLightViewProj * vec4(aPos, 1.0);
}

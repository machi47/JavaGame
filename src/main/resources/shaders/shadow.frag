#version 330 core

// Shadow pass fragment shader - outputs depth only.
// Fragment depth is written automatically by OpenGL.

void main() {
    // Depth is written automatically - no explicit output needed.
    // We could use gl_FragDepth to modify depth if needed,
    // but default behavior is correct for shadow mapping.
}

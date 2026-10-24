#version 150

// The disc round a Mostly Dangerous rift. Placed exactly as vanilla's position_tex_color places
// anything; where each corner looks in the copied frame was worked out on the CPU and arrives as UV0.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;
}

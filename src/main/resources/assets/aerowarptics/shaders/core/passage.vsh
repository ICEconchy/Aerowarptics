#version 150

// One quad over the whole view, for every style of gate passage. Placed exactly as vanilla's position_tex
// places anything; the picture is all in each style's fragment program, worked out from where on the quad
// each pixel is.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
}

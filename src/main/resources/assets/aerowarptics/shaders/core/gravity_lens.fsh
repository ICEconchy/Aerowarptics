#version 150

// The frame as it stood before any rift was drawn, read from wherever the lens bends this pixel to.
// Only the colour is taken from it: a frame's alpha is whatever the last thing drawn there left, not a
// transparency, so how much of the lens shows is the vertex colour's alpha alone - the feather at its edge.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(Sampler0, texCoord0).rgb * vertexColor.rgb, vertexColor.a) * ColorModulator;
}

#version 150

// A Rift Gate passage, Down the Pipe style: an iris shuts, a pipe goes by in three flat greens and big
// square pixels, and an iris opens on wherever it came out.
//
// The tunnel trick with everything smooth taken out of it. The view is cut into square pixels and the
// whole picture worked out once for each, the shading is three colours and no more, and nothing fades:
// it begins behind an iris that opens, ends behind one that shuts, and the far side arrives through a
// third. That last one is this style's veil - a hole that is nothing at all while the veil is whole, and
// wider than the view once it has gone; see GatePassageStyle. Every number that moves comes from
// GatePassage on the CPU.

uniform float Depth;
uniform float Flare;
uniform float Splash;
uniform float Veil;
uniform float Aspect;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 smoothView = (texCoord0 - 0.5) * 2.0 * vec2(Aspect, 1.0);
    // Big square pixels, ninety to the height of the view, each drawn from its own middle.
    float pixels = 45.0;
    vec2 view = (floor(smoothView * pixels) + 0.5) / pixels;
    float radius = length(view);
    float angle = atan(view.y, view.x);

    // Three flat greens: a stripe of light where the lamp catches the pipe, dark across from it.
    float lamp = 0.5 + 0.5 * cos(angle - 2.2);
    vec3 green = lamp > 0.86 ? vec3(0.56, 0.93, 0.42)
        : (lamp > 0.36 ? vec3(0.10, 0.66, 0.16) : vec3(0.02, 0.36, 0.08));

    // A joint every length of pipe, going by: the lip of it catching the light, and its shadow behind.
    // They crowd together down the pipe, which is what says it is one.
    float along = 0.6 / max(radius, 0.0005) + Depth * 1.8;
    float joint = fract(along * 1.6);
    green = mix(green, vec3(0.78, 1.00, 0.62), step(0.76, joint));
    green = mix(green, vec3(0.00, 0.20, 0.04), step(0.86, joint));

    // Darker the further down it goes, in steps, and black where it goes on out of sight.
    float lit = floor(smoothstep(0.05, 0.60, radius) * 4.0 + 0.5) / 4.0;
    vec3 colour = green * (0.25 + 0.75 * lit) * step(0.07, radius);

    // Wider than the view from its middle to its furthest corner, so an iris that far open is not there.
    float corner = length(vec2(Aspect, 1.0)) * 1.05;

    // It begins behind an iris that opens, and ends behind one that shuts.
    colour *= 1.0 - step((1.0 - Splash) * corner, radius);
    colour *= 1.0 - step((1.0 - Flare) * corner, radius);

    // And the far side comes in through a third.
    float solid = step((1.0 - Veil) * corner, radius);

    fragColor = vec4(colour, solid);
}

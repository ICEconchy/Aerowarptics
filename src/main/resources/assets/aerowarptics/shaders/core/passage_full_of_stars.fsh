#version 150

// A Rift Gate passage, Full of Stars style: the long corridor of light. Two planes of colour, one above
// and one below a burning slit, streaming out of it and past in every hue there is, turning as they come.
//
// Not a tunnel: two flat planes seen edge on, meeting at a line. How far a pixel is from that line is how
// far down the plane it is looking, and the planes are painted with bars of light laid along the way of
// travel - which is what a slit-scan camera makes of anything. Every number that moves comes from
// GatePassage on the CPU.

uniform float Depth;
uniform float Rush;
uniform float Roll;
uniform float Chroma;
uniform float Flare;
uniform float Splash;
uniform float Veil;
uniform float Clock;
uniform float Aspect;

in vec2 texCoord0;

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 cell = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(cell), hash(cell + vec3(1.0, 0.0, 0.0)), f.x),
            mix(hash(cell + vec3(0.0, 1.0, 0.0)), hash(cell + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(hash(cell + vec3(0.0, 0.0, 1.0)), hash(cell + vec3(1.0, 0.0, 1.0)), f.x),
            mix(hash(cell + vec3(0.0, 1.0, 1.0)), hash(cell + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z);
}

// Every hue there is, by how far round the wheel.
vec3 hue(float turn) {
    return 0.5 + 0.5 * cos(TAU * (turn + vec3(0.0, 0.33, 0.67)));
}

void main() {
    vec2 view = (texCoord0 - 0.5) * 2.0 * vec2(Aspect, 1.0);
    // The slit turns as the passage goes on: lying flat, then standing on end.
    float turnCos = cos(Roll);
    float turnSin = sin(Roll);
    vec2 turned = vec2(turnCos * view.x + turnSin * view.y, turnCos * view.y - turnSin * view.x);

    // How far from the slit is how far down the plane; which side of it is which plane.
    float height = abs(turned.y);
    float plane = turned.y < 0.0 ? 1.0 : 2.0;
    float reach = 0.3 / max(height, 0.004);
    float across = turned.x * reach;
    float along = reach + Depth * 2.2;

    // How many of a pattern's stripes there are to a pixel here. Towards the slit there are more than
    // one, and a pattern drawn finer than the pixels is noise - so each fades out as it gets there.
    float crowding = fwidth(across);

    // Bars of light laid along the way of travel, each a colour of its own and broken into lengths.
    float lanes = 7.0;
    float lane = floor(across * lanes);
    float tint = hash(vec3(lane, plane, 1.0));
    float lengths = noise(vec3(lane * 3.1, along * (0.5 + tint), plane * 5.0));
    float edge = abs(fract(across * lanes) - 0.5) * 2.0;
    float bar = (1.0 - smoothstep(0.55, 0.95, edge)) * (1.0 - smoothstep(0.25, 0.9, crowding * lanes));
    vec3 colour = hue(tint + Clock * 0.06 + Chroma * 0.15) * pow(lengths, 3.0) * 3.0 * bar;

    // And threads of it between the bars, finer and brighter.
    float thread = noise(vec3(across * 23.0, along * 1.4, plane * 3.0));
    colour += hue(tint + 0.5 + Clock * 0.09) * pow(thread, 6.0) * 2.0
        * (1.0 - smoothstep(0.2, 0.7, crowding * 23.0));
    colour *= 0.6 + 0.6 * Rush;

    // The slit: where both planes run out, and the light they are coming out of - a glow, and a white
    // heart to it.
    colour += vec3(1.00, 0.90, 0.78) * exp(-height * 8.0) * 0.8;
    colour = mix(colour, vec3(1.00, 0.98, 0.94), 1.0 - smoothstep(0.0, 0.035, height));

    // It begins as nothing but that light, drawing back to the slit it comes from.
    float drawn = Splash * Splash * Splash * 5.0;
    float pane = (1.0 - smoothstep(drawn, drawn + 0.2, height)) * min(1.0, Splash * 8.0);
    colour = mix(colour, vec3(1.00, 0.97, 0.92), pane);

    // And ends as the slit opening wide, and then all of the view at once.
    float wide = Flare * Flare * 4.5;
    float open = (1.0 - smoothstep(wide * 0.6, wide + 0.02, height)) * min(1.0, Flare * 10.0);
    float white = max(open, smoothstep(0.6, 1.0, Flare));
    colour = mix(colour, vec3(1.0), white);

    fragColor = vec4(colour, Veil);
}

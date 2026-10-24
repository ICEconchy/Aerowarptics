#version 150

// A Rift Gate passage, Punch It style: the jump to lightspeed. The stars hold for a breath, pull out into
// lines, and then there is only the mottled blue-white of the lane rushing past until it lets go.
//
// Two pictures and a blend between them, on how fast the passage says it is going: space with stars in
// it, each a point until speed draws it out along the line from the middle, and the lane, which is the
// tunnel trick again with its noise stretched a long way down the direction of travel. Every number that
// moves comes from GatePassage on the CPU.

uniform float Depth;
uniform float Rush;
uniform float Roll;
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

float cloud(vec3 p) {
    float sum = 0.0;
    float weight = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        sum += weight * noise(p);
        p = p * 2.03 + 17.1;
        weight *= 0.5;
    }
    return sum;
}

// One ring of stars: a star to a slice of the view round the middle, each going outwards at a pace of its
// own. Its place along the line from the middle is kept on a log scale, so it gathers speed as it comes,
// and `stretch` is how far its light is drawn out behind it - nothing while the ship is still.
float stars(float turn, float radius, float slices, float stretch) {
    float slice = floor(turn * slices);
    float lit = hash(vec3(slice, slices, 3.0));
    float pace = 0.5 + hash(vec3(slice, slices, 7.0));
    float phase = fract(log(radius) * 0.45 - Depth * 0.22 * pace + lit * 7.0);
    float tail = pow(phase, mix(90.0, 2.0, stretch));
    float across = abs(fract(turn * slices) - 0.5) * 2.0;
    float thin = 1.0 - smoothstep(0.0, 0.35, across);
    return tail * thin * step(0.35, lit);
}

void main() {
    vec2 view = (texCoord0 - 0.5) * 2.0 * vec2(Aspect, 1.0);
    float radius = max(length(view), 0.0005);
    float angle = atan(view.y, view.x);
    float turn = fract(angle / TAU);

    // Space, and the stars in it.
    float stretch = smoothstep(0.25, 0.75, Rush);
    float starlight = stars(turn, radius, 97.0, stretch) + 0.7 * stars(fract(turn + 0.37), radius, 61.0, stretch);
    vec3 space = vec3(0.000, 0.004, 0.018) + vec3(0.90, 0.95, 1.00) * starlight * (0.8 + 1.4 * stretch);

    // The lane: cloud drawn out so far along the way of travel that it reads as streaks, turning slowly.
    float along = 0.5 / radius + Depth * 1.6;
    float wound = angle + Roll * 0.6;
    vec2 rim = vec2(cos(wound), sin(wound));
    float streaks = cloud(vec3(rim * 2.6, along * 0.10 + Clock * 0.05));
    float grain = noise(vec3(rim * 9.0, along * 0.35));
    float bright = smoothstep(0.30, 0.75, streaks * 0.8 + grain * 0.3);
    vec3 lane = mix(vec3(0.00, 0.015, 0.09), vec3(0.22, 0.52, 1.00), bright)
        + vec3(0.85, 0.93, 1.00) * pow(bright, 5.0);
    lane *= 1.0 - 0.4 * smoothstep(0.4, 2.4, radius);
    lane = mix(vec3(0.72, 0.86, 1.00), lane, smoothstep(0.02, 0.22, radius));
    lane += vec3(0.70, 0.85, 1.00) * exp(-radius * 5.0) * 0.8;
    lane += vec3(0.90, 0.95, 1.00) * starlight * 0.5;

    vec3 colour = mix(space, lane, smoothstep(0.60, 0.97, Rush));

    // The pane's light, gone almost at once: it is the dark the stars are in that this begins with.
    colour = mix(colour, vec3(0.86, 0.90, 1.00), Splash * Splash * Splash);

    // The flare: a disc of the far end's light growing from the middle with a glow about it, and then,
    // before it has reached the corners, all of the view at once.
    float reach = Flare * Flare * 2.2;
    float disc = (1.0 - smoothstep(reach * 0.45, reach + 0.02, radius)) * min(1.0, Flare * 10.0);
    float glow = exp(-radius * (9.0 - 8.0 * Flare)) * Flare;
    float white = clamp(max(disc, smoothstep(0.55, 1.0, Flare)) + glow, 0.0, 1.0);
    colour = mix(colour, vec3(1.0), white);

    fragColor = vec4(colour, Veil);
}

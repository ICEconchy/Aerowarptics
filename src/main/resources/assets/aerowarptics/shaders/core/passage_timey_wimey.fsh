#version 150

// A Rift Gate passage, Timey Wimey style: a vortex of storm cloud wound into a spiral, ember red sliding
// to cold blue and back, turning hard about its middle with lightning cracking down its length.
//
// The same tunnel trick as the conduit - distance from the far end is depth, angle is where on the wall -
// with the angle wound further round the further out it is, which is all a spiral is. Every number that
// moves comes from GatePassage on the CPU.

uniform float Depth;
uniform float Roll;
uniform vec2 Sway;
uniform float Flare;
uniform float Splash;
uniform float Veil;
uniform float Clock;
uniform float Seed;
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

// One bolt at a time, five beats a second and not on every beat: a jagged line down the length of the
// vortex at whatever angle the beat picked, bright as it strikes and gone before the next.
float bolt(float turn, float radius) {
    float beat = Clock * 5.0;
    float slot = floor(beat);
    float struck = step(0.5, hash(vec3(slot, 9.0, Seed)));
    float path = hash(vec3(slot, 4.0, Seed))
        + 0.030 * sin(radius * 21.0 + slot * 1.7)
        + 0.015 * sin(radius * 55.0 + slot * 3.1);
    float away = abs(fract(turn - path + 0.5) - 0.5);
    float stroke = 1.0 - smoothstep(0.0, 0.006 + 0.010 * radius, away);
    float fade = 1.0 - fract(beat);
    return struck * stroke * fade * fade;
}

void main() {
    vec2 view = (texCoord0 - 0.5) * 2.0 * vec2(Aspect, 1.0);
    // The far end swings off the middle and the near end does not: a bend.
    vec2 fromEnd = view - Sway * exp(-1.5 * length(view - Sway));
    float radius = max(length(fromEnd), 0.0005);

    float along = 0.5 / radius + Depth * 1.3;
    // The spiral: the further out, the further round - and all of it turning three times as hard as the
    // conduit does.
    float angle = atan(fromEnd.y, fromEnd.x) + Roll * 3.0 + 2.2 * log(radius);
    // Round the wall as a point on a circle, so there is no seam where the angle wraps.
    vec2 rim = vec2(cos(angle), sin(angle));

    vec3 at = vec3(rim * 1.6, along * 0.55);
    float billow = cloud(at + vec3(0.0, 0.0, Clock * 0.15));
    float curl = noise(at * 2.3 + vec3(rim * billow, -Clock * 0.5));
    float thick = smoothstep(0.22, 0.72, billow * 0.8 + curl * 0.35);

    vec3 ember = mix(vec3(0.16, 0.01, 0.02), vec3(1.00, 0.42, 0.06), thick)
        + vec3(1.00, 0.85, 0.50) * pow(thick, 4.0) * 0.8;
    vec3 frost = mix(vec3(0.01, 0.03, 0.20), vec3(0.18, 0.52, 1.00), thick)
        + vec3(0.75, 0.90, 1.00) * pow(thick, 4.0) * 0.8;
    // Which way through time: one colour, then the other, and never long in between.
    float tide = smoothstep(0.3, 0.7, 0.5 + 0.5 * sin(Clock * 0.9 + Seed));
    vec3 colour = mix(ember, frost, tide);

    float turn = fract(angle / TAU);
    colour += vec3(0.80, 0.90, 1.00) * bolt(turn, radius) * 2.0;

    // Darker out at the near wall, and at the far end a haze of the light the vortex is lit by - which
    // is also what keeps the cloud, very fine that far down, from glittering.
    colour *= 1.0 - 0.45 * smoothstep(0.3, 2.4, radius);
    vec3 heart = mix(vec3(1.00, 0.86, 0.62), vec3(0.76, 0.90, 1.00), tide);
    colour = mix(heart, colour, smoothstep(0.02, 0.20, radius));
    colour += heart * exp(-radius * 7.0) * 0.6;

    // It begins in a flash, as a bolt would light the whole of it.
    colour = mix(colour, vec3(0.92, 0.96, 1.00), Splash * Splash);

    // The flare: a disc of the far end's light growing from the middle with a glow about it, and then,
    // before it has reached the corners, all of the view at once.
    float reach = Flare * Flare * 2.2;
    float disc = (1.0 - smoothstep(reach * 0.45, reach + 0.02, radius)) * min(1.0, Flare * 10.0);
    float glow = exp(-radius * (9.0 - 8.0 * Flare)) * Flare;
    float white = clamp(max(disc, smoothstep(0.55, 1.0, Flare)) + glow, 0.0, 1.0);
    colour = mix(colour, vec3(1.0), white);

    fragColor = vec4(colour, Veil);
}

#version 150

// A Rift Gate passage, Kawoosh style: the view down a twisting conduit of blue and violet plasma.
//
// Nothing here is a texture. The conduit is the old tunnel trick - a pixel's distance from the far end is
// how far down the conduit it is looking, and its angle round the far end is where on the wall - painted
// with noise worked out on the spot. Every number that moves comes from GatePassage on the CPU, so the
// timing is the tested one and this only draws.

uniform float Depth;
uniform float Rush;
uniform float Roll;
uniform vec2 Sway;
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

// The streaks of light along the walls: one to a slice of the conduit's circumference, each on a pace of
// its own, each a bright head with a tail drawn out behind it - longer the faster the conduit goes by,
// which is all the motion blur there is.
float streaks(float turn, float along) {
    float slices = 56.0;
    float slice = floor(turn * slices);
    float lit = hash(vec3(slice, 3.0, 11.0));
    float pace = 0.12 + 0.3 * hash(vec3(slice, 7.0, 5.0));
    float phase = fract(along * pace + hash(vec3(slice, 1.0, 2.0)));
    // Brightest just behind its leading end, and rounded off at it: a hard end would be split by the
    // chromatic shift into a block of one colour hanging off the front of every streak.
    float tail = pow(phase, mix(16.0, 2.5, Rush)) * (1.0 - smoothstep(0.88, 1.0, phase));
    float across = abs(fract(turn * slices) - 0.5) * 2.0;
    float thin = 1.0 - smoothstep(0.0, 0.5 + 0.4 * lit, across);
    return tail * thin * step(0.45, lit);
}

// The conduit as it would look `stretch` further out or in than it is. Asked twice, a little apart, with
// the red kept from one and the blue from the other: that is the whole of the chromatic shift, and the
// reason this is a function. Twice rather than three times because it is most of the cost of a frame.
vec3 conduit(vec2 view, float stretch) {
    // The far end swings off the middle and the near end does not: a bend.
    vec2 fromEnd = view - Sway * exp(-1.5 * length(view - Sway));
    float radius = max(length(fromEnd) * stretch, 0.0005);

    // Looking further out is looking at wall that is nearer. The power is the stretch at the edges: the
    // faster it goes, the more the near wall is pulled out along the line of travel.
    float along = 0.42 / pow(radius, 1.0 + 0.35 * Rush) + Depth;
    float angle = atan(fromEnd.y, fromEnd.x) + Roll + 0.4 * sin(along * 0.31 + Clock * 0.7);
    // Round the wall as a point on a circle, so there is no seam where the angle wraps.
    vec2 rim = vec2(cos(angle), sin(angle));

    // As fine along the conduit as round it, so the walls are cloud and not beams, with a ripple running
    // down them as it would down a pipe of water.
    vec3 at = vec3(rim * 2.2, along * 1.5);
    float drift = noise(at * 0.5 + vec3(0.0, 0.0, Clock * 0.2));
    float plasma = cloud(at + vec3(rim * drift * 0.9, drift * 1.4 - Clock * 0.35))
        + 0.05 * sin(along * 7.0 - Clock * 2.0);
    float nebula = noise(vec3(rim * 0.9, along * 0.3 + Clock * 0.08));

    vec3 colour = mix(vec3(0.02, 0.05, 0.28), vec3(0.10, 0.38, 1.00), smoothstep(0.28, 0.62, plasma));
    colour = mix(colour, vec3(0.55, 0.22, 0.95), smoothstep(0.42, 0.78, nebula) * 0.75);
    colour += vec3(0.65, 0.85, 1.00) * pow(plasma, 5.0) * 2.2;

    float turn = fract(angle / TAU);
    colour += vec3(0.85, 0.93, 1.00) * streaks(turn, along) * (0.5 + 0.9 * Rush);

    // Darker out at the near wall, so the eye is led down the middle.
    colour *= 1.0 - 0.5 * smoothstep(0.2, 2.4, radius);
    // The far end is a haze of light - which is also what keeps the noise, very fine that far down, from
    // glittering - with a brighter heart to it.
    colour = mix(vec3(0.70, 0.85, 1.00), colour, smoothstep(0.02, 0.22, radius));
    colour += vec3(0.60, 0.78, 1.00) * exp(-radius * 6.5) * (0.6 + 0.6 * Rush);
    return colour;
}

void main() {
    vec2 view = (texCoord0 - 0.5) * 2.0 * vec2(Aspect, 1.0);
    float radius = length(view - Sway);

    float pull = 0.05 * Chroma;
    vec3 outer = conduit(view, 1.0 + pull);
    vec3 inner = conduit(view, 1.0 - pull);
    vec3 colour = vec3(outer.r, 0.5 * (outer.g + inner.g), inner.b);

    // The pane's own light, as the passage begins: over everything, and clearing from the middle out as
    // the traveller goes through it.
    float opening = (1.0 - Splash) * 3.0;
    float pane = smoothstep(opening - 0.6, opening, length(view)) * min(1.0, Splash * 8.0);
    vec3 horizon = mix(vec3(0.55, 0.74, 1.00), vec3(0.86, 0.80, 1.00),
        0.5 + 0.5 * sin(length(view) * 16.0 - Clock * 13.0));
    colour = mix(colour, horizon, pane);

    // The flare: a disc of the far end's light growing from the middle with a glow about it, and then,
    // before it has reached the corners, all of the view at once.
    float reach = Flare * Flare * 2.2;
    float disc = (1.0 - smoothstep(reach * 0.45, reach + 0.02, radius)) * min(1.0, Flare * 10.0);
    float glow = exp(-radius * (9.0 - 8.0 * Flare)) * Flare;
    float white = clamp(max(disc, smoothstep(0.55, 1.0, Flare)) + glow, 0.0, 1.0);
    colour = mix(colour, vec3(1.0), white);

    fragColor = vec4(colour, Veil);
}

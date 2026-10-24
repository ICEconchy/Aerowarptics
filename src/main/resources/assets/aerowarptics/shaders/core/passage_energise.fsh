#version 150

// A Rift Gate passage, Energise style: no journey, only a taking apart and a putting together. A column
// of light, a shimmer of gold and blue motes rising through it and thickening to white - and thinning,
// mote by mote, into the far side.
//
// The one style with no tunnel in it, and the one whose veil does not thin evenly: as the traveller
// arrives the view comes through a grain of the screen at a time, each on a threshold of its own. With
// the veil whole every grain is solid, which is the rule; see GatePassageStyle. Every number that moves
// comes from GatePassage on the CPU.

uniform float Rush;
uniform float Flare;
uniform float Splash;
uniform float Veil;
uniform float Clock;
uniform float Aspect;

in vec2 texCoord0;

out vec4 fragColor;

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

// One layer of motes: a point of light somewhere in each cell of a grid, twinkling on a beat of its own,
// and only in as many of the cells as `density` allows.
float motes(vec2 at, float size, float density) {
    vec2 grid = at * size;
    vec2 cell = floor(grid);
    vec2 within = fract(grid) - 0.5;
    float born = hash(vec3(cell, 17.0));
    vec2 offset = (vec2(hash(vec3(cell, 3.0)), hash(vec3(cell, 5.0))) - 0.5) * 0.5;
    float twinkle = pow(max(0.0, sin(Clock * (5.0 + 9.0 * born) + born * 40.0)), 4.0);
    float point = 1.0 - smoothstep(0.0, 0.26, length(within - offset));
    return point * point * twinkle * step(1.0 - density, born);
}

void main() {
    vec2 view = (texCoord0 - 0.5) * 2.0 * vec2(Aspect, 1.0);

    // The column the traveller stands in. It widens as the putting together comes on, until it is the
    // whole of the view.
    float column = exp(-view.x * view.x * mix(2.6, 0.0, Flare * Flare));
    // Light going up it in bands, and in a curtain of fine bright threads.
    float band = 0.5 + 0.5 * sin(view.y * 9.0 - Clock * 7.0);
    float threads = pow(noise(vec3(view.x * 38.0, view.y * 1.5 - Clock * 2.5, Clock * 0.6)), 3.0);
    vec3 colour = vec3(0.00, 0.01, 0.05)
        + vec3(0.10, 0.24, 0.50) * column * (0.35 + 0.65 * band)
        + vec3(0.55, 0.75, 1.00) * column * threads * 0.9;

    // Everything rises: two layers of motes, the nearer gold and the further blue, at paces of their own,
    // and thickest in the column.
    float density = clamp(0.35 + 0.4 * Rush + 0.6 * Flare + 0.6 * Splash, 0.0, 1.0);
    float coarse = motes(view - vec2(0.0, Clock * 0.35), 8.0, density);
    float fine = motes(view - vec2(3.7, Clock * 0.21), 19.0, density);
    float shimmer = 0.35 + 0.65 * column;
    colour += vec3(1.00, 0.82, 0.35) * coarse * 2.0 * shimmer;
    colour += vec3(0.60, 0.82, 1.00) * fine * 1.5 * shimmer;

    // A glitter over all of it, a new one ten times a second. It is what the passage begins as, all over
    // the view and thinning, and what it ends as, thickening until there is nothing else.
    float glitter = hash(vec3(floor(view * 70.0), floor(Clock * 10.0)));
    colour = mix(colour, vec3(1.00, 0.97, 0.88), clamp(Splash * 1.6 - glitter * 0.6, 0.0, 1.0));
    colour = mix(colour, vec3(1.0), clamp(Flare * 1.6 - glitter * 0.6, 0.0, 1.0));

    // And the far side comes through the same way, but a grain at a time and each grain once: solid
    // everywhere while the veil is whole, clear everywhere once it has gone, and never back again.
    float grain = hash(vec3(floor(view * 70.0), 23.0));
    float solid = clamp((Veil - grain * 0.85) / 0.15, 0.0, 1.0);

    fragColor = vec4(colour, solid);
}

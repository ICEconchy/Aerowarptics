package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.client.fx.RiftEffectManager.ActiveRift;

/**
 * What a Rift Modulator's theme builds around the aperture it is dressing.
 *
 * <p>Two things live here, and they run one after the other on the same clock. The <em>opening</em> is
 * what is drawn over an intact view while the hole is still winding up to exist - a mechanism coming
 * under load, a circle being written, a pane catching light, a field of stars igniting. The
 * <em>standing</em> furniture is what surrounds the hole for as long as it stands: gears that keep
 * turning, eyes that keep watching, planets that keep going round. The handover between them is
 * automatic, because the standing work is scaled by {@code rift.aperture(...)} - zero for the whole
 * wind-up, then growing in behind it and leaving again with the seal.
 *
 * <h2>Why this is a file of its own</h2>
 * {@link RiftEffectManager} owns the aperture itself: its lifetime, the opaque face that hides a hull,
 * the throat, the fire and the shards. None of that is theme-specific, and all of it is load-bearing.
 * Everything in here is decoration, and decoration wanted room to be specific in - a clock face is
 * worth thirty lines and has no business sharing a file with the geometry that makes an airship
 * disappear. The two are one subsystem split for size, which is why this reaches into
 * {@link ActiveRift} and {@link RiftEffectManager#localVertex} rather than negotiating an API.
 *
 * <h2>The two rules everything here follows</h2>
 * <ol>
 *   <li><b>Colours are the player's, untinted.</b> Every stroke below is drawn in the core colour or
 *       the accent colour the pilot picked at the Modulator, and nothing else. Themes separate
 *       themselves by <em>shape and motion</em>, never by overriding a chosen hue - so a hell portal
 *       in pale blue is entirely possible, and that is the point of a sixteen-swatch picker. What does
 *       vary per element is brightness and alpha, which is lighting rather than colour.
 *   <li><b>Nothing may weaken the face.</b> All of this is additive glow on {@code RIFT_FIRE}, which
 *       writes no depth. It can only ever add light to what is already there, so it cannot punch a
 *       hole in the opaque membrane and cannot reveal a hull inside the aperture - whether it is drawn
 *       outside the rim or straight across the face. It is depth-<em>tested</em> though, which is what
 *       lets a Starlight planet pass genuinely behind the aperture and be hidden by it.
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
final class RiftFurniture {

    /** Half-width of an ordinary drawn line, as a fraction of the aperture's cover. */
    private static final float SCRIBE = 0.011F;

    // ------------------------------------------------------------------ clockwork

    /** Where the iris's hub sits, as a fraction of the rim. Blades run from here outwards. */
    private static final float GEAR_HUB = 0.26F;
    /** How far the ring gear's teeth stand proud of the rim, as a fraction of it. */
    private static final float GEAR_TOOTH = 0.075F;
    /**
     * Radians the mechanism winds through while it is spinning up to release.
     *
     * <p>Also where the blades therefore <em>start</em> their sweep, and where a closing shutter comes
     * back to - {@code RiftEffectManager} reads it for exactly that. Winding the assembly up during the
     * charge and then drawing the blades from zero would snap the whole mechanism back a sixth of a
     * turn at the exact moment it lets go.
     */
    static final float GEAR_REST = 1.5F;
    /** Teeth around the ring gear standing outside the rim. */
    private static final int RING_TEETH = 26;
    /** Where the ring gear's band sits, as fractions of the rim. */
    private static final float RING_INNER = 1.10F;
    private static final float RING_OUTER = 1.21F;
    /** Satellite gears meshing with the ring, and how big each is as a fraction of the rim. */
    private static final int SATELLITES = 3;
    private static final float SATELLITE_SIZE = 0.26F;
    private static final int SATELLITE_TEETH = 9;
    /** Where the clock dial sits on the face, as a fraction of the rim. */
    private static final float DIAL = 0.78F;

    // --------------------------------------------------------------------- arcane

    /** The circles of the rune ring drawn during the wind-up, as fractions of the rim. */
    private static final float[] RUNE_RINGS = {0.28F, 0.66F, 1.0F};
    /** Marks written round the charging circle. They light in sequence as it charges. */
    private static final int RUNE_GLYPHS = 18;
    /**
     * Radians the rune ring counter-turns through while it charges, and therefore where the bands that
     * ignite are held. Against the gear's direction, on purpose.
     */
    static final float RUNE_REST = -0.85F;
    /** Glyphs written round the standing band, each a small figure of two or three strokes. */
    private static final int BAND_GLYPHS = 24;
    private static final float BAND_INNER = 1.09F;
    private static final float BAND_OUTER = 1.30F;
    /** Points of the figure inscribed on the face, and how far round it steps to draw each chord. */
    private static final int STAR_POINTS = 7;
    private static final int STAR_STEP = 3;

    // ---------------------------------------------------------------------- ember

    /** Fangs standing round the rim. Alternating long and short, like a real jaw. */
    private static final int FANGS = 18;
    /** How far a long fang reaches in past the rim, as a fraction of it. */
    private static final float FANG_REACH = 0.17F;
    /** Eyes watching from outside the aperture. */
    private static final int EYES = 7;
    private static final float EYE_ORBIT = 1.30F;

    // ------------------------------------------------------------------ starlight

    /** Orbits drawn around the aperture, each tilted further out of its plane than the last. */
    private static final int ORBITS = 3;
    /** Bands making up each orbit's ring system, so a ring reads as dust rather than as wire. */
    private static final int ORBIT_BANDS = 3;
    /** Bodies travelling those orbits. */
    private static final int PLANETS = 4;
    /** Fixed stars scattered outside the aperture. */
    private static final int STARS = 22;

    private RiftFurniture() {
    }

    // ============================================================== entry points

    /**
     * The wind-up: what a theme draws over the intact view before the hole exists.
     *
     * <p>Only ever called for a theme that is not {@code STANDARD} - struck glass keeps its own cracks,
     * in {@code RiftEffectManager}, untouched.
     */
    static void opening(VertexConsumer vc, Matrix4f m, ActiveRift rift, float partialTick, Vec3 eye) {
        float progress = rift.openProgress(partialTick);
        if (progress >= 1.0F) {
            return;
        }
        float charge = progress / RiftShatter.CRACK_PHASE;
        // Outlives the break by a moment and is then gone, the same shape the cracks fade on.
        float alpha = charge <= 1.0F ? 1.0F : 1.0F - (charge - 1.0F) * 4.0F;
        if (alpha <= 0.0F) {
            return;
        }
        float bias = glowBias(rift, eye);
        double cover = rift.radius;

        switch (rift.theme) {
            case CLOCKWORK -> irisWindUp(vc, m, rift, cover, charge, alpha, bias);
            case ARCANE -> circleWindUp(vc, m, rift, cover, charge, alpha, bias);
            case EMBER -> emberWindUp(vc, m, rift, cover, charge, alpha, bias);
            case STARLIGHT -> starlightWindUp(vc, m, rift, cover, charge, alpha, bias);
            case STANDARD -> {
                // Unreachable: the caller keeps standard on its own cracks.
            }
        }
    }

    /**
     * The standing furniture: what surrounds the aperture for as long as it stands.
     *
     * <p>Sized off the hole as it actually is this frame, so the whole assembly grows in as the rift
     * opens and shrinks away with the seal without keeping a clock of its own.
     */
    static void standing(VertexConsumer vc, Matrix4f m, ActiveRift rift, float partialTick, Vec3 eye) {
        if (rift.theme == uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme.STANDARD) {
            return;
        }
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.01F) {
            return;
        }
        float bias = glowBias(rift, eye);
        double cover = rift.radius * aperture;
        float time = rift.lastAge + partialTick;
        // Everything fades with the hole rather than snapping out with it, so a collapsing rift takes
        // its furniture down with it instead of leaving a ring hanging in the air for a frame.
        float alpha = Mth.clamp(aperture * 1.2F, 0.0F, 1.0F);

        switch (rift.theme) {
            case CLOCKWORK -> clockwork(vc, m, rift, cover, time, alpha, bias);
            case ARCANE -> arcane(vc, m, rift, cover, time, alpha, bias);
            case EMBER -> ember(vc, m, rift, cover, time, alpha, bias, eye);
            case STARLIGHT -> starlight(vc, m, rift, cover, time, alpha, bias);
            case STANDARD -> {
                // Unreachable: returned above.
            }
        }
    }

    // ================================================================= clockwork

    /**
     * A shutter winding up, then releasing.
     *
     * <p>Nothing races out from a point, because nothing struck this - it is a mechanism coming under
     * load. A hub, a rim, ten blade divisions between them and a ring of teeth outside, all turning
     * together and accelerating into the release.
     */
    private static void irisWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                   float charge, float alpha, float bias) {
        float[] core = channels(rift.colour);
        float[] rim = channels(rift.accentColour);
        // Brightening under load, which is the only thing that changes with charge - the colours are
        // the pilot's and stay the pilot's.
        float heat = Math.min(1.0F, charge * charge);
        float lit = 0.55F + 0.45F * heat;
        float turn = charge * charge * GEAR_REST;

        ring(vc, m, rift, cover, 1.0F, cover * SCRIBE, bias, rim, lit, alpha * 0.9F);
        ring(vc, m, rift, cover, GEAR_HUB, cover * SCRIBE * 0.8F, bias, core, lit, alpha * 0.7F);

        int teeth = rift.pattern.cracks();
        float gap = (float) (Math.PI * 2.0D / teeth);
        for (int tooth = 0; tooth < teeth; tooth++) {
            double divide = tooth * gap + turn;
            double edge = cover * rift.reach(divide, rift.rimTime);
            scribe(vc, m, rift, divide, cover * GEAR_HUB, edge, cover * SCRIBE, bias,
                    core, lit, alpha * 0.8F);

            // A tooth standing proud between each pair of blades, which is what actually says "gear"
            // rather than "wheel with spokes".
            double crest = divide + gap * 0.5D;
            double crestEdge = cover * rift.reach(crest, rift.rimTime);
            scribe(vc, m, rift, crest, crestEdge, crestEdge * (1.0D + GEAR_TOOTH),
                    cover * SCRIBE * 2.6F, bias, rim, lit, alpha * 0.85F);
        }
    }

    /**
     * The standing mechanism: a toothed ring gear, satellites meshing with it, and a clock face.
     *
     * <p>The satellites counter-rotate against the ring and are stepped round it slowly, which is the
     * whole reason this reads as a mechanism rather than as three wheels that happen to be nearby.
     * Their tooth counts and rates are not physically derived - a gear train worked out properly would
     * cost more than it bought at this size - but the <em>directions</em> are, and that is the part
     * the eye actually checks.
     */
    private static void clockwork(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                  float time, float alpha, float bias) {
        float[] core = channels(rift.colour);
        float[] rim = channels(rift.accentColour);
        float turn = GEAR_REST + time * 0.008F;

        // --- the ring gear ---
        ring(vc, m, rift, cover, RING_INNER, cover * SCRIBE, bias, rim, 0.85F, alpha * 0.8F);
        ring(vc, m, rift, cover, RING_OUTER, cover * SCRIBE, bias, rim, 0.85F, alpha * 0.8F);
        float ringGap = (float) (Math.PI * 2.0D / RING_TEETH);
        for (int tooth = 0; tooth < RING_TEETH; tooth++) {
            double angle = tooth * ringGap + turn;
            double edge = cover * rift.reach(angle, rift.rimTime);
            scribe(vc, m, rift, angle, edge * RING_OUTER, edge * (RING_OUTER + GEAR_TOOTH),
                    cover * SCRIBE * 2.2F, bias, rim, 1.0F, alpha * 0.9F);
            // Spokes across the band, so the ring is a gear rather than two circles.
            scribe(vc, m, rift, angle + ringGap * 0.5D, edge * RING_INNER, edge * RING_OUTER,
                    cover * SCRIBE * 0.8F, bias, rim, 0.5F, alpha * 0.45F);
        }

        // --- satellites, meshing with the outside of the ring ---
        double satRadius = cover * (RING_OUTER + GEAR_TOOTH + SATELLITE_SIZE * 0.85D);
        for (int sat = 0; sat < SATELLITES; sat++) {
            double seat = sat * (Math.PI * 2.0D / SATELLITES) + turn * 0.35D;
            double centreU = Math.cos(seat) * satRadius;
            double centreV = Math.sin(seat) * satRadius;
            // Against the ring, because a gear driven from its outside turns the other way. This is
            // the one bit of gear-train truth that is cheap and that everyone notices when it is wrong.
            float spin = -turn * 2.4F;
            gear(vc, m, rift, centreU, centreV, cover * SATELLITE_SIZE, SATELLITE_TEETH, spin, bias,
                    core, alpha * 0.75F);
        }

        // --- the clock face, across the aperture ---
        ring(vc, m, rift, cover, DIAL, cover * SCRIBE * 0.9F, bias, core, 0.8F, alpha * 0.6F);
        for (int hour = 0; hour < 12; hour++) {
            double angle = hour * (Math.PI / 6.0D);
            boolean quarter = hour % 3 == 0;
            double edge = cover * rift.reach(angle, rift.rimTime) * DIAL;
            scribe(vc, m, rift, angle, edge * (quarter ? 0.84D : 0.91D), edge,
                    cover * SCRIBE * (quarter ? 1.8F : 1.0F), bias, core, 1.0F,
                    alpha * (quarter ? 0.85F : 0.55F));
        }
        // Two hands at honest relative rates: the hour hand turns at a twelfth of the minute hand.
        double minute = -time * 0.030D + Math.PI * 0.5D;
        double hour = minute / 12.0D;
        hand(vc, m, rift, minute, cover * DIAL * 0.86D, cover * SCRIBE * 1.3F, bias, core, alpha);
        hand(vc, m, rift, hour, cover * DIAL * 0.52D, cover * SCRIBE * 2.0F, bias, rim, alpha);
        spot(vc, m, rift, 0.0D, 0.0D, bias, cover * 0.035D, core, 1.0F, alpha);
    }

    /** One free-standing gear: a hub, a rim and a ring of teeth, turned to {@code spin}. */
    private static void gear(VertexConsumer vc, Matrix4f m, ActiveRift rift, double centreU,
                             double centreV, double radius, int teeth, float spin, float bias,
                             float[] colour, float alpha) {
        float gap = (float) (Math.PI * 2.0D / teeth);
        for (int tooth = 0; tooth < teeth; tooth++) {
            double a0 = tooth * gap + spin;
            double a1 = a0 + gap * 0.5D;
            // The rim, drawn as a short chord between consecutive teeth.
            line(vc, m, rift, centreU + Math.cos(a0) * radius, centreV + Math.sin(a0) * radius,
                    centreU + Math.cos(a0 + gap) * radius, centreV + Math.sin(a0 + gap) * radius,
                    radius * 0.09D, bias, colour, 0.8F, alpha * 0.8F);
            // A tooth standing off it.
            line(vc, m, rift, centreU + Math.cos(a1) * radius, centreV + Math.sin(a1) * radius,
                    centreU + Math.cos(a1) * radius * 1.26D, centreV + Math.sin(a1) * radius * 1.26D,
                    radius * 0.13D, bias, colour, 1.0F, alpha * 0.9F);
            // And a spoke in to the hub, so it is visibly turning rather than a static ring.
            line(vc, m, rift, centreU, centreV,
                    centreU + Math.cos(a0) * radius, centreV + Math.sin(a0) * radius,
                    radius * 0.05D, bias, colour, 0.45F, alpha * 0.4F);
        }
        spot(vc, m, rift, centreU, centreV, bias, radius * 0.20D, colour, 1.0F, alpha);
    }

    /** A clock hand: a tapered stroke from the middle out to {@code length}. */
    private static void hand(VertexConsumer vc, Matrix4f m, ActiveRift rift, double angle,
                             double length, double halfWidth, float bias, float[] colour, float alpha) {
        double along = Math.cos(angle);
        double across = Math.sin(angle);
        double sideU = -across * halfWidth;
        double sideV = along * halfWidth;
        // Wide at the boss and a point at the tip, which is what makes it a hand and not a stick.
        RiftEffectManager.localVertex(vc, m, rift, -along * length * 0.12D - sideU,
                -across * length * 0.12D - sideV, bias,
                colour[0], colour[1], colour[2], alpha);
        RiftEffectManager.localVertex(vc, m, rift, -along * length * 0.12D + sideU,
                -across * length * 0.12D + sideV, bias,
                colour[0], colour[1], colour[2], alpha);
        RiftEffectManager.localVertex(vc, m, rift, along * length, across * length, bias,
                colour[0], colour[1], colour[2], alpha * 0.75F);
        RiftEffectManager.localVertex(vc, m, rift, along * length, across * length, bias,
                colour[0], colour[1], colour[2], alpha * 0.75F);
    }

    // ==================================================================== arcane

    /**
     * A circle written, charged, and discharged inwards.
     *
     * <p>Where the glass opening runs outwards from a struck point, this converges: the light finishes
     * at the centre, which is where the hole then arrives from.
     */
    private static void circleWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                     float charge, float alpha, float bias) {
        float[] core = channels(rift.colour);
        float[] rim = channels(rift.accentColour);
        float turn = charge * RUNE_REST;

        for (int index = 0; index < RUNE_RINGS.length; index++) {
            // The figure builds itself rather than simply brightening.
            float due = index / (float) RUNE_RINGS.length;
            float drawn = Mth.clamp((charge - due) * 3.0F, 0.0F, 1.0F);
            if (drawn <= 0.0F) {
                continue;
            }
            ring(vc, m, rift, cover, RUNE_RINGS[index], cover * SCRIBE * 0.85F, bias,
                    index == RUNE_RINGS.length - 1 ? rim : core, 1.0F, alpha * drawn * 0.8F);
        }

        for (int glyph = 0; glyph < RUNE_GLYPHS; glyph++) {
            float phase = glyph / (float) RUNE_GLYPHS;
            float lit = Mth.clamp((charge - phase * 0.8F) * 4.0F, 0.0F, 1.0F);
            if (lit <= 0.0F) {
                continue;
            }
            double angle = phase * Math.PI * 2.0D + turn;
            double edge = cover * rift.reach(angle, rift.rimTime);
            // Marks of differing length, so the ring reads as written rather than as a dial's ticks.
            double length = 0.10D + 0.12D * RiftShatter.noise(rift.seed, 5_000 + glyph);
            scribe(vc, m, rift, angle, edge * (1.0D - length), edge, cover * SCRIBE * 1.4F, bias,
                    core, 0.4F + 0.6F * lit, alpha * lit * 0.9F);
        }

        // The discharge: brightest at the moment the circle is complete and the hole arrives.
        float flash = Math.max(0.0F, charge - 0.75F) * 4.0F * alpha;
        if (flash > 0.0F) {
            spot(vc, m, rift, 0.0D, 0.0D, bias, cover * (0.05D + 0.14D * flash),
                    new float[]{1.0F, 1.0F, 1.0F}, 1.0F, flash);
        }
    }

    /**
     * The standing circle: a band of glyphs around the aperture and a figure inscribed on its face.
     *
     * <p>The figure is a star polygon - {@link #STAR_POINTS} points joined every {@link #STAR_STEP} -
     * which is one loop of chords and reads instantly as a summoning circle. The band counter-turns
     * against it, so the two never sit still relative to one another.
     */
    private static void arcane(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                               float time, float alpha, float bias) {
        float[] core = channels(rift.colour);
        float[] rim = channels(rift.accentColour);
        float turn = RUNE_REST - time * 0.006F;

        ring(vc, m, rift, cover, BAND_INNER, cover * SCRIBE * 0.8F, bias, rim, 0.9F, alpha * 0.75F);
        ring(vc, m, rift, cover, BAND_OUTER, cover * SCRIBE * 0.8F, bias, rim, 0.9F, alpha * 0.75F);

        float glyphGap = (float) (Math.PI * 2.0D / BAND_GLYPHS);
        for (int glyph = 0; glyph < BAND_GLYPHS; glyph++) {
            double angle = glyph * glyphGap + turn;
            // Each glyph breathes on its own clock, so the ring flickers like something being read
            // rather than pulsing as one lamp.
            float pulse = 0.45F + 0.55F * Mth.sin(time * 0.06F + glyph * 1.7F);
            writeGlyph(vc, m, rift, cover, angle, bias, core, pulse, alpha * (0.5F + 0.5F * pulse));
        }

        // The inscribed figure, on the face.
        double figure = cover * 0.70D;
        float spin = time * 0.004F;
        for (int point = 0; point < STAR_POINTS; point++) {
            double a0 = point * (Math.PI * 2.0D / STAR_POINTS) + spin;
            double a1 = ((point + STAR_STEP) % STAR_POINTS) * (Math.PI * 2.0D / STAR_POINTS) + spin;
            line(vc, m, rift, Math.cos(a0) * figure, Math.sin(a0) * figure,
                    Math.cos(a1) * figure, Math.sin(a1) * figure,
                    cover * SCRIBE * 0.9D, bias, core, 0.9F, alpha * 0.55F);
            spot(vc, m, rift, Math.cos(a0) * figure, Math.sin(a0) * figure, bias, cover * 0.022D,
                    rim, 1.0F, alpha * 0.8F);
        }
        ring(vc, m, rift, cover, 0.70F, cover * SCRIBE * 0.7F, bias, core, 0.7F, alpha * 0.4F);
    }

    /**
     * One written mark: two or three strokes at angles taken from the rift's own seed.
     *
     * <p>Not a real alphabet, and not trying to be. What makes writing read as writing at this size is
     * that every mark is different and none of them are symmetrical, which a hash gives for free.
     */
    private static void writeGlyph(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                   double angle, float bias, float[] colour, float lit, float alpha) {
        double edge = cover * rift.reach(angle, rift.rimTime);
        double inner = edge * BAND_INNER;
        double outer = edge * BAND_OUTER;
        double mid = (inner + outer) * 0.5D;
        double height = (outer - inner) * 0.5D;
        int hash = (int) (angle * 1000.0D);

        // The stem, always there, so every mark shares a baseline the way script does.
        scribe(vc, m, rift, angle, inner + height * 0.25D, outer - height * 0.25D,
                cover * SCRIBE * 0.9F, bias, colour, lit, alpha);

        // Then one or two cross strokes, placed and sized off the hash.
        int strokes = 1 + (Math.abs(hash) % 2);
        for (int stroke = 0; stroke < strokes; stroke++) {
            float pick = RiftShatter.noise(rift.seed, 9_000 + hash * 3 + stroke);
            double at = inner + height * (0.4D + 1.1D * pick);
            double sweep = 0.030D + 0.045D * RiftShatter.noise(rift.seed, 9_500 + hash * 3 + stroke);
            // Across the stem rather than along it - an arc at this radius, so it stays on the band.
            line(vc, m, rift,
                    Math.cos(angle - sweep) * at, Math.sin(angle - sweep) * at,
                    Math.cos(angle + sweep) * mid, Math.sin(angle + sweep) * mid,
                    cover * SCRIBE * 0.8D, bias, colour, lit, alpha * 0.85F);
        }
    }

    // ===================================================================== ember

    /** The pane catching: heat spreading from the middle out, before anything has burned through. */
    private static void emberWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                    float charge, float alpha, float bias) {
        float[] core = channels(rift.colour);
        float[] rim = channels(rift.accentColour);

        // A creeping edge of heat, running out from where it caught. Drawn as a ring that grows, which
        // is what a burn front on a flat sheet actually is.
        float front = Mth.clamp(charge * 1.15F, 0.0F, 1.0F);
        if (front > 0.02F) {
            ring(vc, m, rift, cover, front, cover * 0.045D, bias,
                    new float[]{1.0F, 1.0F, 1.0F}, 1.0F, alpha * 0.5F * front);
            ring(vc, m, rift, cover, front * 0.94F, cover * 0.030D, bias, core, 1.0F, alpha * 0.7F);
        }

        // Fangs sharpening into place around the rim as the burn reaches it, so the mouth is already
        // there by the time the hole is.
        float bite = Mth.clamp((charge - 0.45F) * 2.6F, 0.0F, 1.0F);
        if (bite > 0.0F) {
            fangs(vc, m, rift, cover, 0.0F, bias, rim, alpha * bite, bite);
        }
    }

    /**
     * A mouth: fangs biting inwards over the rim, and eyes watching from outside it.
     *
     * <p>The fangs reach in <em>past</em> the rim on purpose. They are additive glow over the opaque
     * face, so they cannot open the aperture up - what they do is stop the hole reading as a clean
     * circle, which is most of what separates a mouth from a doorway.
     */
    private static void ember(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                              float time, float alpha, float bias, Vec3 eye) {
        float[] core = channels(rift.colour);
        float[] rim = channels(rift.accentColour);

        // Breathing, so the jaw is alive rather than a fixed decoration.
        float chew = 0.82F + 0.18F * Mth.sin(time * 0.035F);
        fangs(vc, m, rift, cover, time, bias, rim, alpha, chew);

        // Which way the eyes should look, in the aperture's own frame.
        double toEyeU = (eye.x - rift.centre.x) * rift.right.x
                + (eye.y - rift.centre.y) * rift.right.y
                + (eye.z - rift.centre.z) * rift.right.z;
        double toEyeV = (eye.x - rift.centre.x) * rift.up.x
                + (eye.y - rift.centre.y) * rift.up.y
                + (eye.z - rift.centre.z) * rift.up.z;
        double look = Math.sqrt(toEyeU * toEyeU + toEyeV * toEyeV);
        double gazeU = look < 1.0e-4D ? 0.0D : toEyeU / look;
        double gazeV = look < 1.0e-4D ? 0.0D : toEyeV / look;

        float eyeGap = (float) (Math.PI * 2.0D / EYES);
        for (int index = 0; index < EYES; index++) {
            double angle = index * eyeGap + 0.25D;
            double edge = cover * rift.reach(angle, rift.rimTime) * EYE_ORBIT;
            double centreU = Math.cos(angle) * edge;
            double centreV = Math.sin(angle) * edge;

            // Each eye blinks on its own clock, mostly open, shutting briefly. Nothing is more
            // obviously a row of decorations than a row of eyes blinking in unison.
            float phase = (time * 0.011F + RiftShatter.noise(rift.seed, 7_000 + index)) % 1.0F;
            float open = phase < 0.055F ? Math.abs(phase - 0.0275F) / 0.0275F : 1.0F;
            if (open <= 0.02F) {
                continue;
            }
            demonEye(vc, m, rift, centreU, centreV, cover * 0.115D, open, gazeU, gazeV, bias,
                    core, rim, alpha);
        }
    }

    /** The ring of fangs, drawn at a given bite. Alternating long and short, like a real jaw. */
    private static void fangs(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover, float time,
                              float bias, float[] colour, float alpha, float bite) {
        float gap = (float) (Math.PI * 2.0D / FANGS);
        for (int fang = 0; fang < FANGS; fang++) {
            double angle = fang * gap;
            double edge = cover * rift.reach(angle, rift.rimTime);
            double reach = (fang % 2 == 0 ? 1.0D : 0.58D) * FANG_REACH * bite;
            // A touch of per-fang variation, so the jaw is not machined.
            reach *= 0.8D + 0.4D * RiftShatter.noise(rift.seed, 6_000 + fang);
            double half = gap * 0.36D;

            // Base spanning the rim, apex pointing inwards. A triangle, as a quad with a doubled tip.
            double base = edge * 1.06D;
            double tip = edge * (1.0D - reach);
            RiftEffectManager.localVertex(vc, m, rift, Math.cos(angle - half) * base,
                    Math.sin(angle - half) * base, bias,
                    colour[0] * 0.55F, colour[1] * 0.55F, colour[2] * 0.55F, alpha * 0.5F);
            RiftEffectManager.localVertex(vc, m, rift, Math.cos(angle + half) * base,
                    Math.sin(angle + half) * base, bias,
                    colour[0] * 0.55F, colour[1] * 0.55F, colour[2] * 0.55F, alpha * 0.5F);
            // White at the point: a fang catches the light on its tip, and it is the tip that reads.
            RiftEffectManager.localVertex(vc, m, rift, Math.cos(angle) * tip, Math.sin(angle) * tip,
                    bias, 1.0F, 1.0F, 1.0F, alpha);
            RiftEffectManager.localVertex(vc, m, rift, Math.cos(angle) * tip, Math.sin(angle) * tip,
                    bias, 1.0F, 1.0F, 1.0F, alpha);
        }
    }

    /**
     * One eye: an almond that closes to a line, with a slit pupil that follows the viewer.
     *
     * <p>The lids are two triangles sharing the eye's corners, so shutting it is a single number - the
     * bulge goes to zero and the shape collapses onto its own axis rather than needing a second pose.
     */
    private static void demonEye(VertexConsumer vc, Matrix4f m, ActiveRift rift, double centreU,
                                 double centreV, double size, float open, double gazeU, double gazeV,
                                 float bias, float[] core, float[] rim, float alpha) {
        double halfWidth = size;
        double halfHeight = size * 0.55D * open;

        // Upper and lower lid, meeting at the corners.
        for (int side = 0; side < 2; side++) {
            double bulge = side == 0 ? halfHeight : -halfHeight;
            RiftEffectManager.localVertex(vc, m, rift, centreU - halfWidth, centreV, bias,
                    core[0] * 0.35F, core[1] * 0.35F, core[2] * 0.35F, alpha * 0.75F);
            RiftEffectManager.localVertex(vc, m, rift, centreU, centreV + bulge, bias,
                    core[0], core[1], core[2], alpha * 0.9F);
            RiftEffectManager.localVertex(vc, m, rift, centreU + halfWidth, centreV, bias,
                    core[0] * 0.35F, core[1] * 0.35F, core[2] * 0.35F, alpha * 0.75F);
            RiftEffectManager.localVertex(vc, m, rift, centreU + halfWidth, centreV, bias,
                    core[0] * 0.35F, core[1] * 0.35F, core[2] * 0.35F, alpha * 0.75F);
        }

        // The pupil, shifted toward whoever is looking and squeezed shut with the lids.
        double pupilU = centreU + gazeU * halfWidth * 0.34D;
        double pupilV = centreV + gazeV * halfHeight * 0.30D;
        double slitWidth = size * 0.13D;
        double slitHeight = size * 0.42D * open;
        RiftEffectManager.localVertex(vc, m, rift, pupilU - slitWidth, pupilV, bias,
                rim[0], rim[1], rim[2], alpha);
        RiftEffectManager.localVertex(vc, m, rift, pupilU, pupilV + slitHeight, bias,
                rim[0], rim[1], rim[2], alpha);
        RiftEffectManager.localVertex(vc, m, rift, pupilU + slitWidth, pupilV, bias,
                rim[0], rim[1], rim[2], alpha);
        RiftEffectManager.localVertex(vc, m, rift, pupilU, pupilV - slitHeight, bias,
                rim[0], rim[1], rim[2], alpha);
    }

    // ================================================================= starlight

    /** Stars arriving one at a time out of nothing, until there are enough to tear the sky. */
    private static void starlightWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                        float charge, float alpha, float bias) {
        float[] core = channels(rift.colour);
        float[] rim = channels(rift.accentColour);

        for (int star = 0; star < STARS; star++) {
            float due = RiftShatter.noise(rift.seed, 8_000 + star);
            float lit = Mth.clamp((charge - due * 0.85F) * 5.0F, 0.0F, 1.0F);
            if (lit <= 0.0F) {
                continue;
            }
            double angle = RiftShatter.noise(rift.seed, 8_200 + star) * Math.PI * 2.0D;
            double radius = cover * (0.25D + 1.15D * RiftShatter.noise(rift.seed, 8_400 + star));
            double size = cover * (0.012D + 0.020D * RiftShatter.noise(rift.seed, 8_600 + star));
            spot(vc, m, rift, Math.cos(angle) * radius, Math.sin(angle) * radius, bias,
                    size * lit, star % 3 == 0 ? rim : core, 1.0F, alpha * lit);
        }

        // The first orbit sketching itself in as the field fills, so the geometry that will stand
        // around the finished rift is already arriving.
        float drawn = Mth.clamp((charge - 0.4F) * 2.2F, 0.0F, 1.0F);
        if (drawn > 0.0F) {
            orbitBand(vc, m, rift, cover, 1.16D, 0.34D, 0.0D, cover * SCRIBE, core, alpha * drawn * 0.7F);
        }
    }

    /**
     * A system: tilted orbits with ring bands in them, and bodies going round.
     *
     * <p>The orbits are genuine circles in three dimensions rather than ellipses drawn flat, which is
     * what makes them read as orbits: each is tilted out of the aperture's plane, so it passes in front
     * of the hole on one side and behind it on the other. The far half is hidden by the aperture's own
     * opaque face for free - the glow pass is depth-tested - so a planet going round actually
     * disappears behind the rift and comes back out, with nothing here to arrange it.
     */
    private static void starlight(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                  float time, float alpha, float bias) {
        float[] core = channels(rift.colour);
        float[] rim = channels(rift.accentColour);

        for (int orbit = 0; orbit < ORBITS; orbit++) {
            double tilt = 0.42D + 0.34D * orbit;
            double twist = orbit * 1.15D + time * 0.0015D;
            double radius = 1.14D + 0.30D * orbit;
            for (int band = 0; band < ORBIT_BANDS; band++) {
                // Concentric bands with gaps, so a ring reads as dust rather than as wire.
                double at = radius * (1.0D + band * 0.055D);
                orbitBand(vc, m, rift, cover, at, tilt, twist, cover * SCRIBE * (band == 1 ? 1.4D : 0.7D),
                        band == 1 ? rim : core, alpha * (band == 1 ? 0.55F : 0.32F));
            }
        }

        for (int planet = 0; planet < PLANETS; planet++) {
            int orbit = planet % ORBITS;
            double tilt = 0.42D + 0.34D * orbit;
            double twist = orbit * 1.15D + time * 0.0015D;
            double radius = cover * (1.14D + 0.30D * orbit);
            double speed = 0.011D / (1.0D + orbit * 0.55D);
            double at = time * speed + planet * 2.4D;

            double[] point = onOrbit(radius, tilt, twist, at);
            double size = cover * (0.045D + 0.030D * RiftShatter.noise(rift.seed, 8_800 + planet));
            // Bodies further round the far side are dimmer, which sells the depth even where the face
            // is not there to occlude them.
            float depth = (float) (0.55D + 0.45D * Mth.clamp((point[2] / (radius * 0.9D) + 1.0D) * 0.5D, 0.0D, 1.0D));
            disc(vc, m, rift, point[0], point[1], point[2] + bias, size,
                    planet % 2 == 0 ? core : rim, depth, alpha * depth);
        }

        // A scatter of fixed stars outside it all, twinkling on their own clocks.
        for (int star = 0; star < STARS; star++) {
            double angle = RiftShatter.noise(rift.seed, 8_200 + star) * Math.PI * 2.0D;
            double radius = cover * (1.05D + 0.85D * RiftShatter.noise(rift.seed, 8_400 + star));
            float twinkle = 0.35F + 0.65F * Mth.sin(time * 0.05F + star * 2.3F);
            double size = cover * (0.010D + 0.016D * RiftShatter.noise(rift.seed, 8_600 + star));
            spot(vc, m, rift, Math.cos(angle) * radius, Math.sin(angle) * radius, bias,
                    size * twinkle, core, 1.0F, alpha * twinkle * 0.8F);
        }
    }

    /**
     * A point on a circle of {@code radius} lying in a plane tilted out of the aperture's.
     *
     * <p>Built from two orthonormal directions rather than by rotating a flat circle, because that
     * keeps it a true circle at any tilt - a scaled ellipse would foreshorten correctly from one angle
     * and wrongly from every other.
     */
    private static double[] onOrbit(double radius, double tilt, double twist, double at) {
        double cosTwist = Math.cos(twist);
        double sinTwist = Math.sin(twist);
        double cosTilt = Math.cos(tilt);
        double sinTilt = Math.sin(tilt);
        double cosAt = Math.cos(at);
        double sinAt = Math.sin(at);
        // First axis lies in the aperture's plane; the second is tilted out of it.
        double u = radius * (cosAt * cosTwist - sinAt * sinTwist * cosTilt);
        double v = radius * (cosAt * sinTwist + sinAt * cosTwist * cosTilt);
        double w = radius * sinAt * sinTilt;
        return new double[]{u, v, w};
    }

    /** One band of an orbit's ring system, as a closed strip of quads following the tilted circle. */
    private static void orbitBand(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                  double radius, double tilt, double twist, double halfWidth,
                                  float[] colour, float alpha) {
        int segments = 48;
        double at = cover * radius;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = (segment / (double) segments) * Math.PI * 2.0D;
            double a1 = ((segment + 1) / (double) segments) * Math.PI * 2.0D;
            double[] inner0 = onOrbit(at - halfWidth, tilt, twist, a0);
            double[] outer0 = onOrbit(at + halfWidth, tilt, twist, a0);
            double[] inner1 = onOrbit(at - halfWidth, tilt, twist, a1);
            double[] outer1 = onOrbit(at + halfWidth, tilt, twist, a1);

            RiftEffectManager.localVertex(vc, m, rift, inner0[0], inner0[1], inner0[2],
                    colour[0], colour[1], colour[2], alpha);
            RiftEffectManager.localVertex(vc, m, rift, inner1[0], inner1[1], inner1[2],
                    colour[0], colour[1], colour[2], alpha);
            RiftEffectManager.localVertex(vc, m, rift, outer1[0], outer1[1], outer1[2],
                    colour[0], colour[1], colour[2], alpha);
            RiftEffectManager.localVertex(vc, m, rift, outer0[0], outer0[1], outer0[2],
                    colour[0], colour[1], colour[2], alpha);
        }
    }

    /** A small body: an octagon in a plane parallel to the aperture, bright in the middle. */
    private static void disc(VertexConsumer vc, Matrix4f m, ActiveRift rift, double centreU,
                             double centreV, double w, double radius, float[] colour, float lit,
                             float alpha) {
        int sides = 8;
        for (int side = 0; side < sides; side++) {
            double a0 = (side / (double) sides) * Math.PI * 2.0D;
            double a1 = ((side + 1) / (double) sides) * Math.PI * 2.0D;
            RiftEffectManager.localVertex(vc, m, rift, centreU, centreV, w,
                    colour[0] * lit, colour[1] * lit, colour[2] * lit, alpha);
            RiftEffectManager.localVertex(vc, m, rift, centreU + Math.cos(a0) * radius,
                    centreV + Math.sin(a0) * radius, w,
                    colour[0] * lit, colour[1] * lit, colour[2] * lit, alpha * 0.35F);
            RiftEffectManager.localVertex(vc, m, rift, centreU + Math.cos(a1) * radius,
                    centreV + Math.sin(a1) * radius, w,
                    colour[0] * lit, colour[1] * lit, colour[2] * lit, alpha * 0.35F);
            RiftEffectManager.localVertex(vc, m, rift, centreU, centreV, w,
                    colour[0] * lit, colour[1] * lit, colour[2] * lit, alpha);
        }
    }

    // =================================================================== drawing

    /** Which side of the plane to nudge glow onto, so it is never fighting the face for depth. */
    private static float glowBias(ActiveRift rift, Vec3 eye) {
        double towardsEye = (eye.x - rift.centre.x) * rift.normal.x
                + (eye.y - rift.centre.y) * rift.normal.y
                + (eye.z - rift.centre.z) * rift.normal.z;
        return towardsEye >= 0.0D ? 0.06F : -0.06F;
    }

    /** A packed colour split into channels, so nothing below has to unpack one twice. */
    private static float[] channels(int colour) {
        return new float[]{((colour >> 16) & 0xFF) / 255.0F,
                ((colour >> 8) & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F};
    }

    /**
     * A circle drawn at a fraction of the rim, following whatever shape the opening actually is.
     *
     * <p>Goes through {@link ActiveRift#reach} like everything else radial here, so a ring drawn on a
     * gate's flood-filled opening bends to it rather than bulging through the frame.
     */
    private static void ring(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                             float fraction, double halfWidth, float bias, float[] colour, float lit,
                             float alpha) {
        int segments = 48;
        float red = colour[0] * lit;
        float green = colour[1] * lit;
        float blue = colour[2] * lit;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = (segment / (double) segments) * Math.PI * 2.0D;
            double a1 = ((segment + 1) / (double) segments) * Math.PI * 2.0D;
            double r0 = cover * rift.reach(a0, rift.rimTime) * fraction;
            double r1 = cover * rift.reach(a1, rift.rimTime) * fraction;
            double cos0 = Math.cos(a0);
            double sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1);
            double sin1 = Math.sin(a1);

            RiftEffectManager.localVertex(vc, m, rift, cos0 * (r0 - halfWidth), sin0 * (r0 - halfWidth),
                    bias, red, green, blue, alpha);
            RiftEffectManager.localVertex(vc, m, rift, cos1 * (r1 - halfWidth), sin1 * (r1 - halfWidth),
                    bias, red, green, blue, alpha);
            RiftEffectManager.localVertex(vc, m, rift, cos1 * (r1 + halfWidth), sin1 * (r1 + halfWidth),
                    bias, red, green, blue, alpha);
            RiftEffectManager.localVertex(vc, m, rift, cos0 * (r0 + halfWidth), sin0 * (r0 + halfWidth),
                    bias, red, green, blue, alpha);
        }
    }

    /** A straight radial stroke of even width - a blade division, a gear tooth, a written mark. */
    private static void scribe(VertexConsumer vc, Matrix4f m, ActiveRift rift, double angle,
                               double inner, double outer, double halfWidth, float bias,
                               float[] colour, float lit, float alpha) {
        double along = Math.cos(angle);
        double across = Math.sin(angle);
        line(vc, m, rift, along * inner, across * inner, along * outer, across * outer,
                halfWidth, bias, colour, lit, alpha);
    }

    /** A straight stroke of even width between two points in the aperture's plane. */
    private static void line(VertexConsumer vc, Matrix4f m, ActiveRift rift, double u0, double v0,
                             double u1, double v1, double halfWidth, float bias, float[] colour,
                             float lit, float alpha) {
        double runU = u1 - u0;
        double runV = v1 - v0;
        double length = Math.sqrt(runU * runU + runV * runV);
        if (length < 1.0e-6D) {
            return;
        }
        double sideU = -runV / length * halfWidth;
        double sideV = runU / length * halfWidth;
        float red = colour[0] * lit;
        float green = colour[1] * lit;
        float blue = colour[2] * lit;

        RiftEffectManager.localVertex(vc, m, rift, u0 - sideU, v0 - sideV, bias, red, green, blue, alpha);
        RiftEffectManager.localVertex(vc, m, rift, u0 + sideU, v0 + sideV, bias, red, green, blue, alpha);
        RiftEffectManager.localVertex(vc, m, rift, u1 + sideU, v1 + sideV, bias, red, green, blue, alpha);
        RiftEffectManager.localVertex(vc, m, rift, u1 - sideU, v1 - sideV, bias, red, green, blue, alpha);
    }

    /** A small four-pointed mark - a star, a boss, a bead on a figure. */
    private static void spot(VertexConsumer vc, Matrix4f m, ActiveRift rift, double u, double v,
                             float bias, double radius, float[] colour, float lit, float alpha) {
        float red = colour[0] * lit;
        float green = colour[1] * lit;
        float blue = colour[2] * lit;
        RiftEffectManager.localVertex(vc, m, rift, u, v + radius, bias, red, green, blue, alpha);
        RiftEffectManager.localVertex(vc, m, rift, u + radius * 0.5D, v, bias, red, green, blue, alpha * 0.6F);
        RiftEffectManager.localVertex(vc, m, rift, u, v - radius, bias, red, green, blue, alpha);
        RiftEffectManager.localVertex(vc, m, rift, u - radius * 0.5D, v, bias, red, green, blue, alpha * 0.6F);
    }
}

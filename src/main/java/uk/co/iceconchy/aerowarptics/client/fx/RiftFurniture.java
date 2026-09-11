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
            case STARBLOCKS -> starblocksWindUp(vc, m, rift, cover, charge, alpha, bias);
            case BEDROCK -> foldWindUp(vc, m, rift, cover, charge, alpha, bias);
            case BOLDLY_GONE -> lensWindUp(vc, m, rift, cover, charge, alpha, bias);
            case LUDICROUS -> plaidWindUp(vc, m, rift, cover, charge, alpha, bias);
            case EVENTFUL_HORIZON -> gravityWindUp(vc, m, rift, cover, charge, alpha, bias);
            case VWORP -> vworpWindUp(vc, m, rift, cover, charge, alpha, bias);
            case IMPROBABILITY -> improbableWindUp(vc, m, rift, cover, charge, alpha, bias);
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
            case STARBLOCKS -> starblocks(vc, m, rift, cover, time, alpha, bias);
            case BEDROCK -> fold(vc, m, rift, cover, time, alpha, bias);
            case BOLDLY_GONE -> lens(vc, m, rift, cover, time, alpha, bias);
            case LUDICROUS -> plaid(vc, m, rift, cover, time, alpha, bias);
            case EVENTFUL_HORIZON -> gravity(vc, m, rift, cover, time, alpha, bias);
            case VWORP -> vworp(vc, m, rift, cover, time, alpha, bias);
            case IMPROBABILITY -> improbable(vc, m, rift, cover, time, alpha, bias);
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

    // ============================================================ borrowed looks
    //
    // The seven themes from here down wear jumps from elsewhere, and unlike the five above they wear
    // those jumps' colours rather than the pilot's. ThemeLook hands each of their rifts a palette of its
    // own when it is created, so rift.colour and rift.accentColour below already are hyperspace blue,
    // horizon gold and the rest; any third or fourth colour comes from ThemeLook directly.
    //
    // Each draws in three places: its wind-up over the intact view, what stands round the mouth for
    // anyone watching, and - through corridor() - what the crew see from inside the bore. For most of
    // them that last is the effect they are named after: hyperspace, warp and the time vortex are all
    // things seen from the inside.

    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] STARLINE = channels(ThemeLook.STARLINE_WHITE);
    private static final float[] HYPERSPACE_PALE = channels(ThemeLook.HYPERSPACE_PALE);
    private static final float[] HORIZON_WHITE = channels(ThemeLook.HORIZON_WHITE);
    private static final float[] GRAVITY_GLOW = channels(ThemeLook.GRAVITY_GLOW);
    private static final float[] VORTEX_BLUE = channels(ThemeLook.VORTEX_BLUE_LIT);
    private static final float[] POLICE_BOX = channels(ThemeLook.POLICE_BOX);
    /** The tartan's threads, unpacked once rather than once a band a frame. */
    private static final float[][] SETT = settChannels();

    /** Ticks a warp-drive rift's engagement flash takes to blow outward and fade. */
    private static final float ENGAGE_FLASH = 14.0F;

    /**
     * The deepest the bore is ever dressed, as a fraction of its length.
     *
     * <p>The throat is full width to 0.82 and closes on a cosine after that - see
     * {@code RiftEffectManager.throatWidth}. Stopping short of the closure keeps every piece of dressing
     * on the straight part of the tube, where "just inside the wall" is exact rather than an
     * approximation to a curve, and the far light covers what is left.
     */
    private static final float BORE_DEEPEST = 0.80F;

    /** The shallowest, so nothing is drawn across the mouth the face already fills. */
    private static final float BORE_SHALLOWEST = 0.02F;

    // ================================================================= the bore

    /**
     * What the crew see from inside the bore, for the themes that borrow their corridor from elsewhere.
     *
     * <p>Every stroke sits just inside the solid tube's own wall, sampled off the same torn rim at the
     * same clock the tube is drawn with - see {@link Bore}. The tube writes depth, so from outside the
     * whole of this is hidden behind it, and only somebody actually inside the bore ever sees any of it.
     * That is deliberate: it is what lets a theme dress the journey without painting over the screen,
     * which {@code WarpCorridorOverlay} explains the mod no longer does and why.
     */
    static void corridor(VertexConsumer vc, Matrix4f m, ActiveRift rift, float partialTick) {
        float open = Mth.lerp(partialTick, rift.throatOpenLast, rift.throatOpen);
        if (open <= 0.001F || rift.throat == 0.0F) {
            return;
        }
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }
        float time = rift.lastAge + partialTick;
        Bore bore = new Bore(rift, rift.radius * aperture, rift.throat * open, time * 0.12F,
                rift.throat > 0.0F ? -1.0F : 1.0F, Mth.clamp(open * aperture, 0.0F, 1.0F));

        switch (rift.theme) {
            case STARBLOCKS -> hyperspaceBore(vc, m, bore, time);
            case BEDROCK -> rippleBore(vc, m, bore, time);
            case BOLDLY_GONE -> warpBore(vc, m, bore, time);
            case LUDICROUS -> plaidBore(vc, m, bore, time);
            case EVENTFUL_HORIZON -> distortionBore(vc, m, bore, time);
            case VWORP -> vortexBore(vc, m, bore, time);
            case IMPROBABILITY -> improbableBore(vc, m, bore, time);
            case STANDARD, EMBER, STARLIGHT, ARCANE, CLOCKWORK -> {
                // Their bore is the plain lit tube, as it always was.
            }
        }
    }

    /**
     * Where the bore is this frame, measured once so every piece of dressing measures it the same way.
     *
     * @param cover     the mouth's radius this frame
     * @param depth     the bore's length, signed along the normal exactly as the throat's is
     * @param reachTime the clock the throat samples its torn rim with. It has to be the same clock: the
     *                  rim wanders by up to {@code RiftTear.RAG}, so dressing sampled at another moment
     *                  would sit inside the wall at one angle and through it at another
     * @param flow      which way along the bore, in fractions of its length, is opposite to the ship -
     *                  the way anything fixed to the tunnel appears to rush past the crew
     * @param alpha     how much of the bore is there, so the dressing grows and fades with it
     */
    private record Bore(ActiveRift rift, double cover, double depth, float reachTime, float flow,
                        float alpha) {
    }

    /** How far out the wall is at this angle and depth, before any inset. */
    private static double wallRadius(Bore bore, double angle, float t) {
        return bore.cover() * bore.rift().reach(angle, bore.reachTime()) * RiftEffectManager.throatWidth(t);
    }

    /** One corner of a piece of dressing on the inside of the wall. */
    private static void wallVertex(VertexConsumer vc, Matrix4f m, Bore bore, double angle, float t,
                                   double inset, float[] colour, float lit, float alpha) {
        double radius = wallRadius(bore, angle, t) * inset;
        RiftEffectManager.localVertex(vc, m, bore.rift(), Math.cos(angle) * radius, Math.sin(angle) * radius,
                bore.depth() * t, colour[0] * lit, colour[1] * lit, colour[2] * lit, alpha);
    }

    /**
     * A patch of the wall's inside face between two depths, whose angular edges may differ at the two
     * ends - which is what lets a strip of it twist round the tube into a helix.
     */
    private static void wallPatch(VertexConsumer vc, Matrix4f m, Bore bore,
                                  double nearFrom, double nearTo, float near,
                                  double farFrom, double farTo, float far,
                                  double inset, float[] colour, float lit, float nearAlpha, float farAlpha) {
        wallVertex(vc, m, bore, nearFrom, near, inset, colour, lit, nearAlpha);
        wallVertex(vc, m, bore, nearTo, near, inset, colour, lit, nearAlpha);
        wallVertex(vc, m, bore, farTo, far, inset, colour, lit, farAlpha);
        wallVertex(vc, m, bore, farFrom, far, inset, colour, lit, farAlpha);
    }

    /**
     * A hoop round the inside of the wall at one depth.
     *
     * @param halfDepth how thick, in fractions of the bore's length either side of {@code t}
     * @param wobble    how far the hoop's inset wanders with angle - a warped hoop rather than a round
     *                  one. Kept small enough that inset plus wobble never reaches the wall
     * @param lobes     how many times it wanders in one turn
     */
    private static void wallHoop(VertexConsumer vc, Matrix4f m, Bore bore, float t, float halfDepth,
                                 double inset, double wobble, int lobes, float phase,
                                 float[] colour, float lit, float alpha) {
        float near = clampBore(t - halfDepth);
        float far = clampBore(t + halfDepth);
        if (far - near < 1.0e-4F || alpha <= 0.0F) {
            return;
        }
        int segments = 32;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = segment * (Math.PI * 2.0D / segments);
            double a1 = (segment + 1) * (Math.PI * 2.0D / segments);
            double i0 = inset + wobble * Math.sin(a0 * lobes + phase);
            double i1 = inset + wobble * Math.sin(a1 * lobes + phase);
            wallVertex(vc, m, bore, a0, near, i0, colour, lit, alpha);
            wallVertex(vc, m, bore, a1, near, i1, colour, lit, alpha);
            wallVertex(vc, m, bore, a1, far, i1, colour, lit, alpha);
            wallVertex(vc, m, bore, a0, far, i0, colour, lit, alpha);
        }
    }

    /**
     * A line running along the wall, bright at its head and fading down its tail - the shape of
     * something passing fast. The tail trails behind the head against the direction of flow.
     */
    private static void wallStreak(VertexConsumer vc, Matrix4f m, Bore bore, double angle, double halfAngle,
                                   float head, float length, double inset, float[] colour, float lit,
                                   float alpha) {
        float tip = clampBore(head);
        float tail = clampBore(head - bore.flow() * length);
        if (Math.abs(tip - tail) < 1.0e-4F || alpha <= 0.0F) {
            return;
        }
        wallPatch(vc, m, bore, angle - halfAngle, angle + halfAngle, tail,
                angle - halfAngle, angle + halfAngle, tip, inset, colour, lit, alpha * 0.08F, alpha);
    }

    private static float clampBore(float t) {
        return Mth.clamp(t, BORE_SHALLOWEST, BORE_DEEPEST);
    }

    /** Where a phase in {@code [0, 1)} sits along the dressed part of the bore. */
    private static float alongBore(float phase) {
        return BORE_SHALLOWEST + (BORE_DEEPEST - BORE_SHALLOWEST) * phase;
    }

    /**
     * A phase in {@code [0, 1)} moving steadily past the crew, whichever way they are travelling.
     *
     * @param speed fractions of the bore per tick
     */
    private static float scroll(Bore bore, float base, float speed, float time) {
        float phase = (base + bore.flow() * speed * time) % 1.0F;
        return phase < 0.0F ? phase + 1.0F : phase;
    }

    // ============================================================ shared strokes

    /** Part of a circle at a fraction of the rim, following the torn edge like {@link #ring} does. */
    private static void arc(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover, float fraction,
                            double from, double to, double halfWidth, float bias, float[] colour,
                            float lit, float alpha) {
        int steps = Math.max(2, (int) Math.ceil(Math.abs(to - from) / 0.12D));
        for (int step = 0; step < steps; step++) {
            double a0 = from + (to - from) * step / steps;
            double a1 = from + (to - from) * (step + 1) / steps;
            double r0 = cover * rift.reach(a0, rift.rimTime) * fraction;
            double r1 = cover * rift.reach(a1, rift.rimTime) * fraction;
            line(vc, m, rift, Math.cos(a0) * r0, Math.sin(a0) * r0, Math.cos(a1) * r1, Math.sin(a1) * r1,
                    halfWidth, bias, colour, lit, alpha);
        }
    }

    /**
     * A figure from a stroke table, placed, scaled and turned.
     *
     * @param w          how far along the normal to draw it - a bias on the face, or a depth in the bore
     * @param widthScale squashes the figure across, which is how a flat outline is made to look as if it
     *                   is turning end over end
     */
    private static void form(VertexConsumer vc, Matrix4f m, ActiveRift rift, float[] strokes,
                             double centreU, double centreV, double w, double scale, double turn,
                             double widthScale, double halfWidth, float[] colour, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        for (int i = 0; i + 3 < strokes.length; i += 4) {
            double x0 = strokes[i] * widthScale;
            double y0 = strokes[i + 1];
            double x1 = strokes[i + 2] * widthScale;
            double y1 = strokes[i + 3];
            line(vc, m, rift,
                    centreU + (x0 * cos - y0 * sin) * scale, centreV + (x0 * sin + y0 * cos) * scale,
                    centreU + (x1 * cos - y1 * sin) * scale, centreV + (x1 * sin + y1 * cos) * scale,
                    halfWidth, (float) w, colour, 1.0F, alpha);
        }
    }

    // ================================================================ starblocks
    // Hyperspace. The stars stretch into long white lines that converge on one point, and the point
    // opens into a swirling, mottled tunnel of blue and white light.

    private static final int JUMP_STARS = 56;
    private static final int SWIRL_ARMS = 6;
    private static final int CONVERGING_LINES = 40;
    private static final int HYPERSPACE_STRANDS = 6;
    private static final int STARLINES = 72;

    /**
     * The starfield going to lines.
     *
     * <p>Cubed, so the stars sit almost still for most of the wind-up and then go all at once. A jump
     * to lightspeed is a threshold with a run-up rather than a ramp, and a linear stretch reads as a
     * zoom instead.
     */
    private static void starblocksWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                         float charge, float alpha, float bias) {
        float draw = charge * charge * charge;
        for (int star = 0; star < JUMP_STARS; star++) {
            double angle = RiftShatter.noise(rift.seed, 9_100 + star) * Math.PI * 2.0D;
            // Square-rooted so the field is even across the face rather than crowding the middle.
            double at = Math.sqrt(RiftShatter.noise(rift.seed, 9_200 + star)) * 0.95D;
            float lit = 0.7F + 0.3F * RiftShatter.noise(rift.seed, 9_400 + star);
            if (draw < 0.02F) {
                spot(vc, m, rift, Math.cos(angle) * cover * at, Math.sin(angle) * cover * at, bias,
                        cover * 0.012D, STARLINE, lit, alpha * 0.9F);
                continue;
            }
            // Drawn out both ways along its own radius: the tail reaching back towards the vanishing
            // point and the head racing for the rim. Tails converging is what makes a field of lines
            // read as one place everything is leaving from, rather than as a wheel of spokes.
            double from = cover * at * (1.0D - 0.7D * draw);
            double to = Math.min(cover * 1.35D,
                    cover * (at + draw * (0.6D + 0.9D * RiftShatter.noise(rift.seed, 9_300 + star))));
            scribe(vc, m, rift, angle, from, to, cover * SCRIBE * (0.8D - 0.45D * draw), bias,
                    star % 7 == 0 ? HYPERSPACE_PALE : STARLINE, lit, alpha * (0.6F + 0.4F * draw));
        }
        // The vanishing point, blue-white and brightening into the jump.
        disc(vc, m, rift, 0.0D, 0.0D, bias, cover * (0.04D + 0.10D * draw), HYPERSPACE_PALE, 1.0F,
                alpha * draw);
    }

    /**
     * The mouth of hyperspace, from outside: a swirl of blue and white arms turning in on the middle,
     * with starlines converging on it from every side.
     *
     * <p>Mottled along each arm - brightness running on two unrelated clocks - because an evenly lit
     * spiral reads as painted, and the tunnel is meant to churn.
     */
    private static void starblocks(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                   float time, float alpha, float bias) {
        float[] blue = channels(rift.colour);
        for (int arm = 0; arm < SWIRL_ARMS; arm++) {
            double base = arm * (Math.PI * 2.0D / SWIRL_ARMS) + time * 0.012D;
            float[] colour = arm % 2 == 0 ? STARLINE : HYPERSPACE_PALE;
            int steps = 20;
            for (int step = 0; step < steps; step++) {
                double t0 = step / (double) steps;
                double t1 = (step + 1) / (double) steps;
                double r0 = cover * (0.06D + 0.92D * t0);
                double r1 = cover * (0.06D + 0.92D * t1);
                double a0 = base + t0 * 2.6D;
                double a1 = base + t1 * 2.6D;
                float mottle = 0.5F + 0.5F * Mth.sin((float) (t0 * 17.0D + arm * 2.3D + time * 0.07D))
                        * Mth.cos((float) (a0 * 3.0D - time * 0.05D));
                // Faded in over the innermost stretch, so the arms leave the vanishing point rather than
                // meeting at a hard hub.
                float inner = (float) Math.min(1.0D, t0 * 4.0D);
                line(vc, m, rift, Math.cos(a0) * r0, Math.sin(a0) * r0, Math.cos(a1) * r1, Math.sin(a1) * r1,
                        cover * (0.02D + 0.05D * t0), bias, colour, 1.0F,
                        alpha * (0.12F + 0.42F * mottle) * inner);
            }
        }
        // Starlines converging on the vanishing point, each on its own clock, gone as they arrive.
        for (int n = 0; n < CONVERGING_LINES; n++) {
            double angle = RiftShatter.noise(rift.seed, 9_500 + n) * Math.PI * 2.0D;
            float phase = (RiftShatter.noise(rift.seed, 9_600 + n) + time * 0.035F) % 1.0F;
            double outer = cover * (1.15D - phase);
            double inner = Math.max(cover * 0.05D,
                    outer - cover * (0.25D + 0.25D * RiftShatter.noise(rift.seed, 9_700 + n)));
            float presence = Mth.sin(phase * (float) Math.PI);
            scribe(vc, m, rift, angle, inner, outer, cover * SCRIBE * 0.6D, bias, STARLINE, 1.0F,
                    alpha * presence * 0.8F);
        }
        // The deep blue behind the swirl, and a white edge round all of it.
        ring(vc, m, rift, cover, 0.55F, cover * SCRIBE * 6.0D, bias, blue, 1.0F, alpha * 0.25F);
        ring(vc, m, rift, cover, 1.0F, cover * SCRIBE * 1.6D, bias, STARLINE, 1.0F, alpha * 0.8F);
    }

    /**
     * Hyperspace from the inside: a mottled helix of blue and white turning slowly round the crew, and
     * starlines rushing past on every side.
     */
    private static void hyperspaceBore(VertexConsumer vc, Matrix4f m, Bore bore, float time) {
        ActiveRift rift = bore.rift();
        float alpha = bore.alpha();
        int steps = 24;
        for (int strand = 0; strand < HYPERSPACE_STRANDS; strand++) {
            double base = strand * (Math.PI * 2.0D / HYPERSPACE_STRANDS) + time * 0.018D;
            float[] colour = strand % 2 == 0 ? STARLINE : HYPERSPACE_PALE;
            for (int step = 0; step < steps; step++) {
                float near = alongBore(step / (float) steps);
                float far = alongBore((step + 1) / (float) steps);
                double a0 = base + near * 5.0D;
                double a1 = base + far * 5.0D;
                float mottle = 0.5F + 0.5F * Mth.sin(near * 29.0F + strand * 1.9F + time * 0.11F)
                        * Mth.cos((float) (a0 * 2.0D + time * 0.07D));
                float glow = alpha * (0.10F + 0.40F * mottle);
                wallPatch(vc, m, bore, a0 - 0.22D, a0 + 0.22D, near, a1 - 0.22D, a1 + 0.22D, far,
                        0.95D, colour, 1.0F, glow, glow);
            }
        }
        for (int n = 0; n < STARLINES; n++) {
            double angle = RiftShatter.noise(rift.seed, 11_000 + n) * Math.PI * 2.0D;
            float speed = 0.022F + 0.025F * RiftShatter.noise(rift.seed, 11_100 + n);
            float phase = scroll(bore, RiftShatter.noise(rift.seed, 11_200 + n), speed, time);
            float length = 0.08F + 0.16F * RiftShatter.noise(rift.seed, 11_300 + n);
            float presence = Mth.sin(phase * (float) Math.PI);
            wallStreak(vc, m, bore, angle, 0.012D, alongBore(phase), length, 0.92D, STARLINE, 1.0F,
                    alpha * presence);
        }
    }

    // =================================================================== bedrock
    // The jump drive. The ship's outer rings spin up and open a singularity ahead of it: a black hole
    // with the light round it bent into an event-horizon halo, and the ship gone into a dark ripple.
    // The black itself is the palette's - the opaque face and bore are the only surfaces in a rift that
    // can be drawn dark, and everything here is additive light around them.

    private static final int OUTER_RINGS = 3;
    private static final int RING_SEGMENTS = 14;
    private static final int LENSED_ARCS = 10;
    private static final int RIPPLES = 4;

    /** The rings spinning up, and the halo tightening into being between them. */
    private static void foldWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                   float charge, float alpha, float bias) {
        float[] gold = channels(rift.accentColour);
        // Alternate rings counter-turn, and the whole assembly accelerates hard into the release.
        float spin = charge * charge * 7.0F;
        for (int k = 0; k < OUTER_RINGS; k++) {
            segmentedRing(vc, m, rift, cover, 1.12F + 0.12F * k, cover * SCRIBE * (2.2D - 0.4D * k), bias,
                    spin * (k % 2 == 0 ? 1.0F : -1.35F) + k * 0.7F, gold, 0.55F + 0.45F * charge,
                    alpha * 0.8F);
        }
        float halo = charge * charge;
        if (halo > 0.001F) {
            ring(vc, m, rift, cover, 0.12F + 0.40F * halo, cover * SCRIBE * (1.0D + 2.5D * halo), bias,
                    gold, 1.0F, alpha * halo);
            ring(vc, m, rift, cover, 0.14F + 0.44F * halo, cover * SCRIBE * 0.8D, bias, HORIZON_WHITE, 1.0F,
                    alpha * halo * 0.6F);
        }
    }

    /**
     * A singularity with the sky bent round it.
     *
     * <p>In order of importance: the halo hugging the rim, the brightest thing here; light bent round
     * it in arcs that are longer and thinner the closer they pass - the one visual cue gravity actually
     * leaves; the rings still turning outside; and dark ripples rolling outward from where the ship went.
     */
    private static void fold(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                             float time, float alpha, float bias) {
        float[] gold = channels(rift.accentColour);
        for (int k = 0; k < OUTER_RINGS; k++) {
            segmentedRing(vc, m, rift, cover, 1.12F + 0.12F * k, cover * SCRIBE * (2.2D - 0.4D * k), bias,
                    time * 0.012F * (k % 2 == 0 ? 1.0F : -1.35F) + k * 0.7F, gold, 0.6F, alpha * 0.7F);
        }
        ring(vc, m, rift, cover, 1.0F, cover * SCRIBE * 2.4D, bias, gold, 1.0F, alpha);
        ring(vc, m, rift, cover, 1.05F, cover * SCRIBE * 0.9D, bias, HORIZON_WHITE, 1.0F, alpha * 0.7F);
        for (int arc = 0; arc < LENSED_ARCS; arc++) {
            float at = 1.08F + 0.55F * (arc / (float) LENSED_ARCS);
            float nearness = Mth.clamp(1.65F - at, 0.0F, 1.0F);
            float from = time * 0.006F * (0.3F + nearness) + RiftShatter.noise(rift.seed, 9_800 + arc) * 6.28F;
            float sweep = 0.6F + 2.2F * nearness;
            lensedArc(vc, m, rift, cover, at, from, sweep, cover * SCRIBE * (0.5D + 0.9D * nearness), bias,
                    arc % 3 == 0 ? HORIZON_WHITE : gold, 1.0F, alpha * (0.15F + 0.45F * nearness));
        }
        // Additive light cannot draw dark, so the ripple is its bright leading edge, rolling out and
        // fading - which is how a ripple is actually seen, by the light it bends.
        for (int k = 0; k < RIPPLES; k++) {
            float phase = (k / (float) RIPPLES + time * 0.012F) % 1.0F;
            ring(vc, m, rift, cover, 1.1F + 1.1F * phase, cover * SCRIBE * (0.6D + 1.2D * (1.0D - phase)), bias,
                    gold, 0.6F, alpha * (1.0F - phase) * 0.35F);
        }
    }

    /**
     * A ring outside the rim cut into lit segments with gaps between them, turned by an angle.
     *
     * <p>The gaps are the whole point: a continuous ring turning is indistinguishable from one standing
     * still, and these rings are defined by the fact that they spin.
     */
    private static void segmentedRing(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                      float fraction, double halfWidth, float bias, float turn,
                                      float[] colour, float lit, float alpha) {
        double span = Math.PI * 2.0D / RING_SEGMENTS;
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double from = turn + segment * span;
            arc(vc, m, rift, cover, fraction, from, from + span * 0.62D, halfWidth, bias, colour, lit, alpha);
        }
    }

    /**
     * One arc of light smeared round the body, fading out at both ends.
     *
     * <p>Tapered rather than cut off, because a lensed arc has no ends - it is the same light getting
     * fainter as it is bent further, and a hard stop reads as wire.
     */
    private static void lensedArc(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                  float fraction, float from, float sweep, double halfWidth,
                                  float bias, float[] colour, float lit, float alpha) {
        int segments = 20;
        for (int segment = 0; segment < segments; segment++) {
            double t0 = segment / (double) segments;
            double t1 = (segment + 1) / (double) segments;
            double a0 = from + t0 * sweep;
            double a1 = from + t1 * sweep;
            double r0 = cover * rift.reach(a0, rift.rimTime) * fraction;
            double r1 = cover * rift.reach(a1, rift.rimTime) * fraction;
            float taper = (float) Math.sin(((t0 + t1) * 0.5D) * Math.PI);
            line(vc, m, rift, Math.cos(a0) * r0, Math.sin(a0) * r0, Math.cos(a1) * r1, Math.sin(a1) * r1,
                    halfWidth, bias, colour, lit, alpha * taper);
        }
    }

    /**
     * Inside the fold: a black tube - the palette sees to that - with faint gold ripples rolling past,
     * and the halo waiting at the far end, which is what the crew are falling towards.
     */
    private static void rippleBore(VertexConsumer vc, Matrix4f m, Bore bore, float time) {
        float[] gold = channels(bore.rift().accentColour);
        float alpha = bore.alpha();
        for (int k = 0; k < 6; k++) {
            float phase = scroll(bore, k / 6.0F, 0.010F, time);
            float presence = Mth.sin(phase * (float) Math.PI);
            wallHoop(vc, m, bore, alongBore(phase), 0.006F, 0.93D, 0.02D, 5, time * 0.05F + k,
                    gold, 0.7F, alpha * presence * 0.45F);
        }
        wallHoop(vc, m, bore, BORE_DEEPEST - 0.02F, 0.012F, 0.90D, 0.0D, 1, 0.0F, gold, 1.0F, alpha * 0.9F);
        wallHoop(vc, m, bore, BORE_DEEPEST - 0.05F, 0.005F, 0.84D, 0.0D, 1, 0.0F, HORIZON_WHITE, 1.0F,
                alpha * 0.6F);
    }

    // =============================================================== boldly gone
    // Warp. Stars streak past as bright forward lines; the drive engages with a blue flash and a ring
    // blown outward; and the ship travels inside a distorted bubble of space.

    private static final float NACELLE_REACH = 1.55F;
    private static final int WARP_STREAKS = 18;

    /** The lens drawing out along the heading while stars streak past it, longer as it nears engagement. */
    private static void lensWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                   float charge, float alpha, float bias) {
        float[] blue = channels(rift.colour);
        float[] flash = channels(rift.accentColour);
        float draw = 1.0F + charge * 0.55F;
        oval(vc, m, rift, cover * 0.95D, draw, cover * SCRIBE * 1.4D, bias, flash, 1.0F, alpha * 0.9F);
        oval(vc, m, rift, cover * 0.62D, draw, cover * SCRIBE * 0.9D, bias, blue, 1.0F, alpha * 0.7F);
        // Every streak parallel to the stretch, because at warp the stars go past, not outward.
        for (int n = 0; n < WARP_STREAKS; n++) {
            double v = (RiftShatter.noise(rift.seed, 12_000 + n) - 0.5D) * 1.7D * cover;
            float phase = (RiftShatter.noise(rift.seed, 12_100 + n) + charge * 1.5F) % 1.0F;
            double head = cover * (-1.6D + 3.2D * phase);
            double length = cover * (0.1D + 0.8D * charge * charge);
            float presence = Mth.sin(phase * (float) Math.PI);
            line(vc, m, rift, head - length, v, head, v, cover * SCRIBE * 0.6D, bias, WHITE, 1.0F,
                    alpha * presence * charge);
        }
        double reach = cover * 0.95D * draw;
        for (int end = 0; end < 2; end++) {
            disc(vc, m, rift, end == 0 ? reach : -reach, 0.0D, bias, cover * 0.08D * charge, flash, 1.0F,
                    alpha * charge);
        }
    }

    /**
     * The bubble, and the moment it engages.
     *
     * <p>The engagement is a one-off at the break: a ring of blue light blown outward from the rim and
     * a white flash in the middle, both gone in {@link #ENGAGE_FLASH} ticks. After that the rift is the
     * bubble - three shells stretched along the heading, shimmering - with stars streaking past it.
     */
    private static void lens(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                             float time, float alpha, float bias) {
        float[] blue = channels(rift.colour);
        float[] flash = channels(rift.accentColour);
        float since = time - rift.openTicks * RiftShatter.CRACK_PHASE;
        if (since >= 0.0F && since < ENGAGE_FLASH) {
            float k = since / ENGAGE_FLASH;
            // Out fast and settling, and fading on a square so the flash is gone before the ring is.
            float spread = k * (2.0F - k);
            float burst = (1.0F - k) * (1.0F - k);
            ring(vc, m, rift, cover, 1.0F + 1.6F * spread, cover * SCRIBE * (5.0D - 3.5D * k), bias, flash,
                    1.0F, alpha * burst);
            ring(vc, m, rift, cover, 1.0F + 1.1F * spread, cover * SCRIBE * 1.5D, bias, WHITE, 1.0F,
                    alpha * burst * 0.8F);
            disc(vc, m, rift, 0.0D, 0.0D, bias, cover * (0.3D + 0.7D * burst), WHITE, 1.0F, alpha * burst * 0.7F);
        }
        float breathe = 1.0F + 0.03F * Mth.sin(time * 0.04F);
        for (int shell = 0; shell < 3; shell++) {
            float shimmer = 0.55F + 0.45F * Mth.sin(time * 0.11F + shell * 2.1F);
            oval(vc, m, rift, cover * (1.04D + 0.09D * shell) * breathe, NACELLE_REACH - 0.12F * shell,
                    cover * SCRIBE * (1.3D - 0.3D * shell), bias, shell == 0 ? flash : blue, 1.0F,
                    alpha * (0.45F - 0.1F * shell) * shimmer);
        }
        for (int n = 0; n < WARP_STREAKS; n++) {
            double v = (RiftShatter.noise(rift.seed, 12_200 + n) - 0.5D) * 2.2D * cover;
            float phase = (RiftShatter.noise(rift.seed, 12_300 + n) + time * 0.045F) % 1.0F;
            double head = cover * (-2.0D + 4.0D * phase);
            double length = cover * (0.35D + 0.4D * RiftShatter.noise(rift.seed, 12_400 + n));
            float presence = Mth.sin(phase * (float) Math.PI);
            line(vc, m, rift, head - length, v, head, v, cover * SCRIBE * 0.6D, bias, WHITE, 1.0F,
                    alpha * presence * 0.6F);
        }
        // The bright points at the ends of the long axis, where a stretched field is thinnest.
        double reach = cover * NACELLE_REACH * 1.04D;
        for (int end = 0; end < 2; end++) {
            float pulse = 0.7F + 0.3F * Mth.sin(time * 0.09F + end * 3.14F);
            disc(vc, m, rift, end == 0 ? reach : -reach, 0.0D, bias, cover * 0.10D * pulse, flash, 1.0F,
                    alpha * 0.8F);
        }
    }

    /**
     * A circle stretched along the aperture's first axis.
     *
     * <p>Takes an absolute radius and skips {@link ActiveRift#reach}, because an ellipse that also
     * followed the torn rim would be two deformations fighting over one edge and read as neither.
     */
    private static void oval(VertexConsumer vc, Matrix4f m, ActiveRift rift, double radius,
                             float stretch, double halfWidth, float bias, float[] colour, float lit,
                             float alpha) {
        int segments = 48;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = (segment / (double) segments) * Math.PI * 2.0D;
            double a1 = ((segment + 1) / (double) segments) * Math.PI * 2.0D;
            line(vc, m, rift, Math.cos(a0) * radius * stretch, Math.sin(a0) * radius,
                    Math.cos(a1) * radius * stretch, Math.sin(a1) * radius,
                    halfWidth, bias, colour, lit, alpha);
        }
    }

    /**
     * Inside the bubble: hoops of warp field rolling past, each warped out of round - the distortion is
     * the bubble - and stars as long bright forward lines, fewer than hyperspace's and much faster.
     */
    private static void warpBore(VertexConsumer vc, Matrix4f m, Bore bore, float time) {
        ActiveRift rift = bore.rift();
        float[] flash = channels(rift.accentColour);
        float alpha = bore.alpha();
        for (int k = 0; k < 5; k++) {
            float phase = scroll(bore, k / 5.0F, 0.008F, time);
            float presence = Mth.sin(phase * (float) Math.PI);
            wallHoop(vc, m, bore, alongBore(phase), 0.007F, 0.88D, 0.05D, 3, time * 0.21F + k * 1.3F,
                    flash, 1.0F, alpha * presence * 0.55F);
        }
        for (int n = 0; n < 40; n++) {
            double angle = RiftShatter.noise(rift.seed, 12_500 + n) * Math.PI * 2.0D;
            float speed = 0.035F + 0.02F * RiftShatter.noise(rift.seed, 12_600 + n);
            float phase = scroll(bore, RiftShatter.noise(rift.seed, 12_700 + n), speed, time);
            float length = 0.18F + 0.20F * RiftShatter.noise(rift.seed, 12_800 + n);
            float presence = Mth.sin(phase * (float) Math.PI);
            wallStreak(vc, m, bore, angle, 0.010D, alongBore(phase), length, 0.93D, WHITE, 1.0F,
                    alpha * presence * 0.9F);
        }
    }

    // ================================================================= ludicrous
    // Plaid. A parody of hyperspace, so it starts as the same white streaks - then they keep
    // accelerating, fatten into blinding bands of colour, and weave into a glowing tartan.

    /** How many repeats of the sett span the face. */
    private static final float SETT_REPEATS = 1.5F;

    /** White starlines fattening into colour, then the colour settling into cloth. */
    private static void plaidWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                    float charge, float alpha, float bias) {
        float streak = Mth.clamp(charge * 2.0F, 0.0F, 1.0F);
        float weave = Mth.clamp(charge * 2.0F - 1.0F, 0.0F, 1.0F);
        float lines = 1.0F - weave;
        if (lines > 0.0F) {
            float colourIn = streak * streak;
            for (int n = 0; n < 40; n++) {
                double angle = RiftShatter.noise(rift.seed, 13_000 + n) * Math.PI * 2.0D;
                double at = Math.sqrt(RiftShatter.noise(rift.seed, 13_100 + n)) * 0.9D;
                float[] band = SETT[n % SETT.length];
                float[] colour = {Mth.lerp(colourIn, 1.0F, band[0]), Mth.lerp(colourIn, 1.0F, band[1]),
                        Mth.lerp(colourIn, 1.0F, band[2])};
                double from = cover * at * (1.0D - 0.6D * streak);
                double to = Math.min(cover * 1.3D,
                        cover * (at + streak * (0.5D + 0.7D * RiftShatter.noise(rift.seed, 13_200 + n))));
                // Blinding: pushed past full brightness, which the glow pass saturates rather than wraps.
                scribe(vc, m, rift, angle, from, to, cover * SCRIBE * (0.7D + 6.0D * colourIn), bias, colour,
                        1.0F + 0.3F * colourIn, alpha * lines * (0.6F + 0.4F * streak));
            }
        }
        // Warp first, then weft - the way cloth is actually made, and far funnier to watch than a grid
        // simply appearing.
        tartan(vc, m, rift, cover, true, Mth.clamp(weave * 2.0F, 0.0F, 1.0F), 0.0F, 1.0F, alpha * 0.55F, bias);
        tartan(vc, m, rift, cover, false, Mth.clamp(weave * 2.0F - 1.0F, 0.0F, 1.0F), 0.0F, 1.0F,
                alpha * 0.55F, bias);
    }

    /**
     * Gone plaid: the tartan across the face, glowing and sliding, and bands of its colours round the
     * rim. Warp and weft slide at different rates, so it reads as cloth being pulled past rather than
     * as a pattern standing still.
     */
    private static void plaid(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                              float time, float alpha, float bias) {
        tartan(vc, m, rift, cover, true, 1.0F, time * 0.05F, 1.0F, alpha * 0.5F, bias);
        tartan(vc, m, rift, cover, false, 1.0F, time * 0.035F, 1.0F, alpha * 0.5F, bias);
        double unitAngle = Math.PI * 2.0D / (2 * ThemeLook.settUnits());
        double at = time * 0.01D;
        for (int band = 0; band < 2 * ThemeLook.settBands(); band++) {
            double width = ThemeLook.settWidth(band) * unitAngle;
            arc(vc, m, rift, cover, 1.06F, at, at + width, cover * SCRIBE * 3.0D, bias,
                    SETT[band % SETT.length], 1.2F, alpha * 0.8F);
            at += width;
        }
    }

    /**
     * One direction of the sett across the face: bands of the sett's own widths and colours, each cut
     * to the chord the circle leaves at its position so the cloth fills a round hole, not a square one.
     *
     * @param across whether these are the warp, running up the face, or the weft, running across it
     * @param drawn  how much of it has been woven so far, arriving from one edge
     * @param offset how far the cloth has slid, in sett units
     */
    private static void tartan(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover, boolean across,
                               float drawn, float offset, float lit, float alpha, float bias) {
        if (drawn <= 0.0F || cover <= 0.0D) {
            return;
        }
        double unit = 2.0D * cover / (SETT_REPEATS * ThemeLook.settUnits());
        double length = ThemeLook.settUnits() * unit;
        double shift = ((offset * unit) % length + length) % length;
        double at = -cover - shift;
        // Bounded rather than trusting the walk to leave the face: it runs every frame.
        for (int band = 0; at < cover && band < 400; band++) {
            double width = ThemeLook.settWidth(band) * unit;
            double middle = at + width * 0.5D;
            at += width;
            if (middle < -cover || middle > cover || (middle + cover) / (2.0D * cover) > drawn) {
                continue;
            }
            double half = Math.sqrt(Math.max(0.0D, cover * cover - middle * middle));
            float[] colour = SETT[Math.floorMod(band, SETT.length)];
            if (across) {
                line(vc, m, rift, middle, -half, middle, half, width * 0.5D, bias, colour, lit, alpha);
            } else {
                // The weft rides a hair proud of the warp, so the two sets cross over and under rather
                // than sharing a plane and fighting for it.
                line(vc, m, rift, -half, middle, half, middle, width * 0.5D, bias * 1.3F, colour, lit, alpha);
            }
        }
    }

    /**
     * Plaid from the inside: the whole wall woven. Warp threads run the bore's length at fixed angles,
     * weft hoops roll past the crew, and where they cross the light adds - which is exactly what a
     * woven check is.
     */
    private static void plaidBore(VertexConsumer vc, Matrix4f m, Bore bore, float time) {
        float alpha = bore.alpha();
        double unitAngle = Math.PI * 2.0D / (2 * ThemeLook.settUnits());
        double angle = time * 0.004D;
        for (int band = 0; band < 2 * ThemeLook.settBands(); band++) {
            double width = ThemeLook.settWidth(band) * unitAngle;
            wallPatch(vc, m, bore, angle, angle + width, BORE_SHALLOWEST, angle, angle + width, BORE_DEEPEST,
                    0.95D, SETT[band % SETT.length], 1.0F, alpha * 0.45F, alpha * 0.45F);
            angle += width;
        }
        float unitT = (BORE_DEEPEST - BORE_SHALLOWEST) / (1.5F * ThemeLook.settUnits());
        float repeat = ThemeLook.settUnits() * unitT;
        float at = BORE_SHALLOWEST - repeat + scroll(bore, 0.0F, 0.015F, time) * repeat;
        for (int band = 0; at < BORE_DEEPEST && band < 200; band++) {
            float width = ThemeLook.settWidth(band) * unitT;
            float middle = at + width * 0.5F;
            at += width;
            if (middle < BORE_SHALLOWEST || middle > BORE_DEEPEST) {
                continue;
            }
            wallHoop(vc, m, bore, middle, width * 0.5F, 0.93D, 0.0D, 1, 0.0F,
                    SETT[Math.floorMod(band, SETT.length)], 1.0F, alpha * 0.45F);
        }
    }

    private static float[][] settChannels() {
        float[][] out = new float[ThemeLook.settBands()][];
        for (int band = 0; band < out.length; band++) {
            out[band] = channels(ThemeLook.settColour(band));
        }
        return out;
    }

    // ========================================================== eventful horizon
    // The gravity drive. A spherical core, spinning inside rings on three axes, opens a dark and violent
    // wormhole that warps spacetime round it and briefly folds light into a distortion field.

    private static final int GIMBALS = 3;
    private static final int SPIKES = 8;
    private static final int GRID_SPOKES = 16;
    private static final int GRID_RINGS = 4;

    /** The core spinning up: three rings on three axes round a bright, spiked heart, faster and faster. */
    private static void gravityWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                      float charge, float alpha, float bias) {
        float[] arc = channels(rift.accentColour);
        float spin = charge * charge * 9.0F;
        for (int k = 0; k < GIMBALS; k++) {
            // Real circles in three dimensions, like Starlight's orbits, so a ring passes in front of
            // the core on one side and behind it on the other and the whole thing reads as a sphere.
            orbitBand(vc, m, rift, cover, 0.45D + 0.08D * k, 0.35D + 0.6D * k,
                    spin * (1.0F + 0.45F * k) + k * 2.1F, cover * SCRIBE * 1.5D,
                    k == 1 ? GRAVITY_GLOW : arc, alpha * (0.5F + 0.4F * charge));
        }
        for (int s = 0; s < SPIKES; s++) {
            double angle = s * (Math.PI * 2.0D / SPIKES) + spin * 0.25D;
            scribe(vc, m, rift, angle, cover * 0.08D, cover * (0.16D + 0.14D * charge), cover * SCRIBE * 1.2D,
                    bias, WHITE, 1.0F, alpha * 0.8F);
        }
        disc(vc, m, rift, 0.0D, 0.0D, bias, cover * (0.06D + 0.10D * charge), arc, 1.0F,
                alpha * (0.4F + 0.6F * charge));
    }

    /**
     * The wormhole, and the drive that opened it.
     *
     * <p>The gimbal, now enormous, turns violently round the hole on three axes. Across the face a polar
     * grid of spacetime is twisted harder the nearer the middle it gets, and jolted into a new shape
     * every couple of ticks rather than flowing between them - what separates violent from merely fast
     * is that it is discontinuous.
     */
    private static void gravity(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                float time, float alpha, float bias) {
        float[] arc = channels(rift.accentColour);
        float spin = time * 0.09F;
        for (int k = 0; k < GIMBALS; k++) {
            orbitBand(vc, m, rift, cover, 1.14D + 0.10D * k, 0.45D + 0.5D * k,
                    spin * (k % 2 == 0 ? 1.0F : -1.3F) + k * 2.1F, cover * SCRIBE * 1.8D,
                    k == 1 ? GRAVITY_GLOW : arc, alpha * 0.6F);
        }
        int jolt = (int) (time * 0.5F);
        for (int spoke = 0; spoke < GRID_SPOKES; spoke++) {
            double base = spoke * (Math.PI * 2.0D / GRID_SPOKES);
            double twist = 1.5D * Math.sin(time * 0.13D + spoke)
                    + (RiftShatter.noise(rift.seed, jolt * 31 + spoke) - 0.5D) * 0.7D;
            float flicker = 0.35F + 0.65F * RiftShatter.noise(rift.seed, 13_500 + jolt * 17 + spoke);
            int steps = 10;
            for (int s = 0; s < steps; s++) {
                double r0 = 0.08D + 0.92D * s / steps;
                double r1 = 0.08D + 0.92D * (s + 1) / steps;
                double a0 = base + twist * (1.0D - r0) * (1.0D - r0);
                double a1 = base + twist * (1.0D - r1) * (1.0D - r1);
                line(vc, m, rift, Math.cos(a0) * cover * r0, Math.sin(a0) * cover * r0,
                        Math.cos(a1) * cover * r1, Math.sin(a1) * cover * r1, cover * SCRIBE * 0.7D, bias,
                        arc, 1.0F, alpha * 0.45F * flicker);
            }
        }
        for (int k = 0; k < GRID_RINGS; k++) {
            double fraction = 0.22D + 0.19D * k;
            int steps = 32;
            for (int s = 0; s < steps; s++) {
                double a0 = s * (Math.PI * 2.0D / steps);
                double a1 = (s + 1) * (Math.PI * 2.0D / steps);
                double w0 = fraction * (1.0D + 0.06D * Math.sin(a0 * 5.0D + time * 0.3D + jolt));
                double w1 = fraction * (1.0D + 0.06D * Math.sin(a1 * 5.0D + time * 0.3D + jolt));
                line(vc, m, rift, Math.cos(a0) * cover * w0, Math.sin(a0) * cover * w0,
                        Math.cos(a1) * cover * w1, Math.sin(a1) * cover * w1, cover * SCRIBE * 0.8D, bias,
                        GRAVITY_GLOW, 1.0F, alpha * 0.4F);
            }
        }
        ring(vc, m, rift, cover, 1.0F, cover * SCRIBE * 2.2D, bias, GRAVITY_GLOW, 1.0F, alpha * 0.85F);
    }

    /**
     * Inside the wormhole: the wall's grid of spacetime, bent. Lines that should run straight down the
     * bore wander round it and hoops that should be round buckle, all of it jolting to a new shape every
     * couple of ticks.
     */
    private static void distortionBore(VertexConsumer vc, Matrix4f m, Bore bore, float time) {
        ActiveRift rift = bore.rift();
        float[] arc = channels(rift.accentColour);
        float alpha = bore.alpha();
        int jolt = (int) (time * 0.5F);
        int steps = 16;
        for (int line = 0; line < 12; line++) {
            double base = line * (Math.PI * 2.0D / 12);
            float flicker = 0.35F + 0.65F * RiftShatter.noise(rift.seed, 14_000 + jolt * 13 + line);
            float glow = alpha * 0.5F * flicker;
            for (int s = 0; s < steps; s++) {
                float near = alongBore(s / (float) steps);
                float far = alongBore((s + 1) / (float) steps);
                // Each end's jitter is keyed on its own step, so neighbouring pieces agree where they
                // meet and the line buckles rather than breaking into dashes.
                double a0 = base + 0.9D * Math.sin(near * 7.0D + time * 0.12D + line)
                        + (RiftShatter.noise(rift.seed, jolt * 97 + line * 7 + s) - 0.5D) * 0.25D;
                double a1 = base + 0.9D * Math.sin(far * 7.0D + time * 0.12D + line)
                        + (RiftShatter.noise(rift.seed, jolt * 97 + line * 7 + s + 1) - 0.5D) * 0.25D;
                wallPatch(vc, m, bore, a0 - 0.03D, a0 + 0.03D, near, a1 - 0.03D, a1 + 0.03D, far,
                        0.94D, arc, 1.0F, glow, glow);
            }
        }
        for (int k = 0; k < 8; k++) {
            wallHoop(vc, m, bore, alongBore((k + 0.5F) / 8.0F), 0.004F, 0.90D, 0.06D, 4,
                    time * 0.25F + k + jolt, GRAVITY_GLOW, 1.0F, alpha * 0.5F);
        }
    }

    // ===================================================================== vworp
    // The time vortex. A swirling tube of shifting currents of light, energy clouds, lightning and
    // clockwork, with a police box tumbling through it.

    /** Ticks one full wheeze of the box trying to land takes. Slow: the joke is that it is labouring. */
    private static final float WHEEZE = 34.0F;
    private static final int VORTEX_ARMS = 4;
    private static final int CLOUD_BANDS = 3;

    /**
     * A police box, as strokes in unit space: the body, the roof and its lamp, the sign band, the split
     * between the doors, and the windows.
     */
    private static final float[] TARDIS = {
            -0.45F, -0.90F, 0.45F, -0.90F,
            0.45F, -0.90F, 0.45F, 0.70F,
            0.45F, 0.70F, -0.45F, 0.70F,
            -0.45F, 0.70F, -0.45F, -0.90F,
            -0.52F, 0.70F, 0.52F, 0.70F,
            -0.40F, 0.80F, 0.40F, 0.80F,
            0.00F, 0.80F, 0.00F, 0.96F,
            -0.45F, 0.58F, 0.45F, 0.58F,
            0.00F, -0.90F, 0.00F, 0.52F,
            -0.36F, 0.36F, -0.08F, 0.36F,
            0.08F, 0.36F, 0.36F, 0.36F,
    };

    /** The vortex winding in from the rim while the box fails to arrive in the middle of it. */
    private static void vworpWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                    float charge, float alpha, float bias) {
        float[] orange = channels(rift.accentColour);
        float spin = charge * charge * 5.0F;
        int steps = 18;
        // Arriving from the rim inward, one step of each arm at a time as the charge rises.
        int drawn = Math.min(steps, (int) Math.ceil(steps * charge));
        for (int arm = 0; arm < VORTEX_ARMS; arm++) {
            double base = arm * (Math.PI * 2.0D / VORTEX_ARMS) + spin;
            float[] colour = arm % 2 == 0 ? orange : VORTEX_BLUE;
            for (int s = 0; s < drawn; s++) {
                double r0 = 1.0D - 0.9D * s / steps;
                double r1 = 1.0D - 0.9D * (s + 1) / steps;
                double a0 = base + (1.0D - r0) * 3.4D;
                double a1 = base + (1.0D - r1) * 3.4D;
                line(vc, m, rift, Math.cos(a0) * cover * r0, Math.sin(a0) * cover * r0,
                        Math.cos(a1) * cover * r1, Math.sin(a1) * cover * r1, cover * (0.03D + 0.05D * r0),
                        bias, colour, 1.0F, alpha * 0.55F);
            }
        }
        // Squared, so each attempt fades in slowly and snaps out - failing, not pulsing.
        float here = Math.abs(Mth.sin(charge * 3.0F * (float) Math.PI));
        form(vc, m, rift, TARDIS, 0.0D, 0.0D, bias, cover * 0.22D, 0.0D, 1.0D, cover * SCRIBE * 1.3D,
                POLICE_BOX, alpha * here * here);
    }

    /**
     * The vortex, from outside: wide soft currents of orange and blue spiralling in and turning fast,
     * a clock dial turning backwards round the rim, and the box tumbling at the heart of it - turning
     * in the plane and flipping edge-on as it goes, so it reads as falling end over end.
     */
    private static void vworp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                              float time, float alpha, float bias) {
        float[] orange = channels(rift.accentColour);
        for (int band = 0; band < CLOUD_BANDS; band++) {
            double base = band * (Math.PI * 2.0D / CLOUD_BANDS) + time * 0.05D;
            float[] colour = band == 1 ? orange : VORTEX_BLUE;
            int steps = 24;
            for (int s = 0; s < steps; s++) {
                double r0 = 0.05D + 0.95D * s / steps;
                double r1 = 0.05D + 0.95D * (s + 1) / steps;
                double a0 = base + r0 * 4.0D;
                double a1 = base + r1 * 4.0D;
                // Brightness running along the current, so it shifts rather than sitting there.
                float current = 0.6F + 0.4F * Mth.sin((float) (r0 * 9.0D - time * 0.2D + band));
                line(vc, m, rift, Math.cos(a0) * cover * r0, Math.sin(a0) * cover * r0,
                        Math.cos(a1) * cover * r1, Math.sin(a1) * cover * r1, cover * (0.08D + 0.10D * r0),
                        bias, colour, 1.0F, alpha * 0.22F * current);
            }
        }
        double dial = -time * 0.006D;
        ring(vc, m, rift, cover, 1.14F, cover * SCRIBE * 0.9D, bias, orange, 0.7F, alpha * 0.6F);
        for (int mark = 0; mark < 12; mark++) {
            double angle = dial + mark * (Math.PI * 2.0D / 12);
            boolean quarter = mark % 3 == 0;
            scribe(vc, m, rift, angle, cover * 1.09D, cover * (quarter ? 1.26D : 1.20D),
                    cover * SCRIBE * (quarter ? 1.6D : 1.0D), bias, orange, 0.9F, alpha * 0.7F);
        }
        float wheeze = 0.55F + 0.45F * Math.abs(Mth.sin((time % WHEEZE) / WHEEZE * (float) Math.PI * 2.0F));
        form(vc, m, rift, TARDIS, 0.0D, 0.0D, bias, cover * 0.18D, time * 0.04D,
                Math.max(0.08D, Math.abs(Math.cos(time * 0.07D))), cover * SCRIBE * 1.2D, POLICE_BOX,
                alpha * wheeze);
    }

    /**
     * The time vortex from the inside: wide currents of orange and blue wound round the tube and turning
     * fast, clock dials rolling past, and the box far down the tube, tumbling towards the crew and
     * wandering off the axis as it comes. The lightning is the bore's own, which this theme keeps.
     */
    private static void vortexBore(VertexConsumer vc, Matrix4f m, Bore bore, float time) {
        ActiveRift rift = bore.rift();
        float[] orange = channels(rift.accentColour);
        float alpha = bore.alpha();
        int steps = 24;
        for (int band = 0; band < 3; band++) {
            double base = band * (Math.PI * 2.0D / 3) + time * 0.06D;
            float[] colour = band == 1 ? orange : VORTEX_BLUE;
            for (int s = 0; s < steps; s++) {
                float near = alongBore(s / (float) steps);
                float far = alongBore((s + 1) / (float) steps);
                double a0 = base + near * 9.0D;
                double a1 = base + far * 9.0D;
                float current = 0.5F + 0.5F * Mth.sin(near * 13.0F - time * 0.17F + band * 2.0F);
                float glow = alpha * 0.22F * current;
                wallPatch(vc, m, bore, a0 - 0.55D, a0 + 0.55D, near, a1 - 0.55D, a1 + 0.55D, far,
                        0.95D, colour, 1.0F, glow, glow);
            }
        }
        for (int dial = 0; dial < 2; dial++) {
            float phase = scroll(bore, dial / 2.0F, 0.009F, time);
            float t = alongBore(phase);
            float presence = Mth.sin(phase * (float) Math.PI);
            wallHoop(vc, m, bore, t, 0.004F, 0.92D, 0.0D, 1, 0.0F, orange, 0.9F, alpha * presence * 0.6F);
            for (int mark = 0; mark < 12; mark++) {
                double angle = mark * (Math.PI * 2.0D / 12) - time * 0.01D;
                wallPatch(vc, m, bore, angle - 0.02D, angle + 0.02D, clampBore(t - 0.012F),
                        angle - 0.02D, angle + 0.02D, clampBore(t + 0.012F), 0.90D, orange, 1.0F,
                        alpha * presence * 0.7F, alpha * presence * 0.7F);
            }
        }
        float deep = BORE_DEEPEST - 0.12F;
        double wall = wallRadius(bore, 0.0D, deep);
        double size = wall * 0.10D;
        double drift = wall * 0.25D;
        form(vc, m, rift, TARDIS, Math.cos(time * 0.031D) * drift, Math.sin(time * 0.023D) * drift,
                bore.depth() * deep, size, time * 0.05D, Math.max(0.08D, Math.abs(Math.cos(time * 0.08D))),
                size * 0.06D, POLICE_BOX, alpha);
    }

    // ============================================================= improbability
    // The Infinite Improbability Drive. The ship shifts through surreal forms - a knitted doll, a whale,
    // a bowl of petunias, plain geometry - across universes that all exist at once, then is simply there.

    /** How many ticks one form survives before the rift becomes something else. */
    private static final float REROLL = 7.0F;
    private static final int ODDMENTS = 12;
    /** The form itself, and the same form in two neighbouring universes. */
    private static final int UNIVERSES = 3;

    private static void improbableWindUp(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                         float charge, float alpha, float bias) {
        improbableFigure(vc, m, rift, cover, charge * 60.0F, charge, alpha, bias);
    }

    private static void improbable(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                   float time, float alpha, float bias) {
        improbableFigure(vc, m, rift, cover, time, 1.0F, alpha, bias);
    }

    /**
     * The form this rift happens to be in this window, in several universes at once.
     *
     * <p>Every {@link #REROLL} ticks it becomes something else from {@link ImprobableForms}, and it is
     * never only one of them: the same form is drawn twice more, shifted, turned and in other colours,
     * faintly. Everything is keyed on the window, so the arrangement holds still long enough to be seen
     * and disbelieved and then changes all at once - rerolling every frame would be noise, and noise
     * reads as a broken renderer rather than as a joke.
     */
    private static void improbableFigure(VertexConsumer vc, Matrix4f m, ActiveRift rift, double cover,
                                         float clock, float scale, float alpha, float bias) {
        int window = (int) (clock / REROLL);
        int seed = rift.seed + window * 7_919;
        float within = clock / REROLL - window;
        // Eased across the join, so a form arrives and leaves rather than cutting.
        float here = 0.4F + 0.6F * Mth.sin(within * (float) Math.PI);
        float[] strokes = ImprobableForms.form(Math.floorMod(seed, ImprobableForms.count()));
        float hue = RiftShatter.noise(seed, 1);
        for (int universe = 0; universe < UNIVERSES; universe++) {
            boolean home = universe == 0;
            float[] colour = channels(ThemeLook.hsv(hue + universe * 0.31F, home ? 0.55F : 0.75F, 1.0F));
            double shiftU = home ? 0.0D : (RiftShatter.noise(seed, 10 + universe) - 0.5D) * cover * 0.6D;
            double shiftV = home ? 0.0D : (RiftShatter.noise(seed, 20 + universe) - 0.5D) * cover * 0.6D;
            double turn = (RiftShatter.noise(seed, 30 + universe) - 0.5D) * (home ? 0.5D : 1.6D);
            form(vc, m, rift, strokes, shiftU, shiftV, bias, cover * (home ? 0.75D : 0.45D) * scale, turn, 1.0D,
                    cover * SCRIBE * (home ? 1.8D : 1.0D), colour, alpha * here * (home ? 1.0F : 0.35F));
        }
        for (int mark = 0; mark < ODDMENTS; mark++) {
            double angle = RiftShatter.noise(seed, 200 + mark) * Math.PI * 2.0D;
            double at = cover * (0.4D + 0.9D * RiftShatter.noise(seed, 300 + mark));
            double size = cover * (0.015D + 0.04D * RiftShatter.noise(seed, 400 + mark));
            float[] each = channels(ThemeLook.hsv(RiftShatter.noise(seed, 500 + mark), 0.6F, 1.0F));
            spot(vc, m, rift, Math.cos(angle) * at, Math.sin(angle) * at, bias, size * scale, each, 1.0F,
                    alpha * here * 0.7F);
        }
    }

    /**
     * Down the bore, the ship's other possibilities hang in the tube ahead: three forms at three depths
     * facing the crew, each on its own reroll, turning slowly.
     */
    private static void improbableBore(VertexConsumer vc, Matrix4f m, Bore bore, float time) {
        ActiveRift rift = bore.rift();
        int window = (int) (time / REROLL);
        float within = time / REROLL - window;
        float here = 0.45F + 0.55F * Mth.sin(within * (float) Math.PI);
        for (int k = 0; k < 3; k++) {
            float t = alongBore(0.25F + 0.25F * k);
            int seed = rift.seed + (window + k * 3) * 7_919 + k;
            float[] strokes = ImprobableForms.form(Math.floorMod(seed, ImprobableForms.count()));
            double size = wallRadius(bore, 0.0D, t) * 0.38D;
            float[] colour = channels(ThemeLook.hsv(RiftShatter.noise(seed, 1), 0.65F, 1.0F));
            form(vc, m, rift, strokes, 0.0D, 0.0D, bore.depth() * t, size, time * 0.02D + k, 1.0D,
                    size * 0.05D, colour, bore.alpha() * here * 0.8F);
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

    /**
     * A packed colour split into channels, so nothing below has to unpack one twice.
     *
     * <p>Package-private rather than private because {@code RiftEffectManager}'s themed shard motions
     * want the same unpacking, and two copies of it would be two places for the byte order to drift.
     */
    static float[] channels(int colour) {
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

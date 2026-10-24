package uk.co.iceconchy.aerowarptics.gate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One frame of a gate passage: every number a style's shader may ask for, worked out from
 * {@link GatePassage}'s curves and named as the shaders name them.
 *
 * <p>A style's shader knows nothing about time. It is handed these and draws. So this is the whole of
 * what a style has to work with, and the one place the names are written down on this side: a uniform
 * is matched by its name three times over - here, in the shader's JSON, in its fragment program - and a
 * name that is in one and not another fails without a word. {@code safeGetUniform} hands back a dummy
 * for a name the JSON does not have, and the JSON's default is drawn for one nobody sets.
 * {@code ResourceIntegrityTest} holds every style's JSON to {@link #UNIFORMS}, and
 * {@code PassageShaderTest} draws each style from these same frames.
 *
 * <p>Minecraft-free, so both of them can.
 *
 * @param depth  how far down the passage the traveller has gone
 * @param rush   how fast it is going by, 0 to 1
 * @param roll   how far round it has turned, in radians
 * @param swayX  how far across the view its far end has swung
 * @param swayY  and how far up it
 * @param chroma how far apart the colours are pulled, 0 to 1
 * @param flare  how much of the view is the light at the far end, 0 to 1
 * @param splash how much of the pane's own light is still over the view, 1 at first
 * @param veil   how much of the world all of it hides, 0 to 1
 * @param clock  seconds since the passage began, for what churns whether or not anything is moving
 * @param seed   this passage's own number, so that no two go the same way
 */
public record PassageFrame(float depth, float rush, float roll, float swayX, float swayY, float chroma,
                           float flare, float splash, float veil, float clock, float seed) {

    /**
     * Every uniform a passage shader may declare, bar the two matrices the game fills in for any shader.
     * A shader declares the ones it reads and no others.
     */
    public static final List<String> UNIFORMS = List.of(
            "Depth", "Rush", "Roll", "Sway", "Chroma", "Flare", "Splash", "Veil", "Clock", "Seed", "Aspect");

    /**
     * The frame at one moment of a passage.
     *
     * @param age      ticks since the passage began, with this frame's fraction of one
     * @param forecast how long the server said the passage would last
     * @param arrived  the age at which it said it was over, or negative while it has not
     * @param seed     this passage's own, 0 to a full turn
     */
    public static PassageFrame at(double age, double forecast, double arrived, double seed) {
        return new PassageFrame(
                (float) GatePassage.depth(age, forecast),
                (float) GatePassage.rush(age, forecast),
                (float) GatePassage.roll(age, seed),
                (float) GatePassage.swayX(age, forecast, arrived, seed),
                (float) GatePassage.swayY(age, forecast, arrived, seed),
                (float) GatePassage.chroma(age, forecast),
                (float) GatePassage.flare(age, forecast, arrived),
                (float) GatePassage.splash(age, forecast),
                (float) GatePassage.veil(age, forecast, arrived),
                (float) (age / 20.0D),
                (float) seed);
    }

    /**
     * The frame as uniforms: each name in {@link #UNIFORMS}, and the one or two numbers it is set to.
     *
     * @param aspect the view's width over its height
     */
    public Map<String, float[]> uniforms(float aspect) {
        Map<String, float[]> uniforms = new LinkedHashMap<>();
        uniforms.put("Depth", new float[] {depth});
        uniforms.put("Rush", new float[] {rush});
        uniforms.put("Roll", new float[] {roll});
        uniforms.put("Sway", new float[] {swayX, swayY});
        uniforms.put("Chroma", new float[] {chroma});
        uniforms.put("Flare", new float[] {flare});
        uniforms.put("Splash", new float[] {splash});
        uniforms.put("Veil", new float[] {veil});
        uniforms.put("Clock", new float[] {clock});
        uniforms.put("Seed", new float[] {seed});
        uniforms.put("Aspect", new float[] {aspect});
        return uniforms;
    }
}

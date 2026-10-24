package uk.co.iceconchy.aerowarptics.sunderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Sunderer's hook-up, read off the source where it cannot be run without a game: that it is the one
 * place a fissure is made and that what it makes is conjured, that it spends nothing on a refusal, that every
 * refusal says something, and that its client scene is drawn under a shader pack.
 */
class SundererWiringTest {

    private static final Path MAIN = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static String source(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "no " + signature);
        return source.substring(start, source.indexOf("\n    }\n", start));
    }

    /**
     * A fissure is found, and the Sunderer is the one exception the owner allowed (2026-10-16). Any other code that
     * sets one is the decision quietly widened.
     */
    @Test
    void aFissureIsMadeBySundererBreachAndByNothingElse() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("RIFT_FISSURE.get().defaultBlockState()")) {
                    assertEquals("SundererBreach.java", file.getFileName().toString(), file + " places a Rift Fissure");
                }
            }
        }
        assertFalse(Files.exists(RESOURCES.resolve("data/aerowarptics/recipe/rift_fissure.json")), "a fissure gained a recipe");
    }

    /** What it makes is conjured, in the very method that sets it - before its first tick rolls a reservoir. */
    @Test
    void whatItTearsIsConjuredInTheSameBreath() {
        String tear = method(source(MAIN.resolve("sunderer/SundererBreach.java")), "public static boolean tear(");
        int set = tear.indexOf("RIFT_FISSURE.get().defaultBlockState()");
        int conjure = tear.indexOf(".conjure(");
        assertTrue(set > 0 && conjure > set, "the tear is not made conjured straight after it is set");
    }

    /** A refusal spends nothing: the tank is drained only after the survey has passed. */
    @Test
    void nothingIsSpentBeforeTheSurveyPasses() {
        String fire = method(source(MAIN.resolve("sunderer/RiftSundererBlockEntity.java")), "void fire(ServerLevel level)");
        int survey = fire.indexOf("SundererSurvey.full(");
        int refused = fire.indexOf("refuse(level, survey.failure())");
        int drain = fire.indexOf("tank.drain(");
        assertTrue(survey > 0 && refused > survey && drain > refused, "the charge is spent before the survey answers");
    }

    /** Every refusal has words: the keys are built on the server, which a source scan cannot see. */
    @Test
    void everyRefusalIsTranslated() {
        JsonObject lang = JsonParser.parseString(source(RESOURCES.resolve("assets/aerowarptics/lang/en_us.json")))
                .getAsJsonObject();
        for (SundererFailure failure : SundererFailure.values()) {
            assertTrue(lang.has("aerowarptics." + failure.translationKey()), "no words for " + failure);
        }
    }

    /** A rupture takes ground only through land protection, as the meltdown's crater does. */
    @Test
    void groundIsTakenOnlyWithLeave() {
        String breach = source(MAIN.resolve("sunderer/SundererBreach.java"));
        assertTrue(method(breach, "private static void carve(").contains("WellheadMeltdown.permitted("),
                "a rupture takes ground without asking land protection");
        assertTrue(method(breach, "private static void burst(").contains("WellheadMeltdown.permitted("),
                "the ring bursts without asking land protection");
        assertTrue(method(breach, "public static void take(").contains("WellheadMeltdown.permitted("),
                "a hand takes a block without asking land protection");
    }

    /** Drawn under a shader pack, and put away when the player changes level. */
    @Test
    void theSceneIsReplayedAndCleared() {
        Path fx = MAIN.resolve("client/fx");
        assertTrue(source(fx.resolve("AfterShaders.java")).contains("SundererSpectacle::onRenderLevel"));
        assertTrue(source(MAIN.resolve("client/LevelChangeReset.java")).contains("SundererSpectacle.clear()"));
    }

    /** The redstone edge is a rising one, and the block tells its entity of every neighbour's change. */
    @Test
    void itFiresOnARisingEdge() {
        String read = method(source(MAIN.resolve("sunderer/RiftSundererBlockEntity.java")), "void readSignal()");
        assertTrue(read.contains("now && !powered"), "it does not fire on a rising edge alone");
        assertTrue(source(MAIN.resolve("sunderer/RiftSundererBlock.java")).contains("sunderer.readSignal()"));
    }

    /**
     * The burst third of the frame leaves the world as it fires (the owner, 2026-10-17) - after the survey and the
     * charge, so a refusal moves nothing - and through land protection; the ring that counts as whole from then on
     * is what still stands; and a misfire, or a Sunderer broken before it commits, puts them back.
     */
    @Test
    void theBurstThirdLiftsAtTheEdgeAndAMisfirePutsItBack() {
        String entity = source(MAIN.resolve("sunderer/RiftSundererBlockEntity.java"));
        String fire = method(entity, "void fire(ServerLevel level)");
        int drain = fire.indexOf("tank.drain(");
        int lift = fire.indexOf("Blocks.AIR.defaultBlockState()");
        assertTrue(drain > 0 && lift > drain, "the frame is lifted before the charge is spent");
        assertTrue(fire.contains("WellheadMeltdown.permitted("), "the frame is lifted without asking land protection");
        assertTrue(fire.contains("standing.removeAll(shattering)"), "the burst third still counts as the standing ring");
        assertTrue(method(entity, "private void misfire(").contains("putBack("), "a misfire leaves the frame lifted");
        assertTrue(method(entity, "void onRemoved()").contains("putBack("), "a Sunderer broken early leaves the frame lifted");
        assertTrue(method(entity, "private void putBack(").contains("isAir()"), "putting back overwrites what is there");
        assertTrue(method(entity, "private void tell(").contains("shattering"), "the clients are not told what to unravel");
    }

    /** The newborn tear is drawn for everybody for a while: the owner's choice, 2026-10-17. */
    @Test
    void theNewbornTearIsRevealed() {
        String tear = method(source(MAIN.resolve("sunderer/SundererBreach.java")), "public static boolean tear(");
        assertTrue(tear.contains(".reveal(REVEALED_FOR)"), "a newborn tear is not revealed");
        String runtime = source(MAIN.resolve("client/ClientRuntime.java"));
        int fissure = runtime.indexOf("static void tickFissure(");
        String body = runtime.substring(fissure, runtime.indexOf("\n    }\n", fissure));
        assertTrue(body.contains("revealedUntil()"), "the client never draws a revealed tear for everybody");
    }

    /** There is no membrane any more: nothing flat is drawn across the opening (the owner, 2026-10-17). */
    @Test
    void nothingFlatIsDrawnAcrossTheOpening() {
        String spectacle = source(MAIN.resolve("client/fx/SundererSpectacle.java"));
        assertFalse(spectacle.contains("CrackWeb") || spectacle.contains("membrane("), "the flat membrane came back");
        assertTrue(spectacle.contains("GravityLens.bend("), "the seed bends nothing");
        assertTrue(spectacle.contains("renderSingleBlock("), "the burst frame is not drawn as blocks");
    }
}

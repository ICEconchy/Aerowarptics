package uk.co.iceconchy.aerowarptics.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;

/**
 * What came back from a sounding.
 *
 * <p>A probe throws a rift at nowhere in particular and reads what it hits. The reading is an
 * ordinary {@link DestinationSurvey} - the same picture the Astrolabe draws of an anchor - which is
 * the whole point of doing it this way: a place nobody has been looks exactly like a place somebody
 * has, and the pilot judges it on the same evidence either way.
 *
 * <p>The one thing a sounding can fail at is finding ground. Everything else - a thin reading, an
 * awkward shore, a mountain in the way - is information rather than failure, and is left for the
 * pilot to look at and decide about.
 *
 * @param bearing which way it was thrown
 * @param range   how far, in blocks, as asked for
 * @param survey  the ground that came back
 */
public record ProbeSounding(ProbeBearing bearing, int range, DestinationSurvey survey) {

    /**
     * Coverage below which a reading is worth flagging.
     *
     * <p>A survey only reads chunks that are actually loaded. The probe holds the far region open
     * while it works, but a server under load may not have finished with all of it, and a picture
     * that is mostly holes should say so rather than being drawn as though it were flat ground.
     */
    public static final float THIN_COVERAGE = 0.75F;

    public static final StreamCodec<RegistryFriendlyByteBuf, ProbeSounding> STREAM_CODEC =
            StreamCodec.of(ProbeSounding::encode, ProbeSounding::decode);

    /** Where a ship aimed at this sounding would be sent. */
    public BlockPos fix() {
        BlockPos centre = survey.centre();
        return new BlockPos(centre.getX(), groundY(), centre.getZ());
    }

    public int groundY() {
        return survey.groundY() == DestinationSurvey.NO_GROUND
                ? centreFallbackY()
                : survey.groundY();
    }

    private int centreFallbackY() {
        return survey.centre().getY();
    }

    /** Whether there is anywhere here to aim a ship at. */
    public boolean usable() {
        return survey.groundY() != DestinationSurvey.NO_GROUND;
    }

    public boolean thin() {
        return survey.coverage() < THIN_COVERAGE;
    }

    /** How this reading is described in one word on the panel. */
    public ProbeVerdict verdict() {
        if (!usable()) {
            return ProbeVerdict.NO_GROUND;
        }
        return thin() ? ProbeVerdict.PARTIAL : ProbeVerdict.CLEAR;
    }

    /** "NE 3,200" - short enough to be a course label on the drive's console. */
    public String label() {
        return bearing.abbreviation() + " " + String.format("%,d", range);
    }

    private static void encode(RegistryFriendlyByteBuf buf, ProbeSounding sounding) {
        buf.writeEnum(sounding.bearing);
        buf.writeVarInt(sounding.range);
        DestinationSurvey.STREAM_CODEC.encode(buf, sounding.survey);
    }

    private static ProbeSounding decode(RegistryFriendlyByteBuf buf) {
        return new ProbeSounding(
                buf.readEnum(ProbeBearing.class),
                buf.readVarInt(),
                DestinationSurvey.STREAM_CODEC.decode(buf));
    }
}

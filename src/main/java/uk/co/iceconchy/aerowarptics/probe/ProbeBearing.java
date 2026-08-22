package uk.co.iceconchy.aerowarptics.probe;

import net.minecraft.util.StringRepresentable;

/**
 * Which way a sounding is thrown.
 *
 * <p>Eight points rather than a free bearing, because a blind jump is a decision and not a
 * measurement. "North-east, three thousand" is something a pilot can say, remember and repeat; a
 * bearing of 47.3 degrees is a number that happened to be under the mouse.
 *
 * <p>The offsets are unit-ish rather than normalised on the diagonals - a north-east sounding of
 * three thousand travels three thousand blocks, not four thousand two hundred - so range means the
 * same thing whichever way it is aimed. {@link #dx} and {@link #dz} are therefore normalised here.
 */
public enum ProbeBearing implements StringRepresentable {

    NORTH("north", 0.0D, -1.0D),
    NORTH_EAST("north_east", 1.0D, -1.0D),
    EAST("east", 1.0D, 0.0D),
    SOUTH_EAST("south_east", 1.0D, 1.0D),
    SOUTH("south", 0.0D, 1.0D),
    SOUTH_WEST("south_west", -1.0D, 1.0D),
    WEST("west", -1.0D, 0.0D),
    NORTH_WEST("north_west", -1.0D, -1.0D);

    private final String name;
    private final double dx;
    private final double dz;

    ProbeBearing(String name, double x, double z) {
        this.name = name;
        double length = Math.sqrt(x * x + z * z);
        this.dx = x / length;
        this.dz = z / length;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "probe.bearing." + name;
    }

    /** Compass degrees clockwise from north, which is what the dial draws itself from. */
    public float degrees() {
        return ordinal() * 45.0F;
    }

    public double dx() {
        return dx;
    }

    public double dz() {
        return dz;
    }

    public ProbeBearing next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public ProbeBearing previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    /** Two-letter abbreviation for a dial with no room for words. */
    public String abbreviation() {
        return switch (this) {
            case NORTH -> "N";
            case NORTH_EAST -> "NE";
            case EAST -> "E";
            case SOUTH_EAST -> "SE";
            case SOUTH -> "S";
            case SOUTH_WEST -> "SW";
            case WEST -> "W";
            case NORTH_WEST -> "NW";
        };
    }

    public int index() {
        return ordinal();
    }

    public static ProbeBearing byIndex(int index) {
        ProbeBearing[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}

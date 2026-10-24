package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * How a Rift Plotter fires: what sets it off, how long its rifts stand, what it does with a vehicle waiting in front
 * of it, and how fast things come out of the far end.
 *
 * <p>The same machine is a weapon and a freight terminal, and which it is is these settings, mixed as a player likes
 * - there is no mode switch, because the two are not exclusive. A battery that fires on a redstone clock at whatever
 * docks is both. The three ways to set it off are independent:
 * <ul>
 *   <li><b>fire when docked</b> - a vehicle the dock helper has lined up is a reason to fire: the plotter charges,
 *   opens just long enough to launch it through, and heals behind it;</li>
 *   <li><b>fire on redstone</b> - a rising edge fires, as the table's Engage does. With a lane, the signal holds it:
 *   the lane stands while the signal is high;</li>
 *   <li><b>hold open as a lane</b> - once open, the rifts stay open, paying essence every second, until stood down,
 *   starved, or (on redstone) the signal falls.</li>
 * </ul>
 *
 * <p>The dock's own size is here too: how far from the rift's axis it catches a vehicle, and how far back. Both are
 * {@link #AUTO} until a player sets them, which is the dock every plotter had before it could be sized - as wide as
 * suits its rift ({@link DockAlign#zoneRadius}) and as deep as the server says. A yard with two plotters side by side
 * wants narrow docks that do not steal each other's ships; a harbour mouth wants one wide enough to be flown into
 * without looking.
 *
 * <p>Clamped once on the way in, as {@link Plot} is, against the server's own limits - which travel to the table
 * rather than being read there.
 *
 * @param dock           whether the dock helper lines up a vehicle waiting in front of the entry and launches it
 * @param fireOnDock     whether a lined-up vehicle fires the plotter by itself
 * @param fireOnRedstone whether a redstone signal at the machine fires it (and, for a lane, holds it)
 * @param lane           whether the rifts stay open once open, paying their upkeep every second
 * @param window         seconds the rifts stand for a strike that is not a lane
 * @param exitSpeed      how fast, in blocks a second, things come out of the far rift - or {@link #KEEP_SPEED}, to keep
 *                       the speed they went in with
 * @param dockDelay      seconds a vehicle has to sit lined up before it fires the plotter by itself
 * @param dockRadius     how far from the rift's axis the dock catches a vehicle, in blocks - or {@link #AUTO}
 * @param dockDepth      how far behind the entry the dock catches a vehicle, in blocks - or {@link #AUTO}
 */
public record Firing(boolean dock, boolean fireOnDock, boolean fireOnRedstone, boolean lane, int window,
                     double exitSpeed, int dockDelay, double dockRadius, double dockDepth) {

    /** An exit speed meaning "whatever speed it went in with". */
    public static final double KEEP_SPEED = -1.0D;

    /** A dock radius or depth meaning "as the plotter sizes it itself". */
    public static final double AUTO = -1.0D;

    /** The longest a docked vehicle can be made to wait before it fires the plotter. */
    public static final int MAX_DOCK_DELAY = 30;

    /** The narrowest and the shallowest a dock may be set: smaller, and nothing's middle is ever inside it. */
    public static final double MIN_DOCK_RADIUS = 2.0D;
    public static final double MIN_DOCK_DEPTH = 4.0D;

    /** A new plotter: fires on the table's Engage or redstone, catches what waits in front of it, keeps speed. */
    public static final Firing DEFAULT = new Firing(true, false, true, false, 20, KEEP_SPEED, 2, AUTO, AUTO);

    public static final StreamCodec<RegistryFriendlyByteBuf, Firing> STREAM_CODEC =
            StreamCodec.of(Firing::encode, Firing::decode);

    /** Whether things keep their own speed through the rift. */
    public boolean keepsSpeed() {
        return exitSpeed < 0.0D;
    }

    /** Whether the dock's radius is left to the plotter, and so follows its rift's. */
    public boolean autoDockRadius() {
        return dockRadius < 0.0D;
    }

    /** Whether the dock's depth is left to the server. */
    public boolean autoDockDepth() {
        return dockDepth < 0.0D;
    }

    /** How far from the axis of a rift so wide the dock reaches: what it was set to, or what suits the rift. */
    public double dockRadius(double riftRadius) {
        return autoDockRadius() ? DockAlign.zoneRadius(riftRadius) : dockRadius;
    }

    /** How far behind the entry the dock reaches: what it was set to, or the depth the server gives one left alone. */
    public double dockDepth(double usual) {
        return autoDockDepth() ? usual : dockDepth;
    }

    /** The same settings inside the server's limits. */
    public Firing clamped(int maxWindow, double maxExitSpeed, double maxDockRadius, double maxDockDepth) {
        double speed = Double.isFinite(exitSpeed) ? exitSpeed : KEEP_SPEED;
        return new Firing(dock, fireOnDock, fireOnRedstone, lane, Mth.clamp(window, 1, Math.max(1, maxWindow)),
                speed < 0.0D ? KEEP_SPEED : Math.min(speed, Math.max(0.0D, maxExitSpeed)),
                Mth.clamp(dockDelay, 0, MAX_DOCK_DELAY),
                sized(dockRadius, MIN_DOCK_RADIUS, maxDockRadius),
                sized(dockDepth, MIN_DOCK_DEPTH, maxDockDepth));
    }

    /** A dock measurement inside its limits. Anything that is not a size - negative, or not a number - is {@link #AUTO}. */
    private static double sized(double value, double min, double max) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return AUTO;
        }
        return Mth.clamp(value, min, Math.max(min, max));
    }

    public Firing withDock(boolean on) {
        return new Firing(on, fireOnDock, fireOnRedstone, lane, window, exitSpeed, dockDelay, dockRadius, dockDepth);
    }

    public Firing withFireOnDock(boolean on) {
        return new Firing(dock, on, fireOnRedstone, lane, window, exitSpeed, dockDelay, dockRadius, dockDepth);
    }

    public Firing withFireOnRedstone(boolean on) {
        return new Firing(dock, fireOnDock, on, lane, window, exitSpeed, dockDelay, dockRadius, dockDepth);
    }

    public Firing withLane(boolean on) {
        return new Firing(dock, fireOnDock, fireOnRedstone, on, window, exitSpeed, dockDelay, dockRadius, dockDepth);
    }

    public Firing withWindow(int seconds) {
        return new Firing(dock, fireOnDock, fireOnRedstone, lane, seconds, exitSpeed, dockDelay, dockRadius, dockDepth);
    }

    public Firing withExitSpeed(double blocksPerSecond) {
        return new Firing(dock, fireOnDock, fireOnRedstone, lane, window, blocksPerSecond, dockDelay, dockRadius,
                dockDepth);
    }

    public Firing withDockDelay(int seconds) {
        return new Firing(dock, fireOnDock, fireOnRedstone, lane, window, exitSpeed, seconds, dockRadius, dockDepth);
    }

    /** The dock's size: blocks out from the rift's axis and blocks back from the entry, either of them {@link #AUTO}. */
    public Firing withDockSize(double radius, double depth) {
        return new Firing(dock, fireOnDock, fireOnRedstone, lane, window, exitSpeed, dockDelay, radius, depth);
    }

    // ------------------------------------------------------------------ saving

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Dock", dock);
        tag.putBoolean("FireOnDock", fireOnDock);
        tag.putBoolean("FireOnRedstone", fireOnRedstone);
        tag.putBoolean("Lane", lane);
        tag.putInt("Window", window);
        tag.putDouble("ExitSpeed", exitSpeed);
        tag.putInt("DockDelay", dockDelay);
        tag.putDouble("DockRadius", dockRadius);
        tag.putDouble("DockDepth", dockDepth);
        return tag;
    }

    /**
     * Saved settings, or the defaults where nothing was saved. Clamped by the caller, which knows the limits.
     *
     * <p>A plotter saved before its dock could be sized has no size in it, and a number that is not there reads as
     * nought - which would be the smallest dock there is, on every plotter already built. It reads as {@link #AUTO}.
     */
    public static Firing load(CompoundTag tag) {
        if (!tag.contains("Window")) {
            return DEFAULT;
        }
        return new Firing(tag.getBoolean("Dock"), tag.getBoolean("FireOnDock"), tag.getBoolean("FireOnRedstone"),
                tag.getBoolean("Lane"), tag.getInt("Window"), tag.getDouble("ExitSpeed"), tag.getInt("DockDelay"),
                tag.contains("DockRadius") ? tag.getDouble("DockRadius") : AUTO,
                tag.contains("DockDepth") ? tag.getDouble("DockDepth") : AUTO);
    }

    private static void encode(RegistryFriendlyByteBuf buf, Firing firing) {
        buf.writeBoolean(firing.dock);
        buf.writeBoolean(firing.fireOnDock);
        buf.writeBoolean(firing.fireOnRedstone);
        buf.writeBoolean(firing.lane);
        buf.writeVarInt(firing.window);
        buf.writeDouble(firing.exitSpeed);
        buf.writeVarInt(firing.dockDelay);
        buf.writeDouble(firing.dockRadius);
        buf.writeDouble(firing.dockDepth);
    }

    private static Firing decode(RegistryFriendlyByteBuf buf) {
        return new Firing(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                buf.readDouble(), buf.readVarInt(), buf.readDouble(), buf.readDouble());
    }
}

package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * Reports a Rift Drive to a Display Link.
 *
 * <p>Eight modes, in the order a pilot builds a console from the top down: what the machine is,
 * what it is doing, how far through it is, and where it is going. The three numeric modes
 * &mdash; charge, sequence progress and cooldown &mdash; are bare values so that a nixie tube or a
 * Display Board can carry them.
 *
 * <p>{@code COURSE} and {@code FAULT} clear the target when there is nothing to report, rather than
 * writing "None". A blank line on a console reads as "no course set" perfectly well, and it means a
 * board built from several links does not accumulate a row of dashes.
 *
 * <p>A drive aboard an assembled hull needs nothing special here. Sable's plots live in reserved
 * chunks of the parent level, so a Display Link mounted on the hull beside the drive resolves it
 * through the ordinary block entity lookup like any other block.
 *
 * <p>The actual eight-case switch lives in {@link DriveReadout}, shared with
 * {@link RiftModulatorDisplaySource} - a link pointed at the drive itself and one pointed at a
 * Modulator bolted beside it report the same drive the same way.
 */
public class RiftDriveDisplaySource extends AWDisplaySource<RiftDriveBlockEntity> {

    /**
     * The registry path, {@code aerowarptics:rift_drive} in full. Baked into every configured
     * link's save data, so changing it orphans every link set up before the change.
     */
    public static final String SOURCE_ID = "rift_drive";

    RiftDriveDisplaySource() {
        super(SOURCE_ID, RiftDriveBlockEntity.class, () -> AWBlockEntities.RIFT_DRIVE.get(),
                DriveReadout.MODES.toArray(String[]::new));
    }

    @Override
    protected boolean numeric(int mode) {
        return DriveReadout.numeric(mode);
    }

    @Override
    protected MutableComponent line(RiftDriveBlockEntity drive, int mode) {
        return DriveReadout.line(drive, mode);
    }
}

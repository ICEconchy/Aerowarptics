package uk.co.iceconchy.aerowarptics.compat.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * Reports a Rift Modulator's linked Rift Drive to a Display Link.
 *
 * <p>The same eight modes {@link RiftDriveDisplaySource} offers, read through
 * {@link RiftModulatorBlockEntity#linkedDrive()} instead of straight off the drive - so a console
 * built where the Modulator happens to be mounted can show what its drive is doing without a second
 * link reaching across the room to the drive itself. A Modulator that has not found a drive to dress
 * reports nothing at all, the same as a machine a Display Link cannot see.
 */
public class RiftModulatorDisplaySource extends AWDisplaySource<RiftModulatorBlockEntity> {

    /**
     * The registry path, {@code aerowarptics:rift_modulator} in full. Baked into every configured
     * link's save data, so changing it orphans every link set up before the change.
     */
    public static final String SOURCE_ID = "rift_modulator";

    RiftModulatorDisplaySource() {
        super(SOURCE_ID, RiftModulatorBlockEntity.class, () -> AWBlockEntities.RIFT_MODULATOR.get(),
                DriveReadout.MODES.toArray(String[]::new));
    }

    @Override
    protected boolean numeric(int mode) {
        return DriveReadout.numeric(mode);
    }

    @Override
    protected MutableComponent line(RiftModulatorBlockEntity modulator, int mode) {
        RiftDriveBlockEntity drive = modulator.linkedDrive();
        return drive == null ? DisplaySource.EMPTY_LINE : DriveReadout.line(drive, mode);
    }
}

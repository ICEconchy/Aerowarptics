package uk.co.iceconchy.aerowarptics.berth;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
import java.util.UUID;

/**
 * A Berth's block: where its record is set down, changed and read off.
 *
 * <p>It does not hold a ship and does not give itself out - {@link Berths} does both, from the record,
 * whether or not this block is loaded. So this keeps a copy of the record, saved with the chunk, and the
 * two are kept in step one way each: a change made here is written to the registry at once
 * ({@link #take}), and whatever happened to the record while the chunk was away is taken up when it loads
 * ({@link #adopt}).
 *
 * <p>A berth stands on the ground. One set down in a ship's plot - built there, or assembled into one
 * despite its refusal to move - keeps no record at all and says so when used.
 */
public class BerthBlockEntity extends SmartBlockEntity {

    private static final String ID = "BerthId";

    @Nullable
    private UUID berthId;
    /** The record as this block has it. Built with a stand-in place until the block knows its level. */
    @Nullable
    private Berth berth;
    private boolean adopted;
    /** Whether a redstone signal was standing here when last looked at, so that a pulse is its rising edge. */
    private boolean powered;

    // What a panel shows that only the server knows, as it was last sent.
    private int maxLength = BerthRules.HARD_MAX_SIZE;
    private int maxWidth = BerthRules.HARD_MAX_SIZE;
    private int maxHeight = BerthRules.HARD_MAX_SIZE;
    private String anchorName = "";
    private String networkName = "";

    public BerthBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.BERTH.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // ------------------------------------------------------------------ what it is

    public UUID berthId() {
        if (berthId == null) {
            berthId = UUID.randomUUID();
        }
        return berthId;
    }

    /** The record as this block has it, or {@code null} for a block that keeps none - see {@link #aboard}. */
    @Nullable
    public Berth berth() {
        return berth;
    }

    /** Whether a player may change this berth: its owner, an operator, or anyone at all for one nobody owns. */
    public boolean mayEdit(Player player) {
        return berth == null || berth.owner() == null || berth.isOwnedBy(player.getUUID()) || player.hasPermissions(2);
    }

    /** The longest, widest and tallest this server lets a slip be - sent, since a client never reads the server's config. */
    public int maxLength() {
        return maxLength;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public int maxHeight() {
        return maxHeight;
    }

    /** The name of the anchor this berth belongs to, as the server last sent it; blank for one not linked. */
    public String anchorName() {
        return anchorName;
    }

    /** The name of the network this berth is kept for, as the server last sent it. */
    public String networkName() {
        return networkName;
    }

    /** Whether this block is in a ship's plot, where a berth does nothing. */
    public boolean aboard() {
        return level instanceof ServerLevel server && Holds.inPlot(server, worldPosition);
    }

    // ------------------------------------------------------------------ set down

    /**
     * Set down by a player: an ordinary slip beyond the block, a ship in it lying bow to the quay, linked to
     * the nearest anchor of theirs in reach.
     *
     * @param front the way the block's face looks, which is back at whoever placed it
     */
    public void placed(@Nullable Player placer, Direction front) {
        if (!(level instanceof ServerLevel server) || aboard()) {
            return;
        }
        Direction beyond = front.getOpposite();
        int out = 1 + BerthRules.DEFAULT_LENGTH / 2;
        UUID owner = placer == null ? null : placer.getUUID();
        WarpAnchor anchor = Berths.nearestAnchor(server, worldPosition, owner);
        berth = Berth.create(berthId(), server.dimension(), worldPosition, anchor == null ? null : anchor.id(), owner,
                placer == null ? "" : placer.getGameProfile().getName(), beyond.getStepX() * out, 0,
                beyond.getStepZ() * out, (int) front.toYRot());
        adopted = true;
        changed();
        if (placer != null) {
            if (anchor == null) {
                AWLang.translate("gui.berth.placed_unlinked", AWConfig.BERTH_LINK_REACH.get()).sendStatus(placer);
            } else {
                AWLang.translate("gui.berth.placed_linked", anchor.displayName()).sendStatus(placer);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && !adopted) {
            adopted = true;
            adopt(server);
        }
    }

    /**
     * Takes up the record as the chunk loads.
     *
     * <p>The record wins over what the block saved: it is the one warps were planned against while the chunk
     * was unloaded, so a reservation made or a ship let go in that time is in it and not in the block. A
     * record somewhere else under this id is another berth's - this one is a copy, a structure block's or a
     * command's - and a copy is given an id of its own, and an empty slip, rather than left fighting the
     * original for one ship.
     */
    private void adopt(ServerLevel server) {
        if (Holds.inPlot(server, worldPosition)) {
            if (berth != null) {
                Trace.log(Trace.BERTH, "berth at {} is in a ship's plot: it keeps no record", Trace.pos(worldPosition));
                BerthRegistry.get(server).remove(berth.id());
                berth = null;
            }
            return;
        }
        if (berth == null) {
            // Set down by something that never said who placed it: a command, a structure. An unlinked slip.
            berth = Berth.create(berthId(), server.dimension(), worldPosition, null, null, "", 0, 0, 0, 0);
        }
        BerthRegistry registry = BerthRegistry.get(server);
        Berth kept = registry.byId(berthId());
        if (kept != null && (!kept.pos().equals(worldPosition) || !kept.dimension().equals(server.dimension()))) {
            Trace.log(Trace.BERTH, "berth at {} shares id {} with the berth at {}: taking a new one",
                    Trace.pos(worldPosition), berthId, Trace.pos(kept.pos()));
            berthId = UUID.randomUUID();
            kept = null;
            berth = Berth.load(settings(berth), berthId, server.dimension(), worldPosition).emptied();
        }
        berth = kept != null ? kept : Berth.load(settings(berth), berthId(), server.dimension(), worldPosition);
        WarpAnchorRegistry anchors = WarpAnchorRegistry.get(server);
        if (berth.anchor() != null && anchors.byId(berth.anchor()) == null) {
            Trace.log(Trace.BERTH, "berth at {} let go of an anchor that has gone", Trace.pos(worldPosition));
            berth = berth.withAnchor(null);
        }
        changed();
    }

    private static CompoundTag settings(Berth of) {
        CompoundTag tag = new CompoundTag();
        of.saveSettings(tag);
        return tag;
    }

    // ------------------------------------------------------------------ changed

    /**
     * Takes a new version of the record - from the panel, or from {@link Berths} as a ship comes and goes -
     * and writes it everywhere it is kept.
     */
    public void take(Berth updated) {
        if (!updated.id().equals(berthId())) {
            return;
        }
        berth = updated;
        changed();
    }

    /** Something about the berth has changed: saved, recorded, shown on its face, and sent. */
    private void changed() {
        setChanged();
        if (!(level instanceof ServerLevel server) || berth == null) {
            return;
        }
        if (adopted) {
            BerthRegistry.get(server).register(berth);
        }
        BlockState state = getBlockState();
        if (state.hasProperty(BerthBlock.STATE) && state.getValue(BerthBlock.STATE) != berth.state()) {
            // Tells the comparators too: the state is what they read.
            level.setBlock(worldPosition, state.setValue(BerthBlock.STATE, berth.state()), Block.UPDATE_ALL);
        }
        sendData();
    }

    /** Broken: whatever it holds is let go, and the record goes with the block. */
    public void broken() {
        if (level instanceof ServerLevel server && berth != null) {
            Berths.removed(server.getServer(), berth);
            berth = null;
        }
    }

    /** A redstone signal has come or gone: its coming casts off whatever the berth holds. */
    public void signalled(boolean high) {
        boolean rose = high && !powered;
        powered = high;
        if (rose && level instanceof ServerLevel server && berth != null && berth.state().holds()) {
            Berths.castOff(server.getServer(), berth, "a redstone pulse");
        }
    }

    // ------------------------------------------------------------------ said

    /** Tells a player what this berth is and what is in it. */
    public void describe(Player player) {
        if (berth == null) {
            AWLang.translate("gui.berth.aboard").sendStatus(player);
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        WarpAnchor anchor = berth.anchor() == null ? null : WarpAnchorRegistry.get(server).byId(berth.anchor());
        if (anchor == null) {
            AWLang.translate("gui.berth.unlinked").sendStatus(player);
            return;
        }
        String key = switch (berth.state()) {
            case EMPTY -> berth.castOff() ? "gui.berth.status.cast_off" : "gui.berth.status.empty";
            case RESERVED -> "gui.berth.status.reserved";
            case DRAWING -> "gui.berth.status.drawing";
            case MOORED -> "gui.berth.status.moored";
        };
        AWLang.translate(key, anchor.displayName(), berth.shipName()).sendStatus(player);
    }

    // ------------------------------------------------------------------ nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID(ID, berthId());
        if (berth != null) {
            berth.saveSettings(tag);
        }
        tag.putBoolean("Powered", powered);
        if (clientPacket && berth != null && level instanceof ServerLevel server) {
            tag.putInt("MaxLength", AWConfig.BERTH_MAX_LENGTH.get());
            tag.putInt("MaxWidth", AWConfig.BERTH_MAX_WIDTH.get());
            tag.putInt("MaxHeight", AWConfig.BERTH_MAX_HEIGHT.get());
            WarpAnchor anchor = berth.anchor() == null ? null : WarpAnchorRegistry.get(server).byId(berth.anchor());
            tag.putString("AnchorName", anchor == null ? "" : anchor.displayName());
            String network = uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry.get(server).nameOf(berth.network());
            tag.putString("NetworkName", network == null ? "" : network);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID(ID)) {
            berthId = tag.getUUID(ID);
        }
        powered = tag.getBoolean("Powered");
        if (clientPacket && tag.contains("MaxLength")) {
            maxLength = tag.getInt("MaxLength");
            maxWidth = tag.getInt("MaxWidth");
            maxHeight = tag.getInt("MaxHeight");
            anchorName = tag.getString("AnchorName");
            networkName = tag.getString("NetworkName");
        }
        // A tag with no slip in it is a block that keeps no record - one in a plot - or one not yet set down.
        if (tag.contains("Length")) {
            // The dimension is the level's once there is one; until then, and on a client, any will do: it is
            // only ever read off the record on the server, after adopt() has put the real one in.
            berth = Berth.load(tag, berthId(), level == null ? Level.OVERWORLD : level.dimension(), worldPosition);
        } else if (clientPacket) {
            berth = null;
        }
    }
}

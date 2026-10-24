package uk.co.iceconchy.aerowarptics.collapse;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.screen.CradleLayout;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundCradlePanelPacket;
import uk.co.iceconchy.aerowarptics.registry.AWMenus;

/**
 * The Collapse Cradle's panel, as a container: its three slots beside the player's inventory.
 *
 * <p>The arms and the cup take anything while the cradle is idle and nothing while it works; the output takes
 * nothing and gives up what the cradle made whenever it is asked. The slots are the cradle's own handler on the
 * server and a copy of it on the client, kept in step by the game's own container sync. Everything else the panel
 * shows - the dial, the gauges, the depots, what Start would do - is a {@link CradlePanel} sent a few times a
 * second to the one player looking, and Start itself is a packet back, checked against this menu being open.
 */
public class CollapseCradleMenu extends AbstractContainerMenu {

    /** Ticks between the panels sent to whoever has it open. */
    private static final int PANEL_INTERVAL = 5;
    /** How far a player may stand from the machine's middle and keep the panel open, squared. */
    private static final double REACH_SQR = 8.0D * 8.0D;

    private static final int CRADLE_SLOTS = CollapseCradleBlockEntity.SLOTS;
    private static final int PLAYER_FIRST = CRADLE_SLOTS;
    private static final int PLAYER_END = PLAYER_FIRST + 36;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final CollapseCradleBlockEntity cradle;
    private int panelTimer;

    /** The server's: the cradle's own slots. */
    public CollapseCradleMenu(int id, Inventory inventory, CollapseCradleBlockEntity cradle) {
        this(id, inventory, cradle.getBlockPos(), cradle.slots(), cradle);
    }

    /** The client's, opened by the server with the cradle's position. */
    public static CollapseCradleMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        CollapseCradleBlockEntity cradle = inventory.player.level().getBlockEntity(pos) instanceof CollapseCradleBlockEntity found
                ? found : null;
        return new CollapseCradleMenu(id, inventory, pos, new CollapseCradleBlockEntity.Slots(), cradle);
    }

    private CollapseCradleMenu(int id, Inventory inventory, BlockPos pos, IItemHandlerModifiable slots,
                               @Nullable CollapseCradleBlockEntity cradle) {
        super(AWMenus.COLLAPSE_CRADLE.get(), id);
        this.pos = pos.immutable();
        this.player = inventory.player;
        this.cradle = cradle;
        addSlot(new CradleSlot(slots, CollapseCradleBlockEntity.CORE, CradleLayout.ARMS_X, CradleLayout.ARMS_Y));
        addSlot(new CradleSlot(slots, CollapseCradleBlockEntity.HEART, CradleLayout.CUP_X, CradleLayout.CUP_Y));
        addSlot(new CradleSlot(slots, CollapseCradleBlockEntity.OUTPUT, CradleLayout.OUT_X, CradleLayout.OUT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, CradleLayout.INVENTORY_X + column * CradleLayout.SLOT,
                        CradleLayout.INVENTORY_Y + row * CradleLayout.SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, CradleLayout.INVENTORY_X + column * CradleLayout.SLOT, CradleLayout.HOTBAR_Y));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    /** The cradle this is the panel of, where the player's level has it. */
    @Nullable
    public CollapseCradleBlockEntity cradle() {
        Level level = player.level();
        return level.getBlockEntity(pos) instanceof CollapseCradleBlockEntity found ? found : cradle;
    }

    private boolean busy() {
        CollapseCradleBlockEntity here = cradle();
        return here != null && here.busy();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof CollapseCradleBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D) <= REACH_SQR;
    }

    /** Sends the panel to the player looking, a few times a second, alongside the game's own slot sync. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server) || !(server.level() instanceof ServerLevel level)) {
            return;
        }
        if (--panelTimer > 0) {
            return;
        }
        panelTimer = PANEL_INTERVAL;
        CollapseCradleBlockEntity here = cradle();
        if (here != null) {
            AWNetwork.sendTo(server, new ClientboundCradlePanelPacket(here.panel(level)));
        }
    }

    /**
     * Shift-click: from the cradle to the inventory; from the inventory to the cup if it goes there, the arms if
     * not. Nothing moves into a cradle at work.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < CRADLE_SLOTS) {
            if (!slot.mayPickup(player) || !moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (busy()) {
                return ItemStack.EMPTY;
            }
            CollapseCradleBlockEntity here = cradle();
            boolean cup = here != null && here.cupFirst(stack);
            int first = cup ? CollapseCradleBlockEntity.HEART : CollapseCradleBlockEntity.CORE;
            int second = cup ? CollapseCradleBlockEntity.CORE : CollapseCradleBlockEntity.HEART;
            if (!moveItemStackTo(stack, first, first + 1, false) && !moveItemStackTo(stack, second, second + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == before.getCount() ? ItemStack.EMPTY : before;
    }

    /** One of the cradle's slots: shut while it works, and the output only ever given up. */
    private final class CradleSlot extends SlotItemHandler {

        private final int index;

        CradleSlot(IItemHandlerModifiable handler, int index, int x, int y) {
            super(handler, index, x, y);
            this.index = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return index != CollapseCradleBlockEntity.OUTPUT && !busy() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return index == CollapseCradleBlockEntity.OUTPUT || !busy();
        }
    }
}

package plugin.talesloader.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import plugin.talesloader.Config;
import plugin.talesloader.block.ChunkLoaderBlockEntity;
import plugin.talesloader.fuel.FuelValues;
import plugin.talesloader.registry.ModBlocks;
import plugin.talesloader.registry.ModMenus;

import javax.annotation.Nullable;

public class ChunkLoaderMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 220;
    /** Height of the brass framed part; Create's player inventory texture is stacked below it. */
    public static final int PANEL_HEIGHT = 144;
    /** Window relative position of Create's 176x108 PLAYER_INVENTORY texture. */
    public static final int INVENTORY_TEXTURE_X = (IMAGE_WIDTH - 176) / 2;
    public static final int INVENTORY_TEXTURE_Y = PANEL_HEIGHT + 2;
    public static final int IMAGE_HEIGHT = INVENTORY_TEXTURE_Y + 108;

    public static final int GRID_X = 10;
    public static final int GRID_Y = 22;
    public static final int CELL_SIZE = 32;
    /** Brass framed field the 3x3 grid sits in. */
    public static final int FIELD_X = GRID_X - 4;
    public static final int FIELD_Y = GRID_Y - 4;
    public static final int FIELD_SIZE = 3 * CELL_SIZE + 8;

    public static final int PANEL_X = 116;
    public static final int PANEL_WIDTH = 96;
    public static final int INDICATOR_Y = 44;
    public static final int BAR_Y = 52;
    public static final int BAR_WIDTH = 72;
    public static final int BAR_HEIGHT = 10;
    public static final int FUEL_SLOT_X = 194;
    public static final int FUEL_SLOT_Y = 48;
    public static final int BUTTON_Y = 118;
    public static final int BUTTON_SIZE = 18;

    // Create's PLAYER_INVENTORY texture carries the slot frames at these offsets.
    private static final int INVENTORY_X = INVENTORY_TEXTURE_X + 8;
    private static final int INVENTORY_Y = INVENTORY_TEXTURE_Y + 18;
    private static final int HOTBAR_Y = INVENTORY_TEXTURE_Y + 76;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final ContainerData data;
    @Nullable
    private final ChunkLoaderBlockEntity loader;
    private final Container fuelInput;
    private boolean convertingFuel;

    /** Client side constructor - state arrives through {@link ContainerData} and payloads. */
    public ChunkLoaderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), null, new SimpleContainerData(ChunkLoaderBlockEntity.DATA_COUNT));
    }

    /** Server side constructor. */
    public ChunkLoaderMenu(int containerId, Inventory inventory, ChunkLoaderBlockEntity loader) {
        this(containerId, inventory, loader.getBlockPos(), loader, loader.getData());
    }

    private ChunkLoaderMenu(int containerId, Inventory inventory, BlockPos pos,
                            @Nullable ChunkLoaderBlockEntity loader, ContainerData data) {
        super(ModMenus.CHUNK_LOADER.get(), containerId);
        this.pos = pos;
        this.loader = loader;
        this.data = data;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.fuelInput = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                ChunkLoaderMenu.this.slotsChanged(this);
            }
        };

        addSlot(new FuelSlot(this, fuelInput, 0, FUEL_SLOT_X, FUEL_SLOT_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(data);
    }

    public BlockPos getPos() {
        return pos;
    }

    /** Reassembles the fuel value from the two 15 bit slots the container data protocol allows. */
    public long getFuel() {
        long low = data.get(ChunkLoaderBlockEntity.DATA_FUEL_LOW) & ChunkLoaderBlockEntity.DATA_CHUNK_MASK;
        long high = data.get(ChunkLoaderBlockEntity.DATA_FUEL_HIGH) & ChunkLoaderBlockEntity.DATA_CHUNK_MASK;
        return (high << ChunkLoaderBlockEntity.DATA_CHUNK_BITS) | low;
    }

    public int getSelectionMask() {
        return data.get(ChunkLoaderBlockEntity.DATA_MASK);
    }

    /** Same formula as the block entity; the SERVER config is synced, so both sides agree. */
    public int getConsumptionRate() {
        return Math.max(1, Config.baseRate() + Config.perChunkRate() * getActiveChunkCount());
    }

    public long getMaxFuel() {
        return Math.max(1L, Config.maxFuel());
    }

    public long getRemainingTicks() {
        return getFuel() / getConsumptionRate();
    }

    public int getActiveChunkCount() {
        return Integer.bitCount(getSelectionMask());
    }

    public boolean isFull() {
        return getFuel() >= getMaxFuel();
    }

    /** A fuel item is only accepted when its full value still fits into the tank. */
    public boolean hasRoomFor(ItemStack stack) {
        long value = FuelValues.valueOf(stack);
        return value > 0L && getFuel() + value <= getMaxFuel();
    }

    // ------------------------------------------------------------------ fuel handling

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container != fuelInput || loader == null || convertingFuel) {
            return;
        }
        convertingFuel = true;
        try {
            ItemStack stack = fuelInput.getItem(0);
            boolean changed = false;
            while (!stack.isEmpty()) {
                long value = FuelValues.valueOf(stack);
                if (value <= 0L || !loader.addFuel(value)) {
                    break;
                }
                stack.shrink(1);
                changed = true;
            }
            if (changed) {
                broadcastChanges();
            }
        } finally {
            convertingFuel = false;
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Client side this also fires when the player opens the map or access sub screen; only the
        // server may hand the leftover fuel item back, otherwise the client shows a ghost item.
        if (!player.level().isClientSide()) {
            clearContainer(player, fuelInput);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CHUNK_LOADER.get())
                && (loader == null || loader.canUse(player));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}

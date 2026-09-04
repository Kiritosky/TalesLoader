package plugin.talesloader.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** GUI-only fuel input. Nothing but a player's hand can ever reach this slot. */
public class FuelSlot extends Slot {
    private final ChunkLoaderMenu menu;

    public FuelSlot(ChunkLoaderMenu menu, Container container, int index, int x, int y) {
        super(container, index, x, y);
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // Rejected outright once the tank cannot swallow another item of this fuel.
        return menu.hasRoomFor(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}

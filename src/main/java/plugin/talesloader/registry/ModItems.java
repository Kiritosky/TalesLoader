package plugin.talesloader.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import plugin.talesloader.Talesloader;
import plugin.talesloader.item.ChunkLoaderItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Talesloader.MODID);

    /** No recipe exists for this item on purpose - it is handed out by admins only. */
    public static final DeferredItem<ChunkLoaderItem> CHUNK_LOADER = ITEMS.register("chunk_loader",
            () -> new ChunkLoaderItem(ModBlocks.CHUNK_LOADER.get(), new Item.Properties().stacksTo(1).fireResistant()));

    private ModItems() {
    }
}

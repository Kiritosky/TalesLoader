package plugin.talesloader.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import plugin.talesloader.Talesloader;
import plugin.talesloader.menu.ChunkLoaderMenu;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Talesloader.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ChunkLoaderMenu>> CHUNK_LOADER =
            MENUS.register("chunk_loader", () -> IMenuTypeExtension.create(ChunkLoaderMenu::new));

    private ModMenus() {
    }
}

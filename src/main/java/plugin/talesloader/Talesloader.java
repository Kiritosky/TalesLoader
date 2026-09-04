package plugin.talesloader;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import plugin.talesloader.registry.ModBlockEntities;
import plugin.talesloader.registry.ModBlocks;
import plugin.talesloader.registry.ModCreativeTabs;
import plugin.talesloader.registry.ModDataComponents;
import plugin.talesloader.registry.ModItems;
import plugin.talesloader.registry.ModMenus;

@Mod(Talesloader.MODID)
public class Talesloader {
    public static final String MODID = "talesloader";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Talesloader(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // SERVER type: lives in the world save and is synced to clients on login, so fuel values
        // and rates can never differ between the two sides.
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}

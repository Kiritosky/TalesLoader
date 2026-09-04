package plugin.talesloader.client;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import plugin.talesloader.Talesloader;
import plugin.talesloader.client.ponder.TalesLoaderPonderPlugin;
import plugin.talesloader.registry.ModItems;
import plugin.talesloader.registry.ModMenus;

@EventBusSubscriber(modid = Talesloader.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    static void registerScreens(final RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHUNK_LOADER.get(), ChunkLoaderScreen::new);
    }

    @SubscribeEvent
    static void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PonderIndex.addPlugin(new TalesLoaderPonderPlugin());
            // Hands the item tooltip to Create, which renders the summary / hold-shift layout its
            // own items use. Reads the "...tooltip.summary/condition1/behaviour1" lang keys.
            TooltipModifier.REGISTRY.register(ModItems.CHUNK_LOADER.get(),
                    new ItemDescription.Modifier(ModItems.CHUNK_LOADER.get(),
                            FontHelper.Palette.STANDARD_CREATE));
        });
    }
}

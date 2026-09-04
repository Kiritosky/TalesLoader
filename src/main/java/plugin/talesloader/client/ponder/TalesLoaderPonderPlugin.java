package plugin.talesloader.client.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import plugin.talesloader.Talesloader;
import plugin.talesloader.registry.ModItems;

/** Hooks the chunk loader scenes into Ponder; registered from {@code ClientSetup}. */
@OnlyIn(Dist.CLIENT)
public class TalesLoaderPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return Talesloader.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(ModItems.CHUNK_LOADER.get());
        helper.forComponents(item)
                .addStoryBoard("chunk_loader", TalesLoaderPonderScenes::chunkLoader)
                .addStoryBoard("chunk_loader", TalesLoaderPonderScenes::fuel);
    }
}

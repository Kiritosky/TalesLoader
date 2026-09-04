package plugin.talesloader.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import plugin.talesloader.Talesloader;
import plugin.talesloader.block.ChunkLoaderBlockEntity;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Talesloader.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChunkLoaderBlockEntity>> CHUNK_LOADER =
            BLOCK_ENTITIES.register("chunk_loader", () -> BlockEntityType.Builder
                    .of(ChunkLoaderBlockEntity::new, ModBlocks.CHUNK_LOADER.get())
                    .build(null));

    private ModBlockEntities() {
    }
}

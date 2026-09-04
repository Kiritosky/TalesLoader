package plugin.talesloader.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import plugin.talesloader.Talesloader;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Talesloader.MODID);

    /** Fuel units carried inside a chunk loader item, so breaking and replacing keeps the tank. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> STORED_FUEL =
            DATA_COMPONENTS.register("stored_fuel", () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());

    private ModDataComponents() {
    }
}

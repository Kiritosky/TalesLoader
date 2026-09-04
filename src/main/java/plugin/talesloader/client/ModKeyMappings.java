package plugin.talesloader.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import plugin.talesloader.Talesloader;

@EventBusSubscriber(modid = Talesloader.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.talesloader";

    /** Opens the chunk map centred on the player. Default: J. */
    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.talesloader.open_map",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    private ModKeyMappings() {
    }

    @SubscribeEvent
    static void register(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
    }
}

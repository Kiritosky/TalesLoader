package plugin.talesloader.event;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import plugin.talesloader.Talesloader;
import plugin.talesloader.block.ChunkLoaderBlock;
import plugin.talesloader.block.ChunkLoaderBlockEntity;

/** Keeps other players from breaking a loader they do not own. */
@EventBusSubscriber(modid = Talesloader.MODID)
public final class ProtectionHandler {
    private ProtectionHandler() {
    }

    @SubscribeEvent
    static void onBreak(final BlockEvent.BreakEvent event) {
        // Cheap block check first: this event fires for every block break on the server.
        if (event.getLevel().isClientSide() || !(event.getState().getBlock() instanceof ChunkLoaderBlock)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof ChunkLoaderBlockEntity loader)) {
            return;
        }
        if (!loader.canBreak(event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(
                    Component.translatable("message.talesloader.break_denied", loader.getOwnerName()), true);
        }
    }
}
